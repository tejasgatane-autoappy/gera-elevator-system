package com.gera.elevator.service;

import com.gera.elevator.domain.AssignmentReason;
import com.gera.elevator.domain.Direction;
import java.util.List;

public record SchedulingDecision(
        String elevatorId,
        int estimatedArrivalTime,
        List<Integer> updatedStops,
        Direction updatedDirection,
        AssignmentReason reason
) {
}
