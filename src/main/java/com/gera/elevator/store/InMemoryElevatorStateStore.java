package com.gera.elevator.store;

import com.gera.elevator.state.ElevatorEvent;
import com.gera.elevator.state.ElevatorSystemState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "elevator.system", name = "state-store", havingValue = "memory")
public class InMemoryElevatorStateStore implements ElevatorStateStore {

    private ElevatorSystemState state;
    private final List<ElevatorEvent> events = new ArrayList<>();

    @Override
    public synchronized Optional<ElevatorSystemState> load() {
        return Optional.ofNullable(state).map(ElevatorSystemState::copy);
    }

    @Override
    public synchronized void save(ElevatorSystemState state) {
        this.state = state.copy();
    }

    @Override
    public synchronized void appendEvent(ElevatorEvent event) {
        events.add(0, event);
    }

    @Override
    public synchronized List<ElevatorEvent> recentEvents(int limit) {
        return new ArrayList<>(events.subList(0, Math.min(limit, events.size())));
    }
}
