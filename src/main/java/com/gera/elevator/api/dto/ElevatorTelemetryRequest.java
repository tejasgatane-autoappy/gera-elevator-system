package com.gera.elevator.api.dto;

import com.gera.elevator.domain.Direction;
import com.gera.elevator.domain.DoorStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ElevatorTelemetryRequest(
        @NotNull @Min(1) Integer currentFloor,
        @NotNull Direction direction,
        @NotNull List<@Min(1) Integer> stops,
        DoorStatus doorStatus
) {
}
