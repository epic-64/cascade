package client

import org.scalajs.dom.{HTMLAnchorElement, HTMLButtonElement, HTMLElement, HTMLFormElement, HTMLInputElement, HTMLLabelElement, HTMLTextAreaElement, document}

import scala.scalajs.js
import scala.util.chaining.*

def el(tag: String, id: String = "", cls: String = "", content: String = ""): HTMLElement =
  document.createElement(tag).asInstanceOf[HTMLElement].tap: element =>
    if id.nonEmpty then element.id = id
    if cls.nonEmpty then element.className = cls
    if content.nonEmpty then element.textContent = content

def div(id: String = "", cls: String = "", content: String = ""): HTMLElement = el("div", id, cls, content)
def span(id: String = "", cls: String = "", content: String = ""): HTMLElement = el("span", id, cls, content)
def p(id: String = "", cls: String = "", content: String = ""): HTMLElement = el("p", id, cls, content)
def h1(id: String = "", cls: String = "", content: String = ""): HTMLElement = el("h1", id, cls, content)
def h2(id: String = "", cls: String = "", content: String = ""): HTMLElement = el("h2", id, cls, content)
def h3(id: String = "", cls: String = "", content: String = ""): HTMLElement = el("h3", id, cls, content)
def h4(id: String = "", cls: String = "", content: String = ""): HTMLElement = el("h4", id, cls, content)

def form(id: String = "", cls: String = ""): HTMLFormElement =
  el("form", id, cls).asInstanceOf[HTMLFormElement]

def input(input_type: String, id: String = "", cls: String = ""): HTMLInputElement =
  el("input", id, cls).asInstanceOf[HTMLInputElement].tap: input =>
    input.`type` = input_type

/** Creates a floating label input field */
def floatingInput(input_type: String, id: String, label: String): HTMLElement =
  div(cls = "floating-field")(
    input(input_type, id = id, cls = "floating-input").tap: inp =>
      inp.placeholder = " " // Required for :placeholder-shown CSS selector
    ,
    el("label", cls = "floating-label", content = label).tap: lbl =>
      lbl.setAttribute("for", id)
  )

def button(button_type: String = "button", id: String = "", cls: String = "", content: String = ""): HTMLButtonElement =
  el("button", id, cls, content).asInstanceOf[HTMLButtonElement].tap: button =>
    button.`type` = button_type

def a(href: String = "#", id: String = "", cls: String = ""): HTMLAnchorElement =
  document.createElement("a").asInstanceOf[HTMLAnchorElement].tap: anchor =>
    anchor.href = href
    if cls.nonEmpty then anchor.className = cls
    if id.nonEmpty then anchor.id = id

def textInput(id: String = "", cls: String = ""): HTMLInputElement =
  input("text", id, cls)

def passwordInput(id: String = "", cls: String = ""): HTMLInputElement =
  input("password", id, cls)

def textarea(id: String = "", cls: String = ""): HTMLTextAreaElement =
  el("textarea", id, cls).asInstanceOf[HTMLTextAreaElement]

def label(forId: String, content: String, id: String = "", cls: String = ""): HTMLLabelElement =
  el("label", id, cls, content).asInstanceOf[HTMLLabelElement].tap: lbl =>
    lbl.setAttribute("for", forId)

extension [T <: HTMLElement](elem: T)
  def apply(children: HTMLElement*): T =
    children.foreach(elem.appendChild(_))
    elem

  def with_click(handler: js.Function1[org.scalajs.dom.MouseEvent, Unit]): T =
    elem.addEventListener("click", handler)
    elem

// Helper functions for DOM manipulation
def getElementById(id: String): Option[HTMLElement] =
  Option(document.getElementById(id)).map(_.asInstanceOf[HTMLElement])

def getElementByIdAs[T <: HTMLElement](id: String): Option[T] =
  Option(document.getElementById(id)).map(_.asInstanceOf[T])

def getInputValue(id: String): Option[String] =
  getElementByIdAs[HTMLInputElement](id).map(_.value)
