package shared.AIChat

import upickle.default.ReadWriter

// AI Chat - Interactive chat with OpenAI models

// Role for chat messages
enum MessageRole derives ReadWriter:
  case System
  case User
  case Assistant

// A single chat message
case class ChatMessage(
    id: String, // Unique ID for editing
    role: MessageRole,
    content: String,
    images: Seq[String] = Seq.empty // base64 encoded images
) derives ReadWriter

// Client -> Server messages
enum ClientMessage derives ReadWriter:
  case SendMessage(message: ChatMessage)
  case EditMessage(message: ChatMessage)
  case DeleteMessage(messageId: String)
  case RegenerateResponse(afterMessageId: String) // Regenerate response after a specific message
  case StopStreaming(messageId: String) // Abort an in-progress streaming response
  case ClearChat()
  case ListModels(apiKey: String)
  case SpeakMessage(messageId: String, text: String, apiKey: String, voice: String = "alloy", prompt: String = "") // TTS via OpenAI

// Server -> Client messages  
enum ServerMessage derives ReadWriter:
  case MessageAdded(message: ChatMessage)
  case MessageUpdated(message: ChatMessage)
  case MessageDeleted(messageId: String)
  case StreamingChunk(messageId: String, chunk: String) // For streaming responses
  case StreamingComplete(messageId: String, model: String = "", promptTokens: Int = 0, completionTokens: Int = 0)
  case ChatCleared()
  case ErrorMessage(message: String)
  case ModelsListed(models: Seq[String])
  case TTSAudio(messageId: String, audioBase64: String) // Base64-encoded mp3 audio
  case TTSError(messageId: String, error: String)

object AIChat:
  val defaultSystemPrompt = "You are a helpful assistant."
  val defaultModel = "gpt-4o"
  val maxImagesPerMessage = 4
  val maxImageSizeMB = 20

