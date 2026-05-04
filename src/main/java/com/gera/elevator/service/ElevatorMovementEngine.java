package com.gera.elevator.service;

import com.gera.elevator.domain.Direction;
import com.gera.elevator.domain.DoorStatus;
import com.gera.elevator.state.ElevatorState;
import com.gera.elevator.state.ElevatorSystemState;
import com.gera.elevator.store.ElevatorStateStore;
import com.gera.elevator.store.StateLockService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import com.gera.elevator.state.ElevatorEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ElevatorMovementEngine {

    private static final Logger log = LoggerFactory.getLogger(ElevatorMovementEngine.class);

    private static final long TICK_MS = 2500;
    private static final long DOOR_HOLD_MS = 1200;
    private static final long DOOR_PHASE_MS = 300;

    private final ElevatorStateStore stateStore;
    private final StateLockService lockService;
    private final Clock clock;

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "elevator-engine");
                t.setDaemon(true);
                return t;
            });

    private final ConcurrentHashMap<String, Boolean> inDoorCycle = new ConcurrentHashMap<>();

    public ElevatorMovementEngine(
            ElevatorStateStore stateStore,
            StateLockService lockService,
            Clock clock
    ) {
        this.stateStore = stateStore;
        this.lockService = lockService;
        this.clock = clock;
    }

    @PostConstruct
    public void start() {
        executor.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        log.info("ElevatorMovementEngine started — tick every {}ms", TICK_MS);
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }

    // ✅ MAIN ENGINE LOOP (FIXED)
    private void tick() {
        try {
            lockService.withLock("engine-tick", () -> {

                ElevatorSystemState current = stateStore.load().orElse(null);
                if (current == null) return null;

                // 🔥 FIX 1: ALWAYS WORK ON COPY
                ElevatorSystemState state = current.copy();

                boolean changed = false;

                for (Map.Entry<String, ElevatorState> entry : state.getElevators().entrySet()) {
                    String id = entry.getKey();
                    ElevatorState elevator = entry.getValue();

                    if (inDoorCycle.containsKey(id)) continue;

                    List<Integer> stops = elevator.getStops();

                    if (stops == null || stops.isEmpty()) {
                        if (elevator.getDirection() != Direction.IDLE ||
                            elevator.getDoorStatus() != DoorStatus.CLOSED) {

                            elevator.setDirection(Direction.IDLE);
                            elevator.setDoorStatus(DoorStatus.CLOSED);
                            elevator.setUpdatedAt(clock.instant());
                            changed = true;
                        }
                        continue;
                    }

                    int target = stops.get(0);
                    int currentFloor = elevator.getCurrentFloor();

                    if (currentFloor == target) {
                        startDoorCycle(id, target);
                        changed = true;

                    } else {
                        int next = currentFloor + (target > currentFloor ? 1 : -1);
                        Direction dir = target > currentFloor ? Direction.UP : Direction.DOWN;

                        // 🔥 DEBUG LOG
                        log.info("Lift {} moving {} → {}", id, currentFloor, next);

                        elevator.setCurrentFloor(next);
                        elevator.setDirection(dir);
                        elevator.setDoorStatus(DoorStatus.CLOSED);
                        elevator.setUpdatedAt(clock.instant());

                        changed = true;
                    }
                }

                if (changed) {
                    state.setVersion(state.getVersion() + 1);
                    state.setUpdatedAt(clock.instant());
                    stateStore.save(state);
                }

                return null;
            });

        } catch (Exception e) {
            log.warn("Engine tick error: {}", e.getMessage());
        }
    }

    // ✅ FIXED DOOR CYCLE
    private void startDoorCycle(String id, int floor) {

        inDoorCycle.put(id, true);

        // OPENING
        executor.schedule(() -> updateDoorState(id, DoorStatus.OPENING, floor, "DOOR_OPENING"),
                0, TimeUnit.MILLISECONDS);

        // OPEN
        executor.schedule(() -> updateDoorState(id, DoorStatus.OPEN, floor, "DOOR_OPEN"),
                DOOR_PHASE_MS, TimeUnit.MILLISECONDS);

        // CLOSING
        executor.schedule(() -> updateDoorState(id, DoorStatus.CLOSING, floor, "DOOR_CLOSING"),
                DOOR_PHASE_MS + DOOR_HOLD_MS, TimeUnit.MILLISECONDS);

        // CLOSED + REMOVE STOP
        executor.schedule(() -> {
            lockService.withLock("door-close-" + id, () -> {

                ElevatorSystemState current = stateStore.load().orElse(null);
                if (current == null) return null;

                ElevatorSystemState state = current.copy();
                ElevatorState e = state.getElevators().get(id);
                if (e == null) return null;

                // 🔥 FIX 2: SAFE LIST COPY
                List<Integer> stops = e.getStops();
                if (!stops.isEmpty()) {
                    stops = new java.util.ArrayList<>(stops.subList(1, stops.size()));
                }
                e.setStops(stops);

                e.setDoorStatus(DoorStatus.CLOSED);

                Direction nextDir = stops.isEmpty()
                        ? Direction.IDLE
                        : (stops.get(0) > e.getCurrentFloor() ? Direction.UP : Direction.DOWN);

                e.setDirection(nextDir);
                e.setUpdatedAt(clock.instant());

                state.setVersion(state.getVersion() + 1);
                state.setUpdatedAt(clock.instant());
                stateStore.save(state);

                appendEvent(state, id, "DOOR_CLOSED", floor);

                inDoorCycle.remove(id);
                return null;
            });

        }, DOOR_PHASE_MS + DOOR_HOLD_MS + DOOR_PHASE_MS, TimeUnit.MILLISECONDS);
    }

    private void updateDoorState(String id, DoorStatus status, int floor, String event) {
        lockService.withLock("door-" + id + "-" + event, () -> {

            ElevatorSystemState current = stateStore.load().orElse(null);
            if (current == null) return null;

            ElevatorSystemState state = current.copy();
            ElevatorState e = state.getElevators().get(id);
            if (e == null) return null;

            e.setDoorStatus(status);
            e.setUpdatedAt(clock.instant());

            state.setVersion(state.getVersion() + 1);
            state.setUpdatedAt(clock.instant());
            stateStore.save(state);

            appendEvent(state, id, event, floor);
            return null;
        });
    }

    private void appendEvent(ElevatorSystemState state, String elevatorId, String type, int floor) {
        stateStore.appendEvent(new ElevatorEvent(
                UUID.randomUUID().toString(),
                type,
                clock.instant(),
                state.getVersion(),
                Map.of("elevatorId", elevatorId, "floor", floor)
        ));
    }
}
















