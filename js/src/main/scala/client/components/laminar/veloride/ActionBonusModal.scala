package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Modal that shows all action bonuses and which ones are unlocked */
object ActionBonusModal:

  /** Represents a bonus tier that can be displayed */
  case class BonusTier(level: Int, description: String)

  /** All bonuses for action levels (currently just Yield for gathering) */
  private val actionBonuses: Vector[BonusTier] = Vector(
    BonusTier(20, "+10% Yield chance (+1 resource)"),
    BonusTier(50, "+10% Yield chance (20% total)"),
    BonusTier(80, "+10% Yield chance (30% total)")
  )

  /** Create the modal element - visibility controlled by isOpen signal */
  def apply(
    actionName: String,
    actionIcon: String,
    actionStateSignal: Signal[ActionState],
    isOpenSignal: Signal[Boolean],
    onClose: () => Unit
  ): HtmlElement =
    val levelSignal = actionStateSignal.map(_.level)

    div(
      cls <-- isOpenSignal.map(open => if open then "velor-modal-overlay show" else "velor-modal-overlay"),
      onClick --> { e =>
        // Close when clicking overlay (but not modal content)
        if e.target == e.currentTarget then onClose()
      },
      div(
        cls := "velor-modal",
        div(
          cls := "velor-modal-header",
          span(s"$actionIcon $actionName Bonuses"),
          button(
            cls := "velor-modal-close",
            "✕",
            onClick --> { _ => onClose() }
          )
        ),
        div(
          cls := "velor-modal-body",
          div(
            cls := "velor-bonus-list",
            actionBonuses.map(bonus => bonusRow(bonus, levelSignal))
          )
        )
      )
    )

  private def bonusRow(bonus: BonusTier, levelSignal: Signal[Int]): HtmlElement =
    val isUnlockedSignal = levelSignal.map(_ >= bonus.level)

    div(
      cls := "velor-bonus-row",
      cls <-- isUnlockedSignal.map(unlocked => if unlocked then "unlocked" else "locked"),
      div(
        cls := "velor-bonus-level",
        s"Lv.${bonus.level}"
      ),
      div(
        cls := "velor-bonus-desc",
        bonus.description
      ),
      div(
        cls := "velor-bonus-status",
        child.text <-- isUnlockedSignal.map(if _ then "✓" else "🔒")
      )
    )

