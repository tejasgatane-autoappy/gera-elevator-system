package com.gera.elevator.state;

import com.gera.elevator.domain.Direction;
import com.gera.elevator.domain.DoorStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ElevatorState {

    private String id;
    private int currentFloor;
    private Direction direction = Direction.IDLE;
    private DoorStatus doorStatus = DoorStatus.CLOSED;
    private List<Integer> stops = new ArrayList<>();
    private Instant updatedAt = Instant.now();

    public ElevatorState() {
    }

    public ElevatorState(
            String id,
            int currentFloor,
            Direction direction,
            DoorStatus doorStatus,
            List<Integer> stops,
            Instant updatedAt
    ) {
        this.id = id;
        this.currentFloor = currentFloor;
        this.direction = direction;
        this.doorStatus = doorStatus;
        this.stops = new ArrayList<>(stops);
        this.updatedAt = updatedAt;
    }

    public ElevatorState copy() {
        return new ElevatorState(id, currentFloor, direction, doorStatus, stops, updatedAt);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public void setCurrentFloor(int currentFloor) {
        this.currentFloor = currentFloor;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public DoorStatus getDoorStatus() {
        return doorStatus;
    }

    public void setDoorStatus(DoorStatus doorStatus) {
        this.doorStatus = doorStatus;
    }

    public List<Integer> getStops() {
        return stops;
    }

    public void setStops(List<Integer> stops) {
        this.stops = new ArrayList<>(stops);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
