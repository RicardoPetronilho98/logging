# CLAUDE.md

## Library Overview

`com.playground:logging` is a Spring Boot library that intercepts every inbound HTTP request/response and emits two structured JSON log entries per call: `REQUEST_IN` (before the handler runs) and `RESPONSE_OUT` (after the handler returns). It attaches a `transactionId` and `traceId` to each pair (read from incoming headers or auto-generated), propagates them into SLF4J MDC, writes them back into the HTTP response headers, and optionally redacts sensitive fields in the JSON payload via JSONPath expressions (mask or hide).

---

## Purpose & Scope

**Does:**
- Intercept all HTTP requests via a servlet filter (`OncePerRequestFilter`)
- Emit `REQUEST_IN` and `RESPONSE_OUT` log entries as serialised JSON via SLF4J
- Buffer request bodies using `CachedBodyRequestWrapper` (eagerly reads body on construction, supports re-reading); buffer response bodies using Spring's `ContentCachingResponseWrapper`
- Capture only `application/json` payloads; treats all other content types as null
- Truncate payloads beyond a configurable character limit
- Redact log fields by JSONPath: mask (replace with a tag string) or hide (delete the key entirely)
- Propagate `transaction-id` / `trace-id` through MDC and HTTP response headers
- Expose `LogContextHolder` (ThreadLocal) so downstream beans can read the current request's identifiers and timing

**Does not:**
- Ship or configure a log appender — consumers bring their own (Logback, Log4j2, etc.)
- Publish to Maven Central — install to local repository with `mvn clean install`
- Support reactive/async stacks — `ThreadLocal` in `LogContextHolder` and `OncePerRequestFilter` are blocking-servlet only
- Auto-configure via Spring Boot's auto-configuration mechanism — requires explicit `scanBasePackages`
- Capture non-JSON request/response bodies (XML, form data, multipart, etc.)

---

## Build & Install

```bash
# Build and install to local Maven repository
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run tests only
mvn clean test
```

Maven coordinates for consumers:

```xml
<dependency>
    <groupId>com.playground</groupId>
    <artifactId>logging</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## Architecture

`RequestInAndResponseOutEnabler` is a `OncePerRequestFilter` at `@Order(1)` — it runs first in the filter chain. On entry it wraps the raw `HttpServletRequest` in `CachedBodyRequestWrapper` (which eagerly drains the body into a `byte[]` so it is available before the handler runs) and the `HttpServletResponse` in Spring's `ContentCachingResponseWrapper` (which buffers the body as it is written). It then builds a `LogContext` via `LoggingMapper`, stores it in `LogContextHolder`, seeds MDC, and emits the `REQUEST_IN` log before calling `chain.doFilter`. After the chain returns, it emits `RESPONSE_OUT`, copies the buffered response body back to the wire, and clears MDC in a `finally` block. Both log entries are serialised to JSON by a library-owned `ObjectMapper` bean, then passed through `LogRedactService`, which applies JSONPath mask/hide rules before the string is handed to SLF4J.

```
Inbound HTTP request
        │
        ▼
CachedBodyRequestWrapper (request) / ContentCachingResponseWrapper (response)
        │
        ▼
RequestInAndResponseOutEnabler  (@Order(1))
    ├── build LogContext (IDs, timers, requestInData)
    ├── store in LogContextHolder (ThreadLocal)
    ├── put IDs in MDC
    ├── emit REQUEST_IN (JSON → redact → log.info)
    │
    ▼  chain.doFilter(wrappedReq, wrappedRes)
    │
    ▼  (downstream handlers execute here)
    │
    ├── write transaction-id / trace-id to response headers
    ├── emit RESPONSE_OUT (JSON → redact → log.info)
    ├── copyBodyToResponse()
    └── MDC.clear()  [finally]
        │
        ▼
