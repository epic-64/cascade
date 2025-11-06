# Cascade

Scala 3 project following a "dumb Scala" philosophy: keep things simple, avoid heavy FP frameworks, leverage the language & small libraries directly.

## Stack
- Scala 3.7.4
- Tapir (endpoint definitions only so far)
- Cask (HTTP server)
- ScalaTest (tests)

## Hello World Endpoint
The Tapir definition lives in `src/main/scala/Endpoints.scala`:

```scala
val helloWorldEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
  endpoint.get.in("hello").out(stringBody.example("Hello, World!"))
```

The server implementation is a Cask route in `WebServer.scala`:

```scala
@cask.get("/hello")
def hello(): String = "Hello, World!"
```

This manually implements the Tapir endpoint. There is no official Tapir Cask server interpreter; attempting to add a `tapir-cask` dependency will fail. For now we keep things explicit and lightweight.

## Running (IntelliJ Built‑In sbt)
Use IntelliJ's built-in sbt shell instead of an external one:
1. Open the project in IntelliJ.
2. Let it import the sbt build (it detects `build.sbt`).
3. Open the sbt tool window (View > Tool Windows > sbt) and use the sbt shell there.
4. In the sbt shell, run:
   ```
   compile
   run
   ```
   You should see:
   `Starting WebServer on port 8080...`
5. Hit `http://localhost:8080/hello` in a browser or curl for `Hello, World!`.
6. Health check: `http://localhost:8080/health` returns `OK`.

Alternatively, create a Run Configuration:
- Run > Edit Configurations > Add Scala Application
- Main class: `main` (the generated @main method)
- Then Run.

## Testing
In IntelliJ's sbt shell:
```
test
```
All tests are in `src/test/scala/HelloEndpointSpec.scala`.

## Next Steps (Optional Enhancements)
- Add JSON support via `tapir-json-circe` and manual Cask JSON responses.
- Generate OpenAPI docs by adding `tapir-openapi` and `tapir-openapi-circe` and rendering `helloWorldEndpoint` docs.
- Factor common server setup (e.g. port/env) into a small config helper.
- Add logging (e.g. tiny wrapper around `println` or a minimal logger lib) while staying lightweight.

## Philosophy Notes
- Keep dependencies minimal; only add what you immediately need.
- Prefer explicit, small functions over abstraction layers.
- Integrate libraries directly without large effect systems.

## Troubleshooting
- If IntelliJ sbt import seems stuck: reload the sbt project (right‑click `build.sbt` > Reload sbt Project).
- If port conflict occurs, set `PORT=9090` env var in the Run Configuration.
- Clean build if classpath issues: run `clean` then `compile` in sbt shell.

## License
Add a license file (`LICENSE`) when ready.

