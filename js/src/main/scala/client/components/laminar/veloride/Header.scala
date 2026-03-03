package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Header component with title, active skill indicator, and gold display */
object Header:

  def apply(): HtmlElement =
    div(
      cls := "velor-header",
      div(
        cls := "velor-header-title",
        "⚔️",
        span("Velor Idle")
      ),
      // Active skill indicator
      activeSkillIndicator(),
      div(
        cls := "velor-gold-display",
        "💰",
        child.text <-- VelorIdleState.goldSignal.map(formatGold)
      )
    )

  private def activeSkillIndicator(): HtmlElement =
    val activeActionSignal = VelorIdleState.activeActionSignal
    val currentSkillSignal = VelorIdleState.currentSkillSignal
    
    div(
      cls := "velor-active-skill",
      child <-- currentSkillSignal.combineWith(activeActionSignal).map:
        case (Some(skill), ActiveAction.Idle) =>
          // Skill selected but idle - don't show icon
          div(
            cls := "velor-active-skill-content idle clickable",
            onClick --> { _ => VelorIdleState.goToSkillTraining() },
            span(cls := "velor-active-skill-status", "Idle")
          )
        case (Some(skill), _) =>
          // Skill actively running - show spinning icon
          div(
            cls := "velor-active-skill-content active clickable",
            onClick --> { _ => VelorIdleState.goToSkillTraining() },
            span(cls := "velor-active-skill-icon spinning", Skill.icon(skill)),
            span(cls := "velor-active-skill-status", "Active")
          )
        case (None, _) =>
          // No skill selected - not clickable
          div(
            cls := "velor-active-skill-content none",
            span(cls := "velor-active-skill-status", "Idle")
          )
    )

  private def formatGold(gold: Long): String =
    if gold >= 1_000_000 then f"${gold / 1_000_000.0}%.1fM"
    else if gold >= 1_000 then f"${gold / 1_000.0}%.1fk"
    else gold.toString

