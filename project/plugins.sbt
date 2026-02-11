addSbtPlugin("org.jetbrains.scala"  % "sbt-ide-settings"      % "1.1.2")  // forgot what this is for
addSbtPlugin("org.scoverage"        % "sbt-scoverage"         % "2.4.4")  // code coverage
addSbtPlugin("com.github.sbt"       % "sbt-native-packager"   % "1.11.1") // create a native package
addSbtPlugin("org.scala-js"         % "sbt-scalajs"           % "1.20.2") // Scala.js support
addSbtPlugin("org.scala-js"         % "sbt-jsdependencies"    % "1.0.2")  // JavaScript dependencies
addSbtPlugin("org.portable-scala"   % "sbt-scalajs-crossproject" % "1.3.2") // Cross-compile for JVM and JS

libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.0" // jsdom environment for tests
