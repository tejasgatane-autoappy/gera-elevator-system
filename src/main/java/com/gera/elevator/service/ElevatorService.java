package com.gera.elevator.service;

import com.gera.elevator.api.dto.ElevatorAssignmentResponse;
import com.gera.elevator.api.dto.ElevatorEventResponse;
import com.gera.elevator.api.dto.ElevatorRequest;
import com.gera.elevator.api.dto.ElevatorSystemResponse;
import com.gera.elevator.api.dto.ElevatorTelemetryRequest;
import com.gera.elevator.api.dto.ExternalRequest;
import com.gera.elevator.api.dto.InternalRequest;
import com.gera.elevator.config.ElevatorProperties;
import com.gera.elevator.domain.Direction;
import com.gera.elevator.domain.DoorStatus;
import com.gera.elevator.domain.RequestType;
import com.gera.elevator.exception.ElevatorNotFoundException;
import com.gera.elevator.exception.InvalidElevatorRequestException;
import com.gera.elevator.exception.StopCapacityExceededException;
import com.gera.elevator.state.ElevatorEvent;
import com.gera.elevator.state.ElevatorState;
import com.gera.elevator.state.ElevatorSystemState;
import com.gera.elevator.store.ElevatorStateStore;
import com.gera.elevator.store.StateLockService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ElevatorService {

    private final ElevatorStateStore stateStore;
    private final StateLockService lockService;
    private final ElevatorScheduler scheduler;
    private final ElevatorProperties properties;
    private final Clock clock;

    @Autowired
    public ElevatorService(
            ElevatorStateStore stateStore,
            StateLockService lockService,
            ElevatorScheduler scheduler,
            ElevatorProperties properties
    ) {
        this(stateStore, lockService, scheduler, properties, Clock.systemUTC());
    }

    public ElevatorService(
            ElevatorStateStore stateStore,
            StateLockService lockService,
            ElevatorScheduler scheduler,
            ElevatorProperties properties,
            Clock clock
    ) {
        this.stateStore = stateStore;
        this.lockService = lockService;
        this.scheduler = scheduler;
        this.properties = properties;
        this.clock = clock;
    }

    public ElevatorSystemResponse getState() {
        return lockService.withLock("get-state", () -> ElevatorSystemResponse.from(loadOrInitialize()));
    }

    public ElevatorAssignmentResponse handleRequest(ElevatorRequest request) {
        if (request == null || request.type() == null) {
            throw new InvalidElevatorRequestException("Request type is required");
        }
        if (request.type() == RequestType.EXTERNAL) {
            if (request.floor() == null) {
                throw new InvalidElevatorRequestException("External request requires floor");
            }
            if (request.direction() == null) {
                throw new InvalidElevatorRequestException("External request requires direction");
            }
            return handleExternal(new ExternalRequest(request.floor(), request.direction()));
        }
        if (request.elevatorId() == null || request.elevatorId().isBlank()) {
            throw new InvalidElevatorRequestException("Internal request requires elevatorId");
        }
        if (request.destinationFloor() == null) {
            throw new InvalidElevatorRequestException("Internal request requires destinationFloor");
        }
        return handleInternal(request.elevatorId(), new InternalRequest(request.destinationFloor()));
    }

    public ElevatorAssignmentResponse handleExternal(ExternalRequest request) {
        return lockService.withLock("external-request", () -> {
            ElevatorSystemState state = loadOrInitialize();
            validateFloor(state, request.floor(), "floor");
            if (request.direction() == Direction.IDLE) {
                throw new InvalidElevatorRequestException("External request direction must be UP or DOWN");
            }

            SchedulingDecision decision = scheduler.selectForExternal(state, request.floor(), request.direction());
            ElevatorSystemState updated = applyDecision(state, decision);
            saveWithEvent(updated, "EXTERNAL_REQUEST_ASSIGNED", Map.of(
                    "floor", request.floor(),
                    "direction", request.direction().name(),
                    "assignedElevator", decision.elevatorId(),
                    "estimatedArrivalTime", decision.estimatedArrivalTime(),
                    "reason", decision.reason().name(),
                    "stopsUpdated", decision.updatedStops()
            ));
            return toAssignmentResponse(decision, updated.getVersion());
        });
    }

    public ElevatorAssignmentResponse handleInternal(String elevatorId, InternalRequest request) {
        return lockService.withLock("internal-request", () -> {
            ElevatorSystemState state = loadOrInitialize();
            String normalizedElevatorId = normalizeElevatorId(elevatorId);
            requireElevator(state, normalizedElevatorId);
            validateFloor(state, request.destinationFloor(), "destinationFloor");

            SchedulingDecision decision = scheduler.selectForInternal(state, normalizedElevatorId, request.destinationFloor());
            ElevatorSystemState updated = applyDecision(state, decision);
            saveWithEvent(updated, "INTERNAL_REQUEST_ASSIGNED", Map.of(
                    "elevatorId", normalizedElevatorId,
                    "destinationFloor", request.destinationFloor(),
                    "estimatedArrivalTime", decision.estimatedArrivalTime(),
                    "stopsUpdated", decision.updatedStops()
            ));
            return toAssignmentResponse(decision, updated.getVersion());
        });
    }

    public ElevatorSystemResponse updateTelemetry(String elevatorId, ElevatorTelemetryRequest request) {
        return lockService.withLock("telemetry-update", () -> {
            ElevatorSystemState state = loadOrInitialize();
            String normalizedElevatorId = normalizeElevatorId(elevatorId);
            ElevatorState elevator = requireElevator(state, normalizedElevatorId);
            validateFloor(state, request.currentFloor(), "currentFloor");
            validateStops(state, request.stops());

            List<Integer> uniqueStops = List.copyOf(new LinkedHashSet<>(request.stops()));
            if (uniqueStops.size() > properties.getMaxStopsPerElevator()) {
                throw new StopCapacityExceededException(normalizedElevatorId, properties.getMaxStopsPerElevator());
            }

            ElevatorSystemState updated = state.copy();
            ElevatorState updatedElevator = updated.getElevators().get(normalizedElevatorId);
            List<Integer> normalizedStops = scheduler.normalizeStopsForTelemetry(
                    request.currentFloor(),
                    request.direction(),
                    uniqueStops
            );
            Direction direction = normalizedStops.isEmpty()
                    ? Direction.IDLE
                    : request.direction() == Direction.IDLE
                    ? scheduler.directionAfterUpdate(request.currentFloor(), normalizedStops)
                    : request.direction();

            Instant now = clock.instant();
            updatedElevator.setCurrentFloor(request.currentFloor());
            updatedElevator.setDirection(direction);
            updatedElevator.setDoorStatus(request.doorStatus() == null ? elevator.getDoorStatus() : request.doorStatus());
            updatedElevator.setStops(normalizedStops);
            updatedElevator.setUpdatedAt(now);
            updated.setUpdatedAt(now);
            updated.setVersion(nextVersion(state));

            saveWithEvent(updated, "TELEMETRY_UPDATED", Map.of(
                    "elevatorId", normalizedElevatorId,
                    "currentFloor", request.currentFloor(),
                    "direction", direction.name(),
                    "doorStatus", updatedElevator.getDoorStatus().name(),
                    "stops", normalizedStops
            ));
            return ElevatorSystemResponse.from(updated);
        });
    }

    public ElevatorSystemResponse reset() {
        return lockService.withLock("reset", () -> {
            ElevatorSystemState initial = ElevatorSystemState.initial(properties, clock.instant());
            saveWithEvent(initial, "SYSTEM_RESET", Map.of("reason", "manual reset"));
            return ElevatorSystemResponse.from(initial);
        });
    }

    public List<ElevatorEventResponse> recentEvents(int limit) {
        return stateStore.recentEvents(limit).stream()
                .map(ElevatorEventResponse::from)
                .toList();
    }

    private ElevatorSystemState loadOrInitialize() {
        return stateStore.load().orElseGet(() -> {
            ElevatorSystemState initial = ElevatorSystemState.initial(properties, clock.instant());
            saveWithEvent(initial, "SYSTEM_INITIALIZED", Map.of("source", "empty state store"));
            return initial;
        });
    }

    private ElevatorSystemState applyDecision(ElevatorSystemState state, SchedulingDecision decision) {
        ElevatorSystemState updated = state.copy();
        ElevatorState elevator = requireElevator(updated, decision.elevatorId());
        if (decision.updatedStops().size() > properties.getMaxStopsPerElevator()) {
            throw new StopCapacityExceededException(decision.elevatorId(), properties.getMaxStopsPerElevator());
        }
        validateStops(updated, decision.updatedStops());

        Instant now = clock.instant();
        elevator.setStops(decision.updatedStops());
        elevator.setDirection(decision.updatedDirection());
        elevator.setDoorStatus(doorStatusAfterAssignment(elevator.getCurrentFloor(), decision.updatedStops()));
        elevator.setUpdatedAt(now);
        updated.setVersion(nextVersion(state));
        updated.setUpdatedAt(now);
        return updated;
    }

    private DoorStatus doorStatusAfterAssignment(int currentFloor, List<Integer> stops) {
        if (!stops.isEmpty() && stops.getFirst() == currentFloor) {
            return DoorStatus.OPENING;
        }
        return DoorStatus.CLOSED;
    }

    private void saveWithEvent(ElevatorSystemState state, String eventType, Map<String, Object> details) {
        stateStore.save(state);
        stateStore.appendEvent(new ElevatorEvent(
                UUID.randomUUID().toString(),
                eventType,
                clock.instant(),
                state.getVersion(),
                new LinkedHashMap<>(details)
        ));
    }

    private ElevatorAssignmentResponse toAssignmentResponse(SchedulingDecision decision, long stateVersion) {
        return new ElevatorAssignmentResponse(
                decision.elevatorId(),
                decision.estimatedArrivalTime(),
                decision.updatedStops(),
                decision.reason(),
                stateVersion
        );
    }

    private long nextVersion(ElevatorSystemState state) {
        return Math.max(1, state.getVersion()) + 1;
    }

    private ElevatorState requireElevator(ElevatorSystemState state, String elevatorId) {
        ElevatorState elevator = state.getElevators().get(elevatorId);
        if (elevator == null) {
            throw new ElevatorNotFoundException(elevatorId);
        }
        return elevator;
    }

    private void validateFloor(ElevatorSystemState state, Integer floor, String fieldName) {
        if (floor == null) {
            throw new InvalidElevatorRequestException(fieldName + " is required");
        }
        if (floor < 1 || floor > state.getTotalFloors()) {
            throw new InvalidElevatorRequestException(fieldName + " must be between 1 and " + state.getTotalFloors());
        }
    }

    private void validateStops(ElevatorSystemState state, List<Integer> stops) {
        if (stops == null) {
            throw new InvalidElevatorRequestException("stops are required");
        }
        for (Integer stop : stops) {
            validateFloor(state, stop, "stop");
        }
    }

    private String normalizeElevatorId(String elevatorId) {
        if (elevatorId == null || elevatorId.isBlank()) {
            throw new InvalidElevatorRequestException("elevatorId is required");
        }
        return elevatorId.trim().toUpperCase(Locale.ROOT);
    }
}