Outbound HTTP response (headers include transaction-id, trace-id)
```

---

## Package Structure

| Package | Contents |
|---|---|
| `com.playground.logging.config` | `LoggingProperties` — `@ConfigurationProperties(prefix = "logging")` binding; `JsonMapperConfig` — defines the library's `ObjectMapper` bean |
| `com.playground.logging.domain` | `LogContext` — per-request mutable state (IDs, timers, request data, attributes); `LogContextHolder` — ThreadLocal accessor; `LogEntry` — immutable log payload sent to SLF4J; `LoggingHttpHeaders` — enum of header names with extraction helpers; `TraceContext` — immutable record representing all four fields of a parsed W3C `traceparent` header |
| `com.playground.logging.interceptor` | `RequestInAndResponseOutEnabler` — the `OncePerRequestFilter` that drives the entire log emission cycle; `CachedBodyRequestWrapper` — eagerly buffers the request body for re-readable access |
| `com.playground.logging.mapper` | `LoggingMapper` — MapStruct interface; maps from servlet wrappers → `LogContext` and `LogContext` → `LogEntry` for both log points |
| `com.playground.logging.redaction` | `LogRedactService` — applies mask and hide rules to the serialised JSON string via Jayway JsonPath |

---

## Key Classes

- **`RequestInAndResponseOutEnabler`** — the only entry point; orchestrates the entire filter lifecycle. `@Order(1)` means it wraps all other filters; removing or changing that order will alter which filters run inside vs. outside the log boundary.
- **`CachedBodyRequestWrapper`** — wraps `HttpServletRequest`; drains the body into a `byte[]` in its constructor and returns a fresh `ByteArrayInputStream` on every `getInputStream()` call. Exposes `getContentAsByteArray()` so `LoggingMapper` can read the body before `chain.doFilter()`.
- **`LoggingMapper`** — MapStruct interface with Spring component model; all field mappings for both log points live here. The `@Named("getRequestHttpHeaders")` method is misnamed — its method signature is `getResponseHttpHeaders(CachedBodyRequestWrapper req)` (request, not response); it handles request headers despite the name.
- **`LogContextHolder`** — a bare `ThreadLocal` wrapper with no null guard on `get()`. Callers outside the filter (e.g., service beans) must handle a `null` return if accessed outside a request thread.
- **`LogRedactService`** — receives the already-serialised JSON string and mutates it with `DocumentContext.set()` (mask) or `DocumentContext.delete()` (hide). Missing JSONPath paths are silently swallowed at `TRACE` level, not thrown.
- **`LoggingProperties`** — registered as a `@Configuration` bean (not just `@ConfigurationProperties`), so it participates in component scan and its absence from the scan path silently produces zero-value fields rather than a startup error.
- **`TraceContext`** — immutable record with four fields mirroring the W3C `traceparent` spec: `version` (2-hex, always `"00"`), `traceId` (32-hex, distributed trace identifier), `parentId` (16-hex, the calling service's active span-id), and `traceFlags` (2-hex, sampling flags; LSB `1` = sampled). The static factory `TraceContext.from(HttpServletRequest)` reads the `traceparent` header via `LoggingHttpHeaders.TRACEPARENT`, validates the four-segment format, and returns `null` if the header is absent or malformed. `version` and `traceFlags` are captured in the record but not currently propagated to `LogContext` or `LogEntry` — they are available for future use (e.g., sampling-aware log filtering).
- **`LoggingHttpHeaders`** — enum of HTTP header name constants with three static extraction helpers. `getTransactionId` reads the custom `transaction-id` header, falling back to a UUID. `getTraceId` calls `TraceContext.from(req)` and returns `traceId` if present, then falls back to the custom `trace-id` header, then to a UUID. `getSpanId` calls `TraceContext.from(req)` and returns `parentId` if present, or `null` — no UUID fallback, since a generated UUID would be semantically misleading as a span-id. Parsing logic lives entirely in `TraceContext`; `LoggingHttpHeaders` only handles fallback and UUID generation.
- **`LogEntry.LogPoint`** — defines four log points (`REQUEST_IN`, `REQUEST_OUT`, `RESPONSE_IN`, `RESPONSE_OUT`) but the library only emits `REQUEST_IN` and `RESPONSE_OUT`. The other two are placeholders.

---

## How It Works

1. **Interception** — `RequestInAndResponseOutEnabler.doFilterInternal()` fires for every HTTP request. It records `System.nanoTime()` as `startTime` and wraps the servlet objects in content-caching wrappers.

2. **Request body buffering** — `CachedBodyRequestWrapper` drains the original request's `InputStream` into a `byte[]` in its constructor. `getContentAsByteArray()` returns that array immediately, so `LoggingMapper.toRequestInData()` captures the full body before `chain.doFilter()` is called. `getInputStream()` returns a fresh `ByteArrayInputStream` backed by the same array on every call, so downstream handlers can still read the body normally via `@RequestBody` or manual stream access.

3. **ID generation** — The `traceparent` header is parsed once by `TraceContext.from(req)` into a four-field record (`version`, `traceId`, `parentId`, `traceFlags`). From that, three identifiers are extracted and stored in `LogContext.Identifiers`:
   - `transactionId`: read from the custom `transaction-id` header; generated as a random UUID if absent. Represents the end-to-end business transaction (may span multiple independent HTTP calls, e.g. saga flows).
   - `traceId`: sourced from `TraceContext.traceId()` when `traceparent` is present (32-char hex); falls back to the custom `trace-id` header, then to a random UUID. Aligns with OTel trace-id when Micrometer Tracing is active in the microservice.
   - `spanId`: sourced from `TraceContext.parentId()` when `traceparent` is present (16-char hex); `null` when `traceparent` is absent. Represents the calling service's active span, enabling log-to-trace linkage in Jaeger/Tempo/Grafana. Note the name mapping: the record field is `parentId` (spec terminology); the log field is `spanId` (consumer-friendly name).
   `TraceContext.version` and `TraceContext.traceFlags` are captured but not currently propagated to `LogContext` or `LogEntry`. `transactionId` and `traceId` are written to MDC and echoed as HTTP response headers. `span-id` is written to MDC only when non-null; it is not echoed as a response header.

4. **Redaction** — `LogRedactService.redact(json)` parses the serialised `LogEntry` JSON with Jayway JsonPath. For each path in `logging.mask.fields` it calls `DocumentContext.set(path, tag)`, replacing the value with the configured tag string. For each path in `logging.hide.fields` it calls `DocumentContext.delete(path)`, removing the key entirely. Missing paths are caught and logged at `TRACE`, never thrown. Redaction runs on the complete JSON string (including headers, URI, etc.), not just the payload field.

5. **REQUEST_IN emission** — `mapper.toRequestIn(context)` builds a `LogEntry` with `logPoint = REQUEST_IN`, timing fields computed at that instant, and the already-captured request payload (populated by `CachedBodyRequestWrapper` at construction time). The entry is serialised, redacted, and passed to `log.info("{}", redactedJson)`.

6. **Response capture and RESPONSE_OUT** — `ContentCachingResponseWrapper` buffers the response body as the downstream handler writes it. After `chain.doFilter` returns, `mapper.toResponseOut(context, res, properties)` reads `res.getContentAsByteArray()` (now populated), the HTTP status, and response headers. The entry is serialised, redacted, and logged. `wrappedRes.copyBodyToResponse()` then flushes the buffered bytes to the actual response stream.

---

## Configuration Reference

| Property key | Type | Default | Description |
|---|---|---|---|
| `logging.payload.maxLength` | `Integer` | none (payload not truncated) | Maximum character length of a captured JSON payload. If the payload exceeds this value, it is truncated and `...[TRUNCATED]` is appended. |
| `logging.hide.fields` | `List<String>` | none (no fields hidden) | JSONPath expressions for fields to delete from the log entry. Applied after serialisation to the full JSON string. |
| `logging.mask.tag` | `String` | none (required when `mask.fields` is set) | Replacement string written over masked field values (e.g., `"****"`). |
| `logging.mask.fields` | `List<String>` | none (no fields masked) | JSONPath expressions for fields whose values are replaced with `mask.tag`. Applied after serialisation to the full JSON string. |

---

## Integration Guide

### 1. Install to local Maven repository

```bash
cd /path/to/logging
mvn clean install
```

### 2. Add the Maven dependency

```xml
<dependency>
    <groupId>com.playground</groupId>
    <artifactId>logging</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 3. Extend component scan

