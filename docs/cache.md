# Caching Mechanism

## Overview

Spring PetClinic implements a caching layer to improve application performance by reducing database queries for frequently accessed data. The caching mechanism is built on Spring's caching abstraction with **Caffeine** as the underlying cache provider.

## Architecture

### Cache Provider: Caffeine

The application uses **Caffeine**, a high-performance, in-memory cache library that is:
- **Native image compatible** (important for GraalVM native builds)
- **Thread-safe** with automatic expiration policies
- **Efficient** with low memory overhead
- **Recommended** by Spring for modern applications

Caffeine is declared as a dependency in `pom.xml`:
```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

### Spring Cache Abstraction

The caching is implemented using Spring's declarative caching framework:
- **`@EnableCaching`** annotation activates caching support
- **`@Cacheable`** annotation marks methods whose results should be cached
- **JCache API (JSR-107)** provides a standard caching interface

## Configuration

### CacheConfiguration Class

Located at: `src/main/java/org/springframework/samples/petclinic/system/CacheConfiguration.java`

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

**Key Points:**
- `@EnableCaching` activates Spring's caching infrastructure
- `JCacheManagerCustomizer` bean creates and configures the "vets" cache
- `setStatisticsEnabled(true)` enables cache statistics accessible via JMX for monitoring

### Cache Names

Currently, the application defines one cache:
- **"vets"** - Caches veterinarian data and related information

## Usage

### Cached Methods

Caching is applied to the `VetRepository` interface:

**File:** `src/main/java/org/springframework/samples/petclinic/vet/VetRepository.java`

```java
public interface VetRepository extends Repository<Vet, Integer> {

    @Transactional(readOnly = true)
    @Cacheable("vets")
    Collection<Vet> findAll() throws DataAccessException;

    @Transactional(readOnly = true)
    @Cacheable("vets")
    Page<Vet> findAll(Pageable pageable) throws DataAccessException;
}
```

**Behavior:**
- First call to `findAll()` queries the database and caches the result
- Subsequent calls return the cached result without database access
- Both methods cache results under the same "vets" cache name
- Results are cached based on method parameters (pageable parameter affects cache key)



## How Caching Works

### Cache Key Generation

Spring automatically generates cache keys based on:
1. **Method parameters** - Different parameter values create different cache entries
2. **Target class** - The class containing the cached method
3. **Method name** - The name of the cached method

For example:
- `findAll()` with no parameters → single cache entry
- `findAll(Pageable)` with page 0 → different entry than page 1

### Cache Hit vs Cache Miss

**Cache Hit (Cached Result Used):**
```
Request → Spring checks cache → Entry found → Return cached result (no DB query)
```

**Cache Miss (Database Query):**
```
Request → Spring checks cache → Entry not found → Query database → Cache result → Return result
```

## Performance Benefits

### Reduced Database Load

- **Veterinarian List**: The vet list is relatively static and frequently accessed
- **Pagination Support**: Each page is cached separately, reducing repeated queries
- **Read-Only Transactions**: Marked with `@Transactional(readOnly = true)` for optimal performance

### Typical Scenario

1. User requests `/vets.html` (page 1)
   - Database query executed
   - Result cached under "vets" cache
   - Response time: ~50-100ms (includes DB query)

2. Another user requests `/vets.html` (page 1)
   - Cache hit
   - No database query
   - Response time: ~5-10ms (cache lookup only)

3. User requests `/vets.html?page=2`
   - Different cache entry (different pageable parameter)
   - Database query executed
   - Result cached
   - Response time: ~50-100ms

## Monitoring Cache Performance

### JMX Metrics

Cache statistics are enabled and accessible via JMX:
- **Cache Hits** - Number of successful cache lookups
- **Cache Misses** - Number of failed cache lookups
- **Hit Rate** - Percentage of successful cache hits

### Actuator Endpoint

Access cache metrics via Spring Boot Actuator:
```
GET /actuator/caches
```

Returns information about all configured caches.

## Cache Invalidation

### Current Implementation

The current implementation does **not** explicitly invalidate the "vets" cache. This means:
- Cache entries persist for the application lifetime
- Suitable for relatively static data like veterinarian information
- If vets data changes, the application must be restarted to reflect changes

### Future Enhancement

To support cache invalidation, use `@CacheEvict`:
```java
@CacheEvict(value = "vets", allEntries = true)
public void updateVet(Vet vet) {
    // Update logic
}
```



## Dependencies

### Maven Dependencies

**Cache Support:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>

<dependency>
    <groupId>javax.cache</groupId>
    <artifactId>cache-api</artifactId>
</dependency>

<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

**Testing:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache-test</artifactId>
    <scope>test</scope>
</dependency>
```

