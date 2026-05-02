package com.gera.elevator.exception;

public class StopCapacityExceededException extends RuntimeException {

    public StopCapacityExceededException(String elevatorId, int maxStops) {
        super("Elevator '" + elevatorId + "' already has the maximum " + maxStops + " pending stops");
    }
}
