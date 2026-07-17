# Observability in Spring PetClinic

> **TL;DR** — Observability is the ability to understand what your application is doing *from the outside*, using the data it emits. For a Spring Boot application like PetClinic, that data comes in three complementary forms: **logs**, **metrics**, and **traces** — often called the *three pillars of observability*.

---

## Table of Contents

1. [Why Observability Matters](#why-observability-matters)
2. [The Three Pillars](#the-three-pillars)
   - [Logging](#1-logging)
   - [Metrics](#2-metrics)
   - [Distributed Tracing](#3-distributed-tracing)
3. [How Spring Boot Supports Observability](#how-spring-boot-supports-observability)
4. [PetClinic at a Glance](#petclinic-at-a-glance)
5. [Quick-Start Checklist](#quick-start-checklist)
6. [Further Reading](#further-reading)

---

## Why Observability Matters

Modern applications — even a "simple" sample app like PetClinic — run in environments where failures are inevitable: network blips, slow database queries, memory pressure, misconfigured caches, and more. Without observability you are left guessing:

- *Why did that request take 3 seconds?*
- *Is the H2 / MySQL / PostgreSQL connection pool exhausted?*
- *Which endpoint is throwing `NullPointerException` in production?*
- *Did the last deployment change the error rate?*

Observability turns those questions into **answerable** ones by giving you continuous, structured insight into the runtime behaviour of your application — without needing to reproduce the problem locally or redeploy with extra debug flags.

---

## The Three Pillars

### 1. Logging

**What it is:** A timestamped, human-readable (and machine-parseable) record of discrete events that happened inside the application.

**Why it matters for PetClinic:**
- Captures business events: a new owner was registered, a pet visit was saved, a validation error was returned.
- Records infrastructure events: datasource initialisation, Hibernate DDL, cache hits/misses.
- Provides the *narrative* that helps you reconstruct exactly what happened before an error.

**Spring Boot defaults:**
Spring Boot auto-configures [Logback](https://logback.qos.ch/) (via SLF4J) out of the box. PetClinic's `application.properties` already sets a baseline level:

```properties
# application.properties
logging.level.org.springframework=INFO
# Uncomment for more detail during development:
# logging.level.org.springframework.web=DEBUG
```

**Best practices:**
| Do | Avoid |
|----|-------|
| Use structured JSON logging in production (e.g. `logstash-logback-encoder`) | Logging sensitive data (passwords, PII) |
| Include a correlation/trace ID in every log line | Excessive `DEBUG`/`TRACE` in production |
| Log at the right level (`INFO` for normal flow, `WARN` for recoverable issues, `ERROR` for failures) | Swallowing exceptions silently |

---

### 2. Metrics

**What it is:** Numeric measurements collected at regular intervals — counters, gauges, histograms, and timers — that describe the *health and performance* of the application over time.

**Why it matters for PetClinic:**
- Track HTTP request rates, error rates, and latency percentiles (p50, p95, p99) per endpoint.
- Monitor JVM internals: heap usage, GC pause duration, thread counts.
- Observe datasource pool utilisation (active connections, pending acquisitions).
- Measure cache effectiveness (hit ratio for the `vets` cache).

**Spring Boot defaults:**
`spring-boot-starter-actuator` (already a dependency in PetClinic) bundles [Micrometer](https://micrometer.io/), a vendor-neutral metrics facade. With the current `application.properties` setting:

```properties
management.endpoints.web.exposure.include=*
```

…the following endpoints are immediately available at runtime:

| Endpoint | What you get |
|----------|-------------|
| `GET /actuator/health` | Liveness / readiness status |
| `GET /actuator/metrics` | List of all registered metric names |
| `GET /actuator/metrics/http.server.requests` | HTTP request statistics |
| `GET /actuator/metrics/jvm.memory.used` | JVM heap & non-heap usage |
| `GET /actuator/metrics/hikaricp.connections` | Connection pool stats |

> ⚠️ **Production note:** Exposing all actuator endpoints publicly is fine for local development but should be restricted (or secured behind Spring Security) before going to production.

To ship metrics to an external system, add the appropriate Micrometer registry dependency — for example:

```xml
<!-- Prometheus (pull-based) -->
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

This exposes a `/actuator/prometheus` scrape endpoint that [Prometheus](https://prometheus.io/) + [Grafana](https://grafana.com/) can consume.

---

### 3. Distributed Tracing

**What it is:** A way to follow a single request as it travels through one or more services, recording the time spent in each operation (a *span*) and linking them together into a *trace*.

**Why it matters for PetClinic:**
Even though PetClinic is a monolith today, tracing is still valuable:
- Pinpoints *which* layer (controller → service → repository → database) is responsible for a slow request.
- Provides a visual waterfall of operations, making it easy to spot N+1 query problems or slow Hibernate fetches.
- Prepares the codebase for a future microservices split — traces carry across service boundaries automatically.

**Spring Boot defaults:**
Spring Boot 3+ (and 4) ships with [Micrometer Tracing](https://micrometer.io/docs/tracing) as the tracing facade. To activate it, add a bridge and an exporter:

```xml
<!-- OpenTelemetry bridge (recommended) -->
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<!-- Export spans to Zipkin -->
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-zipkin</artifactId>
</dependency>
```

Then configure the endpoint in `application.properties`:

```properties
management.tracing.sampling.probability=1.0   # sample every request (dev only)
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
```

Micrometer Tracing automatically instruments Spring MVC, `RestClient`, JDBC, and more — no manual span creation needed for common cases.

---

## How Spring Boot Supports Observability

Spring Boot's observability story is built on three complementary libraries that work together seamlessly:

```
┌─────────────────────────────────────────────────────────────┐
│                     Your Application                        │
│                                                             │
│  Controllers  ──►  Services  ──►  Repositories  ──►  DB    │
└──────────────────────────┬──────────────────────────────────┘
                           │  auto-instrumented by
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
     SLF4J / Logback   Micrometer      Micrometer Tracing
     (Logging)         (Metrics)       (Distributed Tracing)
          │                │                │
          ▼                ▼                ▼
    Log aggregator    Prometheus /     Zipkin / Jaeger /
    (ELK, Loki, …)   Datadog / …      Tempo / OTLP / …
```

The key insight is that all three pillars are **correlated** when you enable tracing: the current `traceId` and `spanId` are automatically injected into every log line via MDC, so you can jump from a metric anomaly → a log line → the full trace in one click.

---

## PetClinic at a Glance

Here is how the three pillars map to concrete PetClinic scenarios:

| Scenario | Pillar | Signal to look for |
|----------|--------|--------------------|
| A user reports the Vets page is slow | **Metrics** | `http.server.requests{uri=/vets.html}` p99 latency spike |
| The same slow request — which query is the culprit? | **Tracing** | Trace waterfall shows a long `SELECT` span in the `VetRepository` |
| An `IllegalArgumentException` is thrown during owner search | **Logging** | `ERROR` log line with stack trace and the offending input |
| The app is running out of DB connections under load | **Metrics** | `hikaricp.connections.pending` counter rising |
| A deployment introduced a regression | **Metrics** | Error rate on `POST /owners/{id}/edit` increases after deploy |
| Correlate a user complaint to a specific request | **Tracing + Logging** | Find `traceId` in logs, open full trace in Zipkin/Jaeger |

---

## Quick-Start Checklist

Use this checklist to go from zero to a fully observable PetClinic instance:

- [ ] **Actuator is on the classpath** — `spring-boot-starter-actuator` ✅ (already in `pom.xml`)
- [ ] **Health endpoint works** — `curl http://localhost:8080/actuator/health`
- [ ] **Metrics endpoint works** — `curl http://localhost:8080/actuator/metrics`
- [ ] **Add a Micrometer registry** — e.g. `micrometer-registry-prometheus` for Prometheus scraping
- [ ] **Add Micrometer Tracing** — `micrometer-tracing-bridge-otel` + an exporter dependency
- [ ] **Run Zipkin locally** — `docker run -p 9411:9411 openzipkin/zipkin` and set `management.zipkin.tracing.endpoint`
- [ ] **Enable structured logging** — add `logstash-logback-encoder` and a JSON appender in `logback-spring.xml`
- [ ] **Restrict actuator exposure in production** — use `management.endpoints.web.exposure.include` + Spring Security

---

## Further Reading

- [Spring Boot Actuator reference](https://docs.spring.io/spring-boot/reference/actuator/index.html)
- [Micrometer documentation](https://micrometer.io/docs)
- [Micrometer Tracing documentation](https://micrometer.io/docs/tracing)
- [Spring Boot Observability guide](https://spring.io/guides/gs/tanzu-observability/)
- [OpenTelemetry for Java](https://opentelemetry.io/docs/languages/java/)
- [Zipkin quickstart](https://zipkin.io/pages/quickstart.html)
- [Grafana + Prometheus + Loki + Tempo (LGTM) stack](https://grafana.com/oss/)
