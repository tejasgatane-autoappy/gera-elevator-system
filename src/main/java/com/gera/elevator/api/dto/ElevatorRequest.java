package com.gera.elevator.api.dto;

import com.gera.elevator.domain.Direction;
import com.gera.elevator.domain.RequestType;
import jakarta.validation.constraints.NotNull;

public record ElevatorRequest(
        @NotNull RequestType type,
        Integer floor,
        Direction direction,
        String elevatorId,
        Integer destinationFloor
) {
}
