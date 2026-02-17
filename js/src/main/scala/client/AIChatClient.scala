package client

import org.scalajs.dom
import org.scalajs.dom.*
import shared.AIChat.*
import client.{el, div, span, p, h1, h2, h3, button, input, form, floatingInput, *}

import scala.scalajs.js
import scala.util.{Try, Success, Failure}
import scala.util.chaining.*
import scala.collection.mutable

// AI Chat Client - Interactive chat with OpenAI

def initializeAIChat(): Unit =
  println("[AIChat] Starting AI Chat client...")
  AIChatClient.buildUI()
  AIChatClient.connectWebSocket()

object AIChatClient:
  // State
  private var chatWebSocket: Option[WebSocket] = None
  private var apiKeySet: Boolean = false
  private var chatMessages: mutable.ArrayBuffer[ChatMessage] = mutable.ArrayBuffer.empty
  private var currentEditingMessageId: Option[String] = None
  private var streamingContent: mutable.Map[String, String] = mutable.Map.empty
  private var pendingImages: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty
  private var messageIdCounter: Long = 0

  // Generate unique message IDs (ScalaJS-compatible)
  private def generateMessageId(): String =
    messageIdCounter += 1
    s"msg-${System.currentTimeMillis()}-$messageIdCounter"

  def buildUI(): Unit =
    document.body.innerHTML = ""

    document.body(
      NavigationBar.render("AI Chat"),
      div(cls = "container chat-container")(
        // Sidebar for settings
        div(id = "chatSidebar", cls = "chat-sidebar")(
          h3(content = "Settings"),
          // API Key input
          div(cls = "sidebar-section")(
            el("label", cls = "sidebar-label", content = "OpenAI API Key"),
            div(cls = "api-key-input")(
              input("password", id = "apiKeyInput", cls = "input-field").tap: inp =>
                inp.placeholder = "sk-..."
                inp.autocomplete = "off"
              ,
              button(cls = "btn btn-sm", content = "Set").tap: btn =>
                btn.addEventListener("click", (e: Event) => setApiKey())
            ),
            span(id = "apiKeyStatus", cls = "status-text")
          ),
          // System prompt
          div(cls = "sidebar-section")(
            el("label", cls = "sidebar-label", content = "System Prompt"),
            el("textarea", id = "systemPrompt", cls = "textarea-field").tap: textarea =>
              textarea.asInstanceOf[HTMLTextAreaElement].placeholder = AIChat.defaultSystemPrompt
              textarea.asInstanceOf[HTMLTextAreaElement].rows = 4
          ),
          // Actions
          div(cls = "sidebar-section sidebar-actions")(
            button(cls = "btn btn-secondary btn-full", content = "Clear Chat").tap: btn =>
              btn.addEventListener("click", (e: Event) => clearChat())
          )
        ),
        // Main chat area
        div(cls = "chat-main")(
          // Messages container
          div(id = "messagesContainer", cls = "messages-container")(
            div(id = "emptyState", cls = "empty-state")(
              h2(content = "Start a conversation"),
              p(content = "Enter your API key and send a message to begin chatting with AI.")
            )
          ),
          // Input area
          div(cls = "chat-input-area")(
            div(cls = "input-container")(
              // Image preview area
              div(id = "imagePreview", cls = "image-preview hidden"),
              // Text input
              div(cls = "input-row")(
                el("textarea", id = "messageInput", cls = "message-input").tap: textarea =>
                  val ta = textarea.asInstanceOf[HTMLTextAreaElement]
                  ta.placeholder = "Type your message..."
                  ta.rows = 1
                  ta.addEventListener("keydown", (e: KeyboardEvent) =>
                    if e.key == "Enter" && !e.shiftKey then
                      e.preventDefault()
                      sendChatMessage()
                  )
                  ta.addEventListener("input", (e: Event) => autoResizeTextarea(ta))
                ,
                // Image upload button
                button(cls = "btn btn-icon", content = "📷").tap: btn =>
                  btn.title = "Add image"
                  btn.addEventListener("click", (e: Event) => triggerImageUpload())
                ,
                // Send button
                button(id = "sendBtn", cls = "btn btn-primary", content = "Send").tap: btn =>
                  btn.addEventListener("click", (e: Event) => sendChatMessage())
              ),
              // Hidden file input for images
              input("file", id = "imageFileInput", cls = "hidden").tap: inp =>
                inp.accept = "image/*"
                inp.multiple = true
                inp.addEventListener("change", (e: Event) => handleImageSelect(e))
            )
          )
        )
      )
    )

  def connectWebSocket(): Unit =
    val protocol = if window.location.protocol == "https:" then "wss:" else "ws:"
    val wsUrl = s"$protocol//${window.location.host}/ws/ai-chat"

    val ws = new WebSocket(wsUrl)
    chatWebSocket = Some(ws)

    ws.onopen = (e: Event) =>
      println("[AIChat] WebSocket connected")

    ws.onmessage = (event: MessageEvent) =>
      handleServerMessage(event.data.toString)

    ws.onerror = (event: Event) =>
      println("[AIChat] WebSocket error")

    ws.onclose = (event: CloseEvent) =>
      println("[AIChat] WebSocket disconnected")
      // Attempt reconnect after delay
      dom.window.setTimeout(() => connectWebSocket(), 3000)

  private def handleServerMessage(data: String): Unit =
    Try(upickle.default.read[ServerMessage](data)) match
      case Success(msg) => processServerMessage(msg)
      case Failure(ex) =>
        println(s"[AIChat] Error parsing message: ${ex.getMessage}")

  private def processServerMessage(msg: ServerMessage): Unit =
    msg match
      case ServerMessage.ApiKeySet(valid) =>
        apiKeySet = valid
        getElementById("apiKeyStatus").foreach: elem =>
          elem.textContent = if valid then "✓ API key set" else "✗ Invalid"
          elem.className = s"status-text ${if valid then "status-success" else "status-error"}"

      case ServerMessage.MessageAdded(message) =>
        hideEmptyState()
        chatMessages += message
        if message.role == MessageRole.Assistant && message.content.isEmpty then
          // Start streaming - add placeholder
          streamingContent(message.id) = ""
          addMessageToUI(message, isStreaming = true)
        else
          addMessageToUI(message)
        scrollToBottom()

      case ServerMessage.MessageUpdated(message) =>
        val idx = chatMessages.indexWhere(_.id == message.id)
        if idx >= 0 then
          chatMessages(idx) = message
          updateMessageInUI(message)

      case ServerMessage.MessageDeleted(messageId) =>
        chatMessages = chatMessages.filterNot(_.id == messageId)
        removeMessageFromUI(messageId)

      case ServerMessage.StreamingChunk(messageId, chunk) =>
        streamingContent.get(messageId) match
          case Some(existing) =>
            val newContent = existing + chunk
            streamingContent(messageId) = newContent
            updateStreamingMessage(messageId, newContent)
          case None =>
            streamingContent(messageId) = chunk
            updateStreamingMessage(messageId, chunk)

      case ServerMessage.StreamingComplete(messageId) =>
        streamingContent.get(messageId).foreach: finalContent =>
          val idx = chatMessages.indexWhere(_.id == messageId)
          if idx >= 0 then
            chatMessages(idx) = chatMessages(idx).copy(content = finalContent)
            finalizeStreamingMessage(messageId, finalContent)
        streamingContent.remove(messageId)

      case ServerMessage.ChatCleared() =>
        chatMessages.clear()
        clearMessagesUI()
        showEmptyState()

      case ServerMessage.ErrorMessage(message) =>
        println(s"[AIChat] Error: $message")
        showError(message)

  private def setApiKey(): Unit =
    getInputValue("apiKeyInput").foreach: apiKey =>
      if apiKey.nonEmpty then
        sendClientMessage(ClientMessage.SetApiKey(apiKey))

  private def sendChatMessage(): Unit =
    if !apiKeySet then
      showError("Please set your API key first")
      return

    val messageText = getElementById("messageInput")
      .map(_.asInstanceOf[HTMLTextAreaElement].value.trim)
      .getOrElse("")

    if messageText.isEmpty && pendingImages.isEmpty then return

    // Get system prompt if this is first message
    val systemPrompt = getElementById("systemPrompt")
      .map(_.asInstanceOf[HTMLTextAreaElement].value.trim)
      .filter(_.nonEmpty)
      .getOrElse(AIChat.defaultSystemPrompt)

    // If chat is empty, add system message first
    if chatMessages.isEmpty then
      val systemMsg = ChatMessage(
        id = generateMessageId(),
        role = MessageRole.System,
        content = systemPrompt
      )
      chatMessages += systemMsg
      // Don't show system message in UI, but include in API calls

    // Create user message
    val userMessage = ChatMessage(
      id = generateMessageId(),
      role = MessageRole.User,
      content = messageText,
      images = pendingImages.toSeq
    )

    // Build full conversation for API (including the new user message)
    val fullConversation = chatMessages.toSeq :+ userMessage

    // Send only GenerateWithContext - it will handle both adding the message and generating response
    sendConversationContext(fullConversation)

    // Clear input
    getElementById("messageInput").foreach: elem =>
      elem.asInstanceOf[HTMLTextAreaElement].value = ""
      autoResizeTextarea(elem.asInstanceOf[HTMLTextAreaElement])
    clearImagePreview()

  // We need to send full context for AI response
  private def sendConversationContext(messages: Seq[ChatMessage]): Unit =
    // The server handler will use SendMessage to generate response
    // but we need to pass all messages for context
    // For now, we'll send a special combined message
    chatWebSocket.foreach: ws =>
      val contextMsg = ujson.Obj(
        "$type" -> "GenerateWithContext",
        "messages" -> upickle.default.writeJs(messages)
      )
      ws.send(ujson.write(contextMsg))

  private def clearChat(): Unit =
    sendClientMessage(ClientMessage.ClearChat())

  private def sendClientMessage(msg: ClientMessage): Unit =
    chatWebSocket.foreach: ws =>
      val json = upickle.default.write(msg)
      ws.send(json)

  private def triggerImageUpload(): Unit =
    getElementById("imageFileInput").foreach: elem =>
      elem.asInstanceOf[HTMLInputElement].click()

  private def handleImageSelect(e: Event): Unit =
    val input = e.target.asInstanceOf[HTMLInputElement]
    val files = input.files

    for i <- 0 until files.length do
      val file = files(i)
      if file.size > AIChat.maxImageSizeMB * 1024 * 1024 then
        showError(s"Image ${file.name} is too large (max ${AIChat.maxImageSizeMB}MB)")
      else if pendingImages.length >= AIChat.maxImagesPerMessage then
        showError(s"Maximum ${AIChat.maxImagesPerMessage} images per message")
      else
        val reader = new FileReader()
        reader.onload = (e: Event) =>
          val base64 = reader.result.asInstanceOf[String]
          pendingImages += base64
          updateImagePreview()
        reader.readAsDataURL(file)

    // Reset input for re-selection
    input.value = ""

  private def updateImagePreview(): Unit =
    getElementById("imagePreview").foreach: container =>
      container.innerHTML = ""
      container.classList.remove("hidden")

      pendingImages.zipWithIndex.foreach: (imgData, idx) =>
        val preview = div(cls = "image-preview-item")(
          el("img").tap: img =>
            img.asInstanceOf[HTMLImageElement].src = imgData
          ,
          button(cls = "remove-image", content = "×").tap: btn =>
            btn.addEventListener("click", (e: Event) =>
              pendingImages.remove(idx)
              updateImagePreview()
            )
        )
        container.appendChild(preview)

      if pendingImages.isEmpty then
        container.classList.add("hidden")

  private def clearImagePreview(): Unit =
    pendingImages.clear()
    getElementById("imagePreview").foreach: container =>
      container.innerHTML = ""
      container.classList.add("hidden")

  private def addMessageToUI(message: ChatMessage, isStreaming: Boolean = false): Unit =
    getElementById("messagesContainer").foreach: container =>
      val messageEl = createMessageElement(message, isStreaming)
      container.appendChild(messageEl)

  private def createMessageElement(message: ChatMessage, isStreaming: Boolean = false): HTMLElement =
    val roleClass = message.role match
      case MessageRole.System => "message-system"
      case MessageRole.User => "message-user"
      case MessageRole.Assistant => "message-assistant"

    val roleName = message.role match
      case MessageRole.System => "System"
      case MessageRole.User => "You"
      case MessageRole.Assistant => "Assistant"

    div(id = s"message-${message.id}", cls = s"message $roleClass")(
      div(cls = "message-header")(
        span(cls = "message-role", content = roleName),
        div(cls = "message-actions")(
          button(cls = "action-btn", content = "✏️").tap: btn =>
            btn.title = "Edit"
            btn.addEventListener("click", (e: Event) => startEditMessage(message.id))
          ,
          button(cls = "action-btn", content = "🗑️").tap: btn =>
            btn.title = "Delete"
            btn.addEventListener("click", (e: Event) => deleteMessage(message.id))
          ,
          // Regenerate button only for assistant messages
          if message.role == MessageRole.Assistant then
            button(cls = "action-btn", content = "🔄").tap: btn =>
              btn.title = "Regenerate"
              btn.addEventListener("click", (e: Event) => regenerateMessage(message.id))
          else
            span() // Empty placeholder
        )
      ),
      // Images if any
      if message.images.nonEmpty then
        val imageElements = message.images.map: imgData =>
          el("img", cls = "message-image").tap: img =>
            img.asInstanceOf[HTMLImageElement].src = imgData
        div(cls = "message-images")(imageElements*)
      else
        span(cls = "hidden")
      ,
      // Content
      {
        val contentElements: Seq[HTMLElement] =
          if isStreaming then Seq(span(cls = "cursor", content = "▌"))
          else formatMessageContent(message.content)
        div(id = s"content-${message.id}", cls = s"message-content${if isStreaming then " streaming" else ""}")(
          contentElements*
        )
      },
      // Edit form (hidden by default)
      div(id = s"edit-${message.id}", cls = "message-edit hidden")(
        el("textarea", cls = "edit-textarea").tap: ta =>
          ta.asInstanceOf[HTMLTextAreaElement].value = message.content
        ,
        div(cls = "edit-actions")(
          button(cls = "btn btn-sm btn-success", content = "Save").tap: btn =>
            btn.addEventListener("click", (e: Event) => saveEditMessage(message.id))
          ,
          button(cls = "btn btn-sm btn-secondary", content = "Cancel").tap: btn =>
            btn.addEventListener("click", (e: Event) => cancelEditMessage(message.id))
        )
      )
    )

  private def formatMessageContent(content: String): Seq[HTMLElement] =
    // Simple formatting - split by double newlines for paragraphs
    // Handle code blocks
    val parts = content.split("```")
    val elements = mutable.ArrayBuffer[HTMLElement]()

    parts.zipWithIndex.foreach: (part, idx) =>
      if idx % 2 == 1 then
        // Code block
        val lines = part.split("\n", 2)
        val lang = if lines.length > 1 && lines(0).nonEmpty then lines(0) else ""
        val code = if lines.length > 1 then lines(1) else part
        elements += el("pre", cls = "code-block")(
          el("code", content = code)
        )
      else
        // Regular text - split into paragraphs
        part.split("\n\n").filter(_.nonEmpty).foreach: para =>
          elements += p(content = para.trim)

    if elements.isEmpty then Seq(p(content = content))
    else elements.toSeq

  private def updateMessageInUI(message: ChatMessage): Unit =
    getElementById(s"content-${message.id}").foreach: elem =>
      elem.innerHTML = ""
      formatMessageContent(message.content).foreach(elem.appendChild(_))

  private def removeMessageFromUI(messageId: String): Unit =
    getElementById(s"message-$messageId").foreach(_.remove())

  private def updateStreamingMessage(messageId: String, content: String): Unit =
    getElementById(s"content-$messageId").foreach: elem =>
      elem.innerHTML = ""
      formatMessageContent(content).foreach(elem.appendChild(_))
      elem.appendChild(span(cls = "cursor", content = "▌"))

  private def finalizeStreamingMessage(messageId: String, content: String): Unit =
    getElementById(s"content-$messageId").foreach: elem =>
      elem.classList.remove("streaming")
      elem.innerHTML = ""
      formatMessageContent(content).foreach(elem.appendChild(_))

  private def startEditMessage(messageId: String): Unit =
    currentEditingMessageId = Some(messageId)
    getElementById(s"content-$messageId").foreach(_.classList.add("hidden"))
    getElementById(s"edit-$messageId").foreach: elem =>
      elem.classList.remove("hidden")
      // Focus textarea
      elem.querySelector("textarea") match
        case ta: HTMLTextAreaElement => ta.focus()
        case _ => ()

  private def cancelEditMessage(messageId: String): Unit =
    currentEditingMessageId = None
    getElementById(s"content-$messageId").foreach(_.classList.remove("hidden"))
    getElementById(s"edit-$messageId").foreach(_.classList.add("hidden"))

  private def saveEditMessage(messageId: String): Unit =
    getElementById(s"edit-$messageId").foreach: editContainer =>
      editContainer.querySelector("textarea") match
        case ta: HTMLTextAreaElement =>
          val newContent = ta.value
          chatMessages.find(_.id == messageId).foreach: msg =>
            val updatedMsg = msg.copy(content = newContent)
            sendClientMessage(ClientMessage.EditMessage(updatedMsg))
            chatMessages(chatMessages.indexWhere(_.id == messageId)) = updatedMsg
            updateMessageInUI(updatedMsg)
        case _ => ()
    cancelEditMessage(messageId)

  private def deleteMessage(messageId: String): Unit =
    if dom.window.confirm("Delete this message?") then
      sendClientMessage(ClientMessage.DeleteMessage(messageId))

  private def regenerateMessage(messageId: String): Unit =
    // Find this message and delete it, then regenerate
    val idx = chatMessages.indexWhere(_.id == messageId)
    if idx >= 0 then
      // Remove this and all following messages
      val toRemove = chatMessages.drop(idx)
      toRemove.foreach: msg =>
        chatMessages -= msg
        removeMessageFromUI(msg.id)

      // Get conversation up to this point
      val conversationSoFar = chatMessages.toSeq

      // Send request to regenerate
      if conversationSoFar.nonEmpty then
        sendConversationContext(conversationSoFar)

  private def clearMessagesUI(): Unit =
    getElementById("messagesContainer").foreach: container =>
      container.innerHTML = ""

  private def showEmptyState(): Unit =
    getElementById("messagesContainer").foreach: container =>
      container.appendChild(
        div(id = "emptyState", cls = "empty-state")(
          h2(content = "Start a conversation"),
          p(content = "Enter your API key and send a message to begin chatting with AI.")
        )
      )

  private def hideEmptyState(): Unit =
    getElementById("emptyState").foreach(_.remove())

  private def scrollToBottom(): Unit =
    getElementById("messagesContainer").foreach: container =>
      container.scrollTop = container.scrollHeight

  private def autoResizeTextarea(ta: HTMLTextAreaElement): Unit =
    ta.style.height = "auto"
    ta.style.height = s"${Math.min(ta.scrollHeight, 200)}px"

  private def showError(message: String): Unit =
    // Show error toast
    val toast: HTMLElement = div(cls = "error-toast")(
      span(content = message),
      button(cls = "toast-close", content = "×")
    )

    // Add close button handler
    Option(toast.querySelector(".toast-close")).foreach: btn =>
      btn.addEventListener("click", (e: Event) => toast.remove())

    document.body.appendChild(toast)

    // Auto-remove after 5 seconds
    dom.window.setTimeout(() => toast.remove(), 5000)