// package com.gera.elevator.service;

// import com.gera.elevator.domain.Direction;
// import com.gera.elevator.domain.DoorStatus;
// import com.gera.elevator.state.ElevatorState;
// import com.gera.elevator.state.ElevatorSystemState;
// import com.gera.elevator.store.ElevatorStateStore;
// import com.gera.elevator.store.StateLockService;
// import java.time.Clock;
// import java.time.Instant;
// import java.util.List;
// import java.util.Map;
// import java.util.UUID;
// import java.util.concurrent.Executors;
// import java.util.concurrent.ScheduledExecutorService;
// import java.util.concurrent.ScheduledFuture;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.TimeUnit;
// import com.gera.elevator.state.ElevatorEvent;
// import jakarta.annotation.PostConstruct;
// import jakarta.annotation.PreDestroy;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Component;

// @Component
// public class ElevatorMovementEngine {

//     private static final Logger log = LoggerFactory.getLogger(ElevatorMovementEngine.class);

//     // How often the engine ticks — drives visual floor-by-floor movement
//     // 300ms matches your frontend FLOOR_MOVE_MS
//     private static final long TICK_MS = 300;

//     // How long doors stay OPEN before closing
//     private static final long DOOR_HOLD_MS = 1200;

//     // How long the OPENING / CLOSING phases last
//     private static final long DOOR_PHASE_MS = 300;

//     private final ElevatorStateStore stateStore;
//     private final StateLockService lockService;
//     private final Clock clock;

//     private final ScheduledExecutorService executor =
//             Executors.newSingleThreadScheduledExecutor(r -> {
//                 Thread t = new Thread(r, "elevator-engine");
//                 t.setDaemon(true);
//                 return t;
//             });

//     // Track elevators that are in a door cycle so the movement tick skips them
//     private final ConcurrentHashMap<String, Boolean> inDoorCycle = new ConcurrentHashMap<>();

