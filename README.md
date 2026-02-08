# Cascade

Full-stack web application using Scala 3, Scala.js, Cask, and WebSockets.

## Features

**Counter App with Real-Time Sync**
- Server maintains counter state (Scala 3 + Cask)
- Client displays and controls counter (Scala.js)
- WebSocket broadcasts updates to all tabs in real-time 🎉
- **Thread-safe** concurrent access using `AtomicInteger`
- Shared types between client and server for type safety
- **HTTP caching** with ETag validation for optimal performance

**Color Rush - Multiplayer Game**
- Fast-paced color matching game
- Real-time multiplayer with WebSocket synchronization
- Type-safe client (ScalaJS) and server communication
- Shared game models across client/server boundary
- 10 rounds, speed bonuses, live scoreboard

## Quick Start

**Terminal 1 - Transpile client (watch mode):**
```bash
sbt ~"js/fastLinkJS"
```

**Terminal 2 - Start server:**
```bash
sbt "jvm/run"
```

**Browser:**
```
http://localhost:8080
```

**Open multiple tabs and watch them sync!**

## Commands

Compile:
```bash
sbt compile
```

Clean compile:
```bash
sbt clean compile
```

Run tests:
```bash
sbt "jvm/test"
```

Start server:
```bash
sbt "jvm/run"
```

Transpile client (dev):
```bash
sbt "js/fastLinkJS"
```

Transpile client (watch):
```bash
sbt ~"js/fastLinkJS"
```

Transpile client (production):
```bash
sbt "js/fullLinkJS"
```

## Endpoints

**Landing Page:**
- `GET /` → Landing page with overview of available apps

**Counter Demo:**
- `GET /counter` → Counter app (HTML client)
- `GET /api/counter` → Get counter value (JSON)
- `POST /api/counter/increment` → Increment counter
- `POST /api/counter/decrement` → Decrement counter
- `WS /ws/counter` → WebSocket for real-time updates

**Color Rush Game:**
- `GET /color-rush` → Color Rush game client
- `WS /ws/color-rush/:gameId` → Game WebSocket endpoint

**Static Assets:**
- `GET /static/*` → Static files (HTML, CSS, JS)
  - `/static/js/main.js` → Compiled Scala.js (includes both apps)
  - `/static/index.html`, `/static/counter.html`, `/static/color-rush.html` → HTML files
  - `/static/styles.css`, `/static/color-rush.css` → Stylesheets

**System:**
- `GET /health` → Server health and statistics (JSON)
  - Status, uptime, memory usage, system info

## Project Goals

- ✅ REST API server
- ✅ Scala.js client with shared types
- ✅ WebSocket real-time sync
- ✅ Test suite (server)
- ⏳ Database persistence
- ⏳ CI pipeline
- ⏳ Cloud deployment

## Documentation

- **[Health Endpoint](docs/HEALTH_ENDPOINT.md)** - Detailed documentation of the `/health` endpoint and its stats

