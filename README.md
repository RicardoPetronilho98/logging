
# Log Framework w/ Redaction

A flexible, lightweight `Spring Boot` Java library to **automatically** log HTTP requests and responses.

Also, **mask** and **hide sensitive fields** in logs dynamically, based on configuration.

✅ Built for production-grade systems with `GDPR`, `CCPA`, and security compliance in mind.

---

## ✨ Features

- 🔍 **Enhanced Traceability**:
  - Automatic injection of `transactionId`, `traceId`, `spanId`, and `log point` into every log event.
  - W3C `traceparent` header support: `traceId` and `spanId` align with OpenTelemetry trace/span IDs for seamless log-to-trace correlation in Jaeger, Tempo, and Grafana.
  - Falls back gracefully when `traceparent` is absent (custom headers or generated UUIDs).
  - Capture execution timestamps and durations with nanosecond precision.
- 📈 **Observability-Ready**:
    - Structured logs for seamless integration into ELK, Grafana Loki, AWS CloudWatch, and OpenTelemetry.
    - Designed for correlation of distributed logs across microservices.
    - `spanId` and `traceId` are addressable via JSONPath (e.g., `$.spanId`, `$.traceId`) for masking or hiding.
- 🔒 **Mask sensitive fields** (e.g., email, tokens, phone numbers) dynamically.
- 🛡 **Hide fields completely** from logs when needed.
- 📜 **Dynamic configuration** via `application.yaml` (no code changes required).
- ⚙️ **JSONPath-based targeting** for fine-grained control.
- 🚀 **Silent and resilient** — never crashes your application even if paths are missing.
- 📈 Optimized for **high-performance** applications.

---

## 📦 Installation

### 1. Add the library to your Maven project:

```xml
<dependency>
    <groupId>com.playground</groupId>
    <artifactId>logging</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

> ⚡ **Note:** This library is **not** published in Maven Central. To use it, install it locally first with `mvn clean install`.

### 2. Enable the library:

Make Spring Boot scan the library packages.

```java
@SpringBootApplication(scanBasePackages = "com.playground.logging")
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

}
```

### 3. Set up the configuration:

⚙️ Configuration (example `application.yaml`)

```yaml
logging:
  payload.maxLength: 5000 # 5 KB
  hide:
    fields:
      - "$.headers.transaction-id"
      - "$.headers.trace-id"
      - "$.headers.postman-token"
      - "$.attributes"
      - "$.payload.keys.[*].n"
  mask:
    tag: "<MASKED>"
    fields:
      - "$.headers.authorization"
      - "$.payload.access_token"
```

| Key                         | Description                                                           |
|:----------------------------|:----------------------------------------------------------------------|
| `logging.payload.maxLength` | Max length of the payload written on logs before being truncated.     |
| `logging.mask.tag`          | The string that will replace sensitive field values.                  |
| `logging.mask.fields`       | List of JSONPath expressions pointing to fields to mask.              |
| `logging.hide.fields`       | List of JSONPath expressions pointing to fields to completely remove. |

> 🛡 **Tip:** If `logging.payload.maxLength` is not configured, the payload will be logged in full (unlimited). Configure it to avoid giant payloads in production logs.

---

## 📋 Example

### Example: `REQUEST_IN`

```json
{
  "executedAt": "2025-04-27T15:33:00.1287702",
  "logPoint": "request-in",
  "transactionId": "d8f15d05-2219-48e3-af4f-ee0a06b4bb7d",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "method": "POST",
  "uri": "/oauth2/token",
  "headers": {
    "authorization": "<MASKED>",
    "content-length": ["1869"]
  }
}
```

> The W3C `traceparent` header (`<version>-<traceId>-<parentId>-<traceFlags>`) is parsed in full. `traceId` maps from the trace identifier and `spanId` maps from the parent span identifier. Without `traceparent`, `traceId` falls back to a UUID and `spanId` is omitted.

### Example: `RESPONSE_OUT`

```json
{
  "executedAt": "2025-04-27T15:33:00.3739197",
  "logPoint": "response-out",
  "transactionId": "d8f15d05-2219-48e3-af4f-ee0a06b4bb7d",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "method": "POST",
  "uri": "/oauth2/token",
  "headers": {},
  "payload": {
    "issued_token_type": "urn:ietf:params:oauth:token-type:access_token",
    "token_type": "Bearer",
    "expires_in": 43200,
    "access_token": "<MASKED>",
    "scope": "DUX_ASSET_POST DUX_ASSET_LIST",
    "aud": ["com.playground.*"]
  },
  "status": 200
}
```

✅ Sensitive data is **masked** and unnecessary fields are **hidden**!

---

## 🚀 Future Ideas

- 🧠 **Policy-based masking** (MASK / HIDE / HASH fields dynamically).
- 🔥 **Regex-based masking** (match field names dynamically).
- ✍️ **Audit trail** of masked fields for security logging.

---

## 🌟 Contributing
Contributions are welcome!

Open an issue or submit a pull request to improve the spec compliance, security, or developer experience.

---

## ✉ Contact
Created with ❤ by [Ricardo Petronilho](https://www.linkedin.com/in/ricardo-petronilho-126a511b2)