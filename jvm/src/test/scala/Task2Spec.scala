import org.scalatest.funsuite.AnyFunSuite
import shared.task.*
import shared.task.Task2.*

import scala.concurrent.ExecutionContext

class Task2Spec extends AnyFunSuite:

  given ExecutionContext = ExecutionContext.global

  // === Test capabilities ===

  trait UserRepo:
    def findUser(id: String): Result[String]

  trait EmailService:
    def sendEmail(to: String, body: String): Result[Unit]

  // === Tests ===

  test("Task2.succeed creates a task with no requirements"):
    val task: Task2[Any, Int] = Task2.succeed(42)
    assert(task.run(()) == Right(42))

  test("Task2.fail creates a failed task"):
    val task: Task2[Any, Int] = Task2.fail("test", "oops")
    assert(task.run(()) == Left(Fail("test", "oops")))

  test("Task2.service accesses a capability"):
    val task: Task2[Clock, Long] =
      Task2.serviceWith[Clock, Long](_.currentTimeMillis)

    val result = task.run(Clock.system)
    assert(result.isRight)
    assert(result.exists(_ > 0))

  test("map transforms success values"):
    val task = Task2.succeed(21).map(_ * 2)
    assert(task.run(()) == Right(42))

  test("flatMap chains tasks and combines requirements"):
    // Define tasks with different requirements
    def getUser(id: String): Task2[UserRepo, String] =
      Task2.serviceWithTask[UserRepo, String](_.findUser(id))

    def sendWelcome(email: String): Task2[EmailService, Unit] =
      Task2.serviceWithTask[EmailService, Unit](_.sendEmail(email, "Welcome!"))

    // Combined task requires BOTH capabilities - visible in the type!
    val combined: Task2[UserRepo & EmailService, Unit] = for
      user <- getUser("123")
      _    <- sendWelcome(user)
    yield ()

    // Provide an environment that satisfies both
    val env = new UserRepo with EmailService:
      def findUser(id: String) = Right("bob@example.com")
      def sendEmail(to: String, body: String) = Right(())

    assert(combined.run(env) == Right(()))

  test("flatMap short-circuits on failure"):
    var secondCalled = false

    val task = for
      _ <- Task2.fail[Unit]("first", "boom")
      _ <- Task2.succeed { secondCalled = true }
    yield ()

    assert(task.run(()).isLeft)
    assert(!secondCalled, "Second task should not run after failure")

  test("zipPar runs tasks in parallel"):
    // Skip on single-core systems
    if Runtime.getRuntime.availableProcessors() <= 1 then
      info("Skipping parallelism test: only 1 processor available")
      cancel()

    case class Timing(name: String, startedAt: Long, endedAt: Long)
    var timings = List.empty[Timing]
    val testStart = System.currentTimeMillis()

    def slowTask(name: String): Task2[Any, String] = Task2[Any, String]: _ =>
      val started = System.currentTimeMillis() - testStart
      Thread.sleep(200)
      val ended = System.currentTimeMillis() - testStart
      synchronized:
        timings = timings :+ Timing(name, started, ended)
      Right(name)

    val combined = slowTask("A").zipPar(slowTask("B"))
    val result = combined.run(())

    assert(result == Right(("A", "B")))

    val a = timings.find(_.name == "A").get
    val b = timings.find(_.name == "B").get
    val overlaps = b.startedAt < a.endedAt && a.startedAt < b.endedAt
    assert(overlaps, s"Expected overlapping execution but got A=$a, B=$b")

  test("retry retries on failure"):
    var attempts = 0

    val task = Task2[Any, String]: _ =>
      attempts += 1
      if attempts < 3 then Left(Fail("flaky", "not yet"))
      else Right("success")

    attempts = 0
    val result = task.retry(5).run(())

    assert(result == Right("success"))
    assert(attempts == 3)

  test("provide lets you partially satisfy requirements"):
    // Task needs both Logs and Clock
    val task: Task2[Logs & Clock, String] = for
      time <- Task2.serviceWith[Clock, Long](_.currentTimeMillis)
      _    <- Task2.serviceWith[Logs, Unit](_.logInfo(s"Time: $time"))
    yield s"logged at $time"

    // Create a layer that provides Clock, leaving Logs as requirement
    val withClock: Task2[Logs, String] = task.provide: logs =>
      new Logs with Clock:
        def logInfo(msg: String) = logs.logInfo(msg)
        def logError(fail: Fail) = logs.logError(fail)
        def currentTimeMillis = System.currentTimeMillis()

    // Now we only need to provide Logs
    val result = withClock.run(Logs.silent)
    assert(result.isRight)

  test("provideEnvironment eliminates all requirements"):
    val task: Task2[Clock, Long] = Task2.serviceWith(_.currentTimeMillis)

    // Provide the environment, resulting in Task2[Any, Long]
    val provided: Task2[Any, Long] = task.provideEnvironment(Clock.system)

    // Can now run with unit
    assert(provided.run(()).isRight)

  test("recover handles failures"):
    val task = Task2.fail[Int]("original", "boom")
      .recover(_ => Right(42))

    assert(task.run(()) == Right(42))

  test("ensuring runs cleanup after success"):
    var cleaned = false

    val task = Task2.succeed(42).ensuring { cleaned = true }
    val result = task.run(())

    assert(result == Right(42))
    assert(cleaned)

  test("ensuring runs cleanup after failure"):
    var cleaned = false

    val task = Task2.fail[Int]("boom", "oops").ensuring { cleaned = true }
    val result = task.run(())

    assert(result.isLeft)
    assert(cleaned)

  test("type system enforces capability requirements"):
    // This test demonstrates that the compiler catches missing capabilities

    def needsLogging: Task2[Logs, Unit] =
      Task2.serviceWith[Logs, Unit](_.logInfo("hello"))

    def needsClock: Task2[Clock, Long] =
      Task2.serviceWith[Clock, Long](_.currentTimeMillis)

    // If you try to run needsLogging with just Clock, it won't compile:
    // needsLogging.run(Clock.system)  // ERROR: Clock is not Logs

    // You must provide the correct capability:
    assert(needsLogging.run(Logs.silent) == Right(()))
    assert(needsClock.run(Clock.system).isRight)

  test("combined requirements are visible in types"):
    def step1: Task2[Logs, Int] =
      Task2.serviceWith[Logs, Int] { logs =>
        logs.logInfo("step1")
        42
      }

    def step2(n: Int): Task2[Clock, Long] =
      Task2.serviceWith[Clock, Long] { clock =>
        clock.currentTimeMillis + n
      }

    // The combined type shows BOTH requirements
    val combined: Task2[Logs & Clock, Long] = for
      n <- step1
      t <- step2(n)
    yield t

    // Must provide environment that satisfies both
    val env = new Logs with Clock:
      def logInfo(msg: String) = ()
      def logError(fail: Fail) = ()
      def currentTimeMillis = 1000L

    assert(combined.run(env) == Right(1042L))

  test("Random capability enables deterministic testing"):
    def rollDice: Task2[Random, Int] =
      Task2.serviceWith[Random, Int](_.nextInt(6) + 1)

    // In production: use live random
    val liveResult = rollDice.run(Random.live)
    assert(liveResult.exists(n => n >= 1 && n <= 6))

    // In tests: use seeded random for determinism
    val seeded = Random.seeded(42L)
    val result1 = rollDice.run(seeded)

    val seeded2 = Random.seeded(42L)  // same seed
    val result2 = rollDice.run(seeded2)

    assert(result1 == result2, "Same seed should produce same results")

  test("complex workflow with three capabilities and retry"):
    // === Capabilities ===
    trait Database:
      def findOrder(id: String): Result[Order]
      def updateStatus(id: String, status: String): Result[Unit]

    trait PaymentGateway:
      def charge(amount: BigDecimal, cardToken: String): Result[String] // returns transaction ID

    case class Order(id: String, amount: BigDecimal, cardToken: String, customerEmail: String)

    // === Task definitions - note how types show requirements ===

    def loadOrder(id: String): Task2[Database, Order] =
      Task2.serviceWithTask[Database, Order](_.findOrder(id))

    def processPayment(order: Order): Task2[PaymentGateway, String] =
      Task2.serviceWithTask[PaymentGateway, String](_.charge(order.amount, order.cardToken))

    def sendReceipt(email: String, transactionId: String): Task2[EmailService, Unit] =
      Task2.serviceWithTask[EmailService, Unit](_.sendEmail(email, s"Receipt: $transactionId"))

    def markComplete(orderId: String): Task2[Database, Unit] =
      Task2.serviceWithTask[Database, Unit](_.updateStatus(orderId, "complete"))

    def logStep(msg: String): Task2[Logs, Unit] =
      Task2.serviceWith[Logs, Unit](_.logInfo(msg))

    // === Combined workflow - type shows ALL requirements ===
    def processOrder(orderId: String): Task2[Database & PaymentGateway & EmailService & Logs, String] =
      for
        _     <- logStep(s"Processing order $orderId")
        order <- loadOrder(orderId)
        _     <- logStep(s"Charging ${order.amount}")
        txnId <- processPayment(order).retry(3)  // retry payment up to 3 times
        _     <- logStep(s"Payment successful: $txnId")
        _     <- sendReceipt(order.customerEmail, txnId).retry(2)  // retry email up to 2 times
        _     <- markComplete(orderId)
        _     <- logStep("Order complete")
      yield txnId

    // === Test implementation with flaky payment ===
    var paymentAttempts = 0
    var emailAttempts = 0
    var logMessages = List.empty[String]

    val testEnv = new Database with PaymentGateway with EmailService with Logs:
      def findOrder(id: String) = Right(Order(id, 99.99, "card_xxx", "bob@test.com"))
      def updateStatus(id: String, status: String) = Right(())

      def charge(amount: BigDecimal, cardToken: String) =
        paymentAttempts += 1
        if paymentAttempts < 2 then Left(Fail("payment", "gateway timeout"))
        else Right("txn_12345")

      def sendEmail(to: String, body: String) =
        emailAttempts += 1
        if emailAttempts < 2 then Left(Fail("email", "SMTP error"))
        else Right(())

      def logInfo(msg: String) = logMessages = logMessages :+ msg
      def logError(fail: Fail) = logMessages = logMessages :+ s"ERROR: $fail"

    // === Execute ===
    paymentAttempts = 0
    emailAttempts = 0
    logMessages = Nil

    val result = processOrder("order_123").run(testEnv)

    // === Verify ===
    assert(result == Right("txn_12345"))
    assert(paymentAttempts == 2, s"Payment should retry once, got $paymentAttempts attempts")
    assert(emailAttempts == 2, s"Email should retry once, got $emailAttempts attempts")
    assert(logMessages.contains("Processing order order_123"))
    assert(logMessages.contains("Payment successful: txn_12345"))
    assert(logMessages.contains("Order complete"))


