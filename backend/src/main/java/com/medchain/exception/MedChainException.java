package com.medchain.exception;

public class MedChainException extends RuntimeException {
    public MedChainException(String message) {
        super(message);
    }

    public MedChainException(String message, Throwable cause) {
        super(message, cause);
    }
}
