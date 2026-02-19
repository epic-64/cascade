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
  private var chatMessages: mutable.ArrayBuffer[ChatMessage] = mutable.ArrayBuffer.empty
  private var currentEditingMessageId: Option[String] = None
  private var streamingContent: mutable.Map[String, String] = mutable.Map.empty
  private var activeStreamingId: Option[String] = None
  private var pendingImages: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty
  private var messageIdCounter: Long = 0
  private var selectedModel: String = AIChat.defaultModel
  private var availableModels: Seq[String] = Seq.empty
  // Metadata per assistant message (model, tokens)
  case class MessageMeta(model: String, promptTokens: Int, completionTokens: Int) derives upickle.default.ReadWriter
  private var messageMeta: mutable.Map[String, MessageMeta] = mutable.Map.empty

  // LocalStorage keys
  private val StorageKeyMessages = "aiChat_messages"
  private val StorageKeySystemPrompt = "aiChat_systemPrompt"
  private val StorageKeyApiKey = "aiChat_apiKey"
  private val StorageKeyModel = "aiChat_model"
  private val StorageKeyMeta = "aiChat_messageMeta"

  // Generate unique message IDs (ScalaJS-compatible)
  private def generateMessageId(): String =
    messageIdCounter += 1
    s"msg-${System.currentTimeMillis()}-$messageIdCounter"

  private def getApiKey(): Option[String] =
    getElementById("apiKeyInput")
      .map(_.asInstanceOf[HTMLInputElement].value.trim)
      .filter(_.nonEmpty)

  def buildUI(): Unit =
    document.body.innerHTML = ""

    document.body(
      NavigationBar.render("AI Chat"),
      div(cls = "container chat-container")(
        // Mobile tab bar
        div(cls = "mobile-tabs")(
          button(cls = "mobile-tab home-tab").tap: btn =>
            btn.innerHTML = """<i class="fa-solid fa-house"></i>"""
            btn.title = "Home"
            btn.addEventListener("click", (e: Event) => dom.window.location.href = "/")
          ,
          button(cls = "mobile-tab active", id = "tabChat").tap: btn =>
            btn.innerHTML = """<i class="fa-solid fa-comment"></i> Chat"""
            btn.addEventListener("click", (e: Event) => switchTab("chat"))
          ,
          button(cls = "mobile-tab", id = "tabSettings").tap: btn =>
            btn.innerHTML = """<i class="fa-solid fa-gear"></i> Settings"""
            btn.addEventListener("click", (e: Event) => switchTab("settings"))
        ),
        // Sidebar for settings (hidden by default on mobile)
        div(id = "chatSidebar", cls = "chat-sidebar mobile-hidden")(
          h3(content = "Settings"),
          // API Key input
          div(cls = "sidebar-section")(
            el("label", cls = "sidebar-label", content = "OpenAI API Key"),
            div(cls = "api-key-input")(
              input("text", id = "apiKeyInput", cls = "input-field api-key-masked").tap: inp =>
                inp.placeholder = "sk-..."
                inp.autocomplete = "off"
                inp.setAttribute("data-1p-ignore", "")
                inp.setAttribute("data-bwignore", "")
                inp.setAttribute("data-lpignore", "true")
                inp.setAttribute("data-form-type", "other")
              ,
              button(cls = "btn btn-sm", content = "Set").tap: btn =>
                btn.addEventListener("click", (e: Event) => setApiKey())
            ),
            span(id = "apiKeyStatus", cls = "status-text")
          ),
          // Model selector
          div(cls = "sidebar-section")(
            el("label", cls = "sidebar-label", content = "Model"),
            el("select", id = "modelSelect", cls = "input-field").tap: sel =>
              val defaultOpt = document.createElement("option").asInstanceOf[HTMLOptionElement]
              defaultOpt.value = AIChat.defaultModel
              defaultOpt.textContent = AIChat.defaultModel
              sel.appendChild(defaultOpt)
              sel.addEventListener("change", (e: Event) =>
                selectedModel = sel.asInstanceOf[HTMLSelectElement].value
                dom.window.localStorage.setItem(StorageKeyModel, selectedModel)
              )
            ,
            span(id = "modelStatus", cls = "status-text")
          ),
          // System prompt
          div(cls = "sidebar-section")(
            el("label", cls = "sidebar-label", content = "System Prompt"),
            el("textarea", id = "systemPrompt", cls = "textarea-field").tap: textarea =>
              textarea.asInstanceOf[HTMLTextAreaElement].placeholder = AIChat.defaultSystemPrompt
              textarea.asInstanceOf[HTMLTextAreaElement].rows = 4
            ,
            div(cls = "system-prompt-actions")(
              button(id = "updateSystemPromptBtn", cls = "btn btn-sm btn-secondary hidden", content = "Update").tap: btn =>
                btn.addEventListener("click", (e: Event) => updateSystemPrompt())
              ,
              span(id = "systemPromptStatus", cls = "status-text")
            )
          ),
          // Actions
          div(cls = "sidebar-section sidebar-actions")(
            button(cls = "btn btn-secondary btn-full", content = "Clear Chat").tap: btn =>
              btn.addEventListener("click", (e: Event) => clearChat())
            ,
            div(cls = "export-import-row")(
              button(cls = "btn btn-secondary btn-half", content = "Export").tap: btn =>
                btn.title = "Export chat to file"
                btn.addEventListener("click", (e: Event) => exportChat())
              ,
              button(cls = "btn btn-secondary btn-half", content = "Import").tap: btn =>
                btn.title = "Import chat from file"
                btn.addEventListener("click", (e: Event) => triggerImport())
            ),
            // Hidden file input for import
            input("file", id = "importFileInput", cls = "hidden").tap: inp =>
              inp.accept = ".json"
              inp.addEventListener("change", (e: Event) => handleImport(e))
          )
        ),
        // Main chat area
        div(id = "chatMain", cls = "chat-main")(
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
                  ta.rows = 2
                  ta.addEventListener("keydown", (e: KeyboardEvent) =>
                    if e.key == "Enter" && !e.shiftKey then
                      e.preventDefault()
                      sendChatMessage()
                  )
                  ta.addEventListener("input", (e: Event) => autoResizeTextarea(ta))
                ,
                // Buttons stacked vertically
                div(cls = "input-buttons")(
                  // Image upload button
                  button(cls = "btn btn-icon").tap: btn =>
                    btn.innerHTML = """<i class="fa-solid fa-camera"></i>"""
                    btn.title = "Add image"
                    btn.addEventListener("click", (e: Event) => triggerImageUpload())
                  ,
                  // Send button (hidden during streaming)
                  button(id = "sendBtn", cls = "btn btn-primary btn-icon").tap: btn =>
                    btn.innerHTML = """<i class="fa-solid fa-paper-plane"></i>"""
                    btn.title = "Send message"
                    btn.addEventListener("click", (e: Event) => sendChatMessage())
                  ,
                  // Stop button (hidden by default, shown during streaming)
                  button(id = "stopBtn", cls = "btn btn-danger btn-icon hidden").tap: btn =>
                    btn.innerHTML = """<i class="fa-solid fa-stop"></i>"""
                    btn.title = "Stop generating"
                    btn.addEventListener("click", (e: Event) => stopStreaming())
                )
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
      loadFromLocalStorage()

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

      case ServerMessage.MessageAdded(message) =>
        hideEmptyState()
        chatMessages += message
        if message.role == MessageRole.Assistant && message.content.isEmpty then
          // Start streaming - add placeholder
          streamingContent(message.id) = ""
          activeStreamingId = Some(message.id)
          showStopButton()
          addMessageToUI(message, isStreaming = true)
        else
          addMessageToUI(message)
        scrollToBottom()
        saveToLocalStorage()

      case ServerMessage.MessageUpdated(message) =>
        val idx = chatMessages.indexWhere(_.id == message.id)
        if idx >= 0 then
          chatMessages(idx) = message
          updateMessageInUI(message)
          saveToLocalStorage()

      case ServerMessage.MessageDeleted(messageId) =>
        chatMessages = chatMessages.filterNot(_.id == messageId)
        removeMessageFromUI(messageId)
        saveToLocalStorage()

      case ServerMessage.StreamingChunk(messageId, chunk) =>
        streamingContent.get(messageId) match
          case Some(existing) =>
            val newContent = existing + chunk
            streamingContent(messageId) = newContent
            updateStreamingMessage(messageId, newContent)
          case None =>
            streamingContent(messageId) = chunk
            updateStreamingMessage(messageId, chunk)

      case ServerMessage.StreamingComplete(messageId, model, promptTokens, completionTokens) =>
        streamingContent.get(messageId).foreach: finalContent =>
          val idx = chatMessages.indexWhere(_.id == messageId)
          if idx >= 0 then
            chatMessages(idx) = chatMessages(idx).copy(content = finalContent)
            finalizeStreamingMessage(messageId, finalContent)
        // Store metadata and render badges
        val meta = MessageMeta(model, promptTokens, completionTokens)
        messageMeta(messageId) = meta
        updateMessageMetaBadges(messageId, meta)
        streamingContent.remove(messageId)
        activeStreamingId = None
        hideStopButton()
        saveToLocalStorage()

      case ServerMessage.ChatCleared() =>
        chatMessages.clear()
        messageMeta.clear()
        clearMessagesUI()
        showEmptyState()
        resetSystemPromptStatus()
        // Clear messages from localStorage but keep settings
        dom.window.localStorage.removeItem(StorageKeyMessages)
        dom.window.localStorage.removeItem(StorageKeyMeta)

      case ServerMessage.ErrorMessage(message) =>
        println(s"[AIChat] Error: $message")
        showError(message)

      case ServerMessage.ModelsListed(models) =>
        availableModels = models
        populateModelSelector(models)

  private def setApiKey(): Unit =
    getApiKey().foreach: apiKey =>
      dom.window.localStorage.setItem(StorageKeyApiKey, apiKey)
      getElementById("apiKeyStatus").foreach: elem =>
        elem.textContent = "✓ API key saved"
        elem.className = "status-text status-success"
      // Fetch models to validate the key and populate the selector
      sendClientMessage(ClientMessage.ListModels(apiKey))

  private def sendChatMessage(): Unit =
    if getApiKey().isEmpty then
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
    val isFirstMessage = chatMessages.isEmpty
    if isFirstMessage then
      val systemMsg = ChatMessage(
        id = generateMessageId(),
        role = MessageRole.System,
        content = systemPrompt
      )
      chatMessages += systemMsg
      // Show feedback that system prompt was applied
      val isCustom = getElementById("systemPrompt")
        .map(_.asInstanceOf[HTMLTextAreaElement].value.trim)
        .exists(_.nonEmpty)
      updateSystemPromptStatus(isCustom)
      // Show the update button now that conversation has started
      getElementById("updateSystemPromptBtn").foreach(_.classList.remove("hidden"))

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

  // Send full conversation context with selected model for AI response
  private def sendConversationContext(messages: Seq[ChatMessage], regenerate: Boolean = false): Unit =
    chatWebSocket.foreach: ws =>
      getApiKey().foreach: apiKey =>
        val contextMsg = ujson.Obj(
          "$type" -> "GenerateWithContext",
          "messages" -> upickle.default.writeJs(messages),
          "model" -> selectedModel,
          "apiKey" -> apiKey,
          "regenerate" -> regenerate
        )
        ws.send(ujson.write(contextMsg))

  private def stopStreaming(): Unit =
    activeStreamingId.foreach: messageId =>
      sendClientMessage(ClientMessage.StopStreaming(messageId))

  private def showStopButton(): Unit =
    getElementById("sendBtn").foreach(_.classList.add("hidden"))
    getElementById("stopBtn").foreach(_.classList.remove("hidden"))

  private def hideStopButton(): Unit =
    getElementById("stopBtn").foreach(_.classList.add("hidden"))
    getElementById("sendBtn").foreach(_.classList.remove("hidden"))

  private def clearChat(): Unit =
    sendClientMessage(ClientMessage.ClearChat())

  private def updateSystemPrompt(): Unit =
    val newPrompt = getElementById("systemPrompt")
      .map(_.asInstanceOf[HTMLTextAreaElement].value.trim)
      .filter(_.nonEmpty)
      .getOrElse(AIChat.defaultSystemPrompt)

    // Find and update the system message in our conversation
    chatMessages.indexWhere(_.role == MessageRole.System) match
      case idx if idx >= 0 =>
        val oldSystemMsg = chatMessages(idx)
        val updatedSystemMsg = oldSystemMsg.copy(content = newPrompt)
        chatMessages(idx) = updatedSystemMsg
        // Update status to show the change was applied
        val isCustom = getElementById("systemPrompt")
          .map(_.asInstanceOf[HTMLTextAreaElement].value.trim)
          .exists(_.nonEmpty)
        updateSystemPromptStatus(isCustom, updated = true)
      case _ =>
        // No system message yet - add one
        val systemMsg = ChatMessage(
          id = generateMessageId(),
          role = MessageRole.System,
          content = newPrompt
        )
        chatMessages.prepend(systemMsg)
        val isCustom = getElementById("systemPrompt")
          .map(_.asInstanceOf[HTMLTextAreaElement].value.trim)
          .exists(_.nonEmpty)
        updateSystemPromptStatus(isCustom, updated = true)

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
        div(cls = "message-header-left")(
          span(cls = "message-role", content = roleName),
          // Meta badges for assistant messages (model + tokens)
          if message.role == MessageRole.Assistant then
            span(id = s"meta-${message.id}", cls = "message-meta").tap: metaEl =>
              // Render badges from stored metadata if available
              messageMeta.get(message.id).foreach: meta =>
                renderMetaBadges(metaEl, meta)
          else
            span(cls = "hidden")
        ),
        div(cls = "message-actions")(
          button(cls = "action-btn").tap: btn =>
            btn.innerHTML = """<i class="fa-solid fa-pen"></i>"""
            btn.title = "Edit"
            btn.addEventListener("click", (e: Event) => startEditMessage(message.id))
          ,
          button(cls = "action-btn").tap: btn =>
            btn.innerHTML = """<i class="fa-solid fa-trash"></i>"""
            btn.title = "Delete"
            btn.addEventListener("click", (e: Event) => deleteMessage(message.id))
          ,
          // Regenerate button only for assistant messages
          if message.role == MessageRole.Assistant then
            button(cls = "action-btn").tap: btn =>
              btn.innerHTML = """<i class="fa-solid fa-rotate"></i>"""
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
        val contentDiv = div(id = s"content-${message.id}", cls = s"message-content${if isStreaming then " streaming" else ""}")()
        if isStreaming then
          contentDiv.innerHTML = """<span class="cursor">▌</span>"""
        else
          contentDiv.innerHTML = renderMarkdown(message.content)
          Markdown.highlightCodeBlocks(contentDiv)
        contentDiv
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

  private def renderMarkdown(content: String): String =
    Markdown.render(content)

  private def updateMessageInUI(message: ChatMessage): Unit =
    getElementById(s"content-${message.id}").foreach: elem =>
      elem.innerHTML = renderMarkdown(message.content)
      Markdown.highlightCodeBlocks(elem)

  private def removeMessageFromUI(messageId: String): Unit =
    getElementById(s"message-$messageId").foreach(_.remove())

  private def updateStreamingMessage(messageId: String, content: String): Unit =
    getElementById(s"content-$messageId").foreach: elem =>
      elem.innerHTML = renderMarkdown(content) + """<span class="cursor">▌</span>"""
    scrollToBottom()

  private def finalizeStreamingMessage(messageId: String, content: String): Unit =
    getElementById(s"content-$messageId").foreach: elem =>
      elem.classList.remove("streaming")
      elem.innerHTML = renderMarkdown(content)
      Markdown.highlightCodeBlocks(elem)

  private def renderMetaBadges(container: HTMLElement, meta: MessageMeta): Unit =
    container.innerHTML = ""
    if meta.model.nonEmpty then
      container.appendChild(span(cls = "meta-badge meta-badge-model", content = meta.model))
    val totalTokens = meta.promptTokens + meta.completionTokens
    if totalTokens > 0 then
      container.appendChild(span(cls = "meta-badge meta-badge-tokens", content = s"${formatTokenCount(totalTokens)} tokens"))

  private def updateMessageMetaBadges(messageId: String, meta: MessageMeta): Unit =
    getElementById(s"meta-$messageId").foreach: elem =>
      renderMetaBadges(elem, meta)

  private def formatTokenCount(n: Int): String =
    if n >= 1000 then f"${n / 1000.0}%.1fk"
    else n.toString

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
        sendConversationContext(conversationSoFar, regenerate = true)

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

  private def switchTab(tab: String): Unit =
    val chatMain = getElementById("chatMain")
    val chatSidebar = getElementById("chatSidebar")
    val tabChat = getElementById("tabChat")
    val tabSettings = getElementById("tabSettings")
    
    tab match
      case "chat" =>
        chatMain.foreach(_.classList.remove("mobile-hidden"))
        chatSidebar.foreach(_.classList.add("mobile-hidden"))
        tabChat.foreach(_.classList.add("active"))
        tabSettings.foreach(_.classList.remove("active"))
      case "settings" =>
        chatMain.foreach(_.classList.add("mobile-hidden"))
        chatSidebar.foreach(_.classList.remove("mobile-hidden"))
        tabChat.foreach(_.classList.remove("active"))
        tabSettings.foreach(_.classList.add("active"))
      case _ => ()

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

  private def populateModelSelector(models: Seq[String]): Unit =
    getElementById("modelSelect").foreach: elem =>
      val select = elem.asInstanceOf[HTMLSelectElement]
      select.innerHTML = ""

      // Restore saved model from localStorage
      val savedModel = Option(dom.window.localStorage.getItem(StorageKeyModel))
        .filter(_.nonEmpty)
        .filter(models.contains)

      models.foreach: model =>
        val opt = document.createElement("option").asInstanceOf[HTMLOptionElement]
        opt.value = model
        opt.textContent = model
        select.appendChild(opt)

      // Set selection: saved model > default model > first option
      val modelToSelect = savedModel
        .orElse(Some(AIChat.defaultModel).filter(models.contains))
        .orElse(models.headOption)

      modelToSelect.foreach: model =>
        select.value = model
        selectedModel = model

    getElementById("modelStatus").foreach: elem =>
      elem.textContent = s"${models.size} models available"
      elem.className = "status-text status-info"

  private def updateSystemPromptStatus(isCustom: Boolean, updated: Boolean = false): Unit =
    getElementById("systemPromptStatus").foreach: elem =>
      if updated then
        elem.textContent = "✓ System prompt updated"
        elem.className = "status-text status-success"
      else if isCustom then
        elem.textContent = "✓ Custom prompt active"
        elem.className = "status-text status-success"
      else
        elem.textContent = "✓ Default prompt active"
        elem.className = "status-text status-info"

  private def resetSystemPromptStatus(): Unit =
    getElementById("systemPromptStatus").foreach: elem =>
      elem.textContent = ""
      elem.className = "status-text"
    getElementById("updateSystemPromptBtn").foreach(_.classList.add("hidden"))

  // === LocalStorage Persistence ===

  private def saveToLocalStorage(): Unit =
    Try:
      // Save messages
      val messagesJson = upickle.default.write(chatMessages.toSeq)
      dom.window.localStorage.setItem(StorageKeyMessages, messagesJson)

      // Save system prompt
      getElementById("systemPrompt").foreach: elem =>
        val prompt = elem.asInstanceOf[HTMLTextAreaElement].value
        dom.window.localStorage.setItem(StorageKeySystemPrompt, prompt)

      // Save API key (if set)
      getElementById("apiKeyInput").foreach: elem =>
        val key = elem.asInstanceOf[HTMLInputElement].value
        if key.nonEmpty then
          dom.window.localStorage.setItem(StorageKeyApiKey, key)

      // Save selected model
      dom.window.localStorage.setItem(StorageKeyModel, selectedModel)

      // Save message metadata
      val metaJson = upickle.default.write(messageMeta.toMap)
      dom.window.localStorage.setItem(StorageKeyMeta, metaJson)
    .recover:
      case ex => println(s"[AIChat] Failed to save to localStorage: ${ex.getMessage}")

  private def loadFromLocalStorage(): Unit =
    Try:
      // Load saved model
      Option(dom.window.localStorage.getItem(StorageKeyModel)).filter(_.nonEmpty).foreach: model =>
        selectedModel = model

      // Load API key first
      Option(dom.window.localStorage.getItem(StorageKeyApiKey)).filter(_.nonEmpty).foreach: apiKey =>
        getElementById("apiKeyInput").foreach: elem =>
          elem.asInstanceOf[HTMLInputElement].value = apiKey
        getElementById("apiKeyStatus").foreach: elem =>
          elem.textContent = "✓ API key saved"
          elem.className = "status-text status-success"
        // Fetch models to populate the selector
        sendClientMessage(ClientMessage.ListModels(apiKey))

      // Load system prompt
      Option(dom.window.localStorage.getItem(StorageKeySystemPrompt)).filter(_.nonEmpty).foreach: prompt =>
        getElementById("systemPrompt").foreach: elem =>
          elem.asInstanceOf[HTMLTextAreaElement].value = prompt

      // Load messages
      Option(dom.window.localStorage.getItem(StorageKeyMessages)).filter(_.nonEmpty).foreach: messagesJson =>
        val messages = upickle.default.read[Seq[ChatMessage]](messagesJson)
        if messages.nonEmpty then
          chatMessages.clear()
          chatMessages ++= messages

          // Load message metadata
          Option(dom.window.localStorage.getItem(StorageKeyMeta)).filter(_.nonEmpty).foreach: metaJson =>
            messageMeta = mutable.Map.from(upickle.default.read[Map[String, MessageMeta]](metaJson))

          restoreMessagesUI()
    .recover:
      case ex => println(s"[AIChat] Failed to load from localStorage: ${ex.getMessage}")

  private def restoreMessagesUI(): Unit =
    clearMessagesUI()
    if chatMessages.nonEmpty then
      hideEmptyState()
      // Only show non-system messages in UI
      chatMessages.filter(_.role != MessageRole.System).foreach: msg =>
        addMessageToUI(msg)
      // Update system prompt status if we have a system message
      chatMessages.find(_.role == MessageRole.System).foreach: sysMsg =>
        val isCustom = sysMsg.content != AIChat.defaultSystemPrompt
        updateSystemPromptStatus(isCustom)
        getElementById("updateSystemPromptBtn").foreach(_.classList.remove("hidden"))
      scrollToBottom()
    else
      showEmptyState()

  // === Export/Import ===

  private case class ChatExport(
    version: Int,
    exportedAt: String,
    systemPrompt: String,
    messages: Seq[ChatMessage]
  ) derives upickle.default.ReadWriter

  private def exportChat(): Unit =
    val systemPrompt = getElementById("systemPrompt")
      .map(_.asInstanceOf[HTMLTextAreaElement].value)
      .getOrElse("")

    val chatExport = ChatExport(
      version = 1,
      exportedAt = new scala.scalajs.js.Date().toISOString(),
      systemPrompt = systemPrompt,
      messages = chatMessages.toSeq
    )

    val json = upickle.default.write(chatExport, indent = 2)
    val blob = new dom.Blob(
      js.Array(json),
      new dom.BlobPropertyBag { `type` = "application/json" }
    )

    val url = dom.URL.createObjectURL(blob)
    val link = document.createElement("a").asInstanceOf[HTMLAnchorElement]
    link.href = url
    link.setAttribute("download", s"ai-chat-export-${new scala.scalajs.js.Date().toISOString().take(10)}.json")
    link.click()
    dom.URL.revokeObjectURL(url)

  private def triggerImport(): Unit =
    getElementById("importFileInput").foreach: elem =>
      elem.asInstanceOf[HTMLInputElement].click()

  private def handleImport(e: Event): Unit =
    val input = e.target.asInstanceOf[HTMLInputElement]
    val files = input.files

    if files.length > 0 then
      val file = files(0)
      val reader = new FileReader()
      reader.onload = (e: Event) =>
        Try:
          val json = reader.result.asInstanceOf[String]
          val chatExport = upickle.default.read[ChatExport](json)

          // Confirm import
          if dom.window.confirm(s"Import chat from ${chatExport.exportedAt.take(10)}? This will replace current chat.") then
            // Clear current chat
            chatMessages.clear()
            clearMessagesUI()

            // Restore system prompt
            getElementById("systemPrompt").foreach: elem =>
              elem.asInstanceOf[HTMLTextAreaElement].value = chatExport.systemPrompt

            // Restore messages
            chatMessages ++= chatExport.messages
            restoreMessagesUI()

            // Save to localStorage
            saveToLocalStorage()

            showSuccess("Chat imported successfully!")
        .recover:
          case ex =>
            println(s"[AIChat] Import error: ${ex.getMessage}")
            showError(s"Failed to import: ${ex.getMessage}")

      reader.readAsText(file)

    // Reset input
    input.value = ""

  private def showSuccess(message: String): Unit =
    val toast: HTMLElement = div(cls = "success-toast")(
      span(content = message),
      button(cls = "toast-close", content = "×")
    )

    Option(toast.querySelector(".toast-close")).foreach: btn =>
      btn.addEventListener("click", (e: Event) => toast.remove())

    document.body.appendChild(toast)
    dom.window.setTimeout(() => toast.remove(), 3000)

