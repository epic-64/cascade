import org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*
import sbtcrossproject.CrossPlugin.autoImport.*

ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.8.1"

val caskVersion    = "0.11.3"
val enableCoverage = sys.env.get("ENABLE_COVERAGE").contains("true")

// ⚠️ Remember to run `sbt bloopInstall` after modifying this file

// Root project that aggregates JS and JVM subprojects
lazy val root = project
  .in(file("."))
  .aggregate(cascade.js, cascade.jvm)
  .settings(
    name           := "cascade",
    publish / skip := true
  )

// Main cross-project with shared code and platform-specific code
lazy val cascade = crossProject(JSPlatform, JVMPlatform)
  .in(file("."))
  .jsConfigure(_.withId("js"))
  .jvmConfigure(_.withId("jvm"))
  .settings(
    name    := "cascade",
    version := "0.1-SNAPSHOT",
    // Shared dependencies that work on both JS and JVM
    // scalafmt: { align.preset = most, danglingParentheses.preset = false }
    libraryDependencies ++= Seq(
      "com.lihaoyi"   %%% "upickle"    % "4.0.2",
      "org.scalatest" %%% "scalatest"  % "3.2.19" % Test
    )
  )
  .jvmSettings(
    Compile / mainClass  := Some("server.WebServer"),
    executableScriptName := "main",
    // Enable coverage for JVM project via environment variable
    coverageEnabled := enableCoverage,
    // JVM-specific dependencies
    // scalafmt: { align.preset = most, danglingParentheses.preset = false }
    libraryDependencies ++= Seq(
      "com.lihaoyi"       %% "cask"            % caskVersion,
      "ch.qos.logback"     % "logback-classic" % "1.5.28",
      "com.lihaoyi"       %% "requests"        % "0.9.0",
      "org.scalatestplus" %% "mockito-5-12"    % "3.2.19.0" % Test,
      "org.scalamock"     %% "scalamock"       % "7.5.5"    % Test
    )
  )
  .jvmEnablePlugins(JavaAppPackaging)
  .jsSettings(
    // Disable coverage for JS project (need to remove all default parameters from methods first)
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

    // JS-specific dependencies
    // scalafmt: { align.preset = most, danglingParentheses.preset = false }
    libraryDependencies ++= Seq(
      "org.scala-js"  %%% "scalajs-dom" % "2.2.0",
      "com.raquo"     %%% "laminar"     % "17.2.0"
    )
  )
