package com.gera.elevator.api;

import com.gera.elevator.api.dto.ElevatorAssignmentResponse;
import com.gera.elevator.api.dto.ElevatorEventResponse;
import com.gera.elevator.api.dto.ElevatorRequest;
import com.gera.elevator.api.dto.ElevatorSystemResponse;
import com.gera.elevator.api.dto.ElevatorTelemetryRequest;
import com.gera.elevator.api.dto.ExternalRequest;
import com.gera.elevator.api.dto.InternalRequest;
import com.gera.elevator.service.ElevatorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ElevatorController {

    private final ElevatorService elevatorService;

    public ElevatorController(ElevatorService elevatorService) {
        this.elevatorService = elevatorService;
    }

    @GetMapping("/elevators")
    public ElevatorSystemResponse getElevators() {
        return elevatorService.getState();
    }

    @PostMapping("/requests")
    public ElevatorAssignmentResponse submitRequest(@Valid @RequestBody ElevatorRequest request) {
        return elevatorService.handleRequest(request);
    }

    @PostMapping("/requests/external")
    public ElevatorAssignmentResponse submitExternalRequest(@Valid @RequestBody ExternalRequest request) {
        return elevatorService.handleExternal(request);
    }

    @PostMapping("/elevators/{elevatorId}/requests")
    public ElevatorAssignmentResponse submitInternalRequest(
            @PathVariable String elevatorId,
            @Valid @RequestBody InternalRequest request
    ) {
        return elevatorService.handleInternal(elevatorId, request);
    }

    @PutMapping("/elevators/{elevatorId}/telemetry")
    public ElevatorSystemResponse updateTelemetry(
            @PathVariable String elevatorId,
            @Valid @RequestBody ElevatorTelemetryRequest request
    ) {
        return elevatorService.updateTelemetry(elevatorId, request);
    }

    @GetMapping("/events")
    public List<ElevatorEventResponse> recentEvents(
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit
    ) {
        return elevatorService.recentEvents(limit);
    }

    @PostMapping("/admin/reset")
    public ElevatorSystemResponse reset() {
        return elevatorService.reset();
    }
}
