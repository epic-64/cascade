package client

import org.scalajs.dom
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

def div(id: String = "", cls: String = "", content: String = ""): HTMLElement  = el("div", id, cls, content)
def span(id: String = "", cls: String = "", content: String = ""): HTMLElement = el("span", id, cls, content)
def p(id: String = "", cls: String = "", content: String = ""): HTMLElement    = el("p", id, cls, content)
def h1(id: String = "", cls: String = "", content: String = ""): HTMLElement   = el("h1", id, cls, content)
def h2(id: String = "", cls: String = "", content: String = ""): HTMLElement   = el("h2", id, cls, content)
def h3(id: String = "", cls: String = "", content: String = ""): HTMLElement   = el("h3", id, cls, content)

def form(id: String = "", cls: String = ""): HTMLFormElement =
  document.createElement("form").asInstanceOf[HTMLFormElement].tap: form =>
    if cls.nonEmpty then form.className = cls
    if id.nonEmpty then form.id = id

def input(input_type: String, id: String = "", cls: String = ""): HTMLInputElement =
  document.createElement("input").asInstanceOf[HTMLInputElement].tap: input =>
    input.`type` = input_type
    if cls.nonEmpty then input.className = cls
    if id.nonEmpty then input.id = id

def button(button_type: String = "button", id: String = "", cls: String = ""): HTMLButtonElement =
  document.createElement("button").asInstanceOf[HTMLButtonElement].tap: button =>
    button.`type` = button_type
    if id.nonEmpty then button.id = id
    if cls.nonEmpty then button.className = cls

def a(href: String = "#", id: String = "", cls: String = ""): HTMLAnchorElement =
  document.createElement("a").asInstanceOf[HTMLAnchorElement].tap: anchor =>
    anchor.href = href
    if cls.nonEmpty then anchor.className = cls
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

// Helper functions for DOM manipulation
def getElementById(id: String): Option[HTMLElement] =
  Option(document.getElementById(id).asInstanceOf[HTMLElement])

def getElement(id: String): Option[dom.Element] =
  Option(document.getElementById(id))

def getInputElement(id: String): Option[HTMLInputElement] =
  Option(document.getElementById(id).asInstanceOf[HTMLInputElement])

def getInputValue(id: String): Option[String] =
  getInputElement(id).map(_.value)
