package client

import shared.{SharedGreeter, User}
import org.scalajs.dom.document

@main def clientMain(): Unit =
  // Simple Scala.js entrypoint demonstrating use of shared types
  val el = document.createElement("div")
  el.textContent = "Client started"
  document.body.appendChild(el)

  val sample = User(42, "Alice")
  val msg    = SharedGreeter.greet(sample)
  // For now we just log to the JS console; no DOM dependency required
  println(s"[client] $msg")

