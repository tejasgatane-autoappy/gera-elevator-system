package com.gera.elevator.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "elevator.system")
public class ElevatorProperties {

    @Min(2)
    private int floors = 16;

    @NotEmpty
    private List<@NotBlank String> elevatorIds = new ArrayList<>(List.of("A", "B", "C", "D"));

    @Positive
    private int secondsPerFloor = 30;

    @Min(0)
    private int doorOpenSeconds = 10;

    @Min(0)
    private int doorCloseSeconds = 10;

    @Positive
    private int maxStopsPerElevator = 64;

    @NotBlank
    private String stateStore = "redis";

    @Positive
    private long lockTimeoutMillis = 3000;

    @Valid
    private Redis redis = new Redis();

    @Valid
    private Cors cors = new Cors();

    public int getFloors() {
        return floors;
    }

    public void setFloors(int floors) {
        this.floors = floors;
    }

    public List<String> getElevatorIds() {
        return elevatorIds;
    }

    public void setElevatorIds(List<String> elevatorIds) {
        this.elevatorIds = new ArrayList<>(elevatorIds);
    }

    public int getSecondsPerFloor() {
        return secondsPerFloor;
    }

    public void setSecondsPerFloor(int secondsPerFloor) {
        this.secondsPerFloor = secondsPerFloor;
    }

    public int getDoorOpenSeconds() {
        return doorOpenSeconds;
    }

    public void setDoorOpenSeconds(int doorOpenSeconds) {
        this.doorOpenSeconds = doorOpenSeconds;
    }

    public int getDoorCloseSeconds() {
        return doorCloseSeconds;
    }

    public void setDoorCloseSeconds(int doorCloseSeconds) {
        this.doorCloseSeconds = doorCloseSeconds;
    }

    public int getDoorCycleSeconds() {
        return doorOpenSeconds + doorCloseSeconds;
    }

    public int getMaxStopsPerElevator() {
        return maxStopsPerElevator;
    }

    public void setMaxStopsPerElevator(int maxStopsPerElevator) {
        this.maxStopsPerElevator = maxStopsPerElevator;
    }

    public String getStateStore() {
        return stateStore;
    }

    public void setStateStore(String stateStore) {
        this.stateStore = stateStore;
    }

    public long getLockTimeoutMillis() {
        return lockTimeoutMillis;
    }

    public void setLockTimeoutMillis(long lockTimeoutMillis) {
        this.lockTimeoutMillis = lockTimeoutMillis;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public static class Redis {

        @NotBlank
        private String stateKey = "gera:elevator:state:current";

        @NotBlank
        private String eventsKey = "gera:elevator:events";

        @NotBlank
        private String lockKey = "gera:elevator:lock:state";

        @Positive
        private long lockTtlMillis = 5000;

        @Positive
        private long eventHistoryLimit = 1000;

        public String getStateKey() {
            return stateKey;
        }

        public void setStateKey(String stateKey) {
            this.stateKey = stateKey;
        }

        public String getEventsKey() {
            return eventsKey;
        }

        public void setEventsKey(String eventsKey) {
            this.eventsKey = eventsKey;
        }

        public String getLockKey() {
            return lockKey;
        }

        public void setLockKey(String lockKey) {
            this.lockKey = lockKey;
        }

        public long getLockTtlMillis() {
            return lockTtlMillis;
        }

        public void setLockTtlMillis(long lockTtlMillis) {
            this.lockTtlMillis = lockTtlMillis;
        }

        public long getEventHistoryLimit() {
            return eventHistoryLimit;
        }

        public void setEventHistoryLimit(long eventHistoryLimit) {
            this.eventHistoryLimit = eventHistoryLimit;
        }
    }

    public static class Cors {

        @NotEmpty
        private List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:5173"
        ));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = new ArrayList<>(allowedOrigins);
        }
    }
}
