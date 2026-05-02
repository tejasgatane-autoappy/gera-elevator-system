package com.gera.elevator.store;

import java.util.function.Supplier;

public interface StateLockService {

    <T> T withLock(String operationName, Supplier<T> work);
}
