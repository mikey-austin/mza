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

### Run with PIPEWIRE Backend (for development)

To control audio on the host system running Pipewire:

```bash
docker run -d \
  --name mza \
  -p 8080:8080 \
  --add-host=host.docker.internal:host-gateway \
  -v $HOME/.mza:/var/lib/mza \
  -v /run/user/1000/pipewire-0:/tmp/pipewire-0 \
  -e SPRING_PROFILES_ACTIVE=pipewire \
  -e PIPEWIRE_RUNTIME_DIR=/tmp \
  -e AUDIO_INTERFACE_BACKEND=PIPEWIRE \
  mza:latest
```

**Configuration (pipewire backend):**
- **Volume Mount**: Mounts the host's Pipewire socket (usually in `/run/user/<uid>/`) to a location in the container (e.g., `/tmp`).
- **Runtime Dir**: Sets `AUDIO_INTERFACE_PIPEWIRE_RUNTIME_DIR` to the directory containing the mounted socket (e.g., `/tmp`).
- **Backend**: Overrides the default backend of the profile with `AUDIO_INTERFACE_BACKEND=PIPEWIRE`.

### Remote PipeWire Debugging with qpwgraph/helvum

To visualize and debug the PipeWire graph on a remote headless machine using qpwgraph or helvum on your local workstation, you can forward the PipeWire socket over SSH.

**1. Create a directory and forward the remote PipeWire socket:**

```bash
# Create directory for the forwarded socket
mkdir -p /tmp/remote-pipewire

# Forward the socket (replace 'user@remote-host' with your server)
# The socket must be named 'pipewire-0' in the directory
ssh -L /tmp/remote-pipewire/pipewire-0:/run/user/1000/pipewire-0 user@remote-host
```

**2. In another terminal, run a PipeWire GUI with the forwarded socket:**

```bash
# All three environment variables are required
PIPEWIRE_RUNTIME_DIR=/tmp/remote-pipewire \
XDG_RUNTIME_DIR=/tmp/remote-pipewire \
PIPEWIRE_REMOTE=pipewire-0 \
helvum

# Or with qpwgraph
PIPEWIRE_RUNTIME_DIR=/tmp/remote-pipewire \
XDG_RUNTIME_DIR=/tmp/remote-pipewire \
PIPEWIRE_REMOTE=pipewire-0 \
qpwgraph
```

You can also run other PipeWire tools against the remote instance:

```bash
# List remote nodes
PIPEWIRE_RUNTIME_DIR=/tmp/remote-pipewire XDG_RUNTIME_DIR=/tmp/remote-pipewire PIPEWIRE_REMOTE=pipewire-0 pw-cli list-objects

# Dump remote graph
PIPEWIRE_RUNTIME_DIR=/tmp/remote-pipewire XDG_RUNTIME_DIR=/tmp/remote-pipewire PIPEWIRE_REMOTE=pipewire-0 pw-dump
```

**Note:** The SSH session must remain open for the socket forwarding to work. The remote user's UID (1000 in the example) should match the user running PipeWire on the remote machine.

### Configuring Zones and Sources

Zones and sources are configured in your application YAML file. Each zone represents an audio output destination (e.g., a room with speakers), and each source represents an audio input (e.g., a media player).

**Zone configuration:**
```yaml
zones:
  - name: living_room
    description: Living room speakers
    left:
      name: "playback_FL"    # Left channel port/mixer name
    right:
      name: "playback_FR"    # Right channel port/mixer name
```

**Source configuration:**
```yaml
sources:
  - name: "mpd"
    left:
      name: "monitor_FL"     # Left channel port/mixer name
    right:
      name: "monitor_FR"     # Right channel port/mixer name
```

The `left` and `right` properties define the audio channel mapping. For mono zones/sources, use the same port name for both channels.

For backwards compatibility, `leftOutput`/`rightOutput` (zones) and `leftInput`/`rightInput` (sources) are also accepted.

#### PipeWire Node:Port Format

When using the PipeWire backend, channel names support an extended `node:port` format that allows specifying a different PipeWire node for each channel. This is useful for hardware sources where the node name differs from the logical source name.

**Simple format** (node derived from source/zone name):
```yaml
sources:
  - name: "mpd"
    left:
      name: "monitor_FL"      # Uses node "mpd" with port "monitor_FL"
    right:
      name: "monitor_FR"      # Uses node "mpd" with port "monitor_FR"
```

**Extended node:port format** (explicit node name):
```yaml
sources:
  - name: "hw_source1"
    left:
      name: "alsa_input.usb-Focusrite_Scarlett_18i20:capture_AUX0"
    right:
      name: "alsa_input.usb-Focusrite_Scarlett_18i20:capture_AUX1"
```

In the extended format, the part before the colon is the PipeWire node name, and the part after is the port name. This allows hardware inputs to have user-friendly source names (e.g., "hw_source1") while routing audio from specific PipeWire nodes.

The same format works for zones:
```yaml
zones:
  - name: "kitchen"
    left:
      name: "alsa_output.usb-audio-device:playback_FL"
    right:
      name: "alsa_output.usb-audio-device:playback_FR"
```

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
