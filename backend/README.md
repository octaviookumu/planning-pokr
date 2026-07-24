# Planning Poker — Backend

Spring Boot API + WebSocket server for the planning poker app. See the
[top-level README](../README.md) for how this fits together with the
frontend; this doc covers just this half.

## Running it

### Without Docker

Requires Java 17+ and Maven.

```bash
mvn spring-boot:run
```

Starts the API at **http://localhost:8080**. There's no database to set
up — see "Where the data lives" below.

### With Docker

```bash
docker compose up --build
```

Same result, same port (8080), just containerized. See the note in the
top-level README about this being unverified in a sandboxed environment —
if the build fails on a base image ("no match for platform" or similar),
that's almost always an architecture-mismatch issue with a specific base
image tag, not a problem with the app code itself.

## Where the data lives

There's no database. A `Room` is a plain Java object that lives in memory
for as long as the server process runs. Restarting the server (or the
container) clears every room. This is intentional — the brief was to keep
this as simple as possible, and a real database felt like overkill for
ephemeral voting rooms that only need to exist for the length of a
meeting.

## File-by-file walkthrough

Everything lives under `src/main/java/com/pokerroom/`.

### `PokerRoomApplication.java`
The entry point. `@SpringBootApplication` is what tells Spring Boot to
scan this package (and everything under it) for components, wire them
together, and start an embedded web server. Running this class *is*
running the whole backend.

### `model/Room.java`
The only piece of "data" in this app. A `Room` holds:
- `id` — the short shareable code
- `revealed` — whether votes are currently shown or hidden
- `votes` — a `Map<String, String>` of participant name → their chosen
  card value

Votes are keyed by **name**, not by WebSocket connection. That's
deliberate: if someone's laptop sleeps and their connection drops, then
reconnects a few seconds later, their vote for the current round is still
there — it isn't tied to the specific socket, just to who they are.

### `store/RoomStore.java`
A simple in-memory registry: `Map<String, Room>`. `createRoom()` generates
a random 6-character ID (skipping visually confusing characters like `0`
vs `O` or `1` vs `l`, so a room code is easy to read aloud or type from
memory) and stores a new `Room` under it. `find(id)` looks one up. This is
the entire "database" for the app.

### `dto/RoomResponse.java`
The JSON shape returned by the REST endpoints below — just `{id,
created_at}`. Using a Java `record` here is just a compact way to declare
an immutable data holder without writing getters by hand.

### `controller/RoomController.java`
Two plain REST endpoints:
- `POST /api/rooms` — create a room, return its ID
- `GET /api/rooms/{id}` — check a room exists (the frontend calls this
  before showing the "enter your name" screen, so it can show a clear
  "room not found" message instead of a confusing blank page)

### `config/CorsConfig.java`
Browsers block a page on `localhost:4200` from calling an API on
`localhost:8080` unless the server explicitly allows it. This tells Spring
to add the right CORS headers for anything under `/api/**`. (There's no
Spring Security in this version — no login means nothing to secure at the
HTTP layer beyond CORS.)

### The WebSocket pieces (`websocket/` package)

This is the real-time heart of the app. Four small classes work together:

**`RoomHandshakeInterceptor.java`** — runs once, right when a browser
tries to open a WebSocket connection (before the connection is accepted).
It reads the room ID out of the URL path and the participant's name out of
the `?name=` query parameter, checks the room actually exists, and — if
everything looks good — stashes `roomId` and `name` as attributes on the
session so the handler can use them later. If the room doesn't exist, it
rejects the connection with a 404 before it's even established.

**`RoomConnectionManager.java`** — keeps track of *which browser
connections are currently open for which room*. Think of it as the
"who's actually at the table right now" list. It's a
`Map<roomId, Set<Connection>>` where each `Connection` pairs a raw
WebSocket session with the name that came with it. `broadcast()` loops
over everyone connected to a room and pushes a message to each of them.

**`RoomStateBuilder.java`** — the "what does everyone see right now"
logic. Given a room ID, it builds a plain `Map` describing:
- who's connected (from `RoomConnectionManager`)
- whether each of them has voted yet (from `Room.votes`)
- if the round is revealed, their actual value; otherwise `null`
- the fixed list of possible card values

This map gets serialized straight to JSON and sent to everyone — it's the
single source of truth the frontend renders from.

**`RoomWebSocketHandler.java`** — the actual WebSocket endpoint logic.
Three lifecycle methods matter:
- `afterConnectionEstablished` — someone just joined; register them and
  broadcast the updated state to everyone
- `handleTextMessage` — someone sent a message. Messages are tiny JSON
  objects like `{"action": "vote", "value": 5}`, `{"action": "reveal"}`,
  or `{"action": "reset"}`. This method applies whichever action to the
  `Room`, then rebroadcasts the new state to everyone
- `afterConnectionClosed` — someone left (closed the tab, lost
  connection); remove them from the connected list and rebroadcast (so
  everyone else's participant list updates)

Note that *any* participant can reveal or reset — there's no "room owner"
concept, matching the "keep it simple" brief. If you want a facilitator
role later, that's the natural place to add it.

### `config/WebSocketConfig.java`
Wires the handler and interceptor into Spring's WebSocket support,
registering the handler at `/ws/rooms/*` and only allowing connections
from `http://localhost:4200` (same purpose as the CORS config, but for
WebSocket upgrades specifically, which aren't covered by regular CORS
rules).

### `application.properties`
Two settings: the server port, and
`spring.jackson.property-naming-strategy=SNAKE_CASE`, which makes Spring
serialize JSON keys as `created_at` instead of `createdAt`, `has_voted`
instead of `hasVoted`, etc. — matching what the Angular code expects,
without needing to annotate every field by hand.

## Docker files

- `Dockerfile` — multi-stage build: compiles the jar with Maven in a
  throwaway build stage, then copies just the jar into a slim JRE-only
  image for the final runtime image (no Maven, no JDK, no source code in
  what actually ships)
- `docker-compose.yml` — runs that image, publishing port 8080
- `.dockerignore` — keeps `target/` and IDE files out of the build context

## Known limitations / natural next steps

- **No persistence** — restarting the backend drops all rooms.
- **No room owner / facilitator** — anyone can reveal or reset.
- **No duplicate-name handling** — two people can join as "Sam" and
  they'll be shown as one row (participant names are deduplicated).
- **HTTP only** — for anything beyond local use, you'd want TLS in front
  of this (a reverse proxy like Caddy or an ALB) and `wss://` instead of
  `ws://`.
