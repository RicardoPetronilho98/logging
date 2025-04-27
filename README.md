
# Log Framework w/ Redaction

A flexible, lightweight `Spring Boot` Java library to **automatically** log HTTP requests and responses.

Also, **mask** and **hide sensitive fields** in logs dynamically, based on configuration.

✅ Built for production-grade systems with `GDPR`, `CCPA`, and security compliance in mind.

---

## ✨ Features

- 🔍 **Enhanced Traceability**:
  - Automatic injection of `transactionId`, `traceId`, and `log point` into every log event.
  - Capture execution timestamps and durations with nanosecond precision.
- 📈 **Observability-Ready**:
    - Structured logs for seamless integration into ELK, Grafana Loki, AWS CloudWatch, and OpenTelemetry.
    - Designed for correlation of distributed logs across microservices.
- 🔒 **Mask sensitive fields** (e.g., email, tokens, phone numbers) dynamically.
- 🛡 **Hide fields completely** from logs when needed.
- 📜 **Dynamic configuration** via `application.yaml` (no code changes required).
- ⚙️ **JSONPath-based targeting** for fine-grained control.
- 🚀 **Silent and resilient** — never crashes your application even if paths are missing.
- 📈 Optimized for **high-performance** applications.

---

## 📦 Installation

Add the library to your Maven project:

```xml
<dependency>
    <groupId>com.playground</groupId>
    <artifactId>logging</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## ⚙️ Configuration (example `application.yaml`)

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

| Key                   | Description                                                           |
|:----------------------|:----------------------------------------------------------------------|
| `logging.mask.tag`    | The string that will replace sensitive field values.                  |
| `logging.mask.fields` | List of JSONPath expressions pointing to fields to mask.              |
| `logging.hide.fields` | List of JSONPath expressions pointing to fields to completely remove. |

---

## 📋 Example

### Example: `REQUEST_IN`

```json
{
  "executedAt": "2025-04-27T15:33:00.1287702",
  "logPoint": "REQUEST_IN",
  "transactionId": "d8f15d05-2219-48e3-af4f-ee0a06b4bb7d",
  "traceId": "f0653cd1-8659-4c85-8a22-b2ce0333863e",
  "method": "POST",
  "uri": "/oauth2/token",
  "headers": {
    "authorization": "<MASKED>",
    "content-length": ["1869"]
  }
}
```

### Example: `RESPONSE_OUT`

```json
{
  "executedAt": "2025-04-27T15:33:00.3739197",
  "logPoint": "RESPONSE_OUT",
  "transactionId": "d8f15d05-2219-48e3-af4f-ee0a06b4bb7d",
  "traceId": "f0653cd1-8659-4c85-8a22-b2ce0333863e",
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
- 🛰 **Integration with OpenTelemetry tracing**.
- ✍️ **Audit trail** of masked fields for security logging.

---

## 🌟 Contributing
Contributions are welcome!

Open an issue or submit a pull request to improve the spec compliance, security, or developer experience.

---

## ✉ Contact
Created with ❤ by [Ricardo Petronilho](https://www.linkedin.com/in/ricardo-petronilho-126a511b2)