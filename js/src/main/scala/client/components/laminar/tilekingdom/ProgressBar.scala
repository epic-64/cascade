package client.components.laminar.tilekingdom

import com.raquo.laminar.api.L.*

/** Reusable progress bar component for tile production cycles.
  *
  * Shows a horizontal bar that fills from 0% to 100% based on the progress signal.
  */
object ProgressBar:

  /** Create a progress bar element
    *
    * @param progressSignal Signal containing progress value (0.0 to 1.0)
    * @param extraClass Optional additional CSS class (e.g., "woodcutter-progress", "bureau-progress")
    */
  def apply(
    progressSignal: Signal[Double],
    extraClass: String = ""
  ): HtmlElement =
    val barClass = if extraClass.isEmpty then "tile-progress-bar" else s"tile-progress-bar $extraClass"

    div(
      cls := "tile-progress-container",
      div(
        cls := barClass,
        styleAttr <-- progressSignal.map(p => s"width: ${(p * 100).toInt}%")
      )
    )

  /** Create a progress bar that can be hidden
    *
    * @param progressSignal Signal containing progress value (0.0 to 1.0)
    * @param visibleSignal Signal indicating whether the bar should be visible
    * @param extraClass Optional additional CSS class
    */
  def withVisibility(
    progressSignal: Signal[Double],
    visibleSignal: Signal[Boolean],
    extraClass: String = ""
  ): HtmlElement =
    val barClass = if extraClass.isEmpty then "tile-progress-bar" else s"tile-progress-bar $extraClass"

    div(
      cls := "tile-progress-container",
      display <-- visibleSignal.map(v => if v then "block" else "none"),
      div(
        cls := barClass,
        styleAttr <-- progressSignal.map(p => s"width: ${(p * 100).toInt}%")
      )
    )

