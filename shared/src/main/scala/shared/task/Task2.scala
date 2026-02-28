package shared.task

import scala.annotation.tailrec
import scala.util.Try
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*

/**
 * Task with effect tracking.
 *
 * @tparam R The required capabilities (environment) - what this task needs to run
 * @tparam A The success type
 *
 * Example:
 *   def getUser(id: String): Task2[Database, User]
 *   def log(msg: String): Task2[Logging, Unit]
 *   def getUserAndLog(id: String): Task2[Database & Logging, User]
 */
opaque type Task2[-R, +A] = R => Result[A]

object Task2:
  // === Constructors ===

  /** Create a task that requires capability R */
  def apply[R, A](f: R => Result[A]): Task2[R, A] = f

  /** Create a task from a result (no requirements) */
  def fromResult[A](result: Result[A]): Task2[Any, A] = _ => result

  /** Wrap a pure value (no requirements, cannot fail) */
  def succeed[A](a: A): Task2[Any, A] = _ => Right(a)

  /** Create a failed task */
  def fail[A](context: String, cause: Any): Task2[Any, A] = _ => Left(Fail(context, cause))

  /** Access a capability from the environment */
  def service[R]: Task2[R, R] = r => Right(r)

  /** Create a task that uses a specific service */
  def serviceWith[R, A](f: R => A): Task2[R, A] = r => Right(f(r))

  /** Create a task that uses a service and may fail */
  def serviceWithTask[R, A](f: R => Result[A]): Task2[R, A] = f

  /** Lift a Try into a Task2 */
  def fromTry[A](context: String)(t: => Try[A]): Task2[Any, A] = _ => t.toResult(context)

  /** Lift an Option into a Task2 */
  def fromOption[A](context: String, ifNone: => Any = "not found")(o: => Option[A]): Task2[Any, A] =
    _ => o.toResult(context, ifNone)

  /** Lift an Either into a Task2 */
  def fromEither[A](context: String)(e: => Either[Any, A]): Task2[Any, A] =
    _ => e.left.map(err => Fail(context, err))

  // === Extension methods ===

  extension [R, A](self: Task2[R, A])
    /** Run the task with the provided environment */
    def run(env: R): Result[A] = self(env)

    /** Run a task that has no requirements */
    def runUnit(using ev: Any <:< R): Result[A] = self(())

    def map[B](f: A => B): Task2[R, B] =
      r => self(r).map(f)

    def flatMap[R1 <: R, B](f: A => Task2[R1, B]): Task2[R1, B] =
      r => self(r) match
        case Left(fail) => Left(fail)
        case Right(a) => f(a)(r)

    /** Combine with another task, running them sequentially */
    def zip[R1 <: R, B](other: Task2[R1, B]): Task2[R1, (A, B)] =
      r => for
        a <- self(r)
        b <- other(r)
      yield (a, b)

    /** Combine with another task, running them in parallel */
    def zipPar[R1 <: R, B](other: Task2[R1, B])(using ExecutionContext): Task2[R1, (A, B)] =
      r =>
        val futureA = Future(self(r))
        val futureB = Future(other(r))
        val combined = for
          a <- futureA
          b <- futureB
        yield (a, b) match
          case (Right(av), Right(bv)) => Right((av, bv))
          case (Left(f), _) => Left(f)
          case (_, Left(f)) => Left(f)
        Await.result(combined, 30.seconds)

    def retry(attempts: Int): Task2[R, A] =
      r =>
        @tailrec
        def loop(remaining: Int): Result[A] =
          self(r) match
            case Right(a) => Right(a)
            case Left(f) if remaining > 1 => loop(remaining - 1)
            case Left(f) => Left(f)
        loop(attempts)

    def timeout(duration: FiniteDuration)(using ExecutionContext): Task2[R, A] =
      r =>
        val future = Future(self(r))
        Try(Await.result(future, duration)) match
          case scala.util.Success(result) => result
          case scala.util.Failure(_: java.util.concurrent.TimeoutException) =>
            Left(Fail("timeout", s"timed out after $duration"))
          case scala.util.Failure(err) =>
            Left(Fail("timeout", err.getMessage))

    /** Provide part of the environment */
    def provide[R0](layer: R0 => R): Task2[R0, A] =
      r0 => self(layer(r0))

    /** Provide the entire environment, eliminating the requirement */
    def provideEnvironment(env: R): Task2[Any, A] =
      _ => self(env)

    /** Recover from failures */
    def recover(f: Fail => Result[A]): Task2[R, A] =
      r => self(r) match
        case Left(fail) => f(fail)
        case right => right

    /** Ensure cleanup runs regardless of success/failure */
    def ensuring(cleanup: => Unit): Task2[R, A] =
      r =>
        val result = Try(self(r)) match
          case scala.util.Success(res) => res
          case scala.util.Failure(ex) =>
            cleanup
            Left(Fail("ensuring", ex.getMessage))
        cleanup
        result


// === Common capability traits ===

/** Capability for logging */
trait Logs:
  def logInfo(msg: String): Unit
  def logError(fail: Fail): Unit

object Logs:
  val console: Logs = new Logs:
    def logInfo(msg: String): Unit = println(s"INFO: $msg")
    def logError(fail: Fail): Unit = println(s"ERROR: $fail")

  val silent: Logs = new Logs:
    def logInfo(msg: String): Unit = ()
    def logError(fail: Fail): Unit = ()

/** Capability for timing/metrics */
trait Metrics:
  def record(name: String, durationMs: Long, success: Boolean): Unit

object Metrics:
  val silent: Metrics = (_, _, _) => ()
  val console: Metrics = (name, ms, ok) =>
    val status = if ok then "OK" else "FAILED"
    println(f"TIMING: $name%-30s ${ms}%6dms [$status]")

/** Capability for clock/time */
trait Clock:
  def currentTimeMillis: Long

object Clock:
  val system: Clock = new Clock:
    def currentTimeMillis: Long = System.currentTimeMillis()

/** Capability for random numbers */
trait Random:
  def nextInt(bound: Int): Int
  def nextDouble: Double

object Random:
  val live: Random = new Random:
    private val rng = new scala.util.Random
    def nextInt(bound: Int): Int = rng.nextInt(bound)
    def nextDouble: Double = rng.nextDouble()

  def seeded(seed: Long): Random = new Random:
    private val rng = new scala.util.Random(seed)
    def nextInt(bound: Int): Int = rng.nextInt(bound)
    def nextDouble: Double = rng.nextDouble()


// === Example: combining capabilities ===

object Task2Examples:
  import Task2.*

  // A task that needs logging
  def logMessage(msg: String): Task2[Logs, Unit] =
    Task2.serviceWith[Logs, Unit](_.logInfo(msg))

  // A task that needs a clock
  def currentTime: Task2[Clock, Long] =
    Task2.serviceWith[Clock, Long](_.currentTimeMillis)

  // A task that combines both - note the type!
  def logCurrentTime: Task2[Logs & Clock, Unit] =
    for
      time <- currentTime
      _ <- logMessage(s"Current time: $time")
    yield ()

  // Running it:
  // val env: Logs & Clock = new Logs with Clock { ... }
  // logCurrentTime.run(env)

