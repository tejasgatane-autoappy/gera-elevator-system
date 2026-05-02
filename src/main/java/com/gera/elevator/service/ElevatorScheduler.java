package com.gera.elevator.service;

import com.gera.elevator.config.ElevatorProperties;
import com.gera.elevator.domain.AssignmentReason;
import com.gera.elevator.domain.Direction;
import com.gera.elevator.state.ElevatorState;
import com.gera.elevator.state.ElevatorSystemState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ElevatorScheduler {

    private final ElevatorProperties properties;

    public ElevatorScheduler(ElevatorProperties properties) {
        this.properties = properties;
    }

    public SchedulingDecision selectForExternal(ElevatorSystemState state, int floor, Direction requestedDirection) {
        List<Candidate> passing = state.orderedElevators().stream()
                .filter(elevator -> isSameDirectionPassing(elevator, floor, requestedDirection))
                .map(elevator -> buildExternalCandidate(elevator, floor, requestedDirection, AssignmentReason.SAME_DIRECTION_PASSING))
                .sorted(Candidate.ORDERING)
                .toList();

        if (!passing.isEmpty()) {
            return passing.getFirst().toDecision();
        }

        List<Candidate> idle = state.orderedElevators().stream()
                .filter(elevator -> elevator.getDirection() == Direction.IDLE)
                .map(elevator -> buildExternalCandidate(elevator, floor, requestedDirection, AssignmentReason.NEAREST_IDLE))
                .sorted(Comparator
                        .comparingInt((Candidate candidate) -> Math.abs(candidate.elevator().getCurrentFloor() - floor))
                        .thenComparingInt(Candidate::estimatedArrivalTime)
                        .thenComparingInt(candidate -> candidate.elevator().getStops().size())
                        .thenComparing(candidate -> candidate.elevator().getId()))
                .toList();

        if (!idle.isEmpty()) {
            return idle.getFirst().toDecision();
        }

        return state.orderedElevators().stream()
                .map(elevator -> buildExternalCandidate(elevator, floor, requestedDirection, AssignmentReason.MINIMUM_ETA))
                .sorted(Candidate.ORDERING)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No elevators are configured"))
                .toDecision();
    }

    public SchedulingDecision selectForInternal(ElevatorSystemState state, String elevatorId, int destinationFloor) {
        ElevatorState elevator = state.getElevators().get(elevatorId);
        List<Integer> route = routeWithInternalStop(elevator, destinationFloor);
        int eta = estimateArrival(elevator.getCurrentFloor(), route, destinationFloor);
        return new SchedulingDecision(
                elevatorId,
                eta,
                route,
                directionAfterUpdate(elevator.getCurrentFloor(), route),
                AssignmentReason.INTERNAL_SELECTED
        );
    }

    public int estimateArrival(ElevatorState elevator, int targetFloor) {
        return estimateArrival(elevator.getCurrentFloor(), routeWithInternalStop(elevator, targetFloor), targetFloor);
    }

    public Direction directionAfterUpdate(int currentFloor, List<Integer> stops) {
        if (stops.isEmpty()) {
            return Direction.IDLE;
        }
        int firstStop = stops.getFirst();
        if (firstStop > currentFloor) {
            return Direction.UP;
        }
        if (firstStop < currentFloor) {
            return Direction.DOWN;
        }
        return Direction.IDLE;
    }

    public List<Integer> normalizeStopsForTelemetry(int currentFloor, Direction direction, List<Integer> stops) {
        return orderStops(currentFloor, direction, stops);
    }

    private Candidate buildExternalCandidate(
            ElevatorState elevator,
            int floor,
            Direction requestedDirection,
            AssignmentReason reason
    ) {
        List<Integer> route = routeWithExternalStop(elevator, floor, requestedDirection);
        int eta = estimateArrival(elevator.getCurrentFloor(), route, floor);
        Direction updatedDirection = directionAfterUpdate(elevator.getCurrentFloor(), route);
        return new Candidate(elevator, eta, route, updatedDirection, reason);
    }

    private List<Integer> routeWithInternalStop(ElevatorState elevator, int targetFloor) {
        LinkedHashSet<Integer> stops = new LinkedHashSet<>(elevator.getStops());
        stops.add(targetFloor);
        Direction direction = effectiveDirection(elevator, targetFloor);
        return orderStops(elevator.getCurrentFloor(), direction, stops);
    }

    private List<Integer> routeWithExternalStop(ElevatorState elevator, int targetFloor, Direction requestedDirection) {
        if (elevator.getStops().contains(targetFloor)) {
            return routeWithInternalStop(elevator, targetFloor);
        }

        Direction currentDirection = elevator.getDirection();
        if (currentDirection == Direction.UP && requestedDirection == Direction.DOWN && targetFloor >= elevator.getCurrentFloor()) {
            return routeOppositeDirectionAbove(elevator, targetFloor);
        }
        if (currentDirection == Direction.DOWN && requestedDirection == Direction.UP && targetFloor <= elevator.getCurrentFloor()) {
            return routeOppositeDirectionBelow(elevator, targetFloor);
        }

        return routeWithInternalStop(elevator, targetFloor);
    }

    private List<Integer> routeOppositeDirectionAbove(ElevatorState elevator, int targetFloor) {
        List<Integer> upLeg = sorted(
                withFilter(elevator.getStops(), stop -> stop >= elevator.getCurrentFloor()),
                Comparator.naturalOrder()
        );
        LinkedHashSet<Integer> downTargets = new LinkedHashSet<>(
                withFilter(elevator.getStops(), stop -> stop < elevator.getCurrentFloor())
        );
        downTargets.add(targetFloor);
        List<Integer> downLeg = sorted(downTargets, Comparator.reverseOrder());
        return concatenate(upLeg, downLeg);
    }

    private List<Integer> routeOppositeDirectionBelow(ElevatorState elevator, int targetFloor) {
        List<Integer> downLeg = sorted(
                withFilter(elevator.getStops(), stop -> stop <= elevator.getCurrentFloor()),
                Comparator.reverseOrder()
        );
        LinkedHashSet<Integer> upTargets = new LinkedHashSet<>(
                withFilter(elevator.getStops(), stop -> stop > elevator.getCurrentFloor())
        );
        upTargets.add(targetFloor);
        List<Integer> upLeg = sorted(upTargets, Comparator.naturalOrder());
        return concatenate(downLeg, upLeg);
    }

    private Direction effectiveDirection(ElevatorState elevator, int targetFloor) {
        if (elevator.getDirection().isMoving()) {
            return elevator.getDirection();
        }
        if (targetFloor > elevator.getCurrentFloor()) {
            return Direction.UP;
        }
        if (targetFloor < elevator.getCurrentFloor()) {
            return Direction.DOWN;
        }
        return Direction.IDLE;
    }

    private boolean isSameDirectionPassing(ElevatorState elevator, int floor, Direction requestedDirection) {
        if (requestedDirection == Direction.IDLE || elevator.getDirection() != requestedDirection) {
            return false;
        }
        return switch (requestedDirection) {
            case UP -> elevator.getCurrentFloor() <= floor;
            case DOWN -> elevator.getCurrentFloor() >= floor;
            case IDLE -> false;
        };
    }

    private List<Integer> orderStops(int currentFloor, Direction direction, Collection<Integer> stops) {
        LinkedHashSet<Integer> uniqueStops = new LinkedHashSet<>(stops);
        if (uniqueStops.isEmpty()) {
            return List.of();
        }

        if (direction == Direction.DOWN) {
            List<Integer> downLeg = sorted(withFilter(uniqueStops, stop -> stop <= currentFloor), Comparator.reverseOrder());
            List<Integer> upLeg = sorted(withFilter(uniqueStops, stop -> stop > currentFloor), Comparator.naturalOrder());
            return concatenate(downLeg, upLeg);
        }

        List<Integer> upLeg = sorted(withFilter(uniqueStops, stop -> stop >= currentFloor), Comparator.naturalOrder());
        List<Integer> downLeg = sorted(withFilter(uniqueStops, stop -> stop < currentFloor), Comparator.reverseOrder());
        return concatenate(upLeg, downLeg);
    }

    private int estimateArrival(int currentFloor, List<Integer> route, int targetFloor) {
        int elapsedSeconds = 0;
        int cursor = currentFloor;
        for (Integer stop : route) {
            elapsedSeconds += Math.abs(stop - cursor) * properties.getSecondsPerFloor();
            if (stop == targetFloor) {
                return elapsedSeconds;
            }
            elapsedSeconds += properties.getDoorCycleSeconds();
            cursor = stop;
        }
        return elapsedSeconds + Math.abs(targetFloor - cursor) * properties.getSecondsPerFloor();
    }

    private List<Integer> concatenate(List<Integer> first, List<Integer> second) {
        List<Integer> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    private List<Integer> sorted(Collection<Integer> values, Comparator<Integer> comparator) {
        return values.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(comparator)
                .toList();
    }

    private List<Integer> withFilter(Collection<Integer> values, StopFilter filter) {
        return values.stream()
                .filter(Objects::nonNull)
                .filter(filter::matches)
                .toList();
    }

    private interface StopFilter {
        boolean matches(int floor);
    }

    private record Candidate(
            ElevatorState elevator,
            int estimatedArrivalTime,
            List<Integer> updatedStops,
            Direction updatedDirection,
            AssignmentReason reason
    ) {

        private static final Comparator<Candidate> ORDERING = Comparator
                .comparingInt(Candidate::estimatedArrivalTime)
                .thenComparingInt(candidate -> candidate.elevator().getStops().size())
                .thenComparing(candidate -> candidate.elevator().getId());

        private SchedulingDecision toDecision() {
            return new SchedulingDecision(
                    elevator.getId(),
                    estimatedArrivalTime,
                    updatedStops,
                    updatedDirection,
                    reason
            );
        }
    }
}
