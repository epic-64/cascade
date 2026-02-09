package client

import org.scalajs.dom.*

import scala.util.chaining.scalaUtilChainingOps

object NavigationBar:

  def render(currentPage: String): HTMLElement =
    el("nav").with_classes("nav-bar")(
      el("div").with_classes("nav-bar-content")(
        // Left side - Home link
        a("/").with_classes("nav-home")(
          el("span").with_classes("nav-logo").with_content("Cascade")
        ),
        // Right side - Breadcrumb
        el("div").with_classes("nav-breadcrumb")(
          el("span").with_classes("nav-separator").with_content("/"),
          el("span").with_classes("nav-current").with_content(currentPage)
        )
      )
    )
