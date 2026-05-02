package com.gera.elevator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gera.elevator.api.dto.ElevatorAssignmentResponse;
import com.gera.elevator.api.dto.ElevatorSystemResponse;
import com.gera.elevator.api.dto.ElevatorTelemetryRequest;
import com.gera.elevator.api.dto.ExternalRequest;
import com.gera.elevator.api.dto.InternalRequest;
import com.gera.elevator.config.ElevatorProperties;
import com.gera.elevator.domain.Direction;
import com.gera.elevator.domain.DoorStatus;
import com.gera.elevator.exception.ElevatorNotFoundException;
import com.gera.elevator.exception.InvalidElevatorRequestException;
import com.gera.elevator.exception.StopCapacityExceededException;
import com.gera.elevator.store.InMemoryElevatorStateStore;
import com.gera.elevator.store.LocalStateLockService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ElevatorServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void bootstrapsAllConfiguredElevatorsAtFloorOne() {
        ElevatorService service = service(new InMemoryElevatorStateStore(), new ElevatorProperties());

        ElevatorSystemResponse response = service.getState();

        assertThat(response.totalFloors()).isEqualTo(16);
        assertThat(response.elevators()).hasSize(4);
        assertThat(response.elevators()).allSatisfy(elevator -> {
            assertThat(elevator.currentFloor()).isEqualTo(1);
            assertThat(elevator.direction()).isEqualTo(Direction.IDLE);
            assertThat(elevator.stops()).isEmpty();
        });
    }

    @Test
    void persistsStateSoNewServiceInstanceRecoversPendingStops() {
        InMemoryElevatorStateStore store = new InMemoryElevatorStateStore();
        ElevatorProperties properties = new ElevatorProperties();
        ElevatorService firstInstance = service(store, properties);

        ElevatorAssignmentResponse assignment = firstInstance.handleExternal(new ExternalRequest(4, Direction.UP));
        ElevatorService recoveredInstance = service(store, properties);
        ElevatorSystemResponse recovered = recoveredInstance.getState();

        assertThat(assignment.assignedElevator()).isEqualTo("A");
        assertThat(recovered.version()).isEqualTo(2);
        assertThat(recovered.elevators().getFirst().stops()).containsExactly(4);
    }

    @Test
    void rejectsFloorAboveConfiguredBuildingHeight() {
        ElevatorService service = service(new InMemoryElevatorStateStore(), new ElevatorProperties());

        assertThatThrownBy(() -> service.handleExternal(new ExternalRequest(17, Direction.UP)))
                .isInstanceOf(InvalidElevatorRequestException.class)
                .hasMessageContaining("between 1 and 16");
    }

    @Test
    void rejectsExternalIdleDirection() {
        ElevatorService service = service(new InMemoryElevatorStateStore(), new ElevatorProperties());

        assertThatThrownBy(() -> service.handleExternal(new ExternalRequest(4, Direction.IDLE)))
                .isInstanceOf(InvalidElevatorRequestException.class)
                .hasMessageContaining("UP or DOWN");
    }

    @Test
    void rejectsUnknownElevatorForInternalRequest() {
        ElevatorService service = service(new InMemoryElevatorStateStore(), new ElevatorProperties());

        assertThatThrownBy(() -> service.handleInternal("Z", new InternalRequest(4)))
                .isInstanceOf(ElevatorNotFoundException.class)
                .hasMessageContaining("Z");
    }

    @Test
    void enforcesMaximumPendingStops() {
        ElevatorProperties properties = new ElevatorProperties();
        properties.setMaxStopsPerElevator(1);
        ElevatorService service = service(new InMemoryElevatorStateStore(), properties);
        service.updateTelemetry("A", new ElevatorTelemetryRequest(1, Direction.UP, List.of(2), DoorStatus.CLOSED));

        assertThatThrownBy(() -> service.handleInternal("A", new InternalRequest(3)))
                .isInstanceOf(StopCapacityExceededException.class)
                .hasMessageContaining("maximum 1");
    }

    @Test
    void telemetryDeduplicatesAndNormalizesStops() {
        ElevatorService service = service(new InMemoryElevatorStateStore(), new ElevatorProperties());

        ElevatorSystemResponse response = service.updateTelemetry(
                "A",
                new ElevatorTelemetryRequest(5, Direction.UP, List.of(8, 6, 8, 2), DoorStatus.CLOSED)
        );

        assertThat(response.elevators().getFirst().stops()).containsExactly(6, 8, 2);
    }

    @Test
    void storesEventsForAssignmentsAndRecoveryActions() {
        InMemoryElevatorStateStore store = new InMemoryElevatorStateStore();
        ElevatorService service = service(store, new ElevatorProperties());

        service.getState();
        service.handleExternal(new ExternalRequest(4, Direction.UP));

        assertThat(service.recentEvents(10))
                .extracting("type")
                .contains("SYSTEM_INITIALIZED", "EXTERNAL_REQUEST_ASSIGNED");
    }

    private ElevatorService service(InMemoryElevatorStateStore store, ElevatorProperties properties) {
        ElevatorScheduler scheduler = new ElevatorScheduler(properties);
        LocalStateLockService lockService = new LocalStateLockService(properties);
        return new ElevatorService(store, lockService, scheduler, properties, FIXED_CLOCK);
    }
}
