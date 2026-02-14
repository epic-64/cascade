package client

import org.scalajs.dom.*

object NavigationBar:
  def render(currentPage: String): HTMLElement =
    el("nav", cls = "nav-bar")(
      div(cls = "nav-bar-content")(
        // Left side - Home link
        a("/", cls = "nav-home")(
          span(cls = "nav-logo", content = "Cascade")
        ),
        // Right side - Breadcrumb
        div(cls = "nav-breadcrumb")(
          span(cls = "nav-separator", content = "/"),
          span(cls = "nav-current", content = currentPage)
        )
      )
    )
