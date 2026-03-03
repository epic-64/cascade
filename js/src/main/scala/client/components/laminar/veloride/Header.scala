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
    
    div(
      cls := "velor-active-skill",
      child <-- activeActionSignal.map:
        case ActiveAction.Gathering(skill, _) => activeSkillDiv(skill)
        case ActiveAction.Processing(skill, _) => activeSkillDiv(skill)
        case ActiveAction.Idle =>
          div(
            cls := "velor-active-skill-content none",
            span(cls := "velor-active-skill-status", "Idle")
          )
    )

  private def activeSkillDiv(skill: Skill): HtmlElement =
    div(
      cls := "velor-active-skill-content active clickable",
      onClick --> { _ => VelorIdleState.selectSkill(skill) },
      span(cls := "velor-active-skill-icon spinning", Skill.icon(skill)),
      span(cls := "velor-active-skill-status", "Active")
    )

  private def formatGold(gold: Long): String = VelorUtils.formatNumber(gold)