The library does not use Spring Boot auto-configuration. All beans (`@Component`, `@Configuration`, `@Service`, MapStruct `@Mapper`) live under `com.playground.logging` and are invisible to the consumer's default scan. Add the library's base package to `scanBasePackages`:

```java
@SpringBootApplication(scanBasePackages = {
    "com.your.service",       // your own package
    "com.playground.logging"  // logging library
})
public class YourApplication { ... }
```

Without this, no logging filter or beans are registered and nothing is logged.

### 4. Minimal `application.yaml`

The library starts with all redaction disabled and no truncation. No properties are strictly required:

```yaml
logging:
  payload:
    maxLength: 2000
```

### 5. Masking and hiding fields

```yaml
logging:
  payload:
    maxLength: 2000
  mask:
    tag: "****"
    fields:
      - "$.headers.authorization[0]"   # mask Authorization header value
      - "$.payload.password"           # mask password field in JSON body
  hide:
    fields:
      - "$.payload.internalToken"      # remove field entirely from log
```

JSONPath expressions are evaluated against the full serialised `LogEntry` JSON, so top-level keys (e.g., `$.transactionId`, `$.uri`) are also addressable.

---

## Patterns

- **Dual log-point design** — `REQUEST_IN` is emitted before the handler runs (captures intent: method, URI, headers, IDs, timestamp) and `RESPONSE_OUT` is emitted after (captures outcome: status, response headers, response body, elapsed time). This makes it possible to detect requests that never produce a response (process crash, timeout) by correlating unpaired `REQUEST_IN` entries.
- **JSONPath over custom field annotations** — redaction rules are externalised to `application.yaml` as JSONPath strings, applied to the final JSON string, with no coupling to domain model classes. Consumers can add or change redaction rules without recompiling the library.
- **Silent path miss** — if a JSONPath expression matches nothing, it is swallowed at `TRACE` level. This is intentional: partial configuration (e.g., a path that only exists in some endpoints) never causes a log failure.
- **`@JsonRawValue` on `LogEntry.payload`** — the captured payload string is already JSON. `@JsonRawValue` inlines it as a JSON object inside the log entry rather than double-encoding it as a JSON string.
- **Payload truncation appends `...[TRUNCATED]`** — the truncation marker is appended after cutting at `maxLength` characters. If the cut falls inside a JSON string value, the result will be malformed JSON. `LogRedactService` wraps JSONPath parsing in a try/catch, so redaction fails gracefully and returns the (truncated but unredacted) log.

