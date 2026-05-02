package com.gera.elevator.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gera.elevator.api.dto.ElevatorRequest;
import com.gera.elevator.api.dto.ElevatorTelemetryRequest;
import com.gera.elevator.domain.Direction;
import com.gera.elevator.domain.DoorStatus;
import com.gera.elevator.domain.RequestType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "elevator.system.state-store=memory")
@AutoConfigureMockMvc
class ElevatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetSystem() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reset"))
                .andExpect(status().isOk());
    }

    @Test
    void getElevatorsReturnsInitialState() throws Exception {
        mockMvc.perform(get("/api/v1/elevators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFloors").value(16))
                .andExpect(jsonPath("$.elevators", hasSize(4)))
                .andExpect(jsonPath("$.elevators[0].id").value("A"))
                .andExpect(jsonPath("$.elevators[0].currentFloor").value(1))
                .andExpect(jsonPath("$.elevators[0].direction").value("IDLE"));
    }

    @Test
    void externalRequestMatchesAssignmentSample() throws Exception {
        updateTelemetry("A", new ElevatorTelemetryRequest(2, Direction.UP, List.of(6), DoorStatus.CLOSED));
        updateTelemetry("B", new ElevatorTelemetryRequest(8, Direction.DOWN, List.of(3), DoorStatus.CLOSED));
        updateTelemetry("C", new ElevatorTelemetryRequest(1, Direction.IDLE, List.of(), DoorStatus.CLOSED));

        ElevatorRequest request = new ElevatorRequest(RequestType.EXTERNAL, 4, Direction.UP, null, null);

        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedElevator").value("A"))
                .andExpect(jsonPath("$.estimatedArrivalTime").value(60))
                .andExpect(jsonPath("$.stopsUpdated[0]").value(4))
                .andExpect(jsonPath("$.stopsUpdated[1]").value(6))
                .andExpect(jsonPath("$.reason").value("SAME_DIRECTION_PASSING"));
    }

    @Test
    void internalRequestRoutesToPathElevator() throws Exception {
        mockMvc.perform(post("/api/v1/elevators/B/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationFloor\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedElevator").value("B"))
                .andExpect(jsonPath("$.stopsUpdated[0]").value(9));
    }

    @Test
    void invalidFloorReturnsBadRequest() throws Exception {
        ElevatorRequest request = new ElevatorRequest(RequestType.EXTERNAL, 99, Direction.UP, null, null);

        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("floor must be between 1 and 16"));
    }

    @Test
    void unknownElevatorReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/elevators/Z/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationFloor\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Elevator 'Z' does not exist"));
    }

    private void updateTelemetry(String elevatorId, ElevatorTelemetryRequest request) throws Exception {
        mockMvc.perform(put("/api/v1/elevators/{elevatorId}/telemetry", elevatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
