ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.7.4"

coverageEnabled      := sys.env.get("ENABLE_COVERAGE").contains("true")
executableScriptName := "main" // required by nixpacks

val caskVersion = "0.11.3"

lazy val cascade = crossProject(JSPlatform, JVMPlatform)
  .in(file("."))
  .settings(
    name    := "cascade",
    version := "0.1-SNAPSHOT",
  )
  .jvmSettings(
    Compile / mainClass   := Some("CascadeServer"),
    libraryDependencies ++= Seq(
      "com.lihaoyi"       %% "cask"         % caskVersion,
      // Testing stack
      "org.scalatest"     %% "scalatest"    % "3.2.19"   % Test,
      "org.scalatestplus" %% "mockito-5-12" % "3.2.19.0" % Test,
      "org.scalamock"     %% "scalamock"    % "7.5.2"    % Test,
    )
  )
  .jsSettings(
    // Enable main module initializer so cascadeJS/run works and main.js is generated
    scalaJSUseMainModuleInitializer := true,
  )

// Convenience vals to reference subprojects explicitly
lazy val cascadeJS  = cascade.js
lazy val cascadeJVM = cascade.jvm
