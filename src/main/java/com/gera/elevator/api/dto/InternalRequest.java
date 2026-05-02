package com.gera.elevator.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InternalRequest(
        @NotNull @Min(1) Integer destinationFloor
) {
}
