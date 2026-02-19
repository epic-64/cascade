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


  // Track active streaming message IDs per connection for cancellation
  private val activeStreams = mutable.Map[cask.WsChannelActor, mutable.Set[String]]()

  def handleWebSocket(): WebsocketResult =
    cask.WsHandler: channel =>
      cask.WsActor:
        case Ws.Text(data) =>
          handleMessage(channel, data)
        case Ws.Close(_, _) =>
          activeStreams.remove(channel)
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
    json.obj.get("apiKey").map(_.str).filter(_.nonEmpty) match
      case Some(apiKey) =>
        Try:
          val messages = read[Seq[ChatMessage]](json("messages"))
          val model = json.obj.get("model").map(_.str).getOrElse(AIChat.defaultModel)
          val isRegenerate = json.obj.get("regenerate").exists(_.bool)
          // Echo back the user message only for new messages, not regenerations
          if !isRegenerate then
            messages.lastOption.filter(_.role == MessageRole.User).foreach: userMsg =>
              sendMessage(channel, ServerMessage.MessageAdded(userMsg))
          generateResponse(channel, apiKey, messages, model)
        .recover:
          case ex: Exception =>
            logger.error(s"[AIChat] Failed to parse context: ${ex.getMessage}")
            sendMessage(channel, ServerMessage.ErrorMessage(s"Failed to parse context: ${ex.getMessage}"))
      case None =>
        sendMessage(channel, ServerMessage.ErrorMessage("Please include your API key"))

  private def processMessage(channel: cask.WsChannelActor, msg: ClientMessage): Unit =
    msg match
      case ClientMessage.SendMessage(message) =>
        // Single-message send without context; not used in normal flow
        sendMessage(channel, ServerMessage.ErrorMessage("Please use the chat interface to send messages"))

      case ClientMessage.EditMessage(message) =>
        sendMessage(channel, ServerMessage.MessageUpdated(message))

      case ClientMessage.DeleteMessage(messageId) =>
        sendMessage(channel, ServerMessage.MessageDeleted(messageId))

      case ClientMessage.RegenerateResponse(afterMessageId) =>
        // Client will send the conversation history separately
        sendMessage(channel, ServerMessage.MessageDeleted(afterMessageId))

      case ClientMessage.StopStreaming(messageId) =>
        logger.info(s"[AIChat] Stop streaming requested for message $messageId")
        activeStreams.get(channel).foreach(_.remove(messageId))

      case ClientMessage.ClearChat() =>
        sendMessage(channel, ServerMessage.ChatCleared())

      case ClientMessage.ListModels(apiKey) =>
        if apiKey.nonEmpty then
          Future:
            fetchModels(channel, apiKey)
          .recover:
            case ex: Exception =>
              logger.error(s"[AIChat] Error fetching models: ${ex.getMessage}", ex)
              sendMessage(channel, ServerMessage.ErrorMessage(s"Failed to fetch models: ${ex.getMessage}"))
        else
          sendMessage(channel, ServerMessage.ErrorMessage("Please set your API key first"))

      case ClientMessage.SpeakMessage(messageId, text, apiKey, voice) =>
        if apiKey.nonEmpty then
          Future:
            generateTTS(channel, messageId, text, apiKey, voice)
          .recover:
            case ex: Exception =>
              logger.error(s"[AIChat] TTS error: ${ex.getMessage}", ex)
              sendMessage(channel, ServerMessage.TTSError(messageId, s"TTS failed: ${ex.getMessage}"))
        else
          sendMessage(channel, ServerMessage.TTSError(messageId, "Please set your API key first"))

  private def generateResponse(channel: cask.WsChannelActor, apiKey: String, messages: Seq[ChatMessage], model: String): Unit =
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
      "model" -> model,
      "messages" -> ujson.Arr(openAIMessages*),
      "stream" -> true,
      "stream_options" -> ujson.Obj("include_usage" -> true)
    )

    // Send initial message to indicate streaming has started
    val initialMessage = ChatMessage(responseId, MessageRole.Assistant, "")
    sendMessage(channel, ServerMessage.MessageAdded(initialMessage))

    // Register this stream as active (for cancellation support)
    activeStreams.getOrElseUpdate(channel, mutable.Set.empty).add(responseId)

    Future:
      streamOpenAIRequest(channel, responseId, url, apiKey, requestBody, model)
    .recover:
      case ex: Exception =>
        logger.error(s"[AIChat] Error generating response: ${ex.getMessage}", ex)
        sendMessage(channel, ServerMessage.ErrorMessage(s"Error: ${ex.getMessage}"))

  private val httpClient = java.net.http.HttpClient.newBuilder()
    .connectTimeout(java.time.Duration.ofSeconds(30))
    .build()

  private def fetchModels(channel: cask.WsChannelActor, apiKey: String): Unit =
    val request = java.net.http.HttpRequest.newBuilder()
      .uri(java.net.URI.create("https://api.openai.com/v1/models"))
      .header("Authorization", s"Bearer $apiKey")
      .timeout(java.time.Duration.ofSeconds(30))
      .GET()
      .build()

    try
      val response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
      if response.statusCode() != 200 then
        logger.error(s"[AIChat] OpenAI models API error: ${response.body()}")
        sendMessage(channel, ServerMessage.ErrorMessage(s"Failed to fetch models (status ${response.statusCode()})"))
      else
        val json = ujson.read(response.body())
        val allModels = json("data").arr.map(_("id").str).toSeq

        // Filter to chat-capable models (gpt-*, o1-*, o3-*, o4-*, chatgpt-*)
        val chatModels = allModels
          .filter: id =>
            id.startsWith("gpt-") ||
            id.startsWith("o1-") ||
            id.startsWith("o3-") ||
            id.startsWith("o4-") ||
            id.startsWith("chatgpt-")
          .filterNot: id =>
            id.contains("instruct") ||
            id.contains("realtime") ||
            id.contains("audio") ||
            id.contains("tts") ||
            id.contains("whisper") ||
            id.contains("transcribe") ||
            id.contains("embedding")
          .sorted

        logger.info(s"[AIChat] Fetched ${chatModels.size} chat models")
        sendMessage(channel, ServerMessage.ModelsListed(chatModels))
    catch
      case ex: Exception =>
        logger.error(s"[AIChat] Error fetching models: ${ex.getMessage}", ex)
        sendMessage(channel, ServerMessage.ErrorMessage(s"Failed to fetch models: ${ex.getMessage}"))

  private def isStreamActive(channel: cask.WsChannelActor, messageId: String): Boolean =
    activeStreams.get(channel).exists(_.contains(messageId))

  private def streamOpenAIRequest(
      channel: cask.WsChannelActor,
      messageId: String,
      url: String,
      apiKey: String,
      requestBody: ujson.Obj,
      model: String
  ): Unit =
    val request = java.net.http.HttpRequest.newBuilder()
      .uri(java.net.URI.create(url))
      .header("Content-Type", "application/json")
      .header("Authorization", s"Bearer $apiKey")
      .header("Accept", "text/event-stream")
      .timeout(java.time.Duration.ofSeconds(120))
      .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody.toString))
      .build()

    var promptTokens = 0
    var completionTokens = 0

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
      var cancelled = false
      while !cancelled && { line = reader.readLine(); line != null } do
        if !isStreamActive(channel, messageId) then
          cancelled = true
          logger.info(s"[AIChat] Stream $messageId cancelled by user")
        else if line.startsWith("data: ") then
          val data = line.substring(6)
          if data != "[DONE]" then
            Try(ujson.read(data)) match
              case Success(json) =>
                // Extract usage from the final chunk (when stream_options include_usage is set)
                json.obj.get("usage").foreach: usage =>
                  Try:
                    promptTokens = usage("prompt_tokens").num.toInt
                    completionTokens = usage("completion_tokens").num.toInt

                // Extract content delta
                Try:
                  val delta = json("choices")(0)("delta")
                  if delta.obj.contains("content") then
                    val chunk = delta("content").str
                    sendMessage(channel, ServerMessage.StreamingChunk(messageId, chunk))
              case Failure(_) => // Ignore parse errors for SSE

      reader.close()
      inputStream.close()

      sendMessage(channel, ServerMessage.StreamingComplete(messageId, model, promptTokens, completionTokens))

    catch
      case ex: Exception =>
        logger.error(s"[AIChat] Streaming error: ${ex.getMessage}", ex)
        sendMessage(channel, ServerMessage.ErrorMessage(s"Streaming error: ${ex.getMessage}"))
    finally
      activeStreams.get(channel).foreach(_.remove(messageId))

  private def generateTTS(
      channel: cask.WsChannelActor,
      messageId: String,
      text: String,
      apiKey: String,
      voice: String
  ): Unit =
    val url = "https://api.openai.com/v1/audio/speech"
    val requestBody = ujson.Obj(
      "model" -> "tts-1",
      "input" -> text,
      "voice" -> voice,
      "response_format" -> "mp3"
    )

    val request = java.net.http.HttpRequest.newBuilder()
      .uri(java.net.URI.create(url))
      .header("Content-Type", "application/json")
      .header("Authorization", s"Bearer $apiKey")
      .timeout(java.time.Duration.ofSeconds(60))
      .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody.toString))
      .build()

    try
      val response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())

      if response.statusCode() != 200 then
        val errorText = new String(response.body(), "UTF-8")
        logger.error(s"[AIChat] TTS API error (status ${response.statusCode()}): $errorText")
        sendMessage(channel, ServerMessage.TTSError(messageId, s"TTS API error (status ${response.statusCode()})"))
      else
        val audioBytes = response.body()
        val audioBase64 = java.util.Base64.getEncoder.encodeToString(audioBytes)
        logger.info(s"[AIChat] TTS generated ${audioBytes.length} bytes for message $messageId")
        sendMessage(channel, ServerMessage.TTSAudio(messageId, audioBase64))
    catch
      case ex: Exception =>
        logger.error(s"[AIChat] TTS request error: ${ex.getMessage}", ex)
        sendMessage(channel, ServerMessage.TTSError(messageId, s"TTS request failed: ${ex.getMessage}"))

  private def sendMessage(channel: cask.WsChannelActor, msg: ServerMessage): Unit =
    Try:
      val json = write(msg)
      channel.send(Ws.Text(json))
    .recover:
      case ex: Exception =>
        logger.error(s"[AIChat] Failed to send message: ${ex.getMessage}")

