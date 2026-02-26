package client

import org.scalajs.dom.{HTMLAnchorElement, HTMLButtonElement, HTMLElement, HTMLFormElement, HTMLInputElement, HTMLLabelElement, HTMLTextAreaElement, document}

import scala.scalajs.js
import scala.util.chaining.*

def el(tag: String): HTMLElement =
  document.createElement(tag).asInstanceOf[HTMLElement]

def div: HTMLElement = el("div")
def span: HTMLElement = el("span")
def p: HTMLElement = el("p")
def h1: HTMLElement = el("h1")
def h2: HTMLElement = el("h2")
def h3: HTMLElement = el("h3")
def h4: HTMLElement = el("h4")

def form: HTMLFormElement =
  el("form").asInstanceOf[HTMLFormElement]

def input(input_type: String): HTMLInputElement =
  el("input").asInstanceOf[HTMLInputElement].tap: input =>
    input.`type` = input_type

/** Creates a floating label input field */
def floatingInput(input_type: String, elementId: String, labelText: String): HTMLElement =
  div.cls("floating-field")(
    input(input_type).idx(elementId).cls("floating-input").tap: inp =>
      inp.placeholder = " " // Required for :placeholder-shown CSS selector
    ,
    el("label").cls("floating-label").content(labelText).tap: lbl =>
      lbl.setAttribute("for", elementId)
  )

def button: HTMLButtonElement =
  el("button").asInstanceOf[HTMLButtonElement].tap: btn =>
    btn.`type` = "button"

def submitButton: HTMLButtonElement =
  el("button").asInstanceOf[HTMLButtonElement].tap: btn =>
    btn.`type` = "submit"

def a: HTMLAnchorElement =
  document.createElement("a").asInstanceOf[HTMLAnchorElement].tap: anchor =>
    anchor.href = "#"

def textInput: HTMLInputElement =
  input("text")

def passwordInput: HTMLInputElement =
  input("password")

def textarea: HTMLTextAreaElement =
  el("textarea").asInstanceOf[HTMLTextAreaElement]

def label(forId: String, labelContent: String): HTMLLabelElement =
  el("label").asInstanceOf[HTMLLabelElement].tap: lbl =>
    lbl.setAttribute("for", forId)
    lbl.textContent = labelContent

extension [T <: HTMLElement](elem: T)
  def idx(value: String): T =
    elem.id = value
    elem

  def cls(value: String): T =
    elem.className = value
    elem

  def content(value: String): T =
    elem.textContent = value
    elem

  def apply(children: HTMLElement*): T =
    children.foreach(elem.appendChild(_))
    elem

  def with_click(handler: js.Function1[org.scalajs.dom.MouseEvent, Unit]): T =
    elem.addEventListener("click", handler)
    elem

extension (elem: HTMLAnchorElement)
  def hrefx(value: String): HTMLAnchorElement =
    elem.href = value
    elem

extension (elem: HTMLButtonElement)
  def buttonType(value: String): HTMLButtonElement =
    elem.`type` = value
    elem

// Helper functions for DOM manipulation
def getElementById(id: String): Option[HTMLElement] =
  Option(document.getElementById(id)).map(_.asInstanceOf[HTMLElement])

def getElementByIdAs[T <: HTMLElement](id: String): Option[T] =
  Option(document.getElementById(id)).map(_.asInstanceOf[T])

def getInputValue(id: String): Option[String] =
  getElementByIdAs[HTMLInputElement](id).map(_.value)
