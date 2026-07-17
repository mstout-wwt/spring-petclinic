# Caching in Spring Petclinic

This application uses a **two-layer caching strategy** aimed at reducing unnecessary work on both the server and the client:

1. **In-process application cache** (Spring Cache + JCache + Caffeine) — avoids redundant database queries for the rarely-changing vet list.
2. **HTTP browser cache** — reduces static-asset round-trips by instructing browsers and CDNs to cache CSS, JS, and image files locally.

This document explains how each layer works, which files are involved, and how to extend or observe the cache — all the information a new contributor needs to work confidently with caching in this project.

---

## Technology Stack

Three dependencies in `pom.xml` work together to power the application cache:

| Dependency | `pom.xml` coordinates | Role |
|---|---|---|
| `spring-boot-starter-cache` | `org.springframework.boot:spring-boot-starter-cache` | Enables Spring's caching abstraction (`@EnableCaching`, `@Cacheable`, etc.) |
| `javax.cache:cache-api` | `javax.cache:cache-api` | JCache (JSR-107) standard API; used by `CacheConfiguration` to register named caches |
| `caffeine` | `com.github.ben-manes.caffeine:caffeine` *(scope: `runtime`)* | The actual in-memory cache implementation, auto-detected by Spring Boot |

> **Note:** `caffeine` is declared with `<scope>runtime</scope>`, meaning it is never referenced directly in application code. Spring Boot's auto-configuration detects it on the classpath at startup and wires it in automatically as the JCache provider.

---

## Application Cache Configuration

**File:** `src/main/java/org/springframework/samples/petclinic/system/CacheConfiguration.java`

```java
@Configuration(proxyBeanMethods = false)
@EnableCaching
class CacheConfiguration {

    @Bean
    public JCacheManagerCustomizer petclinicCacheConfigurationCustomizer() {
        return cm -> cm.createCache("vets", cacheConfiguration());
    }

    private javax.cache.configuration.Configuration<Object, Object> cacheConfiguration() {
        return new MutableConfiguration<>().setStatisticsEnabled(true);
    }
}
```

Here is what each part does:

