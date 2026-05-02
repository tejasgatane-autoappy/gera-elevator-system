package com.gera.elevator.domain;

public enum Direction {
    UP,
    DOWN,
    IDLE;

    public boolean isMoving() {
        return this == UP || this == DOWN;
    }
}
