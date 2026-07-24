# Planning Poker

A minimal, real-time planning poker room. No accounts, no projects, no
ticket management — just: create a room, share the link, everyone picks a
card, reveal, done.

```
planning-poker/
├── backend/    Spring Boot API + WebSocket server (Java 17)
│   ├── README.md   ← backend-specific docs
│   └── docker-compose.yml
└── frontend/   Angular app (2 pages: home, room)
    ├── README.md   ← frontend-specific docs
    └── docker-compose.yml
```

This doc covers how the two apps fit together. For details on what's
inside each one, see `backend/README.md` and `frontend/README.md`.

## How it works, end to end

1. Someone opens the app and clicks **Create a room**. The backend
   generates a short room ID (like `k7p2mq`) and the browser navigates to
   `/rooms/k7p2mq`.
2. They share that URL. Anyone who opens it is asked for their **name**
   (nothing else — no login).
3. Once a name is submitted, the browser opens a **WebSocket** connection
   to the backend for that room. Everyone connected to the same room ID is
   in the same "table."
4. Each person picks a card (0, 1, 2, 3, 5, 8, 13, 21, 34, ?, ☕). As people
   vote, everyone sees *who* has voted (a dot) but not the value yet.
5. Anyone can click **Reveal** — the backend flips every vote to its real
   value and broadcasts that to everyone at once.
6. **Reset** clears all votes for a new round.

There's no database. A `Room` is just an object living in the backend's
memory for as long as the server process runs. That's intentional — it
matches "keep it as simple as possible." If you restart the backend,
existing room links stop working. (More on this in `backend/README.md`.)

## Running both together

Each app runs independently and doesn't need to know about the other at
build or container level — they only connect at runtime, over HTTP/
WebSocket, from the browser. See each app's own README for its specific
run options (with/without Docker); the short version:

```bash
# Terminal 1
cd backend
mvn spring-boot:run          # or: docker compose up --build

# Terminal 2
cd frontend
npm install && npm start     # or: docker compose up --build
```

Then open **http://localhost:4200**. The frontend expects the backend on
**http://localhost:8080** — that's a hardcoded value in
`frontend/src/app/core/config.ts`, not auto-detected, so if you ever run
the backend on a different port you'll need to update it there too (see
`frontend/README.md`).

> Neither backend nor frontend Docker builds were run to completion in
> the sandbox this was built in (no Docker available there) — they're
> built on standard, common patterns, but your first `docker compose up`
> for each is the real test. A couple of issues already surfaced and got
> fixed this way (an Alpine base image that doesn't support arm64, and a
> transient npm network error) — if you hit something new, share the
> exact error and it's usually a quick fix.

## The WebSocket protocol

This is the contract between the two apps — the one thing that has to
stay in sync if you change either side independently. Documented in full
in `backend/README.md`, but the short version:

**Browser → server** (one of):
```json
{ "action": "vote", "value": 5 }
{ "action": "reveal" }
{ "action": "reset" }
```

**Server → browser** (broadcast to everyone in the room after any action,
or whenever someone joins/leaves):
```json
{
  "type": "state",
  "room_id": "k7p2mq",
  "revealed": false,
  "participants": [
    { "name": "Amara", "has_voted": true, "value": null },
    { "name": "Diego", "has_voted": false, "value": null }
  ],
  "card_values": [0, 1, 2, 3, 5, 8, 13, 21, 34, "?", "coffee"]
}
```
Once `revealed` is `true`, each participant's `value` is filled in instead
of `null`.
