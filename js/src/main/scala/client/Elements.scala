package client

import org.scalajs.dom
import org.scalajs.dom.{HTMLAnchorElement, HTMLButtonElement, HTMLElement, HTMLFormElement, HTMLInputElement, document}

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

def form(id: String = "", cls: String = ""): HTMLFormElement =
  el("form", id, cls).asInstanceOf[HTMLFormElement]

def input(input_type: String, id: String = "", cls: String = ""): HTMLInputElement =
  el("input", id, cls).asInstanceOf[HTMLInputElement].tap: input =>
    input.`type` = input_type

def button(button_type: String = "button", id: String = "", cls: String = "", content: String = ""): HTMLButtonElement =
  el("button", id, cls, content).asInstanceOf[HTMLButtonElement].tap: button =>
    button.`type` = button_type

def a(href: String = "#", id: String = "", cls: String = ""): HTMLAnchorElement =
  document.createElement("a").asInstanceOf[HTMLAnchorElement].tap: anchor =>
    anchor.href = href
    if cls.nonEmpty then anchor.className = cls
    if id.nonEmpty then anchor.id = id

extension [T <: HTMLElement](elem: T)
  def apply(children: HTMLElement*): T =
    children.foreach(elem.appendChild(_))
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
