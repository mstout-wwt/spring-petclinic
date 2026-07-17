# Caching in Spring Petclinic

This application uses a two-layer caching strategy: an **in-process application cache** (Spring Cache + JCache + Caffeine) that avoids redundant database queries for the rarely-changing vet list, and an **HTTP browser cache** for static assets that reduces round-trips to the server. Understanding both layers is essential before modifying any data-access or front-end code.

## Technology Stack

Three dependencies in `pom.xml` work together to power the application cache:

| Dependency | Role |
|---|---|
| `spring-boot-starter-cache` | Enables Spring's caching abstraction (`@EnableCaching`, `@Cacheable`, etc.) |
| `javax.cache:cache-api` | Provides the JCache (JSR-107) standard API used by `CacheConfiguration` |
| `com.github.ben-manes.caffeine:caffeine` | The actual in-memory cache implementation, auto-detected by Spring Boot at runtime |

> **Note:** `caffeine` is declared with `<scope>runtime</scope>`. It is never referenced directly in application code — Spring Boot's auto-configuration wires it in automatically based on its presence on the classpath.

## Application Cache Configuration

**File:** `src/main/java/org/springframework/samples/petclinic/system/CacheConfiguration.java`

This class is the single source of truth for the application cache setup:

- `@Configuration(proxyBeanMethods = false)` — declares this as a lightweight configuration class; Spring does not create a CGLIB subclass, so bean method calls are not intercepted.
- `@EnableCaching` — activates Spring's AOP-based caching proxy across the entire application. Without this annotation, `@Cacheable` annotations on repository methods would have no effect.
- `JCacheManagerCustomizer` bean (`petclinicCacheConfigurationCustomizer`) — runs at startup and calls `cm.createCache("vets", cacheConfiguration())` to programmatically register the single named cache **`"vets"`** with the JCache `CacheManager`.
- `MutableConfiguration.setStatisticsEnabled(true)` — enables JMX-accessible hit/miss/eviction statistics for the `"vets"` cache (also surfaced via Spring Boot Actuator; see [Observing Cache Behavior](#observing-cache-behavior)).

> **Important for contributors:** The JCache `MutableConfiguration` API does not expose Caffeine-specific settings such as maximum size or TTL. To configure those, add a `spring.cache.caffeine.spec` property to `application.properties` (e.g., `spring.cache.caffeine.spec=maximumSize=500,expireAfterWrite=10m`). No such spec is currently set, so the cache is **unbounded with no expiry** — the vet list, once loaded, stays in memory for the lifetime of the JVM process.

## What Is Cached

**File:** `src/main/java/org/springframework/samples/petclinic/vet/VetRepository.java`

Two methods are annotated with `@Cacheable("vets")`:

1. `Collection<Vet> findAll()` — fetches the full vet list; used by the JSON API endpoint.
2. `Page<Vet> findAll(Pageable pageable)` — fetches a paginated vet list; used by the HTML endpoint.

**How the cache key works:**
- `findAll()` has no arguments, so Spring uses a default constant key. All calls share a single cached value.
- `findAll(Pageable pageable)` uses the `Pageable` object (which encodes page number and page size) as the cache key. Each unique page request is cached separately.

**Cache population:**
- On the **first call** for a given key, the result is fetched from the database and stored in the `"vets"` cache.
- On **subsequent calls** with the same key, the cached value is returned immediately and the database is not queried.

**Why only vets?** The vet list is effectively read-only in normal application usage and changes infrequently, making it the ideal candidate for caching. Other repositories (`OwnerRepository`, `PetRepository`, etc.) are not cached because they are written to frequently, which would require careful cache invalidation logic.

## How the Cache Is Used at Runtime

**File:** `src/main/java/org/springframework/samples/petclinic/vet/VetController.java`

Both vet-related HTTP endpoints call through `VetRepository` and benefit from the cache transparently:

- `GET /vets.html?page={n}` → `VetController.showVetList()` → `vetRepository.findAll(PageRequest.of(page-1, 5))` → **hits `"vets"` cache** (keyed by `Pageable`)
- `GET /vets` (JSON) → `VetController.showResourcesVetList()` → `vetRepository.findAll()` → **hits `"vets"` cache** (default key)

The controller code itself has no awareness of caching — the Spring AOP proxy intercepts calls to `VetRepository` before they reach the actual JPA implementation.

```
HTTP Request
    │
    ▼
VetController
    │
    ▼
Spring Cache Proxy (@Cacheable)
    │
    ├─ Cache HIT  ──► return cached result (no DB call)
    │
    └─ Cache MISS ──► VetRepository (JPA → DB) ──► store in "vets" cache ──► return result
```

## HTTP Static Resource Cache

This is a separate, browser-level cache that is entirely independent of Spring Cache and Caffeine.

**Config:** `src/main/resources/application.properties`

```properties
spring.web.resources.cache.cachecontrol.max-age=12h
```

This instructs Spring MVC's `ResourceHttpRequestHandler` to add a `Cache-Control: max-age=43200` HTTP response header to all static resources (CSS, JavaScript, and WebJar assets such as Bootstrap and Font Awesome). Browsers and CDNs will serve these assets from their local cache for up to 12 hours without making a new request to the server.

No application code changes are needed to modify this behavior — adjust the value in `application.properties` only.

## Observing Cache Behavior

**JMX:** Because `setStatisticsEnabled(true)` is set in `CacheConfiguration`, cache statistics are exposed as JMX MBeans. Connect with `jconsole` or `VisualVM` and navigate to the `javax.cache` domain to see hit count, miss count, and eviction count for the `"vets"` cache.

**Actuator metrics:** With `management.endpoints.web.exposure.include=*` set in `application.properties`, Spring Boot Actuator exposes `cache.*` metrics at the `/actuator/metrics` endpoint. Examples:

```
GET http://localhost:8080/actuator/metrics/cache.gets
GET http://localhost:8080/actuator/metrics/cache.puts
```

Filter by `cache` tag to scope results to the `"vets"` cache.

**Dev tip:** When running locally with `spring-boot-devtools`, each application restart clears the in-memory cache automatically, so you will always see a cache miss on the first request after a restart.

## Adding a New Cache

Follow these steps to cache the result of a new repository or service method:

1. **Register the cache name** — open `src/main/java/org/springframework/samples/petclinic/system/CacheConfiguration.java` and add a new `cm.createCache("your-cache-name", cacheConfiguration())` call inside the `JCacheManagerCustomizer` lambda, alongside the existing `"vets"` registration.

2. **Annotate the method** — add `@Cacheable("your-cache-name")` to the repository or service method whose result should be cached.
