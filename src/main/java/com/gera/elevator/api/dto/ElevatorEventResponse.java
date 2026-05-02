package com.gera.elevator.api.dto;

import com.gera.elevator.state.ElevatorEvent;
import java.time.Instant;
import java.util.Map;

public record ElevatorEventResponse(
        String eventId,
        String type,
        Instant occurredAt,
        long stateVersion,
        Map<String, Object> details
) {

    public static ElevatorEventResponse from(ElevatorEvent event) {
        return new ElevatorEventResponse(
                event.getEventId(),
                event.getType(),
                event.getOccurredAt(),
                event.getStateVersion(),
                Map.copyOf(event.getDetails())
        );
    }
}
