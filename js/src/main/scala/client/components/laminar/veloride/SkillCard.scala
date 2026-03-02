package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Current skill card - shows skill details, XP progress, and current action */
object SkillCard:

  def apply(onStartAction: String => Unit, onStopAction: () => Unit): HtmlElement =
    div(
      cls := "velor-skill-card",
      child <-- VelorIdleState.currentSkillSignal.map:
        case None => noSkillSelected()
        case Some(skill) => skillContent(skill, onStartAction, onStopAction)
    )

  private def noSkillSelected(): HtmlElement =
    div(
      cls := "velor-skill-card-empty",
      p("Select a skill to begin training")
    )

  private def skillContent(skill: Skill, onStartAction: String => Unit, onStopAction: () => Unit): HtmlElement =
    val skillStateSignal = VelorIdleState.skillStateSignal(skill)

    div(
      // Header with skill name and level
      div(
        cls := "velor-skill-card-header",
        div(
          cls := "velor-skill-card-title",
          span(Skill.icon(skill)),
          span(Skill.displayName(skill))
        ),
        div(
          cls := "velor-skill-card-level",
          child.text <-- skillStateSignal.map(s => s"Level ${s.level}")
        )
      ),

      // XP Progress bar
      xpProgressBar(skill, skillStateSignal),

      // Action progress (if active)
      actionProgress(skill, onStopAction),

      // Action selector based on skill type
      if Skill.isGathering(skill) then
        ActionSelector(skill, onStartAction)
      else if Skill.isProcessing(skill) then
        ProcessingSelector(skill, onStartAction)
      else
        div(cls := "velor-text-muted", "Coming soon...")
    )

  private def xpProgressBar(skill: Skill, stateSignal: Signal[SkillState]): HtmlElement =
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

  private def actionProgress(skill: Skill, onStopAction: () => Unit): HtmlElement =
    val activeSignal = VelorIdleState.activeActionSignal
    val progressSignal = VelorIdleState.actionProgressSignal

    div(
      cls := "velor-action-container",
      display <-- activeSignal.map:
        case ActiveAction.Idle => "none"
        case _ => "block"
      ,
      child <-- activeSignal.map:
        case ActiveAction.Idle => emptyNode
        case ActiveAction.Gathering(action) =>
          renderActionProgress(action.icon, action.name, action.timeSeconds, action.xpGain, 
            action.output, progressSignal, onStopAction)
        case ActiveAction.Processing(action) =>
          renderActionProgress(action.icon, action.name, action.timeSeconds, action.xpGain,
            action.output, progressSignal, onStopAction)
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
        cls := "velor-action-bar",
        div(
          cls := "velor-action-bar-fill",
          styleAttr <-- progressSignal.map(p => s"width: ${(p * 100).toInt}%")
        )
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