//     @Autowired
//     public ElevatorMovementEngine(
//             ElevatorStateStore stateStore,
//             StateLockService lockService,
//             Clock clock
//     ) {
//         this.stateStore = stateStore;
//         this.lockService = lockService;
//         this.clock = clock;
//     }

//     @PostConstruct
//     public void start() {
//         executor.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
//         log.info("ElevatorMovementEngine started — tick every {}ms", TICK_MS);
//     }

//     @PreDestroy
//     public void stop() {
//         executor.shutdownNow();
//     }

//     // ── main tick: runs every TICK_MS ─────────────────────────────────────────
//     private void tick() {
//         try {
//             lockService.withLock("engine-tick", () -> {
//                 ElevatorSystemState state = stateStore.load().orElse(null);
//                 if (state == null) return null;

//                 boolean changed = false;

//                 for (Map.Entry<String, ElevatorState> entry : state.getElevators().entrySet()) {
//                     String id = entry.getKey();
//                     ElevatorState elevator = entry.getValue();

//                     // Skip if in a door cycle — door transitions are handled by separate callbacks
//                     if (inDoorCycle.containsKey(id)) continue;

//                     List<Integer> stops = elevator.getStops();
//                     if (stops == null || stops.isEmpty()) {
//                         // No stops — ensure IDLE + CLOSED
//                         if (elevator.getDirection() != Direction.IDLE
//                                 || elevator.getDoorStatus() != DoorStatus.CLOSED) {
//                             elevator.setDirection(Direction.IDLE);
//                             elevator.setDoorStatus(DoorStatus.CLOSED);
//                             elevator.setUpdatedAt(clock.instant());
//                             changed = true;
//                         }
//                         continue;
//                     }

//                     int target = stops.get(0);
//                     int current = elevator.getCurrentFloor();

//                     if (current == target) {
//                         // Arrived — start door cycle
//                         startDoorCycle(state, id, elevator, target);
//                         changed = true;
//                     } else {
//                         // Move one floor toward target
//                         int next = current + (target > current ? 1 : -1);
//                         Direction dir = target > current ? Direction.UP : Direction.DOWN;
//                         elevator.setCurrentFloor(next);
//                         elevator.setDirection(dir);
//                         elevator.setDoorStatus(DoorStatus.CLOSED);
//                         elevator.setUpdatedAt(clock.instant());
//                         changed = true;
//                     }
//                 }

//                 if (changed) {
//                     state.setVersion(state.getVersion() + 1);
//                     state.setUpdatedAt(clock.instant());
//                     stateStore.save(state);
//                 }

//                 return null;
//             });
//         } catch (Exception e) {
//             log.warn("Engine tick error: {}", e.getMessage());
//         }
//     }

//     // ── door cycle: OPENING → OPEN → CLOSING → CLOSED ─────────────────────────
//     private void startDoorCycle(ElevatorSystemState state, String id, ElevatorState elevator, int arrivedFloor) {
//         inDoorCycle.put(id, true);

//         // Phase 1: OPENING
//         elevator.setDoorStatus(DoorStatus.OPENING);
//         elevator.setUpdatedAt(clock.instant());
//         appendEvent(state, id, "DOOR_OPENING", arrivedFloor);

//         // Phase 2: OPEN after DOOR_PHASE_MS
//         executor.schedule(() -> {
//             lockService.withLock("door-open-" + id, () -> {
//                 ElevatorSystemState s = stateStore.load().orElse(null);
//                 if (s == null) return null;
//                 ElevatorState e = s.getElevators().get(id);
//                 if (e == null) return null;

//                 e.setDoorStatus(DoorStatus.OPEN);
//                 e.setUpdatedAt(clock.instant());
//                 s.setVersion(s.getVersion() + 1);
//                 s.setUpdatedAt(clock.instant());
//                 stateStore.save(s);
//                 appendEvent(s, id, "DOOR_OPEN", arrivedFloor);
//                 return null;
//             });

//             // Phase 3: CLOSING after DOOR_HOLD_MS
//             executor.schedule(() -> {
//                 lockService.withLock("door-closing-" + id, () -> {
//                     ElevatorSystemState s = stateStore.load().orElse(null);
//                     if (s == null) return null;
//                     ElevatorState e = s.getElevators().get(id);
//                     if (e == null) return null;

