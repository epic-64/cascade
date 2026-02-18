package client

import scala.scalajs.js
import scala.util.Try

// Facade for marked.js + DOMPurify loaded via CDN
object Markdown:

  private def marked: js.Dynamic = js.Dynamic.global.marked
  private def DOMPurify: js.Dynamic = js.Dynamic.global.DOMPurify

  /** Parse markdown to sanitized HTML. Falls back to escaped plain text on error. */
  def render(markdown: String): String =
    Try:
      val raw = marked.parse(markdown).asInstanceOf[String]
      DOMPurify.sanitize(raw).asInstanceOf[String]
    .getOrElse:
      escapeHtml(markdown)

  /** Configure marked for AI chat: enable GFM, breaks, and no mangle/headerIds. */
  def configure(): Unit =
    Try:
      marked.setOptions(js.Dynamic.literal(
        gfm = true,
        breaks = true
      ))

  private def escapeHtml(text: String): String =
    text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")


