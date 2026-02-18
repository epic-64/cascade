package server

import cask.WebsocketResult
import cask.util.Ws
import org.slf4j.LoggerFactory
import shared.AIChat.*
import upickle.default.*
import castor.Context.Simple.global

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global as ec
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object AIChatHandler:
  private val logger = LoggerFactory.getLogger(getClass)

  // Castor context for WebSocket operations
  given castor.Context = global

  // Cask logger for WebSocket operations
  given cask.util.Logger = cask.util.Logger.Console.globalLogger

  // Store API key per connection (in memory only, not persisted)
  private val connectionApiKeys = mutable.Map[cask.WsChannelActor, String]()

  def handleWebSocket(): WebsocketResult =
    cask.WsHandler: channel =>
      cask.WsActor:
        case Ws.Text(data) =>
          handleMessage(channel, data)
        case Ws.Close(_, _) =>
          connectionApiKeys.remove(channel)
          logger.info("[AIChat] WebSocket connection closed")

  private def handleMessage(channel: cask.WsChannelActor, data: String): Unit =
    // First try to parse as a special GenerateWithContext message
    Try(ujson.read(data)) match
      case Success(json) if json.obj.get("$type").exists(_.str == "GenerateWithContext") =>
        handleGenerateWithContext(channel, json)
      case _ =>
        // Otherwise parse as regular ClientMessage
        Try(read[ClientMessage](data)) match
          case Success(msg) => processMessage(channel, msg)
          case Failure(ex) =>
            logger.error(s"[AIChat] Failed to parse message: ${ex.getMessage}")
            sendMessage(channel, ServerMessage.ErrorMessage(s"Invalid message format: ${ex.getMessage}"))

  private def handleGenerateWithContext(channel: cask.WsChannelActor, json: ujson.Value): Unit =
    connectionApiKeys.get(channel) match
      case Some(apiKey) =>
        Try:
          val messages = read[Seq[ChatMessage]](json("messages"))
          // Echo back the user message (last message in conversation) before generating
          messages.lastOption.filter(_.role == MessageRole.User).foreach: userMsg =>
            sendMessage(channel, ServerMessage.MessageAdded(userMsg))
          generateResponse(channel, apiKey, messages)
        .recover:
          case ex: Exception =>
            logger.error(s"[AIChat] Failed to parse context: ${ex.getMessage}")
            sendMessage(channel, ServerMessage.ErrorMessage(s"Failed to parse context: ${ex.getMessage}"))
      case None =>
        sendMessage(channel, ServerMessage.ErrorMessage("Please set your API key first"))

  private def processMessage(channel: cask.WsChannelActor, msg: ClientMessage): Unit =
    msg match
      case ClientMessage.SetApiKey(apiKey) =>
        connectionApiKeys(channel) = apiKey
        logger.info("[AIChat] API key set for connection")
        sendMessage(channel, ServerMessage.ApiKeySet(true))

      case ClientMessage.SendMessage(message) =>
        connectionApiKeys.get(channel) match
          case Some(apiKey) =>
            // First, echo back the user message
            sendMessage(channel, ServerMessage.MessageAdded(message))
            // Then generate AI response
            generateResponse(channel, apiKey, Seq(message))
          case None =>
            sendMessage(channel, ServerMessage.ErrorMessage("Please set your API key first"))

      case ClientMessage.EditMessage(message) =>
        sendMessage(channel, ServerMessage.MessageUpdated(message))

      case ClientMessage.DeleteMessage(messageId) =>
        sendMessage(channel, ServerMessage.MessageDeleted(messageId))

      case ClientMessage.RegenerateResponse(afterMessageId) =>
        // Client will send the conversation history separately
        sendMessage(channel, ServerMessage.MessageDeleted(afterMessageId))

      case ClientMessage.ClearChat() =>
        sendMessage(channel, ServerMessage.ChatCleared())

  private def generateResponse(channel: cask.WsChannelActor, apiKey: String, messages: Seq[ChatMessage]): Unit =
    // Create a new message ID for the response
    val responseId = java.util.UUID.randomUUID().toString

    // Build the messages for OpenAI API
    val openAIMessages = messages.map: msg =>
      val contentParts = mutable.ArrayBuffer[ujson.Value]()

      // Add text content
      if msg.content.nonEmpty then
        contentParts += ujson.Obj("type" -> "text", "text" -> msg.content)

      // Add images if present
      msg.images.foreach: imageData =>
        val cleanBase64 = imageData.replaceFirst("^data:image/[^;]+;base64,", "")
        contentParts += ujson.Obj(
          "type" -> "image_url",
          "image_url" -> ujson.Obj("url" -> s"data:image/png;base64,$cleanBase64")
        )

      val role = msg.role match
        case MessageRole.System => "system"
        case MessageRole.User => "user"
        case MessageRole.Assistant => "assistant"

      if contentParts.length == 1 && msg.images.isEmpty then
        ujson.Obj("role" -> role, "content" -> msg.content)
      else
        ujson.Obj("role" -> role, "content" -> ujson.Arr(contentParts.toSeq*))

    // Make the API request with streaming
    val url = "https://api.openai.com/v1/chat/completions"
    val requestBody = ujson.Obj(
      "model" -> "gpt-4o",
      "messages" -> ujson.Arr(openAIMessages*),
      "stream" -> true
    )

    // Send initial message to indicate streaming has started
    val initialMessage = ChatMessage(responseId, MessageRole.Assistant, "")
    sendMessage(channel, ServerMessage.MessageAdded(initialMessage))

    Future:
      streamOpenAIRequest(channel, responseId, url, apiKey, requestBody)
    .recover:
      case ex: Exception =>
        logger.error(s"[AIChat] Error generating response: ${ex.getMessage}", ex)
        sendMessage(channel, ServerMessage.ErrorMessage(s"Error: ${ex.getMessage}"))

  private val httpClient = java.net.http.HttpClient.newBuilder()
    .connectTimeout(java.time.Duration.ofSeconds(30))
    .build()

  private def streamOpenAIRequest(
      channel: cask.WsChannelActor,
      messageId: String,
      url: String,
      apiKey: String,
      requestBody: ujson.Obj
  ): Unit =
    val request = java.net.http.HttpRequest.newBuilder()
      .uri(java.net.URI.create(url))
      .header("Content-Type", "application/json")
      .header("Authorization", s"Bearer $apiKey")
      .header("Accept", "text/event-stream")
      .timeout(java.time.Duration.ofSeconds(120))
      .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody.toString))
      .build()

    try
      val response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream())

      if response.statusCode() != 200 then
        val errorResponse = scala.io.Source.fromInputStream(response.body(), "UTF-8").mkString
        response.body().close()
        logger.error(s"[AIChat] OpenAI API error: $errorResponse")
        sendMessage(channel, ServerMessage.ErrorMessage(s"API error (status ${response.statusCode()})"))
        return

      val inputStream = response.body()
      val reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream, "UTF-8"), 1)

      var line: String = null
      while { line = reader.readLine(); line != null } do
        if line.startsWith("data: ") then
          val data = line.substring(6)
          if data != "[DONE]" then
            Try(ujson.read(data)) match
              case Success(json) =>
                val delta = json("choices")(0)("delta")
                if delta.obj.contains("content") then
                  val chunk = delta("content").str
                  sendMessage(channel, ServerMessage.StreamingChunk(messageId, chunk))
              case Failure(_) => // Ignore parse errors for SSE

      reader.close()
      inputStream.close()

      sendMessage(channel, ServerMessage.StreamingComplete(messageId))

    catch
      case ex: Exception =>
        logger.error(s"[AIChat] Streaming error: ${ex.getMessage}", ex)
        sendMessage(channel, ServerMessage.ErrorMessage(s"Streaming error: ${ex.getMessage}"))

  private def sendMessage(channel: cask.WsChannelActor, msg: ServerMessage): Unit =
    Try:
      val json = write(msg)
      channel.send(Ws.Text(json))
    .recover:
      case ex: Exception =>
        logger.error(s"[AIChat] Failed to send message: ${ex.getMessage}")

