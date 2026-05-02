package com.gera.elevator.exception;

public class StateLockTimeoutException extends RuntimeException {

    public StateLockTimeoutException(String message) {
        super(message);
    }
}
