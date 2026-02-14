package server

object VersionEndpoint:
  /** Returns the build version for cache busting. Uses server start time as the version - each deployment restarts the
    * server, so this effectively represents "when this code was deployed".
    */
  def version(startTime: Long): ujson.Value =
    ujson.Obj(
      "buildVersion" -> startTime,
      "timestamp" -> System.currentTimeMillis()
    )
