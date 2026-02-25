package client.components.laminar

import com.raquo.laminar.api.L.*

/** Laminar-based dev tools popup for TileKingdom.
  *
  * Provides debug cheats for development.
  */
object DevToolsPopup:

  /** A single dev action button */
  case class DevAction(
    label: String,
    action: () => Unit
  )

  /** The dev tools popup element */
  def apply(onClose: () => Unit, actions: Seq[DevAction]): HtmlElement =
    div(
      idAttr := "tile-kingdom-dev-popup",
      cls := "help-popup",
      div(
        cls := "help-popup-content dev-tools-content",
        div(
          cls := "help-popup-header",
          h3("🛠️ Dev Tools"),
          button(
            cls := "help-close-btn",
            "✕",
            onClick --> { _ => onClose() }
          )
        ),
        div(
          cls := "help-popup-body",
          actions.map(devAction =>
            button(
              cls := "btn-dev-action",
              devAction.label,
              onClick --> { _ => devAction.action() }
            )
          )
        )
      )
    )

