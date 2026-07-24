# Planning Poker — Frontend

Angular app for the planning poker room: a home page and a room page,
nothing else. See the [top-level README](../README.md) for how this fits
together with the backend; this doc covers just this half.

## Running it

### Without Docker (recommended for day-to-day development)

Requires Node.js 18+.

```bash
npm install
npm start
```

Starts the dev server at **http://localhost:4200** with hot reload —
saving a file refreshes the browser automatically, no rebuild step. This
is `ng serve` under the hood (see the `start` script in `package.json`).

The backend URL is **not** auto-detected — it's a hardcoded constant in
`src/app/core/config.ts`. As long as the backend is running on port 8080
(the default for `mvn spring-boot:run` and its Docker setup), this just
works with no changes needed.

### With Docker

```bash
docker compose up --build
```

Builds the app and serves the static output via nginx on
**http://localhost:4200**. Slower to iterate with than `npm start` (no hot
reload — every change needs a full rebuild), so this is more useful for
production-like testing or deployment than for active development.

### Plain static build (no Docker, no dev server)

```bash
npm run build
```

Outputs static files to `dist/frontend/browser/` — droppable onto any
static host (nginx, Caddy, Netlify, S3+CloudFront, `npx serve`, etc.)
regardless of whether you use Docker anywhere else in your stack.

## File-by-file walkthrough

Everything lives under `src/app/`.

### `core/models/models.ts`
The three shapes of data that flow between frontend and backend:
- `RoomInfo` — what `POST /api/rooms` and `GET /api/rooms/{id}` return
- `Participant` — one row in the participant list (`name`, `has_voted`,
  `value`)
- `RoomState` — the full WebSocket broadcast: the room ID, whether votes
  are revealed, the list of `Participant`s, and the possible card values

These are just TypeScript types for editor autocomplete and compile-time
safety — they don't do anything at runtime.

### `core/config.ts`
The two backend URLs (`http://localhost:8080/api` for REST,
`ws://localhost:8080` for WebSocket). If you deploy this somewhere other
than localhost, or run the backend on a different port, this is the one
file to change.

### `core/services/room.service.ts`
A thin wrapper around the two REST calls (`createRoom`, `checkRoom`).
Nothing clever — just keeps the raw `HttpClient` calls in one place
instead of scattered through components.

### `core/services/room-socket.service.ts`
The WebSocket client counterpart to the backend's `RoomWebSocketHandler`.
- `connect(roomId, name)` opens the socket (name goes in the URL as a
  query param, same as the backend expects)
- `vote()`, `reveal()`, `reset()` each send one of those small JSON action
  messages
- Every incoming message updates a `signal` called `state` — Angular
  signals are a reactive value holder; anything in a template that reads
  `roomState()` automatically re-renders when a new message arrives,
  without you having to manually wire up change detection
- `connected` is a second signal just tracking whether the socket is
  currently open, used to show the little connection-status dot

### `features/home/home.component.*`
The very first screen. One button: **Create a room**. Clicking it calls
`RoomService.createRoom()` and navigates to `/rooms/{id}` once the backend
responds with a new room ID.

### `features/room/room.component.ts`
The main piece of frontend logic. On load, it:
1. Reads `roomId` from the URL
2. Calls `checkRoom()` to confirm the room actually exists (shows a "room
   not found" message if not)
3. Checks `sessionStorage` for a name already used in *this browser tab*
   for *this room* (key: `poker_name_{roomId}`) — so refreshing the page
   doesn't make you re-type your name
4. If a name is known, connects immediately; otherwise shows the name
   entry form, and connects once submitted

Voting, revealing, resetting, and copying the invite link
(`navigator.clipboard.writeText`) are all thin methods that just forward
to `RoomSocketService`.

### `features/room/room.component.html` / `.css`
The visual layout: a participant list (your own row highlighted), a
status box per person (dot before reveal, real value after), and a row of
clickable cards at the bottom for your own vote. The CSS uses a small set
of custom properties defined once in `styles.css` (`--accent`, `--border`,
`--surface`, etc.) rather than repeating raw color values everywhere, so
re-theming later only means editing one place.

### `app.routes.ts`
Two routes: `''` → home, `'rooms/:roomId'` → room. Both are lazy-loaded
(`loadComponent`), meaning the code for the room page isn't downloaded by
the browser until someone actually navigates there.

### `app.config.ts`
Application-wide setup: registers the router and `HttpClient`. There's no
auth interceptor or route guard in this version — nothing to intercept or
guard, since there's no login.

### `styles.css`
Global design tokens (colors, fonts, border radius) as CSS custom
properties, plus a handful of base element resets. This is where the flat,
neutral look of the app is defined.

## Docker files

- `Dockerfile` — multi-stage build: `npm ci` + `npm run build` in a Node
  build stage, then just the static output files get copied into an nginx
  image for the final runtime image (no Node, no source code, no
  `node_modules` in what actually ships)
- `docker-compose.yml` — runs that image, publishing port 4200 (mapped to
  nginx's port 80 inside the container)
- `nginx.conf` — the one thing worth knowing here: it has a
  `try_files $uri $uri/ /index.html` fallback, which is required for
  Angular's client-side routing to work. Without it, refreshing the page
  on `/rooms/abc123` would 404, because nginx would look for a literal
  file at that path instead of letting Angular's router handle it.
- `.dockerignore` — keeps `node_modules/` and build output out of the
  Docker build context (both get regenerated inside the container anyway)

## Known limitations / natural next steps

- **No reconnect backoff** — if the WebSocket drops, the app doesn't
  automatically retry; refreshing the page reconnects (and remembers your
  name via `sessionStorage`).
- **No environment-based config** — `core/config.ts` is a single hardcoded
  pair of URLs rather than something that varies by build (dev vs.
  staging vs. prod). Angular's `environment.ts` file pattern would be the
  natural next step if this needs to run against more than one backend.
