package client

import org.scalajs.dom.*

object NavigationBar:
  
  def render(currentPage: String): HTMLElement =
    val nav = document.createElement("nav").asInstanceOf[HTMLElement]
    nav.className = "nav-bar"
    
    // Left side - Home link
    val homeLink = document.createElement("a").asInstanceOf[HTMLAnchorElement]
    homeLink.href = "/"
    homeLink.className = "nav-home"
    
    val logo = document.createElement("span").asInstanceOf[HTMLElement]
    logo.className = "nav-logo"
    logo.textContent = "Cascade"
    homeLink.appendChild(logo)
    
    nav.appendChild(homeLink)
    
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
    
    nav.appendChild(breadcrumb)
    
    nav

