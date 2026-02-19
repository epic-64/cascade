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
  case SetApiKey(apiKey: String)
  case SendMessage(message: ChatMessage)
  case EditMessage(message: ChatMessage)
  case DeleteMessage(messageId: String)
  case RegenerateResponse(afterMessageId: String) // Regenerate response after a specific message
  case StopStreaming(messageId: String) // Abort an in-progress streaming response
  case ClearChat()
  case ListModels()

// Server -> Client messages  
enum ServerMessage derives ReadWriter:
  case ApiKeySet(valid: Boolean)
  case MessageAdded(message: ChatMessage)
  case MessageUpdated(message: ChatMessage)
  case MessageDeleted(messageId: String)
  case StreamingChunk(messageId: String, chunk: String) // For streaming responses
  case StreamingComplete(messageId: String)
  case ChatCleared()
  case ErrorMessage(message: String)
  case ModelsListed(models: Seq[String])

object AIChat:
  val defaultSystemPrompt = "You are a helpful assistant."
  val defaultModel = "gpt-4o"
  val maxImagesPerMessage = 4
  val maxImageSizeMB = 20

