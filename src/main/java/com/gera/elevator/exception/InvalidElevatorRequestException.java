package com.gera.elevator.exception;

public class InvalidElevatorRequestException extends RuntimeException {

    public InvalidElevatorRequestException(String message) {
        super(message);
    }
}
