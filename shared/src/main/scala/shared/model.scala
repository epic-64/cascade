package shared

// Shared domain types between server (JVM) and client (Scala.js)
case class User(id: Int, name: String)

trait Greeter:
  def greet(user: User): String

object SharedGreeter extends Greeter:
  def greet(user: User): String = s"Hello, ${user.name}! (#${user.id})"

