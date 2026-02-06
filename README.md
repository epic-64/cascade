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

- `GET /` → HTML client
- `GET /main.js` → Compiled Scala.js
- `GET /health` → Server health and statistics (JSON)
  - Status, uptime, counter state, memory usage, system info
- `GET /counter` → Get counter value
- `POST /counter/increment` → Increment counter
- `POST /counter/decrement` → Decrement counter
- `WS /ws/counter` → WebSocket for real-time updates

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

