package com.work.converter.exception.handler;

import com.work.converter.exception.AudioConversionException;
import com.work.converter.exception.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAudioConversionException_INVALID_FORMAT_returns400() {
        AudioConversionException ex = new AudioConversionException(
                AudioConversionException.ErrorCode.INVALID_FORMAT, "Formato inválido: xyz");

        ResponseEntity<ErrorResponse> response = handler.handleAudioConversionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("INVALID_FORMAT");
        assertThat(response.getBody().message()).isEqualTo("Formato inválido: xyz");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void handleAudioConversionException_CONVERSION_FAILED_returns422() {
        AudioConversionException ex = new AudioConversionException(
                AudioConversionException.ErrorCode.CONVERSION_FAILED, "Conversión fallida");

        ResponseEntity<ErrorResponse> response = handler.handleAudioConversionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().errorCode()).isEqualTo("CONVERSION_FAILED");
    }

    @Test
    void handleAudioConversionException_FILE_PROCESSING_ERROR_returns500() {
        AudioConversionException ex = new AudioConversionException(
                AudioConversionException.ErrorCode.FILE_PROCESSING_ERROR, "Error al procesar");

        ResponseEntity<ErrorResponse> response = handler.handleAudioConversionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().errorCode()).isEqualTo("FILE_PROCESSING_ERROR");
    }

    @ParameterizedTest
    @EnumSource(AudioConversionException.ErrorCode.class)
    void handleAudioConversionException_bodyIsNeverNull(AudioConversionException.ErrorCode errorCode) {
        AudioConversionException ex = new AudioConversionException(errorCode, "mensaje");

        ResponseEntity<ErrorResponse> response = handler.handleAudioConversionException(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(errorCode.name());
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void handleGenericException_returns500WithGenericMessage() {
        Exception ex = new Exception("error interno inesperado");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Error interno del servidor");
    }

    @Test
    void handleGenericException_doesNotExposeInternalDetails() {
        Exception ex = new Exception("detalles sensibles del sistema");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        assertThat(response.getBody().message()).doesNotContain("detalles sensibles");
    }
}
