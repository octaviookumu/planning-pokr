# Planning Poker Backend: Logic Flow

This backend creates short-lived planning-poker rooms and keeps every connected browser in sync through WebSockets. It has no database: rooms, votes, and connections exist only while the server is running.

## Start here

`src/main/java/com/pokerroom/PokerRoomApplication.java` is the entry point. When it starts, Spring Boot scans `com.pokerroom` and creates the application components, including the room store, REST controller, WebSocket handler, and configuration.

```text
PokerRoomApplication
  └─ Spring creates and wires application components
       ├─ RoomStore                 (in-memory rooms)
       ├─ RoomController            (REST API)
       ├─ RoomConnectionManager     (live WebSocket connections)
       ├─ RoomStateBuilder          (shared state JSON)
       └─ RoomWebSocketHandler      (WebSocket events)
```

## The data model

The central object is `src/main/java/com/pokerroom/model/Room.java`.

```text
Room
  id         shareable room code
  createdAt  creation time
  revealed   whether card values are visible
  votes      participant name → chosen card value
```

`src/main/java/com/pokerroom/store/RoomStore.java` holds these rooms in a concurrent in-memory map:

```text
room ID → Room
```

There is no persistence. Restarting the server clears all rooms.

## Flow 1: create a room with REST

The frontend calls `POST /api/rooms`.

```text
POST /api/rooms
  ↓
RoomController
  ↓
RoomStore.createRoom()
  ├─ generate a candidate ID
  ├─ repeat while that ID already exists
  └─ create and store a Room
  ↓
RoomResponse.from(room)
  ↓
201 Created
{ "id": "ABC123", "created_at": "..." }
```

Relevant files:

- `src/main/java/com/pokerroom/controller/RoomController.java` receives the HTTP request.
- `src/main/java/com/pokerroom/store/RoomStore.java` generates the unique ID and stores the room.
- `src/main/java/com/pokerroom/dto/RoomResponse.java` selects the fields returned to the client.

To check a room before joining, the frontend calls `GET /api/rooms/{id}`. The controller looks up the ID through `RoomStore`; it returns the room response if present, or a not-found response otherwise.

## Flow 2: join a room over WebSocket

A browser connects using a URL like:

```text
ws://localhost:8080/ws/rooms/ABC123?name=Octavian
```

`src/main/java/com/pokerroom/config/WebSocketConfig.java` registers this URL pattern and attaches the handshake interceptor.

```text
WebSocket connection attempt
  ↓
RoomHandshakeInterceptor.beforeHandshake()
  ├─ read room ID from /ws/rooms/{roomId}
  ├─ read name from ?name=...
  ├─ confirm the room exists
  ├─ reject with 404 if it does not
  ├─ reject with 400 if the name is blank
  └─ save roomId and name in session attributes
  ↓
RoomWebSocketHandler.afterConnectionEstablished()
  ↓
RoomConnectionManager.add()
  ↓
broadcast the current state to everybody in the room
```

The session attributes are important: the interceptor validates and saves `roomId` and `name` once. Later WebSocket messages use those saved values rather than trusting the client to send them again.

Joining is only the first part of the WebSocket flow. The handler then builds a room state and sends it to every connected browser. It repeats this whenever someone joins, votes, reveals, resets, or leaves.

## The WebSocket mental model

Keep these four responsibilities separate when reading the code:

```text
RoomStore
  = persistent-for-this-server-run room data: votes and reveal status

RoomConnectionManager
  = live connections: which sockets are currently in each room

RoomStateBuilder
  = combines room data and live connections into the JSON the frontend needs

RoomWebSocketHandler
  = reacts to join, client message, and disconnect events
```

## Flow 3: build and broadcast the room state

After every join, message, or disconnect, the handler broadcasts a fresh state object.

