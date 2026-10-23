package com.work.converter.controller;

import com.work.converter.exception.AudioConversionException;
import com.work.converter.exception.handler.GlobalExceptionHandler;
import com.work.converter.service.AudioConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AudioConverterControllerTest {

    @Mock
    private AudioConverter audioConverter;

    @InjectMocks
    private AudioConverterController controller;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // --- POST /api/audio/convert ---

    @Test
    void convertAudio_whenFormatIsUnsupported_returns400() {
        webTestClient.post()
                .uri("/api/audio/convert?format=xyz")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartBody("audio.mp3").build()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INVALID_FORMAT");
    }

    @Test
    void convertAudio_whenConversionFails_returns422() {
        when(audioConverter.converter(anyString(), anyString()))
                .thenReturn(Flux.error(new AudioConversionException(
                        AudioConversionException.ErrorCode.CONVERSION_FAILED, "Fallo la conversión")));

        webTestClient.post()
                .uri("/api/audio/convert?format=wav")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartBody("audio.mp3").build()))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("CONVERSION_FAILED");
    }

    @Test
    void convertAudio_whenSuccessful_returns200WithFile() throws Exception {
        when(audioConverter.converter(anyString(), anyString())).thenAnswer(invocation -> {
            String outputPath = invocation.getArgument(1, String.class);
            Files.writeString(Path.of(outputPath), "fake audio content");
            return Flux.just(0, 50, 100);
        });

        webTestClient.post()
                .uri("/api/audio/convert?format=wav")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartBody("audio.mp3").build()))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches("Content-Disposition", ".*audio\\.wav.*");
    }

    @Test
    void convertAudio_whenFormatIsCaseInsensitive_accepts() throws Exception {
        when(audioConverter.converter(anyString(), anyString())).thenAnswer(invocation -> {
            Files.writeString(Path.of(invocation.getArgument(1, String.class)), "content");
            return Flux.just(100);
        });

        webTestClient.post()
                .uri("/api/audio/convert?format=MP3")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartBody("audio.mp3").build()))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void convertAudio_whenConverterThrowsUnexpectedException_returns500() {
        when(audioConverter.converter(anyString(), anyString()))
                .thenReturn(Flux.error(new RuntimeException("Error inesperado del encoder")));

        webTestClient.post()
                .uri("/api/audio/convert?format=mp3")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartBody("audio.mp3").build()))
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void convertAudio_verifyConverterCalledWithCorrectPaths() throws Exception {
        when(audioConverter.converter(anyString(), anyString())).thenAnswer(invocation -> {
            Files.writeString(Path.of(invocation.getArgument(1, String.class)), "content");
            return Flux.just(100);
        });

        webTestClient.post()
                .uri("/api/audio/convert?format=ogg")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartBody("song.mp3").build()))
                .exchange()
                .expectStatus().isOk();

        verify(audioConverter).converter(
                argThat(input -> input.endsWith("song.mp3")),
                argThat(output -> output.endsWith("song.ogg"))
        );
    }

    // --- POST /api/audio/convert/stream ---

    @Test
    void convertAudioStream_whenFormatIsUnsupported_returnsErrorEvent() {
        webTestClient.post()
                .uri("/api/audio/convert/stream?format=xyz")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartBody("audio.mp3").build()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void convertAudioStream_emitsProgressAndCompleteEvents() {
        when(audioConverter.converter(anyString(), anyString()))
                .thenReturn(Flux.just(0.0, 25.0, 50.0, 75.0, 100.0));

        webTestClient.post()
                .uri("/api/audio/convert/stream?format=wav")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartBody("audio.mp3").build()))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);

        verify(audioConverter).converter(anyString(), anyString());
    }

    @Test
    void convertAudioStream_whenConversionFails_propagatesError() {
        when(audioConverter.converter(anyString(), anyString()))
                .thenReturn(Flux.error(new AudioConversionException(
                        AudioConversionException.ErrorCode.CONVERSION_FAILED, "Fallo")));

        webTestClient.post()
                .uri("/api/audio/convert/stream?format=wav")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartBody("audio.mp3").build()))
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    private MultipartBodyBuilder multipartBody(String filename) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource("fake audio bytes".getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        }, MediaType.APPLICATION_OCTET_STREAM);
        return builder;
    }
}
