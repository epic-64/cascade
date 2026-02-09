package client

import org.scalajs.dom.{
  CSSStyleDeclaration,
  HTMLAnchorElement,
  HTMLButtonElement,
  HTMLElement,
  HTMLFormElement,
  HTMLInputElement,
  document
}

import scala.scalajs.js
import scala.util.chaining.*

def el(tag: String, id: String = "", cls: String = "", content: String = ""): HTMLElement =
  document.createElement(tag).asInstanceOf[HTMLElement].tap: element =>
    if id.nonEmpty then element.id = id
    if cls.nonEmpty then element.className = cls
    if content.nonEmpty then element.textContent = content

def form(id: String = ""): HTMLFormElement =
  document.createElement("form").asInstanceOf[HTMLFormElement].tap: form =>
    if id.nonEmpty then form.id = id

def input(input_type: String, id: String = ""): HTMLInputElement =
  document.createElement("input").asInstanceOf[HTMLInputElement].tap: input =>
    input.`type` = input_type
    if id.nonEmpty then input.id = id

def button(button_type: String = "button", id: String = "", cls: String = ""): HTMLButtonElement =
  document.createElement("button").asInstanceOf[HTMLButtonElement].tap: button =>
    button.`type` = button_type
    if id.nonEmpty then button.id = id
    if cls.nonEmpty then button.className = cls

def a(href: String = "#", id: String = ""): HTMLAnchorElement =
  document.createElement("a").asInstanceOf[HTMLAnchorElement].tap: anchor =>
      anchor.href = href
      if id.nonEmpty then anchor.id = id

extension [T <: HTMLElement](elem: T)
  def apply(children: HTMLElement*): T =
    children.foreach(elem.appendChild(_))
    elem

  def tap_style(block: CSSStyleDeclaration => Unit): T =
    block(elem.style)
    elem

  def append_to(parent: HTMLElement): T =
    parent.appendChild(elem)
    elem

  def with_id(id: String): T =
    elem.id = id
    elem

  def with_classes(classes: String): T =
    elem.className = classes
    elem

  def with_content(content: String): T =
    elem.textContent = content
    elem

  def with_listener(event: String, handler: js.Function1[org.scalajs.dom.Event, Unit]): T =
    elem.addEventListener(event, handler)
    elem

  def with_click(handler: js.Function1[org.scalajs.dom.MouseEvent, Unit]): T =
    elem.addEventListener("click", handler)
    elem