```text
RoomWebSocketHandler.broadcastState(roomId)
  ↓
RoomStateBuilder.build(roomId)
  ├─ get room data from RoomStore
  ├─ get connected names from RoomConnectionManager
  ├─ mark each person as voted/not voted
  ├─ include vote values only when revealed
  └─ add the available card values
  ↓
ObjectMapper converts the map to JSON
  ↓
RoomConnectionManager.broadcast()
  ↓
send JSON to every open socket in that room
```

An unrevealed state has values hidden:

```json
{
  "type": "state",
  "room_id": "ABC123",
  "revealed": false,
  "participants": [
    { "name": "Octavian", "has_voted": true, "value": null }
  ]
}
```

Once revealed, the same participant can receive their selected value:

```json
{ "name": "Octavian", "has_voted": true, "value": "5" }
```

## Flow 4: vote, reveal, and reset

Incoming WebSocket messages are handled by `src/main/java/com/pokerroom/websocket/RoomWebSocketHandler.java`.

```text
Client sends JSON
  ↓
handleTextMessage(session, message)
  ├─ get roomId and name from session attributes
  ├─ find the Room
  ├─ parse the action
  ├─ update the Room
  └─ broadcast a new state
```

Supported actions:

| Client message | Change made to the room |
| --- | --- |
| `{ "action": "vote", "value": "5" }` | Stores `name → "5"` in `Room.votes`. A later vote by the same name replaces their previous one. |
| `{ "action": "reveal" }` | Sets `revealed` to `true`, so vote values appear in the next state. |
| `{ "action": "reset" }` | Clears all votes and sets `revealed` back to `false`. |

Malformed or unrecognised messages do not end the connection. The server simply leaves the room unchanged and broadcasts its current state.

## Flow 5: leave a room

```text
Browser closes tab / connection drops
  ↓
RoomWebSocketHandler.afterConnectionClosed()
  ↓
RoomConnectionManager.remove()
  ↓
broadcast updated state to remaining connections
```

`RoomConnectionManager` removes the socket connection, not the participant's vote. Votes belong to names in `Room.votes`, so a participant who reconnects using the same name can retain their vote for the current round.

## Responsibility map

| File | Role in the flow |
| --- | --- |
| `src/main/java/com/pokerroom/PokerRoomApplication.java` | Starts Spring Boot. |
| `src/main/java/com/pokerroom/model/Room.java` | Holds a room's ID, timestamp, reveal status, and votes. |
| `src/main/java/com/pokerroom/store/RoomStore.java` | Creates and finds in-memory rooms. |
| `src/main/java/com/pokerroom/controller/RoomController.java` | Handles REST room creation and lookup. |
| `src/main/java/com/pokerroom/dto/RoomResponse.java` | Defines the REST response shape. |
| `src/main/java/com/pokerroom/config/CorsConfig.java` | Permits approved browser origins to call `/api/**`. |
| `src/main/java/com/pokerroom/config/WebSocketConfig.java` | Registers `/ws/rooms/*`, origin rules, and handshake validation. |
| `src/main/java/com/pokerroom/websocket/RoomHandshakeInterceptor.java` | Validates a WebSocket join and saves room/name into the session. |
| `src/main/java/com/pokerroom/websocket/RoomConnectionManager.java` | Tracks live sockets and sends broadcasts. |
| `src/main/java/com/pokerroom/websocket/RoomStateBuilder.java` | Produces the state every participant sees. |
| `src/main/java/com/pokerroom/websocket/RoomWebSocketHandler.java` | Responds to join, message, and disconnect events. |
| `src/main/resources/application.properties` | Sets the server and JSON naming configuration. |

## Configuration boundary

`CorsConfig` applies to normal REST calls under `/api/**`. WebSocket connections use `WebSocketConfig.setAllowedOriginPatterns(...)` instead. Both use the configured `app.cors.allowed-origins` property, but they cover different kinds of browser requests.

## Current behavioural limits

- Rooms and votes disappear after a server restart.
- Any connected participant can reveal or reset a round.
- Names are deduplicated in the visible participant list; two people using the same name appear as one participant and share that name's vote.
- This is intended for local HTTP/`ws://` use; production deployment should use TLS and `wss://`.
