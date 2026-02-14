package server

import org.slf4j.LoggerFactory
import scala.concurrent.{Future, ExecutionContext}
import scala.util.Try

// Trait to allow mocking in tests
trait RandomWordClient:
  def fetchRandomWords(count: Int = 2)(using ec: ExecutionContext): Future[Seq[String]]

object RandomWordClient:
  private val logger = LoggerFactory.getLogger(getClass)

  // Swappable instance for testing
  @volatile private var _instance: RandomWordClient = RealRandomWordClient()

  // For tests only - swap the backing implementation
  def setInstance(client: RandomWordClient): Unit =
    _instance = client

  def resetInstance(): Unit =
    _instance = RealRandomWordClient()

  // Delegate method to the swappable instance
  def fetchRandomWords(count: Int = 2)(using ec: ExecutionContext): Future[Seq[String]] =
    _instance.fetchRandomWords(count)

// Real implementation that calls external API
class RealRandomWordClient extends RandomWordClient:
  private val logger = LoggerFactory.getLogger(getClass)

  /** Fetch random words from the random word API */
  def fetchRandomWords(count: Int = 2)(using ec: ExecutionContext): Future[Seq[String]] =
    Future:
      val url = s"https://random-word-api.herokuapp.com/word?number=$count"
      val connection = java.net.URI.create(url).toURL.openConnection().asInstanceOf[java.net.HttpURLConnection]

      Try:
        connection.setRequestMethod("GET")
        connection.setConnectTimeout(10000)
        connection.setReadTimeout(10000)

        val responseCode = connection.getResponseCode
        val inputStream = if responseCode >= 400 then connection.getErrorStream else connection.getInputStream
        val response = scala.io.Source.fromInputStream(inputStream, "UTF-8").mkString
        inputStream.close()

        if responseCode != 200 then
          logger.error(s"Random word API error (status $responseCode): $response")
          throw new RuntimeException(s"Random word API request failed with status $responseCode")

        // Parse JSON array like ["word1", "word2"]
        val words = ujson.read(response).arr.map(_.str).toSeq
        logger.info(s"Fetched random words: ${words.mkString(", ")}")
        words
      .recover:
        case ex: Exception =>
          logger.error(s"Random word API request failed: ${ex.getMessage}", ex)
          throw ex
      .get
