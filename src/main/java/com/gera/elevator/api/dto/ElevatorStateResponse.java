package com.gera.elevator.api.dto;

import com.gera.elevator.domain.Direction;
import com.gera.elevator.domain.DoorStatus;
import com.gera.elevator.state.ElevatorState;
import java.time.Instant;
import java.util.List;

public record ElevatorStateResponse(
        String id,
        int currentFloor,
        Direction direction,
        DoorStatus doorStatus,
        List<Integer> stops,
        Instant updatedAt
) {

    public static ElevatorStateResponse from(ElevatorState state) {
        return new ElevatorStateResponse(
                state.getId(),
                state.getCurrentFloor(),
                state.getDirection(),
                state.getDoorStatus(),
                List.copyOf(state.getStops()),
                state.getUpdatedAt()
        );
    }
}
