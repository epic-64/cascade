package client

import org.scalajs.dom.{CSSStyleDeclaration, HTMLElement, HTMLFormElement, HTMLInputElement, document}

import scala.scalajs.js
import scala.util.chaining.*

def el(tag: String, block: HTMLElement => Unit = _ => ()): HTMLElement =
  document.createElement(tag).asInstanceOf[HTMLElement].tap(block(_))

def form(block: HTMLFormElement => Unit = _ => ()): HTMLFormElement =
  document.createElement("form").asInstanceOf[HTMLFormElement].tap(block(_))

def input(input_type: String, block: HTMLInputElement => Unit = _ => ()): HTMLInputElement =
  document.createElement("input").asInstanceOf[HTMLInputElement].tap { input =>
    input.`type` = input_type
    block(input)
  }

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