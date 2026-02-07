package server

object HealthEndpoint:
  def health(startTime: Long): ujson.Value =
    val runtime = Runtime.getRuntime
    val uptimeMs = System.currentTimeMillis() - startTime
    val uptimeSeconds = uptimeMs / 1000
    val uptimeMinutes = uptimeSeconds / 60
    val uptimeHours = uptimeMinutes / 60

    ujson.Obj(
      "status" -> "healthy",
      "uptime" -> ujson.Obj(
        "milliseconds" -> uptimeMs,
        "seconds" -> uptimeSeconds,
        "formatted" -> f"${uptimeHours}h ${uptimeMinutes % 60}m ${uptimeSeconds % 60}s"
      ),
      "memory" -> ujson.Obj(
        "total" -> runtime.totalMemory(),
        "free" -> runtime.freeMemory(),
        "used" -> (runtime.totalMemory() - runtime.freeMemory()),
        "max" -> runtime.maxMemory()
      ),
      "system" -> ujson.Obj(
        "availableProcessors" -> runtime.availableProcessors(),
        "javaVersion" -> System.getProperty("java.version"),
        "scalaVersion" -> scala.util.Properties.versionNumberString
      ),
      "timestamp" -> System.currentTimeMillis()
    )

