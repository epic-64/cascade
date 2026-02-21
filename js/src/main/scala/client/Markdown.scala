package client

import scala.scalajs.js
import scala.util.Try
import org.scalajs.dom

// Facade for marked.js + DOMPurify + highlight.js loaded via CDN
object Markdown:

  private def marked: js.Dynamic = js.Dynamic.global.marked
  private def DOMPurify: js.Dynamic = js.Dynamic.global.DOMPurify
  private def hljs: js.Dynamic = js.Dynamic.global.hljs

  /** Parse markdown to sanitized HTML. Falls back to escaped plain text on error. */
  def render(markdown: String): String =
    Try:
      val raw = marked.parse(markdown).asInstanceOf[String]
      DOMPurify.sanitize(raw).asInstanceOf[String]
    .getOrElse:
      escapeHtml(markdown)

  /** Highlight all code blocks inside a container element. Call after setting innerHTML. */
  def highlightCodeBlocks(container: dom.Element): Unit =
    Try:
      container.querySelectorAll("pre code").foreach: node =>
        hljs.highlightElement(node)

  /** Configure marked for AI chat and highlight.js. */
  def configure(): Unit =
    Try:
      marked.setOptions(js.Dynamic.literal(
        gfm = true,
        breaks = true
      ))
    Try:
      hljs.configure(js.Dynamic.literal(
        ignoreUnescapedHTML = true
      ))

  private def escapeHtml(text: String): String =
    text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")


