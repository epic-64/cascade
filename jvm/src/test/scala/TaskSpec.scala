import org.scalatest.funsuite.AnyFunSuite
import shared.task.*

import scala.util.Try
import scala.concurrent.ExecutionContext

class TaskSpec extends AnyFunSuite:

  given Logger = Logger.silent
  given Timer = Timer.silent
  given ExecutionContext = ExecutionContext.global

  // Test domain
  case class User(id: String, name: String, vaultKey: String)

  val knownUsers = Set(("456", "Bob", "bob_key"))
  val knownVaults = Map("bob_key" -> List("Bow", "Arrow"))

  def fetchUser(userId: String): Task[User] = Task:
    knownUsers.find(_._1 == userId) match
      case None => Left(Fail("fetchUser", "UserNotFound"))
      case Some((id, name, vaultKey)) => Right(User(id, name, vaultKey))

  def fetchVaultItems(user: User): Task[List[String]] = Task:
    knownVaults.get(user.vaultKey).toResult("fetchVaultItems", "VaultNotFound")

  def invoiceIdentifier(user: User): Task[String] = Task:
    Right(s"User ${user.name} (ID: ${user.id})")

  // Tests

  test("Task.pure creates a successful task"):
    val result = Task.pure(42).execute
    assert(result == Right(42))

  test("Task.fail creates a failed task"):
    val result = Task.fail[Int]("test", "something went wrong").execute
    assert(result == Left(Fail("test", "something went wrong")))

  test("Task maps over successful values"):
    val result = Task.pure(21).map(_ * 2).execute
    assert(result == Right(42))

  test("Task flatMap chains operations"):
    val result = (for
      user  <- fetchUser("456")
      items <- fetchVaultItems(user)
    yield items).execute

    assert(result == Right(List("Bow", "Arrow")))

  test("Task flatMap short-circuits on failure"):
    val result = (for
      user  <- fetchUser("999")  // doesn't exist
      items <- fetchVaultItems(user)
    yield items).execute

    assert(result.isLeft)
    assert(result.left.exists(_.context == "fetchUser"))

  test("Task.zip runs operations in parallel"):
    var order = List.empty[String]

    def slowOp(name: String, delayMs: Int): Task[String] = Task:
      Thread.sleep(delayMs)
      order = order :+ name
      Right(name)

    val startTime = System.currentTimeMillis()
    val result = slowOp("A", 100).zip(slowOp("B", 100)).execute
    val elapsed = System.currentTimeMillis() - startTime

    assert(result == Right(("A", "B")))
    // Parallel should take ~100ms, not ~200ms
    assert(elapsed < 180, s"Expected parallel execution but took ${elapsed}ms")

  test("Task.zip proves concurrency by overlapping execution windows"):
    // Track when each operation starts and ends
    case class Timing(name: String, startedAt: Long, endedAt: Long)
    var timings = List.empty[Timing]
    val testStart = System.currentTimeMillis()

    def timedOp(name: String, durationMs: Int): Task[String] = Task:
      val started = System.currentTimeMillis() - testStart
      Thread.sleep(durationMs)
      val ended = System.currentTimeMillis() - testStart
      synchronized:
        timings = timings :+ Timing(name, started, ended)
      Right(name)

    val result = timedOp("A", 200).zip(timedOp("B", 200)).execute

    assert(result == Right(("A", "B")))

    val a = timings.find(_.name == "A").get
    val b = timings.find(_.name == "B").get

    // If parallel: B starts before A ends (their execution windows overlap)
    // If sequential: B starts after A ends (no overlap)
    val overlaps = b.startedAt < a.endedAt && a.startedAt < b.endedAt
    assert(overlaps, s"Expected overlapping execution but got A=$a, B=$b")

  test("Task.zip returns first failure"):
    val result = Task.fail[Int]("first", "oops")
      .zip(Task.pure("ok"))
      .execute

    assert(result.isLeft)
    assert(result.left.exists(_.context == "first"))

  test("Task.retry retries on failure"):
    var attempts = 0

    def flakyOp: Task[String] = Task:
      attempts += 1
      if attempts < 3 then Left(Fail("flakyOp", "temporary failure"))
      else Right("success")

    attempts = 0
    val result = flakyOp.retry(5).execute

    assert(result == Right("success"))
    assert(attempts == 3)

  test("Task.retry gives up after max attempts"):
    var attempts = 0

    def alwaysFails: Task[String] = Task:
      attempts += 1
      Left(Fail("alwaysFails", "permanent failure"))

    attempts = 0
    val result = alwaysFails.retry(3).execute

    assert(result.isLeft)
    assert(attempts == 3)

  test("Task.fromTry converts Success"):
    val result = Task.fromTry("test")(Try(42)).execute
    assert(result == Right(42))

  test("Task.fromTry converts Failure"):
    val result = Task.fromTry[Int]("test")(Try(throw new RuntimeException("boom"))).execute
    assert(result.isLeft)
    assert(result.left.exists(_.cause == "boom"))

  test("Task.fromOption converts Some"):
    val result = Task.fromOption("test")(Some(42)).execute
    assert(result == Right(42))

  test("Task.fromOption converts None"):
    val result = Task.fromOption[Int]("test", "missing")(None).execute
    assert(result == Left(Fail("test", "missing")))

  test("full pipeline with parallel operations"):
    def fullInvoice(description: String, items: List[String]): String =
      s"Invoice for $description: ${items.mkString(", ")}"

    val result = (for
      user                <- fetchUser("456")
      (items, identifier) <- fetchVaultItems(user).zip(invoiceIdentifier(user))
      invoice             = fullInvoice(identifier, items)
    yield invoice).execute

    assert(result == Right("Invoice for User Bob (ID: 456): Bow, Arrow"))

  test("retry on a middle step retries just that step"):
    var initCount = 0
    var fetchAttempts = 0

    def init: Task[Int] = Task:
      initCount += 1
      Right(42)

    def flakyFetch(n: Int): Task[String] = Task:
      fetchAttempts += 1
      if fetchAttempts < 3 then Left(Fail("flakyFetch", "temporary"))
      else Right(s"data-$n")

    def process(data: String): Task[String] = Task:
      Right(s"processed: $data")

    initCount = 0
    fetchAttempts = 0
    val result = (for
      n      <- init
      data   <- flakyFetch(n).retry(5)  // retry just this step
      output <- process(data)
    yield output).execute

    assert(result == Right("processed: data-42"))
    assert(initCount == 1, s"init should run once but ran $initCount")  // NOT retried
    assert(fetchAttempts == 3, s"flakyFetch should run 3 times but ran $fetchAttempts")

  test("retry on whole pipeline restarts from beginning"):
    var step1Count = 0
    var step2Count = 0

    def step1: Task[String] = Task:
      step1Count += 1
      Right("from step1")

    def step2(input: String): Task[String] = Task:
      step2Count += 1
      if step2Count < 3 then Left(Fail("step2", "temporary"))
      else Right(s"$input -> step2")

    step1Count = 0
    step2Count = 0

    val pipeline = for
      a <- step1
      b <- step2(a)
    yield b

    val result = pipeline.retry(5).execute

    assert(result == Right("from step1 -> step2"))
    assert(step1Count == 3, s"step1 should run 3 times but ran $step1Count")  // re-runs step1 each retry!
    assert(step2Count == 3, s"step2 should run 3 times but ran $step2Count")

  test("named task records timing on success"):
    val timer = new Timer.Collecting

    def slowOp: Task[String] = Task:
      Thread.sleep(50)
      Right("done")

    slowOp.named("slowOp").execute(using Logger.silent, timer)

    val timings = timer.getTimings
    assert(timings.size == 1)
    assert(timings.head.name == "slowOp")
    assert(timings.head.success)
    assert(timings.head.durationMs >= 50)
    assert(timings.head.durationMs <= 60)

  test("named task records timing on failure"):
    val timer = new Timer.Collecting

    def failingOp: Task[String] = Task:
      Thread.sleep(30)
      Left(Fail("failingOp", "oops"))

    failingOp.named("failingOp").execute(using Logger.silent, timer)

    val timings = timer.getTimings
    assert(timings.size == 1)
    assert(timings.head.name == "failingOp")
    assert(!timings.head.success)
    assert(timings.head.durationMs >= 30)
    assert(timings.head.durationMs <= 40)

  test("unnamed tasks do not record timing"):
    val timer = new Timer.Collecting

    Task.pure(42).execute(using Logger.silent, timer)

    assert(timer.getTimings.isEmpty)

  test("timing works with full pipeline"):
    val timer = new Timer.Collecting

    def step1: Task[Int] = Task:
      Thread.sleep(50)
      Right(42)

    def step2(n: Int): Task[String] = Task:
      Thread.sleep(50)
      Right(s"result: $n")

    val result = (for
      a <- step1.named("step1")
      b <- step2(a).named("step2")
    yield b).execute(using Logger.silent, timer)

    assert(result == Right("result: 42"))

    val timings = timer.getTimings
    assert(timings.size == 2)
    assert(timings.map(_.name) == List("step1", "step2"))
    assert(timings.forall(_.success))
    assert(timer.totalMs >= 100)

  test("Timer.Collecting provides summary"):
    val timer = new Timer.Collecting

    Task.pure(1).named("fast").execute(using Logger.silent, timer)
    Task.pure(2).named("also-fast").execute(using Logger.silent, timer)

    val summary = timer.summary
    assert(summary.contains("fast"))
    assert(summary.contains("also-fast"))

