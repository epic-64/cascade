# Cascade
[![Coverage](https://epic-64.github.io/cascade/coverage/coverage-badge.svg)](https://epic-64.github.io/cascade/coverage/index.html)
[![Lines of Code](https://epic-64.github.io/cascade/coverage/loc-badge.svg)](https://epic-64.github.io/cascade/coverage/coverage-index.html)
[![Scala](https://img.shields.io/badge/Scala-3.8.1-red)](https://www.scala-lang.org/)
[![Sbt](https://img.shields.io/badge/sbt-1.12.1-red)](https://www.scala-lang.org/)
[![Scala.js](https://img.shields.io/badge/Scala.js-1.20.2-blue)](https://www.scala-js.org/)
[![Cask](https://img.shields.io/badge/Cask-0.11.3-orange)](https://com-lihaoyi.github.io/cask/)

Full-stack web application using Scala 3, Scala.js, Cask, and WebSockets.

# Live Environments
- main: https://cascade-staging.up.railway.app/
- prod: https://cascade-prod.up.railway.app/

## Features

**Counter App with Real-Time Sync**
- Simple shared counter that syncs across all browser tabs
- Click to increment or decrement
- Watch updates appear instantly everywhere
- Demonstrates real-time WebSocket synchronization

**Color Rush - Multiplayer Game**
- Fast-paced color matching challenge
- Compete against other players in real-time
- 10 rounds with speed bonuses
- Live scoreboard updates as you play

**AI Drawing Challenge - Multiplayer Game**
- Draw prompts and let AI caption your artwork
- OpenAI Vision API analyzes drawings
- AI judge picks the best match with witty commentary
- Players vote for their favorite drawing
- Real-time lobby system with WebSocket sync
- Bring your own OpenAI API key

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

Generate coverage report:
```bash
ENABLE_COVERAGE=true sbt clean coverage test coverageReport
```

Count lines of code:
```bash
./scripts/count-loc.sh
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

**AI Drawing Challenge:**
- `GET /drawing` → AI Drawing Challenge game client
- `WS /ws/drawing/:lobbyId` → Lobby WebSocket endpoint

**Static Assets:**
- `GET /static/*` → Static files (HTML, CSS, JS)
  - `/static/js/main.js` → Compiled Scala.js (includes all apps)
  - `/static/index.html`, `/static/counter.html`, `/static/color-rush.html`, `/static/drawing-game.html` → HTML files
  - `/static/base.css`, `/static/counter.css`, `/static/color-rush.css`, `/static/drawing-game.css` → Stylesheets

**System:**
- `GET /health` → Server health and statistics (JSON)
  - Status, uptime, memory usage, system info

## Project Goals
Stack:
- ✅ Scala 3 for both server and client
- ✅ Cask for lightweight server framework
- ✅ Scala.js for client-side code sharing
- ✅ WebSockets for real-time communication
- ✅ Cache-busting system for static assets
  - ✅ currently solved via client-side hack (manually append server version as query param on static asset URLs)
  - ⏳ build proper solution with hashed filenames and manifest mapping
- ⏳ Database persistence, ORM (PostgreSQL + Doobie?)
- ⏳ DB migration system (Flyway, Liquibase?)
- ⏳ Cache across instances / across restarts (Redis?)
- ⏳ Internationalization (German, English)

Testing:
- ✅ ScalaTest
  - ✅ jvm: works out of the box
  - ✅ js: requires jsdom plugin
  - ✅ cross-project setup to cross-compile shared sources
- ✅ isolated endpoint tests (BDD)
  - ✅ full endpoint traversal (`requests.get(path)`) with coverage
  - ✅ support for mocking side effects
- ✅ WebSocket tests 
- ⏳ e2e tests
  - ⏳ Selenium, Cypress, Katalon? To be decided.
  - ⏳ CI integration (headless)

Continuous Integration:
- ✅ CI pipeline running tests
- ✅ Test coverage report (sbt-scoverage)
  - ❌ unfortunately, ScalaJS is not supported by scoverage, so we cover only the jvm target
- ✅ Lines of Code tracking (auto-generated on each CI run)
- ✅ Cloud deployment (railway), deploy on push
  - main → staging environment
  - production → production environment

Monitoring:
- ✅ health endpoint
- ⏳ endpoint for inspecting number of active games and websockets (with age)
- ⏳ Exception tracing (Sentry, Datadog, BetterStack?)
- ⏳ Log aggregation (Logstash, Datadog, BetterStack?)
- ⏳ Health and Performance monitoring

## Known Issues / Todos
- Missing tests for Drawing game

## Previous Challenges
- ✅ WebSocket closes on its own after some time of inactivity (1-2 minutes)
  - fixed by implementing a heartbeat mechanism: client sends ping every N seconds
- ✅ WebSocket connection is lost on page refresh
  - fixed by storing player id in localStorage and rejoining the game/lobby on page load if player id is found

## Documentation

- **[Health Endpoint](docs/HEALTH_ENDPOINT.md)** - Detailed documentation of the `/health` endpoint and its stats
- **[Game Cleanup](docs/GAME_CLEANUP.md)** - Documentation of automatic game/lobby cleanup mechanisms

