# Cascade

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
