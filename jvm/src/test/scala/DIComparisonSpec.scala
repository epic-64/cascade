import org.scalatest.funsuite.AnyFunSuite
import shared.task.*

/**
 * Comparing three approaches to documenting/tracking side effects:
 * 1. Task2[R, A] - effect in return type
 * 2. (using R) => Task[A] - effect in context parameter
 * 3. Plain DI - effect in regular parameter
 */
class DIComparisonSpec extends AnyFunSuite:

  // Task execution requires these
  given shared.task.Logger = shared.task.Logger.silent
  given Timer = Timer.silent

  test("Task2 approach"):
    import shared.task.Task2.*

    trait Database:
      def findUser(id: String): Result[String]

    trait EmailService:
      def send(to: String, body: String): Result[Unit]

    trait Logger:
      def info(msg: String): Unit

    def getUser(id: String): Task2[Database, String] =
      Task2.serviceWithTask[Database, String](_.findUser(id))

    def notifyUser(email: String, msg: String): Task2[EmailService, Unit] =
      Task2.serviceWithTask[EmailService, Unit](_.send(email, msg))

    def log(msg: String): Task2[Logger, Unit] =
      Task2.serviceWith[Logger, Unit](_.info(msg))

    def workflow(userId: String): Task2[EmailService & Logger & Database, String] =
      for
        _     <- log(s"Looking up user $userId")
        email <- getUser(userId)
        _     <- log(s"Sending welcome to $email")
        _     <- notifyUser(email, "Welcome!")
      yield email

    val env = new Database with EmailService with Logger:
      def findUser(id: String) = Right(s"$id@test.com")
      def send(to: String, body: String) = Right(())
      def info(msg: String) = ()

    val result = workflow("user123").run(env)
    assert(result == Right("user123@test.com"))

  test("Context parameters approach"):
    trait Database:
      def findUser(id: String): Result[String]

    trait EmailService:
      def send(to: String, body: String): Result[Unit]

    trait Logger:
      def info(msg: String): Unit

    def getUser(id: String)(using db: Database): Task[String] =
      Task(db.findUser(id))

    def notifyUser(to: String, msg: String)(using es: EmailService): Task[Unit] =
      Task(es.send(to, msg))

    def log(msg: String)(using logger: Logger): Task[Unit] =
      Task:
        logger.info(msg)
        Right(())

    def workflow(userId: String)(using Database, EmailService, Logger): Task[String] =
      for
        _     <- log(s"Looking up user $userId")
        email <- getUser(userId)
        _     <- log(s"Sending welcome to $email")
        _     <- notifyUser(email, "Welcome!")
      yield email

    given Database = id => Right(s"$id@test.com")
    given EmailService = (to, body) => Right(())
    given Logger = msg => ()

    val result = workflow("user123").execute
    assert(result == Right("user123@test.com"))

  test("Plain parameters approach"):
    trait Database:
      def findUser(id: String): Result[String]

    trait EmailService:
      def send(to: String, body: String): Result[Unit]

    trait Logger:
      def info(msg: String): Unit

    def getUser(id: String, db: Database): Task[String] =
      Task(db.findUser(id))

    def notifyUser(to: String, msg: String, emailService: EmailService): Task[Unit] =
      Task(emailService.send(to, msg))

    def log(msg: String, logger: Logger): Task[Unit] =
      Task:
        logger.info(msg)
        Right(())

    def workflow(userId: String, db: Database, emailService: EmailService, logger: Logger): Task[String] =
      for
        _     <- log(s"Looking up user $userId", logger)
        email <- getUser(userId, db)
        _     <- log(s"Sending welcome to $email", logger)
        _     <- notifyUser(email, "Welcome!", emailService)
      yield email

    val testDb: Database = id => Right(s"$id@test.com")
    val testEmail: EmailService = (to, body) => Right(())
    val testLogger: Logger = msg => ()

    val result = workflow("user123", testDb, testEmail, testLogger).execute
    assert(result == Right("user123@test.com"))

  test("comparison of signatures"):
    // All three document the same dependencies, just differently:

    // Task2: dependencies in return type
    // def workflow(userId: String): Task2[Database & EmailService & Logger, String]

    // Context params: dependencies in using clause
    // def workflow(userId: String)(using Database, EmailService, Logger): Task[String]

    // Plain params: dependencies in parameter list
    // def workflow(userId: String, db: Database, email: EmailService, logger: Logger): Task[String]

    // All three:
    // ✅ Document what effects are needed
    // ✅ Allow swapping implementations for testing
    // ✅ Compiler checks you provide dependencies
    // ❌ None automatically detect undeclared side effects

    succeed // This test is just documentation

  test("Task2 accumulates requirements automatically across module boundaries"):
    import shared.task.Task2.*

    // Imagine these are in separate files/modules, maintained by different people

    trait UserDb:
      def findUser(id: String): Result[String]

    def getUser(id: String): Task2[UserDb, String] =
      Task2.serviceWithTask[UserDb, String](_.findUser(id))

    trait Smtp:
      def send(to: String, body: String): Result[Unit]

    def sendEmail(to: String, body: String): Task2[Smtp, Unit] =
      Task2.serviceWithTask[Smtp, Unit](_.send(to, body))

    trait AuditLog:
      def log(event: String): Result[Unit]

    def audit(event: String): Task2[AuditLog, Unit] =
      Task2.serviceWithTask[AuditLog, Unit](_.log(event))

    // Now in your application code, you compose them:
    // The return type AUTOMATICALLY shows all requirements!
    def welcomeUser(id: String): Task2[UserDb & Smtp & AuditLog, Unit] =
      for
        email <- getUser(id)           // adds UserDb
        _     <- sendEmail(email, "Welcome!")  // adds Smtp
        _     <- audit(s"Welcomed $id")        // adds AuditLog
      yield ()

    // If EmailModule adds a new dependency (e.g., Templates),
    // your function's type AUTOMATICALLY updates.
    // You don't need to change welcomeUser's signature!

    // Test it works
    val env = new UserDb with Smtp with AuditLog:
      def findUser(id: String) = Right("test@test.com")
      def send(to: String, body: String) = Right(())
      def log(event: String) = Right(())

    assert(welcomeUser("123").run(env) == Right(()))

  test("using params require manual declaration of transitive dependencies"):
    trait UserDb:
      def findUser(id: String): Result[String]

    def getUser(id: String)(using db: UserDb): Task[String] =
      Task(db.findUser(id))

    trait Smtp:
      def send(to: String, body: String): Result[Unit]

    def sendEmail(to: String, body: String)(using smtp: Smtp): Task[Unit] =
      Task(smtp.send(to, body))

    trait AuditLog:
      def log(event: String): Result[Unit]

    def audit(event: String)(using log: AuditLog): Task[Unit] =
      Task(log.log(event))

    // You MUST manually list all dependencies
    // If EmailModule adds a Templates dependency, this BREAKS at compile time
    // and you must update EVERY caller
    def welcomeUser(id: String)(using UserDb, Smtp, AuditLog): Task[Unit] =
      for
        email <- getUser(id)
        _     <- sendEmail(email, "Welcome!")
        _     <- audit(s"Welcomed $id")
      yield ()

    // This is GOOD for small projects (explicit is clear)
    // This is BAD for large projects (changing a leaf module breaks all callers)

    given UserDb = id => Right("test@test.com")
    given Smtp = (to, body) => Right(())
    given AuditLog = event => Right(())

    assert(welcomeUser("123").execute == Right(()))

  test("the real difference: adding a dependency to a leaf module"):
    import shared.task.Task2.*

    // === BEFORE: EmailModule has one dependency ===

    trait SmtpV1:
      def send(to: String, body: String): Result[Unit]

    def sendEmailV1(to: String, body: String): Task2[SmtpV1, Unit] =
      Task2.serviceWithTask[SmtpV1, Unit](_.send(to, body))

    // === AFTER: EmailModule adds Templates dependency ===

    trait SmtpV2:
      def send(to: String, body: String): Result[Unit]
    trait Templates:
      def render(name: String): Result[String]

    // Now requires BOTH Smtp and Templates
    def sendEmailV2(to: String, body: String): Task2[SmtpV2 & Templates, Unit] =
      for
        html <- Task2.serviceWithTask[Templates, String](_.render("welcome"))
        _    <- Task2.serviceWithTask[SmtpV2, Unit](_.send(to, html))
      yield ()

    // === With Task2: caller's type updates automatically ===

    // Type is Task2[SmtpV1, Unit]
    def notifyV1(email: String): Task2[SmtpV1, Unit] = sendEmailV1(email, "Hi")

    // Type is AUTOMATICALLY Task2[SmtpV2 & Templates, Unit] - no code change!
    def notifyV2(email: String): Task2[SmtpV2 & Templates, Unit] = sendEmailV2(email, "Hi")

    // === With using: caller must manually update ===

    // Before: def notify(email: String)(using Smtp): Task[Unit]
    // After:  def notify(email: String)(using Smtp, Templates): Task[Unit]  <- MANUAL CHANGE

    // And every caller of notify() must also update... all the way up the chain!

    assert(true) // This test is just documentation of the difference

