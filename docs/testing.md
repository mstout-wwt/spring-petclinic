# Testing Guide for Spring PetClinic

This document explains how to run the test suite for the Spring PetClinic project.

## Prerequisites

- Java 17 or later (full JDK, not a JRE)
- Maven 3.6+ or Gradle 7.0+ (both are included in the project)
- Docker (optional, for running integration tests with databases)

## Running Tests with Maven

### Run All Tests

To run the complete test suite:

```bash
./mvnw test
```

### Run Specific Test Class

To run a single test class:

```bash
./mvnw test -Dtest=OwnerControllerTests
```

### Run Specific Test Method

To run a specific test method within a class:

```bash
./mvnw test -Dtest=OwnerControllerTests#testInitCreationForm
```

### Run Tests with Coverage Report

To run tests and generate a JaCoCo code coverage report:

```bash
./mvnw test jacoco:report
```

The coverage report will be generated at `target/site/jacoco/index.html`.

### Run Tests with Specific Profile

To run tests against a specific database:

```bash
# H2 (default, in-memory)
./mvnw test

# MySQL (requires Docker or local MySQL instance)
./mvnw test -Dspring.profiles.active=mysql

# PostgreSQL (requires Docker or local PostgreSQL instance)
./mvnw test -Dspring.profiles.active=postgres
```

## Running Tests with Gradle

### Run All Tests

To run the complete test suite:

```bash
./gradlew test
```

### Run Specific Test Class

To run a single test class:

```bash
./gradlew test --tests OwnerControllerTests
```

### Run Specific Test Method

To run a specific test method:

```bash
./gradlew test --tests OwnerControllerTests.testInitCreationForm
```

### Run Tests with Specific Profile

To run tests against a specific database:

```bash
# H2 (default, in-memory)
./gradlew test

# MySQL
./gradlew test -Dspring.profiles.active=mysql

# PostgreSQL
./gradlew test -Dspring.profiles.active=postgres
```

## Test Structure

The test suite is organized as follows:

- **Unit Tests**: Located in `src/test/java/org/springframework/samples/petclinic/`
  - `service/ClinicServiceTests.java` - Service layer tests
  - `owner/OwnerControllerTests.java` - Owner controller tests
  - `owner/PetControllerTests.java` - Pet controller tests
  - `owner/VisitControllerTests.java` - Visit controller tests
  - `vet/VetControllerTests.java` - Veterinarian controller tests
  - `system/WelcomeControllerTests.java` - Welcome page tests
  - `system/CrashControllerTests.java` - Error handling tests
  - And more...

- **Integration Tests**: 
  - `PetClinicIntegrationTests.java` - Full integration tests with H2 database
  - `MySqlIntegrationTests.java` - Integration tests with MySQL (uses Testcontainers)
  - `PostgresIntegrationTests.java` - Integration tests with PostgreSQL (uses Docker Compose)

## Running Integration Tests

### H2 Integration Tests (Default)

The default integration tests use an in-memory H2 database and can be run with:

```bash
./mvnw test -Dtest=PetClinicIntegrationTests
```

or with Gradle:

```bash
./gradlew test --tests PetClinicIntegrationTests
```

### MySQL Integration Tests

MySQL integration tests use Testcontainers to automatically start a MySQL container:

```bash
./mvnw test -Dtest=MySqlIntegrationTests
```

or with Gradle:

```bash
./gradlew test --tests MySqlIntegrationTests
```

**Note**: Requires Docker to be running.

### PostgreSQL Integration Tests

PostgreSQL integration tests use Docker Compose to start a PostgreSQL container:

```bash
./mvnw test -Dtest=PostgresIntegrationTests
```

or with Gradle:

```bash
./gradlew test --tests PostgresIntegrationTests
```

**Note**: Requires Docker and Docker Compose to be running.

## Running Tests in Your IDE

### IntelliJ IDEA

1. Open the project in IntelliJ IDEA
2. Right-click on a test class or method in the editor
3. Select "Run" or "Run with Coverage"
4. To run all tests, right-click on the `src/test/java` folder and select "Run Tests"

### Eclipse / Spring Tools Suite

1. Open the project in Eclipse or STS
2. Right-click on a test class or method
3. Select "Run As" → "JUnit Test"
4. To run all tests, right-click on the project and select "Run As" → "Maven test"

### VS Code

1. Install the "Test Runner for Java" extension
2. Open a test file
3. Click on "Run Test" or "Debug Test" above the test method
4. Or use the Testing view in the sidebar

## Test Frameworks and Libraries

The project uses the following testing frameworks:

- **JUnit 5** - Test framework
- **Spring Test** - Spring Boot testing utilities
- **Mockito** - Mocking framework
- **AssertJ** - Fluent assertions
- **Testcontainers** - Docker-based integration testing
- **Spring Boot Docker Compose** - Docker Compose support for tests

## Continuous Integration

Tests are automatically run on every push and pull request via GitHub Actions. The CI pipeline runs tests with both Maven and Gradle against the default H2 database.

## Troubleshooting

### Tests Fail with "Port Already in Use"

If you see errors about ports being in use (e.g., 3306 for MySQL, 5432 for PostgreSQL), ensure:
- No other instances of these services are running
- Docker containers from previous test runs are stopped: `docker ps -a` and `docker rm <container_id>`

### Tests Fail with Docker Connection Issues

If Testcontainers or Docker Compose tests fail:
- Ensure Docker daemon is running: `docker ps`
- Check Docker permissions: `docker run hello-world`
- On Linux, you may need to add your user to the docker group

### Tests Timeout

If tests timeout, you can increase the timeout:
- For Maven: `./mvnw test -DargLine="-Dtestcontainers.ryuk.container.privileged=true"`
- For Gradle: `./gradlew test --args="-Dtestcontainers.ryuk.container.privileged=true"`

### Out of Memory Errors

If you encounter out-of-memory errors, increase the heap size:
- For Maven: `./mvnw test -DargLine="-Xmx1024m"`
- For Gradle: `./gradlew test --args="-Xmx1024m"`

## Best Practices

1. **Run tests frequently** - Run tests before committing changes
2. **Use meaningful test names** - Test method names should clearly describe what is being tested
3. **Keep tests isolated** - Each test should be independent and not rely on other tests
4. **Use appropriate assertions** - Use AssertJ for more readable assertions
5. **Mock external dependencies** - Use Mockito to mock services and repositories
6. **Test edge cases** - Include tests for error conditions and boundary cases
7. **Maintain test coverage** - Aim for high code coverage, especially for critical paths

## Additional Resources

- [Spring Boot Testing Documentation](https://spring.io/guides/gs/testing-web/)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Testcontainers Documentation](https://www.testcontainers.org/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
