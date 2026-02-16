package server

import org.slf4j.LoggerFactory
import scala.concurrent.{Future, ExecutionContext}
import scala.util.Try

// Trait to allow mocking in tests
trait OpenAIClient:
  def captionImage(apiKey: String, imageBase64: String)(using
      ec: ExecutionContext
  ): Future[String]
  def generatePromptFromWords(apiKey: String, words: Seq[String])(using ec: ExecutionContext): Future[String]
  def selectWinner(
      apiKey: String,
      originalPrompt: String,
      captions: Map[String, String]
  )(using ec: ExecutionContext): Future[String]

object OpenAIClient:
  private val logger = LoggerFactory.getLogger(getClass)

  private val visionModel = "gpt-4o"
  private val textModel = "gpt-4o-mini"

  // Swappable instance for testing
  @volatile private var _instance: OpenAIClient = RealOpenAIClient()

  // For tests only - swap the backing implementation
  def setInstance(client: OpenAIClient): Unit =
    _instance = client

  def resetInstance(): Unit =
    _instance = RealOpenAIClient()

  // Delegate methods to the swappable instance

  def captionImage(apiKey: String, imageBase64: String)(using
      ec: ExecutionContext
  ): Future[String] =
    _instance.captionImage(apiKey, imageBase64)

  def generatePromptFromWords(apiKey: String, words: Seq[String])(using ec: ExecutionContext): Future[String] =
    _instance.generatePromptFromWords(apiKey, words)

  def selectWinner(
      apiKey: String,
      originalPrompt: String,
      captions: Map[String, String]
  )(using ec: ExecutionContext): Future[String] =
    _instance.selectWinner(apiKey, originalPrompt, captions)

  // Internal helper for making OpenAI requests (used by RealOpenAIClient)
  private[server] def makeOpenAIRequest(
      url: String,
      apiKey: String,
      requestBody: ujson.Obj
  )(using ec: ExecutionContext): Future[ujson.Value] =
    Future:
      val connection = java.net.URI.create(url).toURL.openConnection().asInstanceOf[java.net.HttpURLConnection]

      Try:
        connection.setRequestMethod("POST")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", s"Bearer $apiKey")
        connection.setDoOutput(true)
        connection.setConnectTimeout(30000) // 30 seconds
        connection.setReadTimeout(30000)

        // Write request body
        val outputStream = connection.getOutputStream
        outputStream.write(requestBody.toString.getBytes("UTF-8"))
        outputStream.flush()
        outputStream.close()

        // Read response
        val responseCode = connection.getResponseCode
        val inputStream = if responseCode >= 400 then connection.getErrorStream else connection.getInputStream
        val response = scala.io.Source.fromInputStream(inputStream, "UTF-8").mkString
        inputStream.close()

        if responseCode != 200 then
          logger.error(s"OpenAI API error (status $responseCode): $response")
          throw new RuntimeException(s"OpenAI API request failed with status $responseCode")

        ujson.read(response)
      .recover:
        case ex: Exception =>
          logger.error(s"OpenAI API request failed: ${ex.getMessage}", ex)
          throw ex
      .get

// Real implementation that calls external APIs
class RealOpenAIClient extends OpenAIClient:
  import OpenAIClient.*

  private val logger = LoggerFactory.getLogger(getClass)
  private val visionModel = "gpt-4o"
  private val textModel = "gpt-4o-mini"

  def captionImage(apiKey: String, imageBase64: String)(using
      ec: ExecutionContext
  ): Future[String] =
    val url = "https://api.openai.com/v1/chat/completions"

    // Remove data URL prefix if present
    val cleanBase64 = imageBase64.replaceFirst("^data:image/png;base64,", "")

    val promptText = "Describe this drawing in 5-10 words. Just say what it depicts. Be short and direct. Do not use quotes."

    val requestBody = ujson.Obj(
      "model" -> visionModel,
      "messages" -> ujson.Arr(
        ujson.Obj(
          "role" -> "user",
          "content" -> ujson.Arr(
            ujson.Obj(
              "type" -> "text",
              "text" -> promptText
            ),
            ujson.Obj(
              "type" -> "image_url",
              "image_url" -> ujson.Obj(
                "url" -> s"data:image/png;base64,$cleanBase64"
              )
            )
          )
        )
      ),
      "max_tokens" -> 50
    )

    makeOpenAIRequest(url, apiKey, requestBody).map: responseJson =>
      Try:
        responseJson("choices")(0)("message")("content").str
      .getOrElse:
        logger.error(s"Failed to parse OpenAI caption response: $responseJson")
        "Unable to caption image"

  def generatePromptFromWords(apiKey: String, words: Seq[String])(using ec: ExecutionContext): Future[String] =
    val url = "https://api.openai.com/v1/chat/completions"

    val wordsList = words.mkString(", ")

    val systemPrompt = """You are a creative prompt generator for a drawing game.
      |Given some random words, create a short, fun, and drawable prompt.
      |The prompt should be 3-6 words max and describe something that can be drawn.
      |Be creative and combine the words in unexpected ways!
      |Just respond with the prompt itself, no quotes or explanation.""".stripMargin

    val userPrompt = s"Create a drawing prompt using these words: $wordsList"

    val requestBody = ujson.Obj(
      "model" -> textModel,
      "messages" -> ujson.Arr(
        ujson.Obj("role" -> "system", "content" -> systemPrompt),
        ujson.Obj("role" -> "user", "content" -> userPrompt)
      ),
      "temperature" -> 0.9,
      "max_tokens" -> 30
    )

    makeOpenAIRequest(url, apiKey, requestBody).map: responseJson =>
      Try:
        responseJson("choices")(0)("message")("content").str.trim
      .getOrElse:
        logger.error(s"Failed to parse OpenAI prompt response: $responseJson")
        // Fallback to just the words
        words.mkString(" ")

  def selectWinner(
      apiKey: String,
      originalPrompt: String,
      captions: Map[String, String]
  )(using ec: ExecutionContext): Future[String] =
    val url = "https://api.openai.com/v1/chat/completions"

    val captionsList = captions.map((name, caption) => s"- $name: \"$caption\"").mkString("\n")

    val systemPrompt = """You are a judge for a drawing game.
      |Players drew an image based on a secret prompt. An AI then captioned each drawing without knowing the prompt.
      |Your job is to pick the winner whose drawing (as interpreted by the AI caption) best matches the original prompt.
      |
      |Respond with ONLY the winner's name, nothing else.""".stripMargin

    val userPrompt = s"""The secret prompt was: "$originalPrompt"
      |
      |Here are the AI-generated captions for each player's drawing:
      |$captionsList
      |
      |Pick the winner.""".stripMargin

    val requestBody = ujson.Obj(
      "model" -> textModel,
      "messages" -> ujson.Arr(
        ujson.Obj("role" -> "system", "content" -> systemPrompt),
        ujson.Obj("role" -> "user", "content" -> userPrompt)
      ),
      "temperature" -> 0.7,
      "max_tokens" -> 50
    )

    makeOpenAIRequest(url, apiKey, requestBody).map: responseJson =>
      Try:
        responseJson("choices")(0)("message")("content").str.trim
      .getOrElse:
        logger.error(s"Failed to parse OpenAI winner response: $responseJson")
        // Fallback: return first player name
        captions.keys.headOption.getOrElse("Unknown")
