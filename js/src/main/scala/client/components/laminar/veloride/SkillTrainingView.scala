package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Dedicated skill training screen - shown when player enters a skill */
object SkillTrainingView:

  def apply(onStartAction: String => Unit, onStopAction: () => Unit): HtmlElement =
    div(
      cls := "velor-training-view",
      child <-- VelorIdleState.currentSkillSignal.map:
        case None =>
          // Shouldn't happen, but fallback
          div("No skill selected")
        case Some(skill) =>
          trainingContent(skill, onStartAction, onStopAction)
    )

  private def trainingContent(skill: Skill, onStartAction: String => Unit, onStopAction: () => Unit): HtmlElement =
    val skillStateSignal = VelorIdleState.skillStateSignal(skill)

    div(
      cls := "velor-training-content",
      // Skill header card with XP bar
      div(
        cls := "velor-skill-header-card",
        div(
          cls := "velor-training-header",
          button(
            cls := "velor-back-btn",
            "←",
            onClick --> { _ => VelorIdleState.setViewMode(VelorIdleState.ViewMode.SkillSelect) }
          ),
          div(
            cls := "velor-training-title",
            span(cls := "velor-training-icon", Skill.icon(skill)),
            span(Skill.displayName(skill)),
            span(cls := "velor-training-level", child.text <-- skillStateSignal.map(s => s"Lv.${s.level}"))
          )
        ),
        // XP Progress bar inside the header card
        xpProgressBar(skillStateSignal)
      ),

      // Action progress (if active) - in its own card
      actionProgress(skill, onStopAction),

      // Action selector based on skill type
      div(
        cls := "velor-action-selector",
        div(cls := "velor-action-selector-title", "Actions"),
        if Skill.isGathering(skill) then
          ActionSelector(skill, onStartAction)
        else if Skill.isProcessing(skill) then
          ProcessingSelector(skill, onStartAction)
        else
          div(cls := "velor-text-muted", styleAttr := "padding: 1rem;", "Coming soon...")
      )
    )

  private def xpProgressBar(stateSignal: Signal[SkillState]): HtmlElement =
    div(
      cls := "velor-xp-bar-container",
      div(
        cls := "velor-xp-bar-label",
        span(child.text <-- stateSignal.map(s => s"XP: ${formatNumber(s.xp)}")),
        span(child.text <-- stateSignal.map { s =>
          if s.level >= 99 then "MAX"
          else
            val nextLevelXp = SkillState.totalXpForLevel(s.level + 1)
            s"Next: ${formatNumber(nextLevelXp)}"
        })
      ),
      div(
        cls := "velor-xp-bar",
        div(
          cls := "velor-xp-bar-fill",
          styleAttr <-- stateSignal.map(s => s"width: ${(SkillState.xpProgress(s) * 100).toInt}%")
        )
      )
    )

  private def actionProgress(viewingSkill: Skill, onStopAction: () => Unit): HtmlElement =
    val activeSignal = VelorIdleState.activeActionSignal
    val progressSignal = VelorIdleState.actionProgressSignal

    div(
      cls := "velor-action-container",
      display <-- activeSignal.map:
        case ActiveAction.Gathering(skill, _) if skill == viewingSkill => "block"
        case ActiveAction.Processing(skill, _) if skill == viewingSkill => "block"
        case _ => "none"
      ,
      child <-- activeSignal.map:
        case ActiveAction.Gathering(skill, action) if skill == viewingSkill =>
          renderActionProgress(action.icon, action.name, action.timeSeconds, action.xpGain,
            action.output, progressSignal, onStopAction)
        case ActiveAction.Processing(skill, action) if skill == viewingSkill =>
          renderActionProgress(action.icon, action.name, action.timeSeconds, action.xpGain,
            action.output, progressSignal, onStopAction)
        case _ => emptyNode
    )

  private def renderActionProgress(
    icon: String,
    name: String,
    timeSeconds: Double,
    xpGain: Int,
    output: Item,
    progressSignal: Signal[Double],
    onStopAction: () => Unit
  ): HtmlElement =
    div(
      div(
        cls := "velor-action-info",
        div(
          cls := "velor-action-name",
          span(icon),
          span(name)
        ),
        div(
          cls := "velor-action-time",
          child.text <-- progressSignal.map { p =>
            val remaining = timeSeconds * (1.0 - p)
            f"${remaining}%.1fs"
          }
        )
      ),
      div(
        cls := "velor-action-bar-wrapper",
        div(
          cls := "velor-action-bar",
          div(
            cls := "velor-action-bar-fill",
            styleAttr <-- progressSignal.map(p => s"width: ${(p * 100).toInt}%")
          )
        ),
        // Floating rewards spawn from here
        FloatingRewards.container()
      ),
      div(
        cls := "velor-action-rewards",
        div(
          cls := "velor-action-reward velor-action-reward-xp",
          s"+$xpGain XP"
        ),
        div(
          cls := "velor-action-reward",
          s"${Item.icon(output)} ${Item.displayName(output)}"
        )
      ),
      button(
        cls := "btn btn-secondary",
        styleAttr := "margin-top: 1rem; width: 100%",
        "Stop",
        onClick --> { _ => onStopAction() }
      )
    )

  private def formatNumber(n: Long): String =
    if n >= 1_000_000 then f"${n / 1_000_000.0}%.1fM"
    else if n >= 1_000 then f"${n / 1_000.0}%.1fk"
    else n.toString

