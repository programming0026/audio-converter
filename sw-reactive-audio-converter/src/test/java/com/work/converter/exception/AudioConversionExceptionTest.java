package com.work.converter.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudioConversionExceptionTest {

    @Test
    void constructor_setsErrorCodeAndMessage() {
        AudioConversionException ex = new AudioConversionException(
                AudioConversionException.ErrorCode.CONVERSION_FAILED, "La conversión falló");

        assertThat(ex.getErrorCode()).isEqualTo(AudioConversionException.ErrorCode.CONVERSION_FAILED);
        assertThat(ex.getMessage()).isEqualTo("La conversión falló");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void constructor_withCause_setsCause() {
        RuntimeException cause = new RuntimeException("causa raíz");
        AudioConversionException ex = new AudioConversionException(
                AudioConversionException.ErrorCode.FILE_PROCESSING_ERROR, "Error de archivo", cause);

        assertThat(ex.getErrorCode()).isEqualTo(AudioConversionException.ErrorCode.FILE_PROCESSING_ERROR);
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void errorCodes_allValuesExist() {
        assertThat(AudioConversionException.ErrorCode.values()).containsExactlyInAnyOrder(
                AudioConversionException.ErrorCode.CONVERSION_FAILED,
                AudioConversionException.ErrorCode.INVALID_FORMAT,
                AudioConversionException.ErrorCode.FILE_PROCESSING_ERROR
        );
    }
}
