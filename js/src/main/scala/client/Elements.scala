package client

import org.scalajs.dom.{CSSStyleDeclaration, HTMLElement, document}

import scala.scalajs.js
import scala.util.chaining.*

def el(
    tag: String,
    block: HTMLElement => Unit = _ => ()
): HTMLElement =
  document
    .createElement(tag)
    .asInstanceOf[HTMLElement]
    .tap: elem =>
      block(elem)

extension (elem: HTMLElement)
  def tap_style(block: CSSStyleDeclaration => Unit): HTMLElement =
    block(elem.style)
    elem

  def append_to(parent: HTMLElement): HTMLElement =
    parent.appendChild(elem)
    elem

  def with_id(id: String): HTMLElement =
    elem.id = id
    elem

  def with_classes(classes: String): HTMLElement =
    elem.className = classes
    elem

  def with_content(content: String): HTMLElement =
    elem.textContent = content
    elem

  def with_listener(event: String, handler: js.Function1[org.scalajs.dom.Event, Unit]): HTMLElement =
    elem.addEventListener(event, handler)
    elem

  def with_click(handler: js.Function1[org.scalajs.dom.MouseEvent, Unit]): HTMLElement =
    elem.addEventListener("click", handler)
    elem