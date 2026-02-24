package shared.task

import scala.annotation.tailrec
import scala.util.Try
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*

case class Fail(context: String, cause: Any):
  override def toString: String = s"[$context] $cause"

type Result[A] = Either[Fail, A]

trait Logger:
  def error(fail: Fail): Unit
  def info(msg: String): Unit

object Logger:
  val console: Logger = new Logger:
    def error(fail: Fail): Unit = println(s"ERROR: $fail")
    def info(msg: String): Unit = println(s"INFO: $msg")

  val silent: Logger = new Logger:
    def error(fail: Fail): Unit = ()
    def info(msg: String): Unit = ()

case class Timing(name: String, durationMs: Long, success: Boolean)

trait Timer:
  def record(timing: Timing): Unit

object Timer:
  val console: Timer = (timing: Timing) =>
    val status = if timing.success then "OK" else "FAILED"
    println(f"TIMING: ${timing.name}%-30s ${timing.durationMs}%6dms [$status]")

  val silent: Timer = (_: Timing) => ()

  class Collecting extends Timer:
    private var timings = List.empty[Timing]
    def record(timing: Timing): Unit = synchronized:
      timings = timings :+ timing
    def getTimings: List[Timing] = synchronized(timings)
    def clear(): Unit = synchronized:
      timings = Nil
    def totalMs: Long = synchronized(timings.map(_.durationMs).sum)
    def summary: String = synchronized:
      timings.map(t => f"${t.name}%-30s ${t.durationMs}%6dms").mkString("\n")

extension [A](opt: Option[A])
  def toResult(context: String, ifNone: => Any = "not found"): Result[A] = opt match
    case Some(value) => Right(value)
    case None        => Left(Fail(context, ifNone))

extension [A](t: Try[A])
  def toResult(context: String): Result[A] = t match
    case scala.util.Success(value) => Right(value)
    case scala.util.Failure(err)   => Left(Fail(context, err.getMessage))

extension [A](result: Result[A])
  def logged(using logger: Logger): Result[A] =
    result.left.foreach(logger.error)
    result

class Task[A](private val work: (Logger, Timer) ?=> Result[A], val name: Option[String] = None):
  /** Run the task and record timing if named */
  private def run(using Logger, Timer): Result[A] =
    val start = System.currentTimeMillis()
    val result = work.logged
    val elapsed = System.currentTimeMillis() - start
    name.foreach(n => summon[Timer].record(Timing(n, elapsed, result.isRight)))
    result

  def execute(using Logger, Timer): Result[A] = run

  def map[B](f: A => B): Task[B] = Task(run.map(f))

  def flatMap[B](f: A => Task[B]): Task[B] = Task:
    run match
      case Left(fail) => Left(fail)
      case Right(a)   => f(a).run

  def zip[B](other: Task[B])(using ExecutionContext): Task[(A, B)] = Task:
    val logger = summon[Logger]
    val timer = summon[Timer]
    val futureA = Future(run(using logger, timer))
    val futureB = Future(other.run(using logger, timer))

    val combined = for
      a <- futureA
      b <- futureB
    yield (a, b) match
      case (Right(av), Right(bv)) => Right((av, bv))
      case (Left(f), _)           => Left(f)
      case (_, Left(f))           => Left(f)

    Await.result(combined, 30.seconds)

  def retry(attempts: Int): Task[A] = Task:
    @tailrec
    def loop(remaining: Int): Result[A] =
      work match  // use work here, not run - we don't want to record each retry attempt
        case Right(a) => Right(a)
        case Left(f) if remaining > 1 =>
          summon[Logger].info(s"Retrying after: $f (${remaining - 1} attempts left)")
          loop(remaining - 1)
        case Left(f) => Left(f)
    loop(attempts)

  def named(taskName: String): Task[A] = new Task(work, Some(taskName))

object Task:
  def apply[A](f: (Logger, Timer) ?=> Result[A]): Task[A] = new Task(f)
  /** Wrap a plain value in a successful Task. (Called "pure" in FP circles.) */
  def succeed[A](a: A): Task[A] = Task(Right(a))
  def fail[A](context: String, cause: Any): Task[A] = Task(Left(Fail(context, cause)))
  def fromTry[A](context: String)(t: => Try[A]): Task[A] = Task(t.toResult(context))
  def fromOption[A](context: String, ifNone: => Any = "not found")(o: => Option[A]): Task[A] =
    Task(o.toResult(context, ifNone))

