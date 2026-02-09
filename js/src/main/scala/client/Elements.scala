package client

import org.scalajs.dom.{
  CSSStyleDeclaration,
  HTMLButtonElement,
  HTMLElement,
  HTMLFormElement,
  HTMLInputElement,
  document
}

import scala.scalajs.js
import scala.util.chaining.*

def el(tag: String, block: HTMLElement => Unit = _ => ()): HTMLElement =
  document.createElement(tag).asInstanceOf[HTMLElement].tap(block(_))

def form(id: Option[String] = None, block: HTMLFormElement => Unit = _ => ()): HTMLFormElement =
  document
    .createElement("form")
    .asInstanceOf[HTMLFormElement]
    .tap(form => id.foreach(form.id = _))
    .tap(block(_))

def input(input_type: String, id: Option[String] = None, block: HTMLInputElement => Unit = _ => ()): HTMLInputElement =
  document
    .createElement("input")
    .asInstanceOf[HTMLInputElement]
    .tap: input =>
      id.foreach(input.id = _)
      input.`type` = input_type
      block(input)

def button(
    button_type: String = "button",
    id: Option[String] = None,
    block: HTMLButtonElement => Unit = _ => ()
): HTMLButtonElement =
  val btn = document.createElement("button").asInstanceOf[HTMLButtonElement]
  btn.tap: button =>
      id.foreach(button.id = _)
      button.`type` = button_type
      block(button)

extension [T <: HTMLElement](elem: T)
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

  def with_children(children: HTMLElement*): T =
    children.foreach(elem.appendChild(_))
    elem

  def apply(children: HTMLElement*): T =
    children.foreach(elem.appendChild(_))
    elem

