package com.work.converter.exception;

public class AudioConversionException extends RuntimeException {

    public enum ErrorCode {
        CONVERSION_FAILED,
        INVALID_FORMAT,
        FILE_PROCESSING_ERROR
    }

    private final ErrorCode errorCode;

    public AudioConversionException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AudioConversionException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
