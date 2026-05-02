package com.gera.elevator.state;

import com.gera.elevator.config.ElevatorProperties;
import com.gera.elevator.domain.Direction;
import com.gera.elevator.domain.DoorStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElevatorSystemState {

    private int totalFloors;
    private long version;
    private Instant updatedAt = Instant.now();
    private Map<String, ElevatorState> elevators = new LinkedHashMap<>();

    public ElevatorSystemState() {
    }

    public ElevatorSystemState(int totalFloors, long version, Instant updatedAt, Map<String, ElevatorState> elevators) {
        this.totalFloors = totalFloors;
        this.version = version;
        this.updatedAt = updatedAt;
        this.elevators = new LinkedHashMap<>(elevators);
    }

    public static ElevatorSystemState initial(ElevatorProperties properties, Instant now) {
        Map<String, ElevatorState> initialElevators = new LinkedHashMap<>();
        for (String id : properties.getElevatorIds()) {
            initialElevators.put(id, new ElevatorState(
                    id,
                    1,
                    Direction.IDLE,
                    DoorStatus.CLOSED,
                    List.of(),
                    now
            ));
        }
        return new ElevatorSystemState(properties.getFloors(), 1, now, initialElevators);
    }

    public ElevatorSystemState copy() {
        Map<String, ElevatorState> copied = new LinkedHashMap<>();
        for (Map.Entry<String, ElevatorState> entry : elevators.entrySet()) {
            copied.put(entry.getKey(), entry.getValue().copy());
        }
        return new ElevatorSystemState(totalFloors, version, updatedAt, copied);
    }

    public List<ElevatorState> orderedElevators() {
        return new ArrayList<>(elevators.values());
    }

    public int getTotalFloors() {
        return totalFloors;
    }

    public void setTotalFloors(int totalFloors) {
        this.totalFloors = totalFloors;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, ElevatorState> getElevators() {
        return elevators;
    }

    public void setElevators(Map<String, ElevatorState> elevators) {
        this.elevators = new LinkedHashMap<>(elevators);
    }
}
