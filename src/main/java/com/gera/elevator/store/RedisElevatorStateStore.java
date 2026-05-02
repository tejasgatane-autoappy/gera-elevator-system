package com.gera.elevator.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gera.elevator.config.ElevatorProperties;
import com.gera.elevator.exception.StateStoreUnavailableException;
import com.gera.elevator.state.ElevatorEvent;
import com.gera.elevator.state.ElevatorSystemState;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "elevator.system", name = "state-store", havingValue = "redis", matchIfMissing = true)
public class RedisElevatorStateStore implements ElevatorStateStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ElevatorProperties properties;

    public RedisElevatorStateStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ElevatorProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<ElevatorSystemState> load() {
        try {
            String payload = redisTemplate.opsForValue().get(properties.getRedis().getStateKey());
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(payload, ElevatorSystemState.class));
        } catch (JsonProcessingException | DataAccessException ex) {
            throw new StateStoreUnavailableException("Unable to load elevator state from Redis", ex);
        }
    }

    @Override
    public void save(ElevatorSystemState state) {
        try {
            redisTemplate.opsForValue().set(properties.getRedis().getStateKey(), objectMapper.writeValueAsString(state));
        } catch (JsonProcessingException | DataAccessException ex) {
            throw new StateStoreUnavailableException("Unable to save elevator state to Redis", ex);
        }
    }

    @Override
    public void appendEvent(ElevatorEvent event) {
        try {
            redisTemplate.opsForList().leftPush(properties.getRedis().getEventsKey(), objectMapper.writeValueAsString(event));
            redisTemplate.opsForList().trim(
                    properties.getRedis().getEventsKey(),
                    0,
                    properties.getRedis().getEventHistoryLimit() - 1
            );
        } catch (JsonProcessingException | DataAccessException ex) {
            throw new StateStoreUnavailableException("Unable to append elevator event to Redis", ex);
        }
    }

    @Override
    public List<ElevatorEvent> recentEvents(int limit) {
        try {
            List<String> values = redisTemplate.opsForList().range(properties.getRedis().getEventsKey(), 0, limit - 1);
            if (values == null) {
                return List.of();
            }
            return values.stream()
                    .map(this::readEvent)
                    .toList();
        } catch (DataAccessException ex) {
            throw new StateStoreUnavailableException("Unable to read elevator events from Redis", ex);
        }
    }

    private ElevatorEvent readEvent(String payload) {
        try {
            return objectMapper.readValue(payload, ElevatorEvent.class);
        } catch (JsonProcessingException ex) {
            throw new StateStoreUnavailableException("Unable to deserialize elevator event from Redis", ex);
        }
    }
}
