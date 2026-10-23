package com.work.converter.controller;

import com.work.converter.exception.AudioConversionException;
import com.work.converter.exception.ErrorResponse;
import com.work.converter.service.AudioConverter;
import com.work.converter.service.TempFileStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

@Tag(name = "Audio Converter", description = "Conversión de archivos de audio entre formatos")
@RestController
@RequestMapping("/api/audio")
@RequiredArgsConstructor
@Slf4j
public class AudioConverterController {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("mp3", "wav", "ogg", "aac", "flac", "wma");

    private final AudioConverter audioConverter;
    private final TempFileStore tempFileStore;

    @Operation(
            summary = "Convertir archivo de audio",
            description = "Recibe un archivo de audio y lo convierte al formato indicado. Retorna el archivo convertido como descarga directa."
    )
    @ApiResponse(responseCode = "200", description = "Archivo convertido exitosamente",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "400", description = "Formato de salida no soportado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Error durante la conversión del archivo",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Resource>> convertAudio(
            @Parameter(description = "Archivo de audio a convertir", required = true)
            @RequestPart("file") FilePart filePart,
            @Parameter(description = "Formato de salida (mp3, wav, ogg, aac, flac, wma)", required = true, example = "mp3")
            @RequestParam("format") String format) {

        String targetFormat = format.toLowerCase().trim();
        String originalFilename = filePart.filename();
        String baseName = originalFilename.replaceAll("\\.[^.]+$", "");

        if (!SUPPORTED_FORMATS.contains(targetFormat)) {
            return Mono.error(new AudioConversionException(
                    AudioConversionException.ErrorCode.INVALID_FORMAT,
                    "Formato no soportado: '" + targetFormat + "'. Formatos válidos: " + SUPPORTED_FORMATS
            ));
        }

        return Mono.fromCallable(AudioConverterController::createSecureTempDirectory)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(IOException.class, e -> new AudioConversionException(
                        AudioConversionException.ErrorCode.FILE_PROCESSING_ERROR,
                        "Error al crear directorio temporal", e))
                .flatMap(tempDir -> {
                    Path inputPath = tempDir.resolve(originalFilename);
                    Path outputPath = tempDir.resolve(baseName + "." + targetFormat);

                    return filePart.transferTo(inputPath)
                            .onErrorMap(e -> new AudioConversionException(
                                    AudioConversionException.ErrorCode.FILE_PROCESSING_ERROR,
                                    "Error al procesar el archivo de entrada: " + originalFilename, e))
                            .thenMany(audioConverter.converter(inputPath.toString(), outputPath.toString()))
                            .then(Mono.just(outputPath));
                })
                .map(outputPath -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + baseName + "." + targetFormat + "\"")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .<Resource>body(new FileSystemResource(outputPath.toFile())));
    }

    @Operation(
            summary = "Convertir audio con progreso en tiempo real (SSE)",
            description = "Convierte un archivo de audio emitiendo el progreso via Server-Sent Events. "
                    + "Eventos: 'progress' con {progress: 0-100} durante la conversión, "
                    + "'complete' con {filename, downloadToken} al finalizar. "
                    + "Usar el downloadToken en GET /api/audio/download/{token} para obtener el archivo."
    )
    @ApiResponse(responseCode = "200", description = "Stream SSE iniciado",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(type = "string",
                            example = "event:progress\ndata:{\"progress\":50}\n\nevent:complete\ndata:{\"filename\":\"audio.wav\",\"downloadToken\":\"uuid\"}\n\n")))
    @ApiResponse(responseCode = "400", description = "Formato de salida no soportado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Error durante la conversión del archivo",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(value = "/convert/stream",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> convertAudioWithProgress(
            @Parameter(description = "Archivo de audio a convertir", required = true)
            @RequestPart("file") FilePart filePart,
            @Parameter(description = "Formato de salida (mp3, wav, ogg, aac, flac, wma)", required = true, example = "wav")
            @RequestParam("format") String format) {

        String targetFormat = format.toLowerCase().trim();
        String originalFilename = filePart.filename();
        String baseName = originalFilename.replaceAll("\\.[^.]+$", "");

        if (!SUPPORTED_FORMATS.contains(targetFormat)) {
            return Flux.error(new AudioConversionException(
                    AudioConversionException.ErrorCode.INVALID_FORMAT,
                    "Formato no soportado: '" + targetFormat + "'. Formatos válidos: " + SUPPORTED_FORMATS
            ));
        }

        return Mono.fromCallable(AudioConverterController::createSecureTempDirectory)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(IOException.class, e -> new AudioConversionException(
                        AudioConversionException.ErrorCode.FILE_PROCESSING_ERROR,
                        "Error al crear directorio temporal", e))
                .flatMapMany(tempDir -> {
                    Path inputPath = tempDir.resolve(originalFilename);
                    Path outputPath = tempDir.resolve(baseName + "." + targetFormat);

                    return filePart.transferTo(inputPath)
                            .onErrorMap(e -> new AudioConversionException(
                                    AudioConversionException.ErrorCode.FILE_PROCESSING_ERROR,
                                    "Error al procesar el archivo de entrada: " + originalFilename, e))
                            .thenMany(audioConverter.converter(inputPath.toString(), outputPath.toString()))
                            .map(progress -> ServerSentEvent.<Map<String, Object>>builder()
                                    .event("progress")
                                    .data(Map.of("progress", progress))
                                    .build())
                            .concatWith(Mono.fromCallable(() -> {
                                String token = tempFileStore.register(outputPath);
                                return ServerSentEvent.<Map<String, Object>>builder()
                                        .event("complete")
                                        .data(Map.of(
                                                "filename", baseName + "." + targetFormat,
                                                "downloadToken", token))
                                        .build();
                            }));
                });
    }

    @Operation(
            summary = "Descargar archivo convertido",
            description = "Descarga el archivo de audio convertido usando el token obtenido del evento 'complete' del stream SSE. El token es de un solo uso."
    )
    @ApiResponse(responseCode = "200", description = "Archivo de audio convertido",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "500", description = "Token inválido o ya utilizado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/download/{token}")
    public Mono<ResponseEntity<Resource>> downloadConvertedFile(
            @Parameter(description = "Token de descarga recibido en el evento 'complete' del SSE", required = true)
            @PathVariable String token) {

        return Mono.fromCallable(() -> tempFileStore.retrieve(token))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalPath -> optionalPath
                        .map(path -> {
                            String filename = path.getFileName().toString();
                            Resource resource = new FileSystemResource(path.toFile());
                            return Mono.just(ResponseEntity.ok()
                                    .header(HttpHeaders.CONTENT_DISPOSITION,
                                            "attachment; filename=\"" + filename + "\"")
                                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                    .<Resource>body(resource));
                        })
                        .orElseGet(() -> Mono.error(new AudioConversionException(
                                AudioConversionException.ErrorCode.FILE_PROCESSING_ERROR,
                                "Token de descarga inválido o ya utilizado: " + token
                        ))));
    }

    private static Path createSecureTempDirectory() throws IOException {
        Path secureBase = Path.of(System.getProperty("user.home"), ".audio-converter");
        Files.createDirectories(secureBase);
        return Files.createTempDirectory(secureBase, "audio-converter-");
    }
}
