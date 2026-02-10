import org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.7.4"

val caskVersion = "0.11.3"
val enableCoverage = sys.env.get("ENABLE_COVERAGE").contains("true")

// Root project that aggregates JS and JVM subprojects
lazy val root = project
  .in(file("."))
  .aggregate(js, jvm, shared)
  .settings(
    name := "cascade",
    publish / skip := true
  )

// Shared code project (cross-compiled for both JS and JVM)
lazy val shared = project
  .in(file("shared"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "cascade-shared",
    version := "0.1-SNAPSHOT",
    // js will run into linking errors if coverage is enabled...
    coverageEnabled := false,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "upickle" % "4.0.2"
    )
  )

// JVM subproject
lazy val jvm = project
  .in(file("jvm"))
  .dependsOn(shared)
  .settings(
    name := "cascade",
    version := "0.1-SNAPSHOT",
    Compile / mainClass := Some("server.WebServer"),
    executableScriptName := "main",
    // Enable coverage for JVM project via environment variable
    coverageEnabled := enableCoverage,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "cask" % caskVersion,
      "ch.qos.logback" % "logback-classic" % "1.5.28",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scalatestplus" %% "mockito-5-12" % "3.2.19.0" % Test,
      "org.scalamock" %% "scalamock" % "7.5.5" % Test,
    )
  )
  .enablePlugins(JavaAppPackaging)

// JS subproject
lazy val js = project
  .in(file("js"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(shared)
  .settings(
    name := "cascade",
    version := "0.1-SNAPSHOT",
    // Disable coverage for JS project (not supported in Scala 3)
    coverageEnabled := false,
    // Enable main module initializer so js/run works and main.js is generated
    scalaJSUseMainModuleInitializer := true,

    // Use jsdom for tests to provide DOM environment
    Test / jsEnv := new JSDOMNodeJSEnv(),

    // Output compiled JS to JVM resources directory for conventional serving
    Compile / fastLinkJS / scalaJSLinkerOutputDirectory :=
      baseDirectory.value / ".." / "jvm" / "src" / "main" / "resources" / "static" / "js",
    Compile / fullLinkJS / scalaJSLinkerOutputDirectory :=
      baseDirectory.value / ".." / "jvm" / "src" / "main" / "resources" / "static" / "js",

    // dependencies for Scala.js project
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.2.0",
      "org.scalatest" %%% "scalatest" % "3.2.19" % Test
    )
  )
