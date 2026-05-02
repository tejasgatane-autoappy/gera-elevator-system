# Gera Elevator System Backend

Production-style Java Spring Boot backend for a configurable building with 4 elevators by default: `A`, `B`, `C`, and `D`.

## What Is Included

- Spring Boot 3.5.14, Java 21, Maven.
- Redis-backed source of truth for elevator state and recovery after app restart or power outage.
- Redis event log for recent assignments and audit/debugging.
- Distributed Redis lock around scheduling writes to prevent concurrent request races.
- REST API for frontend integration: state, external requests, internal requests, telemetry, reset, and events.
- Full validation, deterministic tie-breaking, and consistent error responses.
- Automated unit and API tests for real-world scheduler scenarios.
- Manual OpenAPI contract in [docs/openapi.yaml](docs/openapi.yaml).
- Test matrix in [docs/test-scenarios.md](docs/test-scenarios.md).

## Core Rules

Priority order for external calls:

1. Pick a lift moving in the same direction that will pass the requested floor, using minimum estimated arrival time.
2. Otherwise pick the nearest idle lift.
3. Otherwise pick the lift with the minimum estimated arrival time after finishing its current route.

Time constants:

- Move 1 floor: `30` seconds.
- Door open: `10` seconds.
- Door close: `10` seconds.
- ETA excludes door time at the requested floor, but includes door cycles for stops before that floor.

## Redis Recovery Model

Redis stores:

- `gera:elevator:state:current`: current full system snapshot.
- `gera:elevator:events`: recent assignment/telemetry/reset events.
- `gera:elevator:lock:state`: distributed lock for write safety.

If the backend restarts, it loads `gera:elevator:state:current`. If no state exists, it initializes all elevators at floor `1` with `IDLE` direction. The provided Docker Compose Redis enables append-only persistence, so Redis can also recover after machine or power failure.

## Run In VS Code

Prerequisites:

- Java 21.
- Maven 3.6.3+.
- Docker Desktop or a locally running Redis.

Start Redis:

```powershell
docker compose up -d redis
```

Run the backend:

```powershell
mvn spring-boot:run
```

Backend URL:

```text
http://localhost:8080
```

Health check:

```text
GET http://localhost:8080/actuator/health
```

## Frontend Integration

Use these endpoints from the React/Tailwind frontend:

- `GET /api/v1/elevators`
- `POST /api/v1/requests`
- `POST /api/v1/requests/external`
- `POST /api/v1/elevators/{elevatorId}/requests`
- `PUT /api/v1/elevators/{elevatorId}/telemetry`
- `GET /api/v1/events`

Default CORS allows:

- `http://localhost:3000`
- `http://localhost:5173`
- `http://127.0.0.1:3000`
- `http://127.0.0.1:5173`

## Example Request

```json
{
  "type": "EXTERNAL",
  "floor": 4,
  "direction": "UP"
}
```

Example response:

```json
{
  "assignedElevator": "A",
  "estimatedArrivalTime": 60,
  "stopsUpdated": [4, 6],
  "reason": "SAME_DIRECTION_PASSING",
  "stateVersion": 2
}
```

## Run Tests

```powershell
mvn test
```

Tests use the in-memory store profile, so Redis is not required for unit/API tests.
