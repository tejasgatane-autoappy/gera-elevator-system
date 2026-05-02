package com.gera.elevator.api.dto;

import com.gera.elevator.domain.AssignmentReason;
import java.util.List;

public record ElevatorAssignmentResponse(
        String assignedElevator,
        int estimatedArrivalTime,
        List<Integer> stopsUpdated,
        AssignmentReason reason,
        long stateVersion
) {
}
