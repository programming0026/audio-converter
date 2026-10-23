# SW Reactive Audio Converter — Diagrams

All diagrams are written in [Mermaid](https://mermaid.js.org/) and render natively on GitHub, GitLab and in most IDE Markdown previews.

- [1. UML Class Diagram](#1-uml-class-diagram)
- [2. Component / Package Diagram](#2-component--package-diagram)
- [3. Sequence — Conversion with SSE progress](#3-sequence--conversion-with-sse-progress)
- [4. Sequence — Direct conversion](#4-sequence--direct-conversion)
- [5. Sequence — Download by token](#5-sequence--download-by-token)
- [6. Activity — Conversion pipeline](#6-activity--conversion-pipeline)
- [7. State — Download token lifecycle](#7-state--download-token-lifecycle)
- [8. Error handling](#8-error-handling)
- [9. Deployment Diagram](#9-deployment-diagram)

---

## 1. UML Class Diagram

```mermaid
classDiagram
    direction TB

    class SwfReactiveConverterApplication {
        +main(String[] args)$
    }

    class AudioConverterController {
        <<RestController>>
        -Set~String~ SUPPORTED_FORMATS$
        -AudioConverter audioConverter
        -TempFileStore tempFileStore
        +convertAudio(FilePart filePart, String format) Mono~ResponseEntity~Resource~~
        +convertAudioWithProgress(FilePart filePart, String format) Flux~ServerSentEvent~Map~~
        +downloadConvertedFile(String token) Mono~ResponseEntity~Resource~~
        -createSecureTempDirectory()$ Path
    }

    class AudioConverter {
        <<Service>>
        +converter(String inputFileName, String outputFileName) Flux~Double~
        -validateFile(File inputFile) boolean
        -getDuration(MultimediaObject inputObject) long
        -createAudioAttributes(String format) AudioAttributes
        -createEncodingAttributes(String ext, AudioAttributes attrs) EncodingAttributes
        -getFileExtension(String fileName) String
        -displayRatio(double ratio)
        -formatTime(long ms) String
        -formatFileSize(long bytes) String
    }

    class ProgressListener {
        <<static inner>>
        -FluxSink~Double~ sink
        -long totalDuration
        -long lastUpdateTime
        -long UPDATE_INTERVAL = 500$
        +sourceInfo(MultimediaInfo info)
        +progress(int permil)
        +message(String message)
        -createProgressBar(double percentage) String
        -formatDuration(long durationMs) String
    }

    class EncoderProgressListener {
        <<interface>>
        +sourceInfo(MultimediaInfo info)
        +progress(int permil)
        +message(String message)
    }

    class TempFileStore {
        <<Component>>
        -ConcurrentHashMap~String, Path~ store
        +register(Path filePath) String
        +retrieve(String token) Optional~Path~
    }

    class AudioConversionException {
        -ErrorCode errorCode
        +AudioConversionException(ErrorCode code, String message)
        +AudioConversionException(ErrorCode code, String message, Throwable cause)
        +getErrorCode() ErrorCode
    }

    class ErrorCode {
        <<enumeration>>
        CONVERSION_FAILED
        INVALID_FORMAT
        FILE_PROCESSING_ERROR
    }

    class ErrorResponse {
        <<record>>
        +String errorCode
        +String message
        +LocalDateTime timestamp
        +of(String errorCode, String message)$ ErrorResponse
    }

    class GlobalExceptionHandler {
        <<RestControllerAdvice>>
        +handleAudioConversionException(AudioConversionException ex) ResponseEntity~ErrorResponse~
        +handleGenericException(Exception ex) ResponseEntity~ErrorResponse~
    }

    class CorsConfig {
        <<Configuration>>
        +corsWebFilter() CorsWebFilter
    }

    class OpenApiConfig {
        <<Configuration>>
        +openAPI() OpenAPI
    }

    class RuntimeException

    AudioConverterController --> AudioConverter : uses
    AudioConverterController --> TempFileStore : uses
    AudioConverterController ..> AudioConversionException : throws
    AudioConverter *-- ProgressListener : inner class
    ProgressListener ..|> EncoderProgressListener
    AudioConverter ..> AudioConversionException : emits
    AudioConversionException --|> RuntimeException
    AudioConversionException *-- ErrorCode : nested enum
    GlobalExceptionHandler ..> AudioConversionException : handles
    GlobalExceptionHandler ..> ErrorResponse : creates
```

---

## 2. Component / Package Diagram

```mermaid
flowchart TB
    client(["HTTP client<br/>(React frontend, curl, Swagger UI)"])

    subgraph app["com.work.converter"]
        direction TB
        subgraph config["config"]
            cors["CorsConfig<br/>CorsWebFilter /api/**"]
            openapi["OpenApiConfig"]
        end
        subgraph controller["controller"]
            ctrl["AudioConverterController<br/>/api/audio"]
        end
        subgraph service["service"]
            conv["AudioConverter"]
            store["TempFileStore"]
        end
        subgraph exception["exception"]
            ex["AudioConversionException<br/>+ ErrorCode"]
            resp["ErrorResponse"]
            subgraph handler["exception.handler"]
                geh["GlobalExceptionHandler"]
            end
        end
    end

    jave[["JAVE 3.5<br/>(embedded FFmpeg)"]]
    fs[("File system<br/>~/.audio-converter/")]
    swagger[["springdoc<br/>/swagger-ui.html"]]

    client --> cors --> ctrl
    client --> swagger
    openapi --> swagger
    ctrl --> conv
    ctrl --> store
    ctrl --> fs
    conv --> jave
    jave --> fs
    store -. "token → Path" .-> fs
    ctrl -. "error" .-> geh
    conv -. "error" .-> ex
    geh --> resp
```

---

## 3. Sequence — Conversion with SSE progress

`POST /api/audio/convert/stream?format={format}`

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant Ctrl as AudioConverterController
    participant FS as File system
    participant Conv as AudioConverter
    participant Enc as JAVE Encoder (FFmpeg)
    participant L as ProgressListener
    participant Store as TempFileStore

    C->>Ctrl: POST multipart (file) + format
    alt format not supported
        Ctrl-->>C: 400 INVALID_FORMAT
    else format supported
        Ctrl->>FS: createSecureTempDirectory()<br/>(boundedElastic)
        FS-->>Ctrl: ~/.audio-converter/audio-converter-XXXX
        Ctrl->>FS: filePart.transferTo(inputPath)
        Ctrl->>Conv: converter(inputPath, outputPath)
        Conv->>Conv: Flux.create(...).subscribeOn(boundedElastic)
        Conv->>Enc: encode(input, output, attrs, listener)
        Enc->>L: sourceInfo(info)
        loop every ≥ 500 ms
            Enc->>L: progress(permil)
            L->>Conv: sink.next(percentage)
            Conv-->>Ctrl: Double
            Ctrl-->>C: event: progress<br/>data: {"progress": 42.5}
        end
        Enc-->>Conv: encode() returns
        Conv-->>Ctrl: sink.complete()
        Ctrl->>Store: register(outputPath)
        Store-->>Ctrl: UUID token
        Ctrl-->>C: event: complete<br/>data: {"filename", "downloadToken"}
    end
```

---

## 4. Sequence — Direct conversion

`POST /api/audio/convert` (`file` + `format` in the multipart body / query)

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant Ctrl as AudioConverterController
    participant FS as File system
    participant Conv as AudioConverter
    participant GEH as GlobalExceptionHandler

    C->>Ctrl: POST multipart (file, format)
    alt format not supported
        Ctrl->>GEH: AudioConversionException(INVALID_FORMAT)
        GEH-->>C: 400 ErrorResponse
    else
        Ctrl->>FS: create temp dir + transferTo(inputPath)
        Ctrl->>Conv: converter(inputPath, outputPath)
        Conv-->>Ctrl: Flux<Double> (progress values discarded)
        alt conversion fails
            Conv->>GEH: AudioConversionException(CONVERSION_FAILED)
            GEH-->>C: 422 ErrorResponse
        else success
            Ctrl-->>C: 200 application/octet-stream<br/>Content-Disposition: attachment, filename=name.ext
        end
    end
```

---

## 5. Sequence — Download by token

`GET /api/audio/download/{token}`

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant Ctrl as AudioConverterController
    participant Store as TempFileStore
    participant GEH as GlobalExceptionHandler

    C->>Ctrl: GET /api/audio/download/{token}
    Ctrl->>Store: retrieve(token) (boundedElastic)
    Store->>Store: store.remove(token)
    alt token found
        Store-->>Ctrl: Optional[Path]
        Ctrl-->>C: 200 FileSystemResource<br/>Content-Disposition: attachment
    else token unknown or already used
        Store-->>Ctrl: Optional.empty()
        Ctrl->>GEH: AudioConversionException(FILE_PROCESSING_ERROR)
        GEH-->>C: 500 ErrorResponse
    end
```

---

## 6. Activity — Conversion pipeline

```mermaid
flowchart TD
    start([Request received]) --> norm["Normalize format<br/>toLowerCase().trim()"]
    norm --> valid{"Format in<br/>mp3, wav, ogg,<br/>aac, flac, wma?"}
    valid -- no --> e400[/"INVALID_FORMAT → 400"/]
    valid -- yes --> tmp["Create temp dir in<br/>~/.audio-converter/"]
    tmp -- IOException --> e500a[/"FILE_PROCESSING_ERROR → 500"/]
    tmp --> save["Save upload<br/>filePart.transferTo()"]
    save -- error --> e500b[/"FILE_PROCESSING_ERROR → 500"/]
    save --> exists{"Input file exists<br/>and is a file?"}
    exists -- no --> e422a[/"CONVERSION_FAILED → 422"/]
    exists -- yes --> attrs["Build AudioAttributes<br/>2 ch · 44.1 kHz · codec per format"]
    attrs --> encode["encoder.encode(...)<br/>emit progress every 500 ms"]
    encode -- EncoderException --> e422b[/"CONVERSION_FAILED → 422"/]
    encode -- IllegalArgumentException --> e500c[/"FILE_PROCESSING_ERROR → 500"/]
    encode --> endpoint{"Endpoint?"}
    endpoint -- "/convert" --> file([200 + converted file])
    endpoint -- "/convert/stream" --> token["TempFileStore.register()"] --> done([event: complete + downloadToken])
```

Codec settings applied by `AudioConverter.createAudioAttributes`:

| Format | Codec | Bit rate | Channels | Sample rate |
|---|---|---|---|---|
| `mp3` | `libmp3lame` | 128 kbps | 2 | 44 100 Hz |
| `wav` | `pcm_s16le` | — (lossless) | 2 | 44 100 Hz |
| `ogg` | `libvorbis` | 128 kbps | 2 | 44 100 Hz |
| `aac` | `aac` | 128 kbps | 2 | 44 100 Hz |
| `flac` | `flac` | — (lossless) | 2 | 44 100 Hz |
| `wma` | `wmav2` | 128 kbps | 2 | 44 100 Hz |

---

## 7. State — Download token lifecycle

```mermaid
stateDiagram-v2
    [*] --> Converting : POST /convert/stream
    Converting --> Failed : AudioConversionException
    Converting --> Registered : sink.complete()<br/>TempFileStore.register(path)
    Registered --> Consumed : GET /download/{token}<br/>store.remove(token)
    Consumed --> [*]
    Failed --> [*]

    note right of Registered
        Token lives only in memory
        (ConcurrentHashMap): it is lost
        if the application restarts.
    end note
    note right of Consumed
        A second request with the same
        token returns 500 FILE_PROCESSING_ERROR.
    end note
```

---

## 8. Error handling

```mermaid
flowchart LR
    subgraph sources["Error sources"]
        a["Unsupported format"]
        b["Encoder failure /<br/>invalid input file"]
        c["Temp dir / upload I/O,<br/>invalid token,<br/>IllegalArgumentException"]
        d["Any other Exception"]
    end

    a --> ia["AudioConversionException<br/>INVALID_FORMAT"]
    b --> cf["AudioConversionException<br/>CONVERSION_FAILED"]
    c --> fp["AudioConversionException<br/>FILE_PROCESSING_ERROR"]

    ia --> geh{{"GlobalExceptionHandler"}}
    cf --> geh
    fp --> geh
    d --> geh

    geh --> r400["400 Bad Request"]
    geh --> r422["422 Unprocessable Content"]
    geh --> r500a["500 FILE_PROCESSING_ERROR"]
    geh --> r500b["500 INTERNAL_ERROR<br/>(generic message, no internals leaked)"]
```

Every response body has the `ErrorResponse` shape: `{ "errorCode", "message", "timestamp" }`.

---

## 9. Deployment Diagram

```mermaid
flowchart LR
    browser(["Browser"])

    subgraph host["Docker host"]
        subgraph net["app-network (bridge)"]
            subgraph fe["audio-converter-frontend<br/>node:26-alpine"]
                serve["serve -s dist<br/>:3000"]
            end
            subgraph be["audio-converter-backend<br/>amazoncorretto:17-alpine"]
                runsh["run.sh → java -jar app.jar<br/>:8080<br/>TZ=America/Mexico_City"]
                tmpdir[("/root/.audio-converter")]
                runsh --- tmpdir
            end
        end
    end

    browser -- "HTTP :3000<br/>(static SPA)" --> serve
    browser -- "HTTP :8080<br/>/api/audio/** (CORS localhost:*)" --> runsh
```