//                     e.setDoorStatus(DoorStatus.CLOSING);
//                     e.setUpdatedAt(clock.instant());
//                     s.setVersion(s.getVersion() + 1);
//                     s.setUpdatedAt(clock.instant());
//                     stateStore.save(s);
//                     appendEvent(s, id, "DOOR_CLOSING", arrivedFloor);
//                     return null;
//                 });

//                 // Phase 4: CLOSED after DOOR_PHASE_MS — remove stop and continue
//                 executor.schedule(() -> {
//                     lockService.withLock("door-closed-" + id, () -> {
//                         ElevatorSystemState s = stateStore.load().orElse(null);
//                         if (s == null) return null;
//                         ElevatorState e = s.getElevators().get(id);
//                         if (e == null) return null;

//                         // Remove the completed stop
//                         List<Integer> remaining = e.getStops().subList(
//                                 Math.min(1, e.getStops().size()), e.getStops().size()
//                         );
//                         e.setStops(new java.util.ArrayList<>(remaining));
//                         e.setDoorStatus(DoorStatus.CLOSED);

//                         Direction nextDir = e.getStops().isEmpty()
//                                 ? Direction.IDLE
//                                 : (e.getStops().get(0) > e.getCurrentFloor()
//                                         ? Direction.UP : Direction.DOWN);
//                         e.setDirection(nextDir);
//                         e.setUpdatedAt(clock.instant());

//                         s.setVersion(s.getVersion() + 1);
//                         s.setUpdatedAt(clock.instant());
//                         stateStore.save(s);
//                         appendEvent(s, id, "DOOR_CLOSED", arrivedFloor);

//                         // Release lock — engine tick can drive this elevator again
//                         inDoorCycle.remove(id);
//                         return null;
//                     });
//                 }, DOOR_PHASE_MS, TimeUnit.MILLISECONDS);

//             }, DOOR_HOLD_MS, TimeUnit.MILLISECONDS);

//         }, DOOR_PHASE_MS, TimeUnit.MILLISECONDS);
//     }

//     private void appendEvent(ElevatorSystemState state, String elevatorId, String type, int floor) {
//         stateStore.appendEvent(new ElevatorEvent(
//                 UUID.randomUUID().toString(),
//                 type,
//                 clock.instant(),
//                 state.getVersion(),
//                 Map.of("elevatorId", elevatorId, "floor", floor)
//         ));
//     }
// }






// package com.gera.elevator.service;

// import com.gera.elevator.domain.Direction;
// import com.gera.elevator.domain.DoorStatus;
// import com.gera.elevator.state.ElevatorState;
// import com.gera.elevator.state.ElevatorSystemState;
// import com.gera.elevator.store.ElevatorStateStore;
// import com.gera.elevator.store.StateLockService;
// import org.springframework.scheduling.annotation.Scheduled;
// import org.springframework.stereotype.Service;

// @Service
// public class ElevatorMovementWorker {

//     private final ElevatorStateStore stateStore;
//     private final StateLockService lockService;

//     public ElevatorMovementWorker(
//             ElevatorStateStore stateStore,
//             StateLockService lockService
//     ) {
//         this.stateStore = stateStore;
//         this.lockService = lockService;
//     }

//     @Scheduled(fixedRate = 1000) // runs every 1 sec
//     public void tick() {
//         lockService.withLock("movement-tick", () -> {

//             ElevatorSystemState state = stateStore.load().orElse(null);
//             if (state == null) return null;

//             ElevatorSystemState updated = state.copy();

//             for (ElevatorState e : updated.getElevators().values()) {

//                 if (e.getStops().isEmpty()) {
//                     e.setDirection(Direction.IDLE);
//                     continue;
//                 }

//                 int current = e.getCurrentFloor();
//                 int target = e.getStops().getFirst();

//                 if (current < target) {
//                     e.setCurrentFloor(current + 1);
//                     e.setDirection(Direction.UP);
//                     e.setDoorStatus(DoorStatus.CLOSED);

//                 } else if (current > target) {
//                     e.setCurrentFloor(current - 1);
//                     e.setDirection(Direction.DOWN);
//                     e.setDoorStatus(DoorStatus.CLOSED);

//                 } else {
//                     // reached stop
//                     e.getStops().removeFirst();
//                     e.setDoorStatus(DoorStatus.OPENING);
//                 }
//             }

//             stateStore.save(updated);
//             return null;
//         });
//     }
// }