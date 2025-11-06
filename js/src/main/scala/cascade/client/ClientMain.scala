package cascade.client

import cascade.shared.{User, SharedGreeter}

@main def clientMain(): Unit =
  // Simple Scala.js entrypoint demonstrating use of shared types
  val sample = User(42, "Alice")
  val msg    = SharedGreeter.greet(sample)
  // For now we just log to the JS console; no DOM dependency required
  println(s"[client] $msg")

