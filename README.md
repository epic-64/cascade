# Cascade

This is a learning project, aiming to build a full-stack web application using Scala.

Stack: Scala 3, sbt, Scala.js, Cask, (to be continued)

Scope:
- A long-running web server that serves a REST API.
- A JS client written in Scala.js that shares types with the server.
- Real-time interaction via websockets.
- A database for persistence.
- A testsuite that covers both server and client code and generates code coverage reports.
- A CI pipeline

```bash
sbt compile
```

Clean compile:
```bash
sbt clean compile
```

Start server
```bash
sbt run cascadeJVM/run
```

Transpile Client
```bash
sbt cascadeJS/fastOptJS
```

Transpile Client (Watch)
```bash
sbt ~cascadeJS/fastOptJS
```

Transpile Client (Production)
```bash
sbt cascadeJS/fullOptJS
```
