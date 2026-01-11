# MZA API Specification

**Version:** 0.0.1-SNAPSHOT  
**Base URL:** `http://<host>:8080`  
**Description:** Multi-zone audio control system API for managing audio zones, sources, and groups.

---

## Table of Contents

1. [REST API Endpoints](#rest-api-endpoints)
   - [Zones](#zones)
   - [Sources](#sources)
   - [Groups](#groups)
2. [MQTT Message Specification](#mqtt-message-specification)
3. [Data Models](#data-models)
4. [Error Handling](#error-handling)

---

## REST API Endpoints

### Zones

Zones represent physical audio output locations (e.g., living room, kitchen). Each zone can be connected to a source and has independent volume and mute controls.

#### Get All Zone States

Retrieves the current state of all configured zones.

```http
GET /api/zones
```

**Response:** `200 OK`

```json
[
  {
    "name": "living_room",
    "sourceName": "mpd",
    "volume": 75,
    "muted": false,
    "zoneDetails": {
      "name": "living_room",
      "description": "Living room speakers",
      "leftOutput": {
        "name": "Mix A"
      },
      "rightOutput": {
        "name": "Mix B"
      }
    },
    "sourceDetails": {
      "name": "mpd",
      "leftInput": {
        "name": "Input 01"
      },
      "rightInput": {
        "name": "Input 02"
      }
    }
  }
]
```

---

#### Get Available Zones

Retrieves all zones configured in the system (from configuration).

```http
GET /api/zones/available
```

**Response:** `200 OK`

```json
[
  {
    "name": "living_room",
    "description": "Living room speakers",
    "leftOutput": {
      "name": "Mix A"
    },
    "rightOutput": {
      "name": "Mix B"
    }
  },
  {
    "name": "kitchen",
    "description": "Kitchen speakers",
    "leftOutput": {
      "name": "Mix C"
    },
    "rightOutput": {
      "name": "Mix D"
    }
  }
]
```

---

#### Get Zone State

Retrieves the current state of a specific zone.

```http
GET /api/zones/{name}
```

**Path Parameters:**
- `name` (string, required) - Zone name (e.g., `living_room`)

**Response:** `200 OK`

```json
{
  "name": "living_room",
  "sourceName": "mpd",
  "volume": 75,
  "muted": false,
  "zoneDetails": {
    "name": "living_room",
    "description": "Living room speakers",
    "leftOutput": {
      "name": "Mix A"
    },
    "rightOutput": {
      "name": "Mix B"
    }
  },
  "sourceDetails": {
    "name": "mpd",
    "leftInput": {
      "name": "Input 01"
    },
    "rightInput": {
      "name": "Input 02"
    }
  }
}
```

**Error Response:** `500 Internal Server Error`

```json
{
  "message": "Zone not found"
}
```

---

#### Mute/Unmute Zone

Sets the mute state of a zone.

```http
PATCH /api/zones/{name}/mute?isMuted={boolean}
```

**Path Parameters:**
- `name` (string, required) - Zone name

**Query Parameters:**
- `isMuted` (boolean, required) - `true` to mute, `false` to unmute

**Response:** `200 OK`

Returns the updated `ZoneState` object.

---

#### Toggle Mute Zone

Toggles the mute state of a zone.

```http
PATCH /api/zones/{name}/toggleMute
```

**Path Parameters:**
- `name` (string, required) - Zone name

**Response:** `200 OK`

Returns the updated `ZoneState` object.

---

#### Set Zone Volume

Sets the volume of a zone to a specific percentage.

```http
PATCH /api/zones/{name}/volume?volumePercent={integer}
```

**Path Parameters:**
- `name` (string, required) - Zone name

**Query Parameters:**
- `volumePercent` (integer, required) - Volume percentage (0-100)

**Response:** `200 OK`

Returns the updated `ZoneState` object.

---

#### Increment Zone Volume

Adjusts the volume of a zone by a relative increment.

```http
PATCH /api/zones/{name}/incrementVolume?increment={integer}
```

**Path Parameters:**
- `name` (string, required) - Zone name

**Query Parameters:**
- `increment` (integer, required) - Volume increment (-20 to +20)
  - Positive values increase volume
  - Negative values decrease volume
  - Final volume is clamped to 0-100 range

**Validation:**
- `@Min(-20)` - Minimum increment is -20
- `@Max(20)` - Maximum increment is +20

**Response:** `200 OK`

Returns the updated `ZoneState` object.

---

#### Change Zone Source

Changes the audio source for a zone.

```http
PATCH /api/zones/{name}/source?sourceName={string}
```

**Path Parameters:**
- `name` (string, required) - Zone name

**Query Parameters:**
- `sourceName` (string, required) - Source name (e.g., `mpd`, `upnp1`)

**Behavior:**
- If the zone is currently unmuted, it will be temporarily muted during the source change to prevent audio glitches
- If the requested source is already active, no action is taken

**Response:** `200 OK`

Returns the updated `ZoneState` object.

**Error Response:** `500 Internal Server Error`

```json
{
  "message": "Source not found"
}
```

---

### Sources

Sources represent audio inputs (e.g., MPD, UPnP renderers, hardware inputs).

#### Get All Sources

Retrieves all configured audio sources.

```http
GET /api/sources
```

**Response:** `200 OK`

```json
[
  {
    "name": "mpd",
    "leftInput": {
      "name": "Input 01"
    },
    "rightInput": {
      "name": "Input 02"
    }
  },
  {
    "name": "upnp1",
    "leftInput": {
      "name": "Input 03"
    },
    "rightInput": {
      "name": "Input 04"
    }
  }
]
```

---

### Groups

Groups allow controlling multiple zones simultaneously.

#### Get All Groups

Retrieves all configured zone groups.

```http
GET /api/groups
```

**Response:** `200 OK`

```json
[
  {
    "name": "living_area",
    "zones": ["living_room", "kitchen"]
  },
  {
    "name": "all",
    "zones": [
      "living_room",
      "kitchen",
      "ground_floor_toilet",
      "first_floor_toilet",
      "main_bathroom",
      "laundry_room"
    ]
  }
]
```

---

#### Get Group

Retrieves a specific group by name.

```http
GET /api/groups/{name}
```

**Path Parameters:**
- `name` (string, required) - Group name

**Response:** `200 OK`

```json
{
  "name": "living_area",
  "zones": ["living_room", "kitchen"]
}
```

---

#### Mute/Unmute Group

Sets the mute state for all zones in a group.

```http
PATCH /api/groups/{name}/mute?isMuted={boolean}
```

**Path Parameters:**
- `name` (string, required) - Group name

**Query Parameters:**
- `isMuted` (boolean, required) - `true` to mute, `false` to unmute

**Response:** `200 OK`

Returns an array of updated `ZoneState` objects for all zones in the group.

---

#### Toggle Mute Group

Toggles the mute state for all zones in a group.

```http
PATCH /api/groups/{name}/toggleMute
```

**Path Parameters:**
- `name` (string, required) - Group name

**Response:** `200 OK`

Returns an array of updated `ZoneState` objects for all zones in the group.

---

#### Set Group Volume

Sets the volume for all zones in a group.

```http
PATCH /api/groups/{name}/volume?volumePercent={integer}
```

**Path Parameters:**
- `name` (string, required) - Group name

**Query Parameters:**
- `volumePercent` (integer, required) - Volume percentage (0-100)

**Response:** `200 OK`

Returns an array of updated `ZoneState` objects for all zones in the group.

---

#### Increment Group Volume

Adjusts the volume for all zones in a group by a relative increment.

```http
PATCH /api/groups/{name}/incrementVolume?increment={integer}
```

**Path Parameters:**
- `name` (string, required) - Group name

**Query Parameters:**
- `increment` (integer, required) - Volume increment (-20 to +20)

**Validation:**
- `@Min(-20)` - Minimum increment is -20
- `@Max(20)` - Maximum increment is +20

**Response:** `200 OK`

Returns an array of updated `ZoneState` objects for all zones in the group.

---

#### Set Group Source

Changes the audio source for all zones in a group.

```http
PATCH /api/groups/{name}/source?sourceName={string}
```

**Path Parameters:**
- `name` (string, required) - Group name

**Query Parameters:**
- `sourceName` (string, required) - Source name

**Response:** `200 OK`

Returns an array of updated `ZoneState` objects for all zones in the group.

---

## MQTT Message Specification

The MZA system publishes zone state changes to an MQTT broker for real-time monitoring and integration with home automation systems (e.g., Home Assistant).

### Connection Configuration

**Default Configuration:**
- **Broker URL:** `tcp://mccoy.lan:1883`
- **Client ID:** `mza`
- **Base Topic:** `mza/zone/`

These values are configurable via `application.yaml`:

```yaml
mqtt.broker.url: tcp://<broker-host>:<port>
mqtt.clientId: <client-id>
mqtt.topic.base: <base-topic>
```

### Topic Structure

All zone state updates are published to subtopics under the base topic:

```
<base-topic><zone-name>/<attribute>
```

**Example Topics:**
- `mza/zone/living_room/sourceName`
- `mza/zone/living_room/volume`
- `mza/zone/living_room/muted`
- `mza/zone/living_room/description`

### Published Messages

When a zone state changes, the following messages are published:

#### Source Name

**Topic:** `<base-topic><zone-name>/sourceName`

**Payload:** String (UTF-8 encoded)

**Example:**
```
Topic: mza/zone/living_room/sourceName
Payload: mpd
```

---

#### Volume

**Topic:** `<base-topic><zone-name>/volume`

**Payload:** String representation of integer (0-100)

**Example:**
```
Topic: mza/zone/living_room/volume
Payload: 75
```

---

#### Muted State

**Topic:** `<base-topic><zone-name>/muted`

**Payload:** String representation of boolean (`true` or `false`)

**Example:**
```
Topic: mza/zone/living_room/muted
Payload: false
```

---

#### Description

**Topic:** `<base-topic><zone-name>/description`

**Payload:** String (UTF-8 encoded)

**Condition:** Only published if `zoneDetails` is not null

**Example:**
```
Topic: mza/zone/living_room/description
Payload: Living room speakers
```

---

### Message Publishing Behavior

1. **Trigger:** Messages are published whenever a zone state is synchronized via the `ZoneRouter.syncZone()` method
2. **Reliability:** The system attempts to reconnect to the MQTT broker up to 4 times with 5-second delays if disconnected
3. **QoS:** Default QoS level (0 - at most once delivery)
4. **Retained:** All messages are retained on the broker, allowing new subscribers to immediately receive the latest state without waiting for the next update

### Startup Behavior

On application startup (`ApplicationReadyEvent`):

1. **Zone Reset:** All zones are muted and cycled through all sources to ensure a clean state
2. **State Restoration:** Previously saved zone states are restored from the database
3. **MQTT Publishing:** All restored states are published to MQTT with the retained flag

This ensures that MQTT subscribers receive the current state of all zones when the application starts, and new subscribers can immediately query the latest state from the broker.

---

## Data Models

### ZoneState

Represents the current state of a zone.

```json
{
  "name": "string",
  "sourceName": "string",
  "volume": 0,
  "muted": false,
  "zoneDetails": {
    "name": "string",
    "description": "string",
    "leftOutput": {
      "name": "string"
    },
    "rightOutput": {
      "name": "string"
    }
  },
  "sourceDetails": {
    "name": "string",
    "leftInput": {
      "name": "string"
    },
    "rightInput": {
      "name": "string"
    }
  }
}
```

**Fields:**
- `name` (string) - Zone identifier
- `sourceName` (string) - Currently active source name
- `volume` (integer) - Volume percentage (0-100)
  - Validation: `@Min(0)`, `@Max(100)`
- `muted` (boolean) - Mute state
- `zoneDetails` (Zone, optional) - Zone configuration details
- `sourceDetails` (Source, optional) - Source configuration details

---

### Zone

Represents a zone configuration.

```json
{
  "name": "string",
  "description": "string",
  "leftOutput": {
    "name": "string"
  },
  "rightOutput": {
    "name": "string"
  }
}
```

**Fields:**
- `name` (string) - Zone identifier
- `description` (string) - Human-readable description
- `leftOutput` (Output) - Left channel output configuration
- `rightOutput` (Output) - Right channel output configuration

---

### Source

Represents an audio source configuration.

```json
{
  "name": "string",
  "leftInput": {
    "name": "string"
  },
  "rightInput": {
    "name": "string"
  }
}
```

**Fields:**
- `name` (string) - Source identifier
- `leftInput` (Input) - Left channel input configuration
- `rightInput` (Input) - Right channel input configuration

**Source Types:**
- **PCM Sources:** Software mixer inputs (e.g., `mpd`, `upnp1`, `upnp2`)
- **Hardware Sources:** Physical audio inputs (e.g., `hw_source1`, `hw_source2`)

---

### Group

Represents a collection of zones that can be controlled together.

```json
{
  "name": "string",
  "zones": ["string"]
}
```

**Fields:**
- `name` (string) - Group identifier
- `zones` (array of strings) - List of zone names in the group

---

### Input

Represents an audio input channel.

```json
{
  "name": "string"
}
```

**Fields:**
- `name` (string) - Input channel name (e.g., `Input 01`)

---

### Output

Represents an audio output channel.

```json
{
  "name": "string"
}
```

**Fields:**
- `name` (string) - Output channel name (e.g., `Mix A`)

---

## Error Handling

### Error Response Format

```json
{
  "message": "string"
}
```

### Common Error Scenarios

#### Zone Not Found

**Status Code:** `500 Internal Server Error`

**Response:**
```json
{
  "message": "Zone not found"
}
```

**Cause:** The requested zone name does not exist in the configuration.

---

#### Source Not Found

**Status Code:** `500 Internal Server Error`

**Response:**
```json
{
  "message": "Source not found"
}
```

**Cause:** The requested source name does not exist in the configuration.

---

#### Group Not Found

**Status Code:** `500 Internal Server Error`

**Response:**
```json
{
  "message": "no group found"
}
```

**Cause:** The requested group name does not exist in the configuration.

---

#### Validation Errors

**Status Code:** `400 Bad Request`

**Cause:** Invalid parameter values (e.g., volume out of range, invalid increment).

---

## Configuration

### Application Configuration

The system is configured via `application.yaml`:

```yaml
# MQTT Configuration
mqtt.broker.url: tcp://<broker-host>:<port>
mqtt.clientId: <client-id>
mqtt.topic.base: <base-topic>

# Audio Interface
audio.interface:
  backend: AMIXER
  amixer:
    command: /usr/bin/amixer
    device: USB

# Groups
groups:
  - name: living_area
    zones:
      - living_room
      - kitchen

# Zones
zones:
  - name: living_room
    description: Living room speakers
    leftOutput:
      name: "Mix A"
    rightOutput:
      name: "Mix B"

# Sources
sources:
  - name: "mpd"
    leftInput:
      name: "Input 01"
    rightInput:
      name: "Input 02"
```

---

## Integration Examples

### Home Assistant MQTT Sensor

```yaml
mqtt:
  sensor:
    - name: "Living Room Volume"
      state_topic: "mza/zone/living_room/volume"
      unit_of_measurement: "%"
    
    - name: "Living Room Source"
      state_topic: "mza/zone/living_room/sourceName"
    
  binary_sensor:
    - name: "Living Room Muted"
      state_topic: "mza/zone/living_room/muted"
      payload_on: "true"
      payload_off: "false"
```

### cURL Examples

**Get all zones:**
```bash
curl http://localhost:8080/api/zones
```

**Set zone volume:**
```bash
curl -X PATCH "http://localhost:8080/api/zones/living_room/volume?volumePercent=80"
```

**Mute a zone:**
```bash
curl -X PATCH "http://localhost:8080/api/zones/living_room/mute?isMuted=true"
```

**Change zone source:**
```bash
curl -X PATCH "http://localhost:8080/api/zones/living_room/source?sourceName=mpd"
```

**Control a group:**
```bash
curl -X PATCH "http://localhost:8080/api/groups/living_area/volume?volumePercent=60"
```

---

## Notes

- All REST API endpoints use JSON for request and response bodies
- The system uses SQLite for persistent storage of zone states
- Zone states are automatically restored on application startup
- MQTT messages are published asynchronously and do not block API responses
- The audio interface backend (AMIXER) is configurable and can be extended