## Configuration Properties

### Application Properties

Currently, no explicit cache configuration properties are set in `application.properties`. Caffeine uses default settings:
- **Maximum Size**: Unbounded (grows until memory pressure)
- **Expiration**: No automatic expiration
- **Refresh**: No automatic refresh

### Custom Configuration

To customize Caffeine behavior, add properties to `application.properties`:
```properties
# Example: Set maximum cache size
spring.cache.caffeine.spec=maximumSize=500

# Example: Set expiration after write
spring.cache.caffeine.spec=expireAfterWrite=10m
```

Or configure programmatically in `CacheConfiguration`:
```java
private javax.cache.configuration.Configuration<Object, Object> cacheConfiguration() {
    return new MutableConfiguration<>()
        .setStatisticsEnabled(true)
        .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MINUTES, 10)
        ));
}
```

## Native Image Compatibility

### Why Caffeine?

Caffeine was chosen over EhCache for native image builds because:
- **Reflection-free**: Doesn't require reflection configuration
- **GraalVM compatible**: Works seamlessly with native image compilation
- **No XML parsing**: Avoids complex configuration file parsing

### Native Build Hints

The application includes native hints in `RuntimeHintsRegistrar` to ensure caching works in native images:
- Cache configuration classes are registered
- JCache API classes are registered
- Caffeine implementation classes are registered



## Testing Cache Behavior

### Cache Testing Utilities

Spring provides testing utilities via `spring-boot-starter-cache-test`:

```java
@SpringBootTest
@EnableCaching
class VetRepositoryTests {

    @Autowired
    private VetRepository vetRepository;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void testCacheHit() {
        // First call - cache miss
        Collection<Vet> vets1 = vetRepository.findAll();
        
        // Second call - cache hit
        Collection<Vet> vets2 = vetRepository.findAll();
        
        // Both should return same data
        assertThat(vets1).isEqualTo(vets2);
        
        // Check cache statistics
        Cache cache = cacheManager.getCache("vets");
        CacheStatistics stats = cache.getCacheStatistics();
        assertThat(stats.getCacheHits()).isGreaterThan(0);
    }

    @Test
    void testCacheEviction() {
        // Clear cache
        cacheManager.getCache("vets").clear();
        
        // Verify cache is empty
        assertThat(cacheManager.getCache("vets").get("key")).isNull();
    }
}
```

## Best Practices

### When to Cache

✅ **Good candidates for caching:**
- Read-only data (veterinarians, pet types, specialties)
- Frequently accessed data
- Data that doesn't change often
- Data that is expensive to compute/retrieve

❌ **Poor candidates for caching:**
- User-specific data
- Frequently changing data
- Data with strict consistency requirements
- Small datasets with fast retrieval

### Cache Naming

- Use descriptive names: "vets", "petTypes", "owners"
- Avoid generic names: "cache1", "data"
- Group related data under same cache name

### Monitoring

- Enable statistics for production monitoring
- Use JMX to track cache hit rates
- Monitor memory usage of cache
- Set appropriate cache size limits

## Troubleshooting

### Cache Not Working

**Symptom:** Data changes not reflected immediately

**Solution:** 
- Cache is working as designed for static data
- Restart application to clear cache
- Implement `@CacheEvict` for dynamic updates

### High Memory Usage

**Symptom:** Application memory grows over time

**Solution:**
- Set `maximumSize` in Caffeine configuration
- Set `expireAfterWrite` or `expireAfterAccess`
- Monitor cache statistics via JMX

### Cache Disabled in Tests

**Symptom:** `@Cacheable` not working in tests

**Solution:**
- Add `@EnableCaching` to test class
- Use `@MockBean` for `CacheManager` if needed
- Clear cache between tests: `cacheManager.getCache("vets").clear()`

## References

- [Spring Caching Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#cache)
- [Caffeine Cache Documentation](https://github.com/ben-manes/caffeine/wiki)
- [JSR-107 JCache API](https://jcp.org/en/jsr/detail?id=107)
- [Spring Boot Cache Starter](https://docs.spring.io/spring-boot/docs/current/reference/html/io.html#io.caching)
