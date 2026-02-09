package client

import org.scalajs.dom.*

object NavigationBar:
  def render(currentPage: String): HTMLElement =
    el("nav").with_classes("nav-bar")(
      div().with_classes("nav-bar-content")(
        // Left side - Home link
        a("/").with_classes("nav-home")(
          span().with_classes("nav-logo").with_content("Cascade")
        ),
        // Right side - Breadcrumb
        div().with_classes("nav-breadcrumb")(
          span().with_classes("nav-separator").with_content("/"),
          span().with_classes("nav-current").with_content(currentPage)
        )
      )
    )
