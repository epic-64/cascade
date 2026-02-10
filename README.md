# Cascade
[![Coverage](https://epic-64.github.io/cascade/coverage/coverage-badge.svg)](https://epic-64.github.io/cascade/coverage/index.html)
[![Scala](https://img.shields.io/badge/Scala-3.7.4-red)](https://www.scala-lang.org/)
[![Sbt](https://img.shields.io/badge/sbt-1.12.1-red)](https://www.scala-lang.org/)
[![Scala.js](https://img.shields.io/badge/Scala.js-1.20.2-blue)](https://www.scala-js.org/)
[![Cask](https://img.shields.io/badge/Cask-0.11.3-orange)](https://com-lihaoyi.github.io/cask/)

Full-stack web application using Scala 3, Scala.js, Cask, and WebSockets.

# Live Environments
- main: https://cascade-staging.up.railway.app/
- prod: https://cascade-prod.up.railway.app/

## Features

**Counter App with Real-Time Sync**
- Server maintains counter state (Scala 3 + Cask)
- Client displays and controls counter (Scala.js)
- WebSocket broadcasts updates to all tabs in real-time
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
  - `/static/base.css`, `/static/counter.css`, `/static/color-rush.css` → Stylesheets

**System:**
- `GET /health` → Server health and statistics (JSON)
  - Status, uptime, memory usage, system info

## Project Goals
Stack:
- ✅ Scala 3 for both server and client
- ✅ Cask for lightweight server framework
- ✅ Scala.js for client-side code sharing
- ✅ WebSockets for real-time communication
- ⏳ Cache-busting system for static assets (build-step)
- ⏳ Database persistence, ORM (PostgreSQL + Doobie?)
- ⏳ DB migration system (Flyway, Liquibase?)
- ⏳ Cache across instances / across restarts (Redis?)

Testing:
- ✅ ScalaTest
  - ✅ jvm: works out of the box
  - ✅ js: requires jsdom plugin
  - ✅ cross-project setup to cross-compile shared sources
- ✅ isolated endpoint tests (BDD)
  - ✅ full endpoint traversal (`requests.get(path)`) with coverage
  - ✅ support for mocking side effects
- ⏳ e2e tests
  - ⏳ Selenium, Cypress, Katalon? To be decided.
  - ⏳ CI integration (headless)

Continuous Integration:
- ✅ CI pipeline running tests
- ✅ Test coverage report (sbt-scoverage)
  - ❌ unfortunately, ScalaJS is not supported by scoverage, so we cover only the jvm target
- ✅ Cloud deployment (railway), deploy on push
  - main → staging environment
  - production → production environment

Monitoring:
- ⏳ Exception tracing (Sentry, Datadog, BetterStack?)
- ⏳ Log aggregation (Logstash, Datadog, BetterStack?)
- ⏳ Health and Performance monitoring

## Documentation

- **[Health Endpoint](docs/HEALTH_ENDPOINT.md)** - Detailed documentation of the `/health` endpoint and its stats

