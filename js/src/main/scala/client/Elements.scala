package client

import org.scalajs.dom.{HTMLElement, document}

def el(
    tag: String,
    classes: String = "",
    content: String = "",
    block: Option[HTMLElement => Unit] = None
): HTMLElement =
  val element = document.createElement(tag).asInstanceOf[HTMLElement]
  if classes.nonEmpty then element.className = classes
  if content.nonEmpty then element.textContent = content
  block.foreach(f => f(element))
  element
