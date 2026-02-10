package server

import org.slf4j.LoggerFactory
import scala.concurrent.{Future, ExecutionContext}
import scala.util.Try

object OpenAIClient:
  private val logger = LoggerFactory.getLogger(getClass)

  private val visionModel = "gpt-4o"
  private val textModel = "gpt-4o-mini"

  def captionImage(apiKey: String, imageBase64: String)(using ec: ExecutionContext): Future[String] =
    val url = "https://api.openai.com/v1/chat/completions"

    // Remove data URL prefix if present
    val cleanBase64 = imageBase64.replaceFirst("^data:image/png;base64,", "")

    val requestBody = ujson.Obj(
      "model" -> visionModel,
      "messages" -> ujson.Arr(
        ujson.Obj(
          "role" -> "user",
          "content" -> ujson.Arr(
            ujson.Obj(
              "type" -> "text",
              "text" -> "Describe this drawing in 5 words or less. Be direct and concise."
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

  case class WinnerSelection(winnerName: String, reasoning: String)

  def selectWinner(
    apiKey: String,
    originalPrompt: String,
    captions: Map[String, String]
  )(using ec: ExecutionContext): Future[WinnerSelection] =
    val url = "https://api.openai.com/v1/chat/completions"

    val captionsList = captions.map((name, caption) => s"- $name: \"$caption\"").mkString("\n")

    val systemPrompt = """You are a witty and insightful judge for a drawing game called "AI Drawing Challenge".
      |Players drew an image based on a secret prompt. An AI then captioned each drawing without knowing the prompt.
      |Your job is to pick the winner whose drawing (as interpreted by the AI caption) best matches the original prompt.
      |
      |Be entertaining and specific in your reasoning! Comment on what made the winning drawing stand out.
      |Keep the reasoning to 1-2 sentences max.
      |
      |Respond in this exact JSON format:
      |{"winner": "PlayerName", "reasoning": "Your witty explanation here"}""".stripMargin

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

  private def makeOpenAIRequest(
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

