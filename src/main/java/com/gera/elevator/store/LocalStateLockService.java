package com.gera.elevator.store;

import com.gera.elevator.config.ElevatorProperties;
import com.gera.elevator.exception.StateLockTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "elevator.system", name = "state-store", havingValue = "memory")
public class LocalStateLockService implements StateLockService {

    private final ReentrantLock lock = new ReentrantLock();
    private final ElevatorProperties properties;

    public LocalStateLockService(ElevatorProperties properties) {
        this.properties = properties;
    }

    @Override
    public <T> T withLock(String operationName, Supplier<T> work) {
        boolean locked = false;
        try {
            locked = lock.tryLock(properties.getLockTimeoutMillis(), TimeUnit.MILLISECONDS);
            if (!locked) {
                throw new StateLockTimeoutException("Timed out waiting for local elevator state lock");
            }
            return work.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new StateLockTimeoutException("Interrupted while waiting for local elevator state lock");
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }
}
