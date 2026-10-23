# SW Reactive Audio Converter

A reactive REST API built with **Spring Boot WebFlux** for converting audio files between formats. Supports real-time progress streaming via Server-Sent Events (SSE).

## Tech Stack

| Technology | Version |
|---|---|
| Java | 17 |
| Spring Boot | 4.1.0 |
| Spring WebFlux | 7.x |
| Project Reactor | via Spring Boot |
| JAVE (Java Audio Video Encoder) | 3.5.0 |
| springdoc-openapi WebFlux UI | 3.0.3 |
| Lombok | via Spring Boot |

## Supported Formats

`mp3` · `wav` · `ogg` · `aac` · `flac` · `wma`

## Project Structure

```
src/main/java/com/work/converter/
├── SwfReactiveConverterApplication.java
├── config/
│   └── OpenApiConfig.java
├── controller/
│   └── AudioConverterController.java
├── exception/
│   ├── AudioConversionException.java
│   ├── ErrorResponse.java
│   └── handler/
│       └── GlobalExceptionHandler.java
└── service/
    ├── AudioConverter.java
    └── TempFileStore.java
```

## API Endpoints

### `POST /api/audio/convert`
Converts an audio file and returns it as a direct binary download.

**Request** — `multipart/form-data`

| Parameter | Type | Description |
|---|---|---|
| `file` | File | Audio file to convert |
| `format` | String | Target format (`mp3`, `wav`, `ogg`, `aac`, `flac`, `wma`) |

**Response** — `200 OK` with `application/octet-stream` body (converted file).

---

### `POST /api/audio/convert/stream`
Converts an audio file and streams real-time progress via SSE.

**Request** — `multipart/form-data`

| Parameter | Type | Description |
|---|---|---|
| `file` | File | Audio file to convert |
| `format` | String | Target format |

**Response** — `text/event-stream`

```
event: progress
data: {"progress": 25}

event: progress
data: {"progress": 75}

event: complete
data: {"filename": "audio.wav", "downloadToken": "550e8400-e29b-41d4-a716-446655440000"}
```

Once the `complete` event is received, use the `downloadToken` to retrieve the file.

---

### `GET /api/audio/download/{token}`
Downloads the converted file using the token from the SSE `complete` event.

> Tokens are **single-use** — the file is removed from the store after the first download.

**Response** — `200 OK` with `application/octet-stream` body.

---

## Error Responses

All errors follow this structure:

```json
{
  "errorCode": "CONVERSION_FAILED",
  "message": "Descriptive error message",
  "timestamp": "2025-07-03T10:15:30"
}
```

| HTTP Status | Error Code | Cause |
|---|---|---|
| `400` | `INVALID_FORMAT` | Unsupported target format |
| `422` | `CONVERSION_FAILED` | Encoder failed to convert the file |
| `500` | `FILE_PROCESSING_ERROR` | Error reading/writing temp files |
| `500` | `INTERNAL_ERROR` | Unexpected server error |

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+

### Run

```bash
mvn spring-boot:run
```

### Build

```bash
mvn clean package
java -jar target/swf-reactive-converter-0.0.1-SNAPSHOT.jar
```

### API Documentation

Once running, Swagger UI is available at:

```
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON spec:

```
http://localhost:8080/v3/api-docs
```

---

## Usage Examples

### Convert directly (curl)

```bash
curl -X POST http://localhost:8080/api/audio/convert \
  -F "file=@song.mp3" \
  -F "format=wav" \
  --output song.wav
```

### Stream progress (JavaScript)

```javascript
const formData = new FormData();
formData.append('file', audioFile);

const response = await fetch('/api/audio/convert/stream?format=wav', {
  method: 'POST',
  body: formData,
});

const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;

  const text = decoder.decode(value);
  const lines = text.split('\n').filter(l => l.startsWith('data:'));

  for (const line of lines) {
    const data = JSON.parse(line.replace('data:', '').trim());

    if (data.downloadToken) {
      // Conversion complete — download the file
      window.location.href = `/api/audio/download/${data.downloadToken}`;
    } else {
      console.log(`Progress: ${data.progress}%`);
    }
  }
}
```

---

## Architecture Notes

- **Non-blocking I/O**: All endpoints return `Mono` or `Flux`. Blocking operations (JAVE encoder, file I/O) are offloaded to `Schedulers.boundedElastic()`.
- **Secure temp files**: Converted files are stored under `~/.audio-converter/` (user home) to avoid publicly writable directories (SonarQube S5443).
- **Single-use download tokens**: The `TempFileStore` uses a `ConcurrentHashMap` where tokens are consumed on first retrieval, preventing repeated access to temp files.

## Running Tests

```bash
mvn test
```

Tests use `WebTestClient.bindToController()` with Mockito — no application context required.
