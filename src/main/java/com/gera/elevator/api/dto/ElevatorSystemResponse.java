package com.gera.elevator.api.dto;

import com.gera.elevator.state.ElevatorSystemState;
import java.time.Instant;
import java.util.List;

public record ElevatorSystemResponse(
        int totalFloors,
        long version,
        Instant updatedAt,
        List<ElevatorStateResponse> elevators
) {

    public static ElevatorSystemResponse from(ElevatorSystemState state) {
        return new ElevatorSystemResponse(
                state.getTotalFloors(),
                state.getVersion(),
                state.getUpdatedAt(),
                state.orderedElevators().stream()
                        .map(ElevatorStateResponse::from)
                        .toList()
        );
    }
}
