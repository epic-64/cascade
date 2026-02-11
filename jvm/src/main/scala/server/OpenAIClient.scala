package server

import org.slf4j.LoggerFactory
import scala.concurrent.{Future, ExecutionContext}
import scala.util.Try
import shared.DrawingGame.CaptionStyle

// Trait to allow mocking in tests
trait OpenAIClient:
  def captionImage(apiKey: String, imageBase64: String, captionStyle: CaptionStyle = CaptionStyle.Descriptive)(using
      ec: ExecutionContext
  ): Future[String]
  def generatePromptFromWords(apiKey: String, words: Seq[String])(using ec: ExecutionContext): Future[String]
  def selectWinner(
      apiKey: String,
      originalPrompt: String,
      captions: Map[String, String],
      captionStyle: CaptionStyle = CaptionStyle.Descriptive
  )(using ec: ExecutionContext): Future[OpenAIClient.WinnerSelection]

object OpenAIClient:
  private val logger = LoggerFactory.getLogger(getClass)

  private val visionModel = "gpt-4o"
  private val textModel = "gpt-4o-mini"

  case class WinnerSelection(winnerName: String, reasoning: String)

  // Swappable instance for testing
  @volatile private var _instance: OpenAIClient = RealOpenAIClient()

  // For tests only - swap the backing implementation
  def setInstance(client: OpenAIClient): Unit =
    _instance = client

  def resetInstance(): Unit =
    _instance = RealOpenAIClient()

  // Delegate methods to the swappable instance

  def captionImage(apiKey: String, imageBase64: String, captionStyle: CaptionStyle = CaptionStyle.Descriptive)(using
      ec: ExecutionContext
  ): Future[String] =
    _instance.captionImage(apiKey, imageBase64, captionStyle)

  def generatePromptFromWords(apiKey: String, words: Seq[String])(using ec: ExecutionContext): Future[String] =
    _instance.generatePromptFromWords(apiKey, words)

  def selectWinner(
      apiKey: String,
      originalPrompt: String,
      captions: Map[String, String],
      captionStyle: CaptionStyle = CaptionStyle.Descriptive
  )(using ec: ExecutionContext): Future[WinnerSelection] =
    _instance.selectWinner(apiKey, originalPrompt, captions, captionStyle)

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


  import shared.DrawingGame.CaptionStyle

  def captionImage(apiKey: String, imageBase64: String, captionStyle: CaptionStyle = CaptionStyle.Descriptive)(using
      ec: ExecutionContext
  ): Future[String] =
    val url = "https://api.openai.com/v1/chat/completions"

    // Remove data URL prefix if present
    val cleanBase64 = imageBase64.replaceFirst("^data:image/png;base64,", "")

    val promptText = captionStyle match
      case CaptionStyle.Descriptive =>
        """Describe this drawing in 10-20 words. Include:
          |1. What the drawing depicts
          |2. A brief comment on the artistic skill or style (e.g., "skillfully rendered", "charmingly simple", "impressively detailed", "delightfully wonky")
          |Be witty and entertaining. Do not use quotes.""".stripMargin
      case CaptionStyle.Roast =>
        """Absolutely DESTROY this drawing in 10-20 words. Be ruthlessly savage about:
          |1. What this disaster is supposedly meant to be
          |2. The tragic artistic crimes committed here
          |Channel your inner Gordon Ramsay meets Simon Cowell. Show NO mercy. 
          |Mock everything - the wobbly lines, the questionable proportions, the artistic delusions.
          |Make it hurt (but funny). Do not use quotes.""".stripMargin

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
      "max_tokens" -> 100
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
      captions: Map[String, String],
      captionStyle: CaptionStyle = CaptionStyle.Descriptive
  )(using ec: ExecutionContext): Future[WinnerSelection] =
    val url = "https://api.openai.com/v1/chat/completions"

    val captionsList = captions.map((name, caption) => s"- $name: \"$caption\"").mkString("\n")

    val systemPrompt = captionStyle match
      case CaptionStyle.Descriptive =>
        """You are a witty and insightful judge for a drawing game called "AI Drawing Challenge".
          |Players drew an image based on a secret prompt. An AI then captioned each drawing without knowing the prompt.
          |Your job is to pick the winner whose drawing (as interpreted by the AI caption) best matches the original prompt.
          |
          |Be entertaining and specific in your reasoning! Comment on what made the winning drawing stand out.
          |Keep the reasoning to 1-2 sentences max.
          |
          |Respond in this exact JSON format:
          |{"winner": "PlayerName", "reasoning": "Your witty explanation here"}""".stripMargin
      case CaptionStyle.Roast =>
        """You are a BRUTAL and merciless judge for a drawing game. You take pleasure in destroying artistic dreams.
          |Players drew an image based on a secret prompt. An AI roasted each drawing.
          |Your job is to pick the "winner" - but let's be real, everyone here is a loser at art.
          |
          |Be ABSOLUTELY SAVAGE in your reasoning. Mock the winner for barely being less terrible than the others.
          |Roast their artistic abilities. Question their life choices. Make it personal but hilarious.
          |Channel your inner Simon Cowell having a really bad day. Show NO mercy.
          |Keep the reasoning to 1-2 brutal sentences max.
          |
          |Respond in this exact JSON format:
          |{"winner": "PlayerName", "reasoning": "Your devastating roast here"}""".stripMargin

    val userPrompt = s"""The secret prompt was: "$originalPrompt"
      |
      |Here are the AI-generated captions for each player's drawing:
      |$captionsList
      |
      |Pick the winner and explain your choice!""".stripMargin

    val requestBody = ujson.Obj(
      "model" -> textModel,
      "messages" -> ujson.Arr(
        ujson.Obj("role" -> "system", "content" -> systemPrompt),
        ujson.Obj("role" -> "user", "content" -> userPrompt)
      ),
      "temperature" -> 0.7,
      "max_tokens" -> 150
    )

    makeOpenAIRequest(url, apiKey, requestBody).map: responseJson =>
      Try:
        val content = responseJson("choices")(0)("message")("content").str.trim
        val parsed = ujson.read(content)
        WinnerSelection(
          parsed("winner").str.trim,
          parsed("reasoning").str.trim
        )
      .getOrElse:
        logger.error(s"Failed to parse OpenAI winner response: $responseJson")
        // Fallback: return first player name
        WinnerSelection(
          captions.keys.headOption.getOrElse("Unknown"),
          "The AI couldn't decide, so it picked randomly!"
        )
