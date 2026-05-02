package com.gera.elevator.state;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class ElevatorEvent {

    private String eventId;
    private String type;
    private Instant occurredAt;
    private long stateVersion;
    private Map<String, Object> details = new LinkedHashMap<>();

    public ElevatorEvent() {
    }

    public ElevatorEvent(String eventId, String type, Instant occurredAt, long stateVersion, Map<String, Object> details) {
        this.eventId = eventId;
        this.type = type;
        this.occurredAt = occurredAt;
        this.stateVersion = stateVersion;
        this.details = new LinkedHashMap<>(details);
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public void setStateVersion(long stateVersion) {
        this.stateVersion = stateVersion;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = new LinkedHashMap<>(details);
    }
}
