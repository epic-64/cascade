package client.components

import org.scalajs.dom
import org.scalajs.dom.*
import client.*

import scala.scalajs.js
import scala.util.{Try, Success, Failure}
import scala.util.chaining.*
import scala.concurrent.ExecutionContext.Implicits.global

object ShareableLink:
  /** Render a shareable link component with lobby code and copy button */
  def render(gameType: String, lobbyId: String): HTMLElement =
    val fullUrl = s"${dom.window.location.origin}/$gameType/$lobbyId"

    div(cls = "shareable-link")(
      span(cls = "lobby-code", content = s"Code: $lobbyId"),
      div(cls = "link-actions")(
        input("text", cls = "share-link-input").tap: el =>
          el.value = fullUrl
          el.readOnly = true
          el.addEventListener("click", (e: Event) => el.select())
        ,
        button(cls = "btn btn-secondary copy-btn", content = "📋 Copy").tap: btn =>
          btn.addEventListener("click", (e: Event) => copyToClipboard(fullUrl, btn))
      )
    )

  private def copyToClipboard(text: String, button: HTMLElement): Unit =
    Try(dom.window.navigator.clipboard.writeText(text).toFuture) match
      case scala.util.Success(future) =>
        future.onComplete:
          case Success(_) =>
            val originalText = button.textContent
            button.textContent = "✓ Copied!"
            dom.window.setTimeout(() => button.textContent = originalText, 2000)
          case Failure(_) =>
            button.textContent = "Select & copy"
            dom.window.setTimeout(() => button.textContent = "📋 Copy", 2000)
      case scala.util.Failure(_) =>
        // Clipboard API not available - show fallback message
        button.textContent = "Select & copy"
        dom.window.setTimeout(() => button.textContent = "📋 Copy", 2000)

