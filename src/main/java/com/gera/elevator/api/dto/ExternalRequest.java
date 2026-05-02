package com.gera.elevator.api.dto;

import com.gera.elevator.domain.Direction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExternalRequest(
        @NotNull @Min(1) Integer floor,
        @NotNull Direction direction
) {
}