| Element | Purpose |
|---|---|
| `@Configuration(proxyBeanMethods = false)` | Lightweight config class — Spring does not create a CGLIB subclass, so `@Bean` methods are called directly rather than being proxied. |
| `@EnableCaching` | Activates Spring's AOP-based caching proxy across the entire application. Without this, `@Cacheable` annotations are silently ignored. |
| `JCacheManagerCustomizer` bean | Runs at startup and calls `cm.createCache("vets", ...)` to register the single named cache **`"vets"`** with the JCache `CacheManager`. |
| `MutableConfiguration.setStatisticsEnabled(true)` | Enables JMX-accessible hit/miss/eviction statistics for the `"vets"` cache (see [Observing Cache Behavior](#observing-cache-behavior)). |

### Important: Size Limits and TTL

The JCache `MutableConfiguration` API exposes only a minimal set of options — it does **not** support size limits or time-to-live (TTL). To configure those, you must use Caffeine-specific properties in `application.properties`:

```properties
# Example: cap the cache at 500 entries and expire entries 10 minutes after they are written
spring.cache.caffeine.spec=maximumSize=500,expireAfterWrite=10m
```

No such spec is currently set in this project, so the `"vets"` cache is **unbounded with no expiry** — entries live in memory for the lifetime of the application process.

---

## What Is Cached

**File:** `src/main/java/org/springframework/samples/petclinic/vet/VetRepository.java`

Two methods on `VetRepository` are annotated with `@Cacheable("vets")`:

```java
@Transactional(readOnly = true)
@Cacheable("vets")
Collection<Vet> findAll() throws DataAccessException;

@Transactional(readOnly = true)
@Cacheable("vets")
Page<Vet> findAll(Pageable pageable) throws DataAccessException;
```

### Cache Key Derivation

Spring derives the cache key from the method's arguments:

| Method | Cache key |
|---|---|
| `findAll()` | A default constant key (no arguments → Spring uses `SimpleKey.EMPTY`) |
| `findAll(Pageable pageable)` | The `Pageable` object itself (page number + page size), so each unique page is cached as a separate entry |

### Cache Population

- **First call:** The result is fetched from the database and stored in the `"vets"` cache under the derived key.
- **Subsequent calls with the same key:** The cached value is returned immediately — no database query is made.

### Why Only Vets?

The vet list is effectively **read-only** in normal application usage and changes infrequently, making it the ideal candidate for caching. Other repositories (`OwnerRepository`, `PetRepository`, etc.) are not cached because they are written to frequently; caching them would require careful cache-invalidation logic to avoid serving stale data.

---

## How the Cache Is Used at Runtime

**File:** `src/main/java/org/springframework/samples/petclinic/vet/VetController.java`

Both public endpoints in `VetController` call through `VetRepository` and therefore benefit from the cache transparently:

| HTTP Request | Controller Method | Repository Call | Cache Key |
|---|---|---|---|
| `GET /vets.html?page={n}` | `showVetList(page, model)` | `vetRepository.findAll(PageRequest.of(page-1, 5))` | `Pageable` (page + size) |
| `GET /vets` (JSON) | `showResourcesVetList()` | `vetRepository.findAll()` | `SimpleKey.EMPTY` |

### Request Flow

```
HTTP Request
    │
    ▼
VetController
    │
    ▼
Spring Cache Proxy  (@Cacheable intercepts the call)
    │
    ├─ Cache HIT  ──► return cached result immediately  (no DB call)
    │
    └─ Cache MISS ──► VetRepository (Spring Data JPA → DB)
                            │
                            ▼
                      store result in "vets" cache
                            │
                            ▼
                      return result to caller
```

The cache proxy is transparent — `VetController` has no knowledge of caching; it simply calls `VetRepository` methods as normal.

---

## HTTP Static Resource Cache

This is a completely separate, browser-level cache that has nothing to do with Spring Cache or Caffeine.

**Config:** `src/main/resources/application.properties`

```properties
# Maximum time static resources should be cached
spring.web.resources.cache.cachecontrol.max-age=12h
```

This instructs Spring MVC's `ResourceHttpRequestHandler` to add a `Cache-Control: max-age=43200` HTTP response header to every static resource it serves (CSS, JavaScript, WebJar assets such as Bootstrap and Font Awesome).

- **Effect:** Browsers and CDNs will serve these assets from their own local cache for up to **12 hours** without making a new request to the server.
- **Independence:** This is handled entirely at the HTTP layer. Clearing the in-process Caffeine cache has no effect on browser caches, and vice versa.

---

## Observing Cache Behavior

### JMX

Because `setStatisticsEnabled(true)` is set in `CacheConfiguration`, cache statistics are exposed as JMX MBeans. To inspect them:

1. Start the application.
2. Open **JConsole** (`jconsole`) or **VisualVM** and connect to the running JVM.
3. Navigate to the **MBeans** tab and look under `javax.cache` → `CacheStatistics`.

You will find counters for cache hits, misses, puts, evictions, and more.

### Spring Boot Actuator

Because `application.properties` sets `management.endpoints.web.exposure.include=*`, all Actuator endpoints are exposed. The `metrics` endpoint surfaces cache statistics:

```
# List all available cache metrics
GET http://localhost:8080/actuator/metrics

# Cache hit/miss counts for the "vets" cache
GET http://localhost:8080/actuator/metrics/cache.gets?tag=name:vets
GET http://localhost:8080/actuator/metrics/cache.puts?tag=name:vets
```

> **Warning:** Exposing all Actuator endpoints is intentional for development and testing in this sample app. Do **not** do this in a production deployment.

### Dev Tip: `spring-boot-devtools`

When running locally with `spring-boot-devtools` on the classpath, application restarts triggered by code changes will clear the in-memory Caffeine cache automatically, because the cache lives in the JVM heap and is discarded with each restart.

---

## Adding a New Cache

Follow these steps to cache the result of a new repository or service method:

1. **Register the cache name** in `CacheConfiguration.java` by adding a new `createCache` call inside the `JCacheManagerCustomizer` lambda:

   ```java
   return cm -> {
       cm.createCache("vets", cacheConfiguration());
       cm.createCache("your-cache-name", cacheConfiguration());  // ← add this
   };
   ```

2. **Annotate the read method** with `@Cacheable`:

   ```java
   @Cacheable("your-cache-name")
   SomeResult findSomething(SomeKey key);
   ```

3. **Handle cache invalidation** — if the underlying data can be written, annotate the corresponding write method with `@CacheEvict` to prevent stale data from being served:

   ```java
   @CacheEvict(value = "your-cache-name", allEntries = true)
   void save(SomeEntity entity);
   ```

4. **Tune if needed** — add a Caffeine spec to `application.properties` to set a size limit or TTL for the new cache:

   ```properties
   spring.cache.caffeine.spec=maximumSize=500,expireAfterWrite=10m
   ```

   Note that this property applies a **single spec to all Caffeine-backed caches**. If you need per-cache tuning, consider switching to a `CaffeineSpec`-per-cache approach using a custom `CacheManagerCustomizer<CaffeineCacheManager>`.

5. **Test it** — use `@MockitoBean` on the repository (as done in `VetControllerTests.java`) to unit-test the controller layer in isolation without worrying about the cache. Write a separate integration test to verify cache population and eviction behavior end-to-end.

---

## Key Files Reference

| File | Purpose |
|---|---|
| `pom.xml` | Declares `spring-boot-starter-cache`, `javax.cache:cache-api`, and `caffeine` (runtime) dependencies |
| `src/main/java/.../system/CacheConfiguration.java` | `@EnableCaching`; registers the `"vets"` JCache cache with statistics enabled |
| `src/main/java/.../vet/VetRepository.java` | `@Cacheable("vets")` on both `findAll()` overloads |
| `src/main/java/.../vet/VetController.java` | Calls `VetRepository`; benefits from the cache transparently |
| `src/main/resources/application.properties` | `spring.web.resources.cache.cachecontrol.max-age=12h` for static assets; `management.endpoints.web.exposure.include=*` for Actuator |
| `src/test/java/.../vet/VetControllerTests.java` | Uses `@MockitoBean VetRepository` to test the controller in isolation from the cache |
