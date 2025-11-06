ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.7.4"

coverageEnabled      := sys.env.get("ENABLE_COVERAGE").contains("true")
executableScriptName := "main" // required by nixpacks

libraryDependencies ++= Seq(
  // Testing stack
  "org.scalatest"     %% "scalatest"    % "3.2.19"   % Test,
  "org.scalatestplus" %% "mockito-5-12" % "3.2.19.0" % Test,
  "org.scalamock"     %% "scalamock"    % "7.5.2"    % Test,
  // Web / HTTP stack
  "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.10.8",
  "com.lihaoyi"                  %% "cask"       % "0.9.2"
)