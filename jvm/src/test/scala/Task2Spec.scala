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

  // === Real-world environment patterns ===

  test("pattern 1: case class environment bundles capabilities"):
    // Instead of mixing traits, bundle them in a case class
    case class AppEnv(
      logs: Logs,
      clock: Clock,
      random: Random
    )

    // Tasks access what they need via the bundle
    def logTime: Task2[AppEnv, Unit] = Task2[AppEnv, Unit]: env =>
      val time = env.clock.currentTimeMillis
      env.logs.logInfo(s"Current time: $time")
      Right(())

    def randomDelay: Task2[AppEnv, Int] = Task2[AppEnv, Int]: env =>
      Right(env.random.nextInt(100))

    val workflow: Task2[AppEnv, Int] = for
      _     <- logTime
      delay <- randomDelay
    yield delay

    // Clean instantiation - no mixin gymnastics
    val env = AppEnv(
      logs = Logs.console,
      clock = Clock.system,
      random = Random.live
    )

    assert(workflow.run(env).isRight)

  test("pattern 2: provide layers incrementally"):
    // Start with fine-grained requirements
    def needsClock: Task2[Clock, Long] =
      Task2.serviceWith[Clock, Long](_.currentTimeMillis)

    def needsRandom: Task2[Random, Int] =
      Task2.serviceWith[Random, Int](_.nextInt(100))

    // Combine them
    val combined: Task2[Clock & Random, (Long, Int)] =
      needsClock.zip(needsRandom)

    // Provide capabilities one at a time using provide
    val withClock: Task2[Random, (Long, Int)] = combined.provide: random =>
      new Clock with Random:
        def currentTimeMillis = Clock.system.currentTimeMillis
        def nextInt(bound: Int) = random.nextInt(bound)
        def nextDouble = random.nextDouble

    // Now only Random is needed
    val result = withClock.run(Random.seeded(42L))
    assert(result.isRight)

  test("pattern 3: module pattern for production code"):
    // Define a module that holds related capabilities
    trait OrderModule:
      def database: Database
      def payments: PaymentGateway
      def email: EmailService
      def logs: Logs

    // Capabilities for this test
    trait Database:
      def findOrder(id: String): Result[String]

    trait PaymentGateway:
      def charge(amount: Int): Result[String]

    // Tasks are defined against the module
    def getOrder(id: String): Task2[OrderModule, String] =
      Task2.serviceWithTask[OrderModule, String](_.database.findOrder(id))

    def chargeCard(amount: Int): Task2[OrderModule, String] =
      Task2.serviceWithTask[OrderModule, String](_.payments.charge(amount))

    def sendNotification(msg: String): Task2[OrderModule, Unit] =
      Task2.serviceWithTask[OrderModule, Unit](_.email.sendEmail("user@test.com", msg))

    def log(msg: String): Task2[OrderModule, Unit] =
      Task2.serviceWith[OrderModule, Unit](_.logs.logInfo(msg))

    val workflow: Task2[OrderModule, String] = for
      _       <- log("Starting order processing")
      orderId <- getOrder("123")
      txnId   <- chargeCard(100)
      _       <- sendNotification(s"Order $orderId charged: $txnId")
    yield txnId

    // Production: real implementations
    object ProdModule extends OrderModule:
      val database = new Database:
        def findOrder(id: String) = Right(s"order-$id")
      val payments = new PaymentGateway:
        def charge(amount: Int) = Right(s"txn-${System.currentTimeMillis}")
      val email = new EmailService:
        def sendEmail(to: String, body: String) = Right(()) // real SMTP here
      val logs = Logs.console

    // Test: mock implementations
    object TestModule extends OrderModule:
      val database = new Database:
        def findOrder(id: String) = Right("test-order")
      val payments = new PaymentGateway:
        def charge(amount: Int) = Right("test-txn")
      val email = new EmailService:
        def sendEmail(to: String, body: String) = Right(())
      val logs = Logs.silent

    // Same workflow, different environments
    assert(workflow.run(ProdModule).isRight)
    assert(workflow.run(TestModule) == Right("test-txn"))

  test("pattern 4: main app entry point"):
    // This is how your main app would look

    // 1. Define your capabilities
    trait AppCapabilities:
      def logs: Logs
      def clock: Clock

    // 2. Define your app logic as Task2
    def appLogic: Task2[AppCapabilities, String] = for
      _    <- Task2.serviceWith[AppCapabilities, Unit](_.logs.logInfo("App starting"))
      time <- Task2.serviceWith[AppCapabilities, Long](_.clock.currentTimeMillis)
      _    <- Task2.serviceWith[AppCapabilities, Unit](_.logs.logInfo(s"Time: $time"))
    yield s"Started at $time"

    // 3. In main(), create the live environment and run
    object LiveEnv extends AppCapabilities:
      val logs = Logs.console
      val clock = Clock.system

    // def main(args: Array[String]): Unit =
    //   appLogic.run(LiveEnv) match
    //     case Right(msg) => println(s"Success: $msg")
    //     case Left(fail) => println(s"Failed: $fail"); sys.exit(1)

    // For this test, just verify it works
    assert(appLogic.run(LiveEnv).isRight)

  test("incorporating non-Task2 functions into for-comprehensions"):
    // Regular functions that don't return Task2
    def pureCalculation(x: Int): Int = x * 2
    def parseNumber(s: String): Option[Int] = s.toIntOption
    def riskyParse(s: String): scala.util.Try[Int] = scala.util.Try(s.toInt)
    def validate(n: Int): Either[String, Int] = if n > 0 then Right(n) else Left("must be positive")

    // Method 1: Use = for pure transformations (no lifting needed!)
    val withPure: Task2[Any, Int] = for
      base   <- Task2.succeed(21)
      doubled = pureCalculation(base)  // just use = for pure functions
    yield doubled

    assert(withPure.run(()) == Right(42))

    // Method 2: Use Task2.fromOption for Option-returning functions
    val withOption: Task2[Any, Int] = for
      input  <- Task2.succeed("42")
      parsed <- Task2.fromOption("parseNumber")(parseNumber(input))
    yield parsed

    assert(withOption.run(()) == Right(42))

    val withOptionFail: Task2[Any, Int] = for
      input  <- Task2.succeed("not a number")
      parsed <- Task2.fromOption("parseNumber", "invalid integer")(parseNumber(input))
    yield parsed

    assert(withOptionFail.run(()) == Left(Fail("parseNumber", "invalid integer")))

    // Method 3: Use Task2.fromTry for Try-returning functions
    val withTry: Task2[Any, Int] = for
      input  <- Task2.succeed("42")
      parsed <- Task2.fromTry("riskyParse")(riskyParse(input))
    yield parsed

    assert(withTry.run(()) == Right(42))

    // Method 4: Use Task2.fromEither for Either-returning functions
    val withEither: Task2[Any, Int] = for
      n         <- Task2.succeed(10)
      validated <- Task2.fromEither("validate")(validate(n))
    yield validated

    assert(withEither.run(()) == Right(10))

    val withEitherFail: Task2[Any, Int] = for
      n         <- Task2.succeed(-5)
      validated <- Task2.fromEither("validate")(validate(n))
    yield validated

    assert(withEitherFail.run(()) == Left(Fail("validate", "must be positive")))

    // Method 5: Complex example mixing everything
    def fetchConfig: Task2[Any, Map[String, String]] =
      Task2.succeed(Map("maxRetries" -> "3", "timeout" -> "5000"))

    // Side-effectful function should be wrapped in a capability!
    trait Env:
      def getVar(name: String): Option[String]
    
    object Env:
      val live: Env = name => sys.env.get(name)
      val test: Env = name => if name == "API_KEY" then Some("secret123") else None

    // Now getEnvVar is a proper Task2 with a requirement
    def getEnvVar(name: String): Task2[Env, String] =
      Task2.serviceWithTask[Env, String]: env =>
        env.getVar(name).toResult("env." + name, s"$name not set")

    val complexWorkflow: Task2[Logs & Env, String] = for
      config     <- fetchConfig
      maxRetries  = config.getOrElse("maxRetries", "1").toInt  // pure, use =
      timeout     = config.getOrElse("timeout", "1000").toInt   // pure, use =
      apiKey     <- getEnvVar("API_KEY")  // Now properly tracked as Env requirement!
      _          <- Task2.serviceWith[Logs, Unit](_.logInfo(s"Using $maxRetries retries, ${timeout}ms timeout"))
    yield s"Configured with key ${apiKey.take(3)}***"

    // Environment must provide both Logs and Env
    val testEnv = new Logs with Env:
      def logInfo(msg: String) = ()
      def logError(fail: Fail) = ()
      def getVar(name: String) = Env.test.getVar(name)

    assert(complexWorkflow.run(testEnv) == Right("Configured with key sec***"))

  test("two workflows with overlapping but different environments"):
    // === Define capabilities ===
    trait Auth:
      def validateToken(token: String): Result[String]  // returns userId
    
    trait Cache:
      def get(key: String): Option[String]
      def set(key: String, value: String): Unit
    
    trait Metrics:
      def increment(counter: String): Unit
    
    // === Workflow 1: needs Logs & Auth & Cache ===
    def authenticateAndCache(token: String): Task2[Logs & Auth & Cache, String] = for
      _      <- Task2.serviceWith[Logs, Unit](_.logInfo(s"Authenticating token"))
      userId <- Task2.serviceWithTask[Auth, String](_.validateToken(token))
      _      <- Task2.serviceWith[Cache, Unit](_.set(s"session:$token", userId))
      _      <- Task2.serviceWith[Logs, Unit](_.logInfo(s"Cached session for $userId"))
    yield userId
    
    // === Workflow 2: needs Logs & Auth & Metrics ===
    def authenticateAndTrack(token: String): Task2[Logs & Auth & Metrics, String] = for
      _      <- Task2.serviceWith[Logs, Unit](_.logInfo(s"Authenticating token"))
      userId <- Task2.serviceWithTask[Auth, String](_.validateToken(token))
      _      <- Task2.serviceWith[Metrics, Unit](_.increment("auth.success"))
      _      <- Task2.serviceWith[Logs, Unit](_.logInfo(s"Tracked auth for $userId"))
    yield userId
    
    // === Combined workflow: needs Logs & Auth & Cache & Metrics ===
    // Both workflows share Logs and Auth, but one needs Cache, the other Metrics
    def fullAuthFlow(token: String): Task2[Logs & Auth & Cache & Metrics, String] = for
      userId1 <- authenticateAndCache(token)
      userId2 <- authenticateAndTrack(token)
      _       <- Task2.serviceWith[Metrics, Unit](_.increment("auth.complete"))
    yield userId1
    
    // === Track what happens ===
    var logs = List.empty[String]
    var cacheContents = Map.empty[String, String]
    var metricCounts = Map.empty[String, Int]
    
    // === Environment that satisfies Logs & Auth & Cache & Metrics ===
    val fullEnv = new Logs with Auth with Cache with Metrics:
      def logInfo(msg: String) = logs = logs :+ msg
      def logError(fail: Fail) = logs = logs :+ s"ERROR: $fail"
      def validateToken(token: String) = 
        if token == "valid-token" then Right("user-123") 
        else Left(Fail("auth", "invalid token"))
      def get(key: String) = cacheContents.get(key)
      def set(key: String, value: String) = cacheContents = cacheContents + (key -> value)
      def increment(counter: String) = 
        metricCounts = metricCounts.updatedWith(counter)(c => Some(c.getOrElse(0) + 1))
    
    // === Run the combined workflow ===
    logs = Nil
    cacheContents = Map.empty
    metricCounts = Map.empty
    
    val result = fullAuthFlow("valid-token").run(fullEnv)
    
    assert(result == Right("user-123"))
    assert(cacheContents == Map("session:valid-token" -> "user-123"))
    assert(metricCounts == Map("auth.success" -> 1, "auth.complete" -> 1))
    assert(logs.count(_.contains("Authenticating")) == 2)  // called twice, once per sub-workflow
    
    // === Demonstrate running each workflow separately with smaller envs ===
    
    // Workflow 1 only needs Logs & Auth & Cache
    logs = Nil
    cacheContents = Map.empty
    val cacheOnlyEnv = new Logs with Auth with Cache:
      def logInfo(msg: String) = logs = logs :+ msg
      def logError(fail: Fail) = ()
      def validateToken(token: String) = Right("user-456")
      def get(key: String) = cacheContents.get(key)
      def set(key: String, value: String) = cacheContents = cacheContents + (key -> value)
    
    assert(authenticateAndCache("another-token").run(cacheOnlyEnv) == Right("user-456"))
    assert(cacheContents == Map("session:another-token" -> "user-456"))
    
    // Workflow 2 only needs Logs & Auth & Metrics
    logs = Nil
    metricCounts = Map.empty
    val metricsOnlyEnv = new Logs with Auth with Metrics:
      def logInfo(msg: String) = logs = logs :+ msg
      def logError(fail: Fail) = ()
      def validateToken(token: String) = Right("user-789")
      def increment(counter: String) = 
        metricCounts = metricCounts.updatedWith(counter)(c => Some(c.getOrElse(0) + 1))
    
    assert(authenticateAndTrack("yet-another-token").run(metricsOnlyEnv) == Right("user-789"))
    assert(metricCounts == Map("auth.success" -> 1))
    
    // === What if one workflow needs a DIFFERENT Auth implementation? ===
    // Use provide to substitute a different auth service for one workflow
    
    // Alternative auth that uses a different validation strategy
    trait PremiumAuth extends Auth:
      def validateToken(token: String): Result[String] = 
        if token.startsWith("premium-") then Right(s"premium-user-${token.drop(8)}")
        else Left(Fail("auth", "not a premium token"))
    
    // Workflow that uses premium auth but still shares Logs & Cache
    def premiumAuthenticateAndCache(token: String): Task2[Logs & Cache, String] =
      authenticateAndCache(token).provide: (env: Logs & Cache) =>
        new Logs with Auth with Cache:
          // Delegate Logs to the outer env
          def logInfo(msg: String): Unit = env.logInfo(msg)
          def logError(fail: Fail): Unit = env.logError(fail)
          // Use our own premium auth
          def validateToken(t: String): Result[String] = 
            if t.startsWith("premium-") then Right(s"premium-user-${t.drop(8)}")
            else Left(Fail("auth", "not a premium token"))
          // Delegate Cache to the outer env
          def get(key: String): Option[String] = env.get(key)
          def set(key: String, value: String): Unit = env.set(key, value)
    
    // Now this workflow only needs Logs & Cache - Auth is "baked in"
    logs = Nil
    cacheContents = Map.empty
    val logsCacheEnv = new Logs with Cache:
      def logInfo(msg: String): Unit = logs = logs :+ msg
      def logError(fail: Fail): Unit = ()
      def get(key: String): Option[String] = cacheContents.get(key)
      def set(key: String, value: String): Unit = cacheContents = cacheContents + (key -> value)
    
    // Premium tokens work
    assert(premiumAuthenticateAndCache("premium-abc").run(logsCacheEnv) == Right("premium-user-abc"))
    assert(cacheContents == Map("session:premium-abc" -> "premium-user-abc"))
    
    // Regular tokens fail with premium auth
    cacheContents = Map.empty
    assert(premiumAuthenticateAndCache("regular-token").run(logsCacheEnv).isLeft)
    
    // === Combine workflows with DIFFERENT auth implementations ===
    // One uses standard auth, one uses premium auth
    def mixedAuthFlow(standardToken: String, premiumToken: String): Task2[Logs & Auth & Cache & Metrics, (String, String)] = for
      // This uses the Auth from the environment
      standardUser <- authenticateAndCache(standardToken)
      // This uses premium auth (baked in), but still needs Logs, Cache, Metrics from env
      premiumUser  <- premiumAuthenticateAndCache(premiumToken).provide: (env: Logs & Auth & Cache & Metrics) =>
        // Just pass through the parts premium workflow needs
        new Logs with Cache:
          def logInfo(msg: String): Unit = env.logInfo(msg)
          def logError(fail: Fail): Unit = env.logError(fail)
          def get(key: String): Option[String] = env.get(key)
          def set(key: String, value: String): Unit = env.set(key, value)
      _ <- Task2.serviceWith[Metrics, Unit](_.increment("mixed.auth.complete"))
    yield (standardUser, premiumUser)
    
    logs = Nil
    cacheContents = Map.empty
    metricCounts = Map.empty
    
    val mixedResult = mixedAuthFlow("valid-token", "premium-xyz").run(fullEnv)
    
    assert(mixedResult == Right(("user-123", "premium-user-xyz")))
    assert(cacheContents == Map(
      "session:valid-token" -> "user-123",
      "session:premium-xyz" -> "premium-user-xyz"
    ))
    assert(metricCounts == Map("mixed.auth.complete" -> 1))


