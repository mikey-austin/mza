# MZA - Multi-Zone Audio Control

Multi-zone audio control system for managing audio playback across multiple zones with dynamic group management.

## Features

- **Dynamic Group Management**: Snapcast-style exclusive membership - every zone belongs to exactly one group
- **Auto-Group Creation**: Orphaned zones automatically get single-member groups
- **REST API**: Full CRUD operations for zones and groups
- **MQTT Integration**: Real-time state updates via MQTT
- **SQLite Database**: Persistent state storage
- **Audio Backend Support**: AMIXER and DUMMY backends

## Technology Stack

- **Java 25** (LTS)
- **Spring Boot 3.5.9**
- **SQLite** for persistence
- **Eclipse Paho MQTT** for messaging
- **Docker** for containerization

## Running Locally with Docker

### Prerequisites

- Docker installed
- MQTT broker running (e.g., Mosquitto)

### Build the Application

```bash
# Build the JAR
./mvnw clean package -DskipTests

# Build the Docker image
docker build -t mza:latest .
```

### Run with Dummy Backend (for testing)

The dummy backend is perfect for testing without actual audio hardware:

```bash
sudo docker run -d \
  --name mza \
  -p 8080:8080 \
  --add-host=host.docker.internal:host-gateway \
  -v /tmp/mza:/var/lib/mza \
  -e SPRING_PROFILES_ACTIVE=dummy \
  -e MQTT_BROKER_URL=tcp://host.docker.internal:2883 \
  mza:latest
```

**Configuration (dummy profile):**
- Database: `/var/lib/mza/mza.db` (persistent via volume mount to `/tmp/mza` on host)
- Audio Backend: DUMMY (no actual audio control)
- MQTT: Connects to broker at `host.docker.internal:2883`
- **Network**: `--add-host` allows access to services running on the host machine

### Run with AMIXER Backend (for production)

For actual audio control with ALSA:

```bash
docker run -d \
  --name mza \
  -p 8080:8080 \
  --device /dev/snd \
  --group-add audio \
  --add-host=host.docker.internal:host-gateway \
  -v /home/mikey/.mza:/data \
  -e SPRING_PROFILES_ACTIVE=amixer \
  -e MQTT_BROKER_URL=tcp://host.docker.internal:2883 \
  mza:latest
```

**Configuration (amixer profile):**
- Database: `/data/mza.db` (persistent via volume mount)
- Audio Backend: AMIXER (controls actual audio hardware)
- Audio Device: USB (configurable)
- Requires: `/dev/snd` device access and audio group membership
- **Network**: `--add-host` allows access to services running on the host machine

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active profile (`dummy` or `amixer`) | - |
| `MQTT_BROKER_URL` | MQTT broker URL | `tcp://localhost:1883` |
| `MQTT_CLIENT_ID` | MQTT client ID | `mza` |

### Testing the API

Once running, access the API at `http://localhost:8080`:

```bash
# Get all zones
curl http://localhost:8080/api/zones

# Get all groups
curl http://localhost:8080/api/groups

# Create a new group
curl -X POST http://localhost:8080/api/groups \
  -H "Content-Type: application/json" \
  -d '{
    "name": "living_room",
    "displayName": "Living Room",
    "zones": ["zone1", "zone2"],
    "description": "Main living area"
  }'
```

### Viewing Logs

```bash
# Follow logs
docker logs -f mza

# View last 100 lines
docker logs --tail 100 mza
```

### Stopping and Removing

```bash
# Stop the container
docker stop mza

# Remove the container
docker rm mza
```

## Development

### Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=GroupServiceTest
```

### Building

```bash
# Build without tests
./mvnw clean package -DskipTests

# Build with tests
./mvnw clean package
```

## API Documentation

See [api-spec.md](api-spec.md) for detailed API documentation.

## Architecture

- **GroupService**: Business logic for group management
- **GroupRouter**: MQTT publishing for group state
- **GroupStateRepository**: JPA repository for persistence
- **Audio Backends**: Pluggable audio control (AMIXER, DUMMY)

## License

This project is licensed under the GNU General Public License v3.0 - see [https://www.gnu.org/licenses/gpl-3.0.txt](https://www.gnu.org/licenses/gpl-3.0.txt) for details.

MZA is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
