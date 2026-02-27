package client.components.laminar

import com.raquo.laminar.api.L.*

/** Laminar-based modal shown when save deserialization fails.
  *
  * Informs the player that their save was corrupted and shows how many
  * skill points were recovered from metadata.
  */
object SaveRecoveryModal:

  private val isVisibleVar: Var[Boolean] = Var(false)
  private val recoveredPointsVar: Var[Int] = Var(0)

  /** Show the save recovery modal */
  def show(recoveredSkillPoints: Int): Unit =
    recoveredPointsVar.set(recoveredSkillPoints)
    isVisibleVar.set(true)

  /** Hide the modal */
  def hide(): Unit =
    isVisibleVar.set(false)

  /** The modal element */
  def apply(): HtmlElement =
    div(
      idAttr := "tile-kingdom-save-recovery-modal",
      cls := "welcome-modal",
      cls <-- isVisibleVar.signal.map(visible => if visible then "show" else ""),
      div(
        cls := "welcome-modal-content",
        div(
          cls := "welcome-modal-header",
          h3("⚠️ Save Corrupted")
        ),
        div(
          cls := "welcome-modal-body",
          child <-- recoveredPointsVar.signal.map: points =>
            div(
              p(cls := "welcome-time", if points > 0 then
                "Your save could not be loaded, but your progress was partially recovered."
              else
                "Your save could not be loaded. Here's a head start on your new game."),
              div(
                cls := "welcome-gains",
                Option.when(points > 0)(div(
                  cls := "welcome-gain-item",
                  span(cls := "welcome-gain-icon", "⭐"),
                  span(cls := "welcome-gain-value", s"$points"),
                  span("skill points restored")
                )),
                div(
                  cls := "welcome-gain-item",
                  span(cls := "welcome-gain-icon", "💰"),
                  span(cls := "welcome-gain-value", "5000"),
                  span("gold granted")
                )
              )
            )
        ),
        button(
          cls := "btn-primary welcome-modal-close",
          "Continue",
          onClick --> { _ => hide() }
        )
      )
    )

