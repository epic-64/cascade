package client

import org.scalajs.dom.*

object NavigationBar:

  def render(currentPage: String): HTMLElement =
    val nav = document.createElement("nav").asInstanceOf[HTMLElement]
    nav.className = "nav-bar"

    // Create content wrapper for alignment with container
    val content = document.createElement("div").asInstanceOf[HTMLElement]
    content.className = "nav-bar-content"

    // Left side - Home link
    val homeLink = document.createElement("a").asInstanceOf[HTMLAnchorElement]
    homeLink.href = "/"
    homeLink.className = "nav-home"

    val logo = document.createElement("span").asInstanceOf[HTMLElement]
    logo.className = "nav-logo"
    logo.textContent = "Cascade"
    homeLink.appendChild(logo)

    content.appendChild(homeLink)

    // Right side - Breadcrumb
    val breadcrumb = document.createElement("div").asInstanceOf[HTMLElement]
    breadcrumb.className = "nav-breadcrumb"

    val separator = document.createElement("span").asInstanceOf[HTMLElement]
    separator.className = "nav-separator"
    separator.textContent = "/"
    breadcrumb.appendChild(separator)

    val currentPageSpan = document.createElement("span").asInstanceOf[HTMLElement]
    currentPageSpan.className = "nav-current"
    currentPageSpan.textContent = currentPage
    breadcrumb.appendChild(currentPageSpan)

    content.appendChild(breadcrumb)

    nav.appendChild(content)

    nav