---

## Gotchas

- **`scanBasePackages` is invasive.** Consumers must name every package they want scanned, including their own. If the consumer's application already uses a narrow `scanBasePackages`, they must not forget to include their own packages alongside `com.playground.logging`. A future improvement would be to provide a `spring.factories` / `AutoConfiguration.imports` entry so the library self-registers without requiring `scanBasePackages`.

- **`externalExecutionElapsedTimeNanos` is always 0.** `LoggingMapper.toTimers()` hard-codes `externalElapsedTimeNanos = 0L`, and there is no mechanism to update it. `totalExecutionElapsedTimeNanos` is therefore always equal to `internalExecutionElapsedTimeNanos`. The `externalElapsedTimeNanos` field in `LogContext.Timers` is scaffolded for tracking downstream call time but unimplemented.

- **`LogEntry.LogPoint` has four values; only two are used.** `REQUEST_OUT` and `RESPONSE_IN` are never emitted. They are present as placeholders; do not rely on them appearing in logs.

- **`LogContextHolder.get()` can return null.** If code calls `LogContextHolder.get()` outside of a request thread (e.g., in a background task or `@Async` method), it returns `null` — there is no guard. Always null-check before use.

- **Truncation can produce invalid JSON.** `truncatePayload` cuts at a character boundary, not a JSON token boundary. A payload truncated mid-string produces malformed JSON in the `payload` field. `LogRedactService` catches the resulting parse error and returns the unredacted log, so redaction is silently skipped for truncated payloads.

- **Library publishes an `ObjectMapper` bean named `jsonMapper`.** If the consumer's application context already defines an `ObjectMapper` bean named `jsonMapper`, there will be a conflict. The library bean is configured with `NON_NULL` serialisation and ISO-8601 dates — consuming services that rely on different `ObjectMapper` settings must be aware of this bean. The bean is built via `JsonMapper.builder()` (Jackson 3.x builder API); `ObjectMapper` is immutable after construction so the old mutable `mapper.set…()` pattern is no longer used.

- **Jackson 3.x package rename.** Spring Boot 4.x ships Jackson 3.x, which moved its core packages from `com.fasterxml.jackson` to `tools.jackson`. Annotations (`@JsonRawValue`, `@JsonInclude`) remain under `com.fasterxml.jackson.annotation` via a compatibility shim (`jackson-annotations` 2.21+). `JavaTimeModule` and `ParameterNamesModule` are built into Jackson 3.x core and do not require explicit registration. `JsonProcessingException` was renamed to `tools.jackson.core.JacksonException`.

- **Java 23 required.** The library compiles with `--release 23`. Consumers must run on JDK ≥ 23.

- **`@Named("getRequestHttpHeaders")` method misname in `LoggingMapper`.** The method annotated `@Named("getRequestHttpHeaders")` is declared as `default Map<String, List<String>> getResponseHttpHeaders(ContentCachingRequestWrapper req)`. The method name says "Response" but handles request headers. This is a naming inconsistency; the behaviour is correct.

- **`traceId` format changes when `traceparent` is present.** Without `traceparent`, `traceId` is a UUID string (e.g., `f0653cd1-8659-4c85-8a22-b2ce0333863e`). With `traceparent`, it is a 32-char lowercase hex string (e.g., `4bf92f3577b34da6a3ce929d0e0e4736`). JSONPath redaction rules targeting `$.traceId` still work in both cases; only the value format differs. Dashboards or alerting rules that pattern-match on `traceId` values must account for both formats.

- **`spanId` is null when `traceparent` is absent.** If the inbound request carries no `traceparent` header (non-OTel callers, direct Postman calls, etc.), `spanId` is `null` and is omitted from the log JSON (the `ObjectMapper` is configured with `NON_NULL`). Log appender patterns referencing `%X{span-id}` will produce an empty string in that case and must be treated as optional.

---

## Documentation Maintenance

| When you change… | Update in CLAUDE.md |
|---|---|
| A new `application.yaml` property key is added or renamed | `## Configuration Reference` |
| A package is added, renamed, or removed | `## Package Structure` |
| A class is added, renamed, or removed | `## Key Classes` |
| The request/response interception mechanism changes | `## How It Works`, `## Architecture` |
| The JSONPath redaction logic changes (mask/hide behaviour) | `## How It Works`, `## Patterns`, `## Gotchas` |
| The ID generation strategy changes (`transactionId`, `traceId`) | `## How It Works`, `## Key Classes` |
| The integration steps change (e.g. auto-configuration is added) | `## Integration Guide`, `## Gotchas` |
| Java, Spring Boot, or library dependency versions change | `## Tech Stack` |
| Build or install commands change | `## Build & Install` |
| `LogEntry.LogPoint` values are added or wired up | `## Key Classes`, `## Gotchas`, `## How It Works` |
| Payload truncation logic or the truncation marker changes | `## How It Works`, `## Patterns`, `## Gotchas` |
| The `ObjectMapper` bean configuration changes | `## Gotchas` |
| `LogContextHolder` access semantics change (e.g. null-safety added) | `## Key Classes`, `## Gotchas` |
| Timer fields (`externalElapsedTimeNanos`) are implemented | `## How It Works`, `## Key Classes`, `## Gotchas` |

Never close a task leaving CLAUDE.md inconsistent with the code.

---

## Tech Stack

| Component | Version |
|---|---|
| Java | 23 |
| Spring Boot | 4.0.6 |
| Lombok | 1.18.46 |
| MapStruct | 1.6.3 |
| Jayway JsonPath | 3.0.0 |
| maven-compiler-plugin | 3.15.0 |
