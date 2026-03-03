package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Modal that shows all skill bonuses and which ones are unlocked */
object SkillBonusModal:

  /** Represents a bonus tier that can be displayed */
  case class BonusTier(level: Int, description: String)

  /** All bonuses for gathering skills (Yield moved to Action level) */
  private val gatheringBonuses: Vector[BonusTier] = Vector(
    BonusTier(10, "+5% Efficiency (faster actions)"),
    BonusTier(30, "+5% Mastery chance (double)"),
    BonusTier(40, "+5% Efficiency (10% total)"),
    BonusTier(60, "+5% Mastery chance (10% total)"),
    BonusTier(70, "+5% Efficiency (15% total)"),
    BonusTier(90, "+5% Mastery chance (15% total)")
  )

  /** All bonuses for processing skills */
  private val processingBonuses: Vector[BonusTier] = Vector(
    BonusTier(10, "+5% Efficiency (faster actions)"),
    BonusTier(20, "+5% Double chance (extra output)"),
    BonusTier(30, "+5% Recycle chance (keep inputs)"),
    BonusTier(40, "+5% Efficiency (10% total)"),
    BonusTier(50, "+5% Double chance (10% total)"),
    BonusTier(60, "+5% Recycle chance (10% total)"),
    BonusTier(70, "+5% Efficiency (15% total)"),
    BonusTier(80, "+5% Double chance (15% total)"),
    BonusTier(90, "+5% Recycle chance (15% total)")
  )

  /** Create the modal element - visibility controlled by isOpen signal */
  def apply(skill: Skill, isOpenSignal: Signal[Boolean], onClose: () => Unit): HtmlElement =
    val bonuses = if Skill.isGathering(skill) then gatheringBonuses else processingBonuses
    val levelSignal = VelorIdleState.skillStateSignal(skill).map(_.level)

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
          span(s"${Skill.icon(skill)} ${Skill.displayName(skill)} Bonuses"),
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
            bonuses.map(bonus => bonusRow(bonus, levelSignal))
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

