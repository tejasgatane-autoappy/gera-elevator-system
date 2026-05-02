package com.gera.elevator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gera.elevator.config.ElevatorProperties;
import com.gera.elevator.domain.AssignmentReason;
import com.gera.elevator.domain.Direction;
import com.gera.elevator.domain.DoorStatus;
import com.gera.elevator.state.ElevatorState;
import com.gera.elevator.state.ElevatorSystemState;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ElevatorSchedulerTest {

    private final ElevatorProperties properties = new ElevatorProperties();
    private final ElevatorScheduler scheduler = new ElevatorScheduler(properties);

    @Test
    void choosesSameDirectionPassingElevatorBeforeIdleElevator() {
        ElevatorSystemState state = state(
                elevator("A", 2, Direction.UP, 6),
                elevator("B", 8, Direction.DOWN, 3),
                elevator("C", 1, Direction.IDLE),
                elevator("D", 1, Direction.IDLE)
        );

        SchedulingDecision decision = scheduler.selectForExternal(state, 4, Direction.UP);

        assertThat(decision.elevatorId()).isEqualTo("A");
        assertThat(decision.estimatedArrivalTime()).isEqualTo(60);
        assertThat(decision.updatedStops()).containsExactly(4, 6);
        assertThat(decision.reason()).isEqualTo(AssignmentReason.SAME_DIRECTION_PASSING);
    }

    @Test
    void includesDoorCycleForIntermediateStopsBeforeRequestedFloor() {
        ElevatorSystemState state = state(
                elevator("A", 1, Direction.UP, 3, 8),
                elevator("B", 4, Direction.IDLE),
                elevator("C", 1, Direction.IDLE),
                elevator("D", 1, Direction.IDLE)
        );

        SchedulingDecision decision = scheduler.selectForExternal(state, 4, Direction.UP);

        assertThat(decision.elevatorId()).isEqualTo("A");
        assertThat(decision.estimatedArrivalTime()).isEqualTo(110);
        assertThat(decision.updatedStops()).containsExactly(3, 4, 8);
    }

    @Test
    void choosesFastestAmongSameDirectionPassingElevators() {
        ElevatorSystemState state = state(
                elevator("A", 1, Direction.UP, 10),
                elevator("B", 3, Direction.UP, 9),
                elevator("C", 1, Direction.IDLE),
                elevator("D", 1, Direction.IDLE)
        );

        SchedulingDecision decision = scheduler.selectForExternal(state, 4, Direction.UP);

        assertThat(decision.elevatorId()).isEqualTo("B");
        assertThat(decision.estimatedArrivalTime()).isEqualTo(30);
        assertThat(decision.updatedStops()).containsExactly(4, 9);
    }

    @Test
    void ignoresMovingElevatorThatAlreadyPassedRequestedFloor() {
        ElevatorSystemState state = state(
                elevator("A", 5, Direction.UP, 8),
                elevator("B", 2, Direction.IDLE),
                elevator("C", 10, Direction.DOWN, 1),
                elevator("D", 1, Direction.IDLE)
        );

        SchedulingDecision decision = scheduler.selectForExternal(state, 4, Direction.UP);

        assertThat(decision.elevatorId()).isEqualTo("B");
        assertThat(decision.reason()).isEqualTo(AssignmentReason.NEAREST_IDLE);
        assertThat(decision.estimatedArrivalTime()).isEqualTo(60);
    }

    @Test
    void breaksNearestIdleTieByElevatorId() {
        ElevatorSystemState state = state(
                elevator("A", 2, Direction.IDLE),
                elevator("B", 6, Direction.IDLE),
                elevator("C", 10, Direction.DOWN, 1),
                elevator("D", 12, Direction.UP, 14)
        );

        SchedulingDecision decision = scheduler.selectForExternal(state, 4, Direction.UP);

        assertThat(decision.elevatorId()).isEqualTo("A");
        assertThat(decision.reason()).isEqualTo(AssignmentReason.NEAREST_IDLE);
    }

    @Test
    void fallsBackToMinimumEtaWhenNoPassingOrIdleElevatorExists() {
        ElevatorSystemState state = state(
                elevator("A", 10, Direction.DOWN, 1),
                elevator("B", 6, Direction.DOWN, 2),
                elevator("C", 12, Direction.UP, 15),
                elevator("D", 14, Direction.UP, 16)
        );

        SchedulingDecision decision = scheduler.selectForExternal(state, 4, Direction.UP);

        assertThat(decision.elevatorId()).isEqualTo("B");
        assertThat(decision.reason()).isEqualTo(AssignmentReason.MINIMUM_ETA);
        assertThat(decision.updatedStops()).containsExactly(2, 4);
        assertThat(decision.estimatedArrivalTime()).isEqualTo(200);
    }

    @Test
    void doesNotDuplicateExistingStop() {
        ElevatorSystemState state = state(
                elevator("A", 1, Direction.UP, 5, 8),
                elevator("B", 8, Direction.DOWN, 3),
                elevator("C", 1, Direction.IDLE),
                elevator("D", 1, Direction.IDLE)
        );

        SchedulingDecision decision = scheduler.selectForExternal(state, 5, Direction.UP);

        assertThat(decision.elevatorId()).isEqualTo("A");
        assertThat(decision.updatedStops()).containsExactly(5, 8);
        assertThat(decision.estimatedArrivalTime()).isEqualTo(120);
    }

    @Test
    void internalRequestOnlyUpdatesSelectedElevator() {
        ElevatorSystemState state = state(
                elevator("A", 1, Direction.IDLE),
                elevator("B", 8, Direction.IDLE),
                elevator("C", 1, Direction.IDLE),
                elevator("D", 1, Direction.IDLE)
        );

        SchedulingDecision decision = scheduler.selectForInternal(state, "A", 9);

        assertThat(decision.elevatorId()).isEqualTo("A");
        assertThat(decision.updatedStops()).containsExactly(9);
        assertThat(decision.estimatedArrivalTime()).isEqualTo(240);
        assertThat(decision.reason()).isEqualTo(AssignmentReason.INTERNAL_SELECTED);
    }

    @Test
    void requestAtCurrentFloorHasZeroEta() {
        ElevatorSystemState state = state(
                elevator("A", 4, Direction.IDLE),
                elevator("B", 8, Direction.DOWN, 3),
                elevator("C", 1, Direction.IDLE),
                elevator("D", 1, Direction.IDLE)
        );

        SchedulingDecision decision = scheduler.selectForExternal(state, 4, Direction.UP);

        assertThat(decision.elevatorId()).isEqualTo("A");
        assertThat(decision.estimatedArrivalTime()).isZero();
        assertThat(decision.updatedStops()).containsExactly(4);
    }

    @Test
    void oppositeDirectionExternalCallIsServedAfterCurrentRoute() {
        ElevatorSystemState state = state(elevator("A", 2, Direction.UP, 8));

        SchedulingDecision decision = scheduler.selectForExternal(state, 4, Direction.DOWN);

        assertThat(decision.elevatorId()).isEqualTo("A");
        assertThat(decision.updatedStops()).containsExactly(8, 4);
        assertThat(decision.estimatedArrivalTime()).isEqualTo(320);
    }

    private ElevatorSystemState state(ElevatorState... elevators) {
        Map<String, ElevatorState> map = new LinkedHashMap<>();
        for (ElevatorState elevator : elevators) {
            map.put(elevator.getId(), elevator);
        }
        return new ElevatorSystemState(16, 1, Instant.EPOCH, map);
    }

    private ElevatorState elevator(String id, int currentFloor, Direction direction, Integer... stops) {
        return new ElevatorState(
                id,
                currentFloor,
                direction,
                DoorStatus.CLOSED,
                List.of(stops),
                Instant.EPOCH
        );
    }
}
