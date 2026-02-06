package server

import cask.main.MainRoutes
import shared.{SharedGreeter, User}

object WebServer extends MainRoutes:
  // Counter state
  private var counter: Int = 0

  @cask.get("/hello")
  def hello(): String = "Hello, World!"

  @cask.get("/health")
  def health(): String = "OK"

  @cask.get("/counter")
  def getCounter(): Int = counter

  @cask.post("/counter/increment")
  def incrementCounter(): Int =
    counter += 1
    counter

  @cask.post("/counter/decrement")
  def decrementCounter(): Int =
    counter -= 1
    counter

  @cask.get("/user/:userName")
  def getUserProfile(userName: String) = s"User $userName"

  // New route demonstrating use of a shared cross-compiled type
  // GET /greet/42/Alice => Hello, Alice! (#42)
  @cask.get("/greet/:id/:name")
  def greet(id: Int, name: String): String =
    val user = User(id, name)
    SharedGreeter.greet(user)

  @cask.get("/user2/:userName") // allow unknown params, e.g. HOST/user2/foo?foo=bar&qux=baz
  def getUserProfileAllowUnknown(userName: String, params: cask.QueryParams): String =
    s"User $userName " + params.value

  @cask.get("/article/:articleId") // Mandatory query param, e.g. HOST/article/foo?param=bar
  def getArticle(articleId: Int, param: String) =
    s"Article $articleId $param"

  @cask.get("/article2/:articleId") // Optional query param
  def getArticleOptional(articleId: Int, param: Option[String] = None) =
    s"Article $articleId $param"

  @cask.get("/article3/:articleId") // Optional query param with default
  def getArticleDefault(articleId: Int, param: String = "DEFAULT VALUE") =
    s"Article $articleId $param"

  @cask.get("/article4/:articleId") // 1-or-more param, e.g. HOST/article/foo?param=bar&param=qux
  def getArticleSeq(articleId: Int, param: Seq[String]) =
    s"Article $articleId $param"

  @cask.get("/article5/:articleId") // 0-or-more query param
  def getArticleOptionalSeq(articleId: Int, param: Seq[String] = Nil) =
    s"Article $articleId $param"

  override def port: Int = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(8080)

  initialize()
