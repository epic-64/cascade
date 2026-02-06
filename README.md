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
sbt ~"cascadeJS / fastLinkJS"
```

**Terminal 2 - Start server:**
```bash
sbt "cascadeJVM / run"
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
sbt "cascadeJVM / test"
```

Start server:
```bash
sbt "cascadeJVM / run"
```

Transpile client (dev):
```bash
sbt "cascadeJS / fastLinkJS"
```

Transpile client (watch):
```bash
sbt ~"cascadeJS / fastLinkJS"
```

Transpile client (production):
```bash
sbt "cascadeJS / fullLinkJS"
```

## Endpoints

**Counter Demo:**
- `GET /` → HTML client
- `GET /main.js` → Compiled Scala.js (includes both apps)
- `GET /counter` → Get counter value
- `POST /counter/increment` → Increment counter
- `POST /counter/decrement` → Decrement counter
- `WS /ws/counter` → WebSocket for real-time updates

**Color Rush Game:**
- `GET /game.html` → Color Rush game client
- `GET /game.css` → Game stylesheet
- `WS /ws/game/:gameId` → Game WebSocket endpoint

**System:**
- `GET /health` → Server health and statistics (JSON)
  - Status, uptime, counter state, memory usage, system info

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

