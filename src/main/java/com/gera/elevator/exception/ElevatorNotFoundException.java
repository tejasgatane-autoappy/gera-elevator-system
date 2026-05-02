package com.gera.elevator.exception;

public class ElevatorNotFoundException extends RuntimeException {

    public ElevatorNotFoundException(String elevatorId) {
        super("Elevator '" + elevatorId + "' does not exist");
    }
}
