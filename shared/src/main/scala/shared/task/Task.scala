package shared.task

import scala.util.Try
import scala.concurrent.{Future, Await, ExecutionContext}
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

class Task[A](private val work: Logger ?=> Result[A]):
  def execute(using Logger): Result[A] = work.logged

  def map[B](f: A => B): Task[B] = Task(work.map(f))

  def flatMap[B](f: A => Task[B]): Task[B] = Task:
    work match
      case Left(fail) => Left(fail)
      case Right(a)   => f(a).work

  def zip[B](other: Task[B])(using ExecutionContext): Task[(A, B)] = Task:
    val logger = summon[Logger]
    val futureA = Future(work(using logger))
    val futureB = Future(other.work(using logger))

    val combined = for
      a <- futureA
      b <- futureB
    yield (a, b) match
      case (Right(av), Right(bv)) => Right((av, bv))
      case (Left(f), _)           => Left(f)
      case (_, Left(f))           => Left(f)

    Await.result(combined, 30.seconds)

  def retry(attempts: Int): Task[A] = Task:
    def loop(remaining: Int): Result[A] =
      work match
        case Right(a) => Right(a)
        case Left(f) if remaining > 1 =>
          summon[Logger].info(s"Retrying after: $f (${remaining - 1} attempts left)")
          loop(remaining - 1)
        case Left(f) => Left(f)
    loop(attempts)

object Task:
  def apply[A](f: Logger ?=> Result[A]): Task[A] = new Task(f)
  def pure[A](a: A): Task[A] = Task(Right(a))
  def fail[A](context: String, cause: Any): Task[A] = Task(Left(Fail(context, cause)))
  def fromTry[A](context: String)(t: => Try[A]): Task[A] = Task(t.toResult(context))
  def fromOption[A](context: String, ifNone: => Any = "not found")(o: => Option[A]): Task[A] =
    Task(o.toResult(context, ifNone))

