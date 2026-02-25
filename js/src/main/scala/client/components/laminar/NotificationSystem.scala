package client.components.laminar

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** Laminar-based notification system for TileKingdom.
  *
  * Provides toast-style notifications that auto-dismiss.
  */
object NotificationSystem:

  private val notificationVar: Var[Option[String]] = Var(None)
  private var hideTimeoutHandle: Option[Int] = None

  /** Show a notification message */
  def show(message: String, durationMs: Int = 2000): Unit =
    // Clear any existing timeout
    hideTimeoutHandle.foreach(dom.window.clearTimeout)

    // Show the notification
    notificationVar.set(Some(message))

    // Auto-hide after duration
    hideTimeoutHandle = Some(dom.window.setTimeout(
      () => notificationVar.set(None),
      durationMs
    ))

  /** The notification element */
  def apply(): HtmlElement =
    div(
      idAttr := "tile-kingdom-notification",
      cls := "notification",
      cls <-- notificationVar.signal.map(opt => if opt.isDefined then "show" else ""),
      child.text <-- notificationVar.signal.map(_.getOrElse(""))
    )

