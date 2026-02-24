// ⚠️ Remember to run `sbt bloopInstall` after modifying this file

// scalafmt: { align.preset = most, danglingParentheses.preset = false }
addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings"         % "1.1.2")  // forgot what this is for
addSbtPlugin("org.scoverage"       % "sbt-scoverage"            % "2.4.4")  // code coverage
addSbtPlugin("com.github.sbt"      % "sbt-native-packager"      % "1.11.1") // create a native package
addSbtPlugin("org.scala-js"        % "sbt-scalajs"              % "1.20.2") // Scala.js support
addSbtPlugin("org.scala-js"        % "sbt-jsdependencies"       % "1.0.2")  // JavaScript dependencies
addSbtPlugin("org.portable-scala"  % "sbt-scalajs-crossproject" % "1.3.2")  // Cross-compile for JVM and JS
addSbtPlugin("org.scalameta"       % "sbt-scalafmt"             % "2.5.6")  // Scala code formatter
addSbtPlugin("io.spray"            % "sbt-revolver"             % "0.10.0") // Auto-restart server on changes
addSbtPlugin("ch.epfl.scala"       % "sbt-bloop"                % "2.0.8")  // Faster compilation server

libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.0" // jsdom environment for tests
