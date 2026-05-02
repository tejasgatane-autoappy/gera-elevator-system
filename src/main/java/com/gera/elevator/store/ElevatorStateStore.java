package com.gera.elevator.store;

import com.gera.elevator.state.ElevatorEvent;
import com.gera.elevator.state.ElevatorSystemState;
import java.util.List;
import java.util.Optional;

public interface ElevatorStateStore {

    Optional<ElevatorSystemState> load();

    void save(ElevatorSystemState state);

    void appendEvent(ElevatorEvent event);

    List<ElevatorEvent> recentEvents(int limit);
}
