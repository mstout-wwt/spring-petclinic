# Caching in Spring Petclinic

This application uses a two-layer caching strategy: an **in-process application cache** (Spring Cache + JCache + Caffeine) that avoids redundant database queries for the rarely-changing vet list, and an **HTTP browser cache** for static assets that reduces round-trips to the server. Understanding both layers will help you reason about performance behavior and know where to make changes when adding new cached data.

## Technology Stack

Three dependencies in `pom.xml` work together to power the application cache:

| Dependency | Declared in `pom.xml` | Role |
|---|---|---|
| `spring-boot-starter-cache` | `<dependency>` | Spring's caching abstraction (`@EnableCaching`, `@Cacheable`) |
| `javax.cache:cache-api` | `<dependency>` | JCache (JSR-107) standard API; used by `CacheConfiguration` |
| `com.github.ben-manes.caffeine:caffeine` | `<dependency>` (runtime scope) | In-memory cache implementation, auto-detected by Spring Boot |

> **Note for contributors:** `caffeine` is declared with `<scope>runtime</scope>`. It is never referenced directly in application code — Spring Boot's auto-configuration detects it on the classpath and wires it in automatically as the JCache provider.

## Application Cache Configuration

**File:** `src/main/java/org/springframework/samples/petclinic/system/CacheConfiguration.java`

This is the single class responsible for enabling and configuring the application cache:

- **`@Configuration(proxyBeanMethods = false)`** — declares this as a lightweight Spring configuration class; CGLIB subclassing is disabled since no `@Bean` methods call each other.
- **`@EnableCaching`** — activates Spring's AOP-based caching proxy across the entire application. Without this annotation, `@Cacheable` annotations on repository methods would have no effect.
- **`JCacheManagerCustomizer` bean (`petclinicCacheConfigurationCustomizer`)** — programmatically calls `cm.createCache("vets", cacheConfiguration())` to register the single named cache **`"vets"`** with the JCache `CacheManager` at application startup.
- **`MutableConfiguration.setStatisticsEnabled(true)`** — enables JMX-accessible hit/miss/eviction statistics for the `"vets"` cache (also surfaced via Spring Boot Actuator; see [Observing Cache Behavior](#observing-cache-behavior)).

> **Important caveat for contributors:** The JCache `MutableConfiguration` API does not expose Caffeine-specific settings like size limits or TTL. To configure those, add a `spring.cache.caffeine.spec` property to `application.properties` (e.g., `spring.cache.caffeine.spec=maximumSize=500,expireAfterWrite=10m`). No such spec is currently set, so the `"vets"` cache is **unbounded with no expiry** — cached entries live for the lifetime of the application process.

## What Is Cached

**File:** `src/main/java/org/springframework/samples/petclinic/vet/VetRepository.java`

Two methods on `VetRepository` are annotated with `@Cacheable("vets")`:

1. `Collection<Vet> findAll()` — fetches the full vet list; used by the JSON API endpoint.
2. `Page<Vet> findAll(Pageable pageable)` — fetches a paginated vet list; used by the HTML endpoint.

**How the cache key is derived:**
- `findAll()` uses Spring's default constant key (no arguments → single cache entry).
- `findAll(Pageable pageable)` uses the `Pageable` object (page number + page size) as the key, so each unique page is stored as a separate cache entry.

**Cache population:**
- On the **first call** with a given key, the result is fetched from the database and stored in the `"vets"` cache.
- On **subsequent calls** with the same key, the cached value is returned directly — no database query is made.

**Why only vets?** The vet list is effectively read-only in normal application usage and changes infrequently, making it the ideal candidate for caching. Other repositories (`OwnerRepository`, `PetRepository`, etc.) are not cached because they are frequently written to, which would require careful cache invalidation logic.

## How the Cache Is Used at Runtime

**File:** `src/main/java/org/springframework/samples/petclinic/vet/VetController.java`

Both vet-related controller endpoints call through `VetRepository` and benefit from the cache transparently:

- `GET /vets.html?page={n}` → `VetController.showVetList()` → `vetRepository.findAll(PageRequest.of(page-1, 5))` → **hits `"vets"` cache** (keyed by `Pageable`)
- `GET /vets` (JSON) → `VetController.showResourcesVetList()` → `vetRepository.findAll()` → **hits `"vets"` cache** (default key)

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

The `VetController` has no direct knowledge of caching — the Spring AOP proxy intercepts calls to `VetRepository` methods transparently.

## HTTP Static Resource Cache

This is a separate, browser-level cache that is entirely independent of Spring Cache and Caffeine.

**Config:** `src/main/resources/application.properties`

```properties
spring.web.resources.cache.cachecontrol.max-age=12h
```

This sets a `Cache-Control: max-age=43200` HTTP response header on all static resources served by Spring MVC's `ResourceHttpRequestHandler` — including CSS, JavaScript, and WebJar assets such as Bootstrap and Font Awesome.

Browsers and CDNs will serve these assets from their own local cache for up to 12 hours without making a new request to the server. No application-level code is involved; this is purely an HTTP header instruction to clients.

## Observing Cache Behavior

**JMX:** Because `setStatisticsEnabled(true)` is configured in `CacheConfiguration`, cache hit/miss/eviction counts are exposed as JMX MBeans. Connect with `jconsole` or `VisualVM` and navigate to the `javax.cache` domain to inspect the `"vets"` cache statistics.

**Actuator metrics:** With `management.endpoints.web.exposure.include=*` set in `application.properties`, the `/actuator/metrics` endpoint exposes `cache.*` metrics. For example:

```
GET http://localhost:8080/actuator/metrics/cache.gets
```

Available metric tags include `cache` (cache name) and `result` (hit or miss).

**Dev tip:** During local development with `spring-boot-devtools`, each application restart clears the in-memory cache automatically, since the cache lives in the JVM process. This means you will always see a cache miss on the first request after a restart.

## Adding a New Cache

Follow these steps to cache the results of a new repository or service method:

1. **Register the cache name** — in `CacheConfiguration.java`, add a new call inside the `JCacheManagerCustomizer` lambda:
   ```java
   cm.createCache("your-cache-name", cacheConfiguration());
   ```

2. **Annotate the method** — add `@Cacheable("your-cache-name")` to the repository or service method whose result should be cached.

3. **Consider cache invalidation** — if the underlying data can be written to, add `@CacheEvict("your-cache-name")` (or `@CachePut`) to the methods that modify it, to prevent stale data from being served.

4. **Optionally configure bounds** — add a Caffeine spec to `application.properties` if you need size limits or TTL:
   ```properties
   spring.cache.caffeine.spec=maximumSize=500,expireAfterWrite=10m
   ```
   Note that this spec applies globally to all Caffeine-backed caches in the application.
