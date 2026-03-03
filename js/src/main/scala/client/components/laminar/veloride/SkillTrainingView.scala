package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Dedicated skill training screen - shown when player enters a skill */
object SkillTrainingView:

  def apply(onStartAction: String => Unit, onStopAction: () => Unit): HtmlElement =
    // Get current skill once at creation time - this view is recreated when navigating away and back
    val initialSkill = VelorIdleState.current.currentSkill
    
    div(
      cls := "velor-training-view",
      initialSkill match
        case None =>
          div("No skill selected")
        case Some(skill) =>
          trainingContent(skill, onStartAction, onStopAction)
    )

  private def trainingContent(skill: Skill, onStartAction: String => Unit, onStopAction: () => Unit): HtmlElement =
    val skillStateSignal = VelorIdleState.skillStateSignal(skill)
    val modalOpenVar = Var(false)

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
            span(cls := "velor-training-level", child.text <-- skillStateSignal.map(s => s"Lv.${s.level}")),
            button(
              cls := "velor-help-btn",
              "?",
              onClick --> { _ => modalOpenVar.set(true) }
            )
          )
        ),
        // XP Progress bar inside the header card
        xpProgressBar(skillStateSignal),
        // Perk bonuses for processing skills
        if Skill.isProcessing(skill) then perkBonuses(skillStateSignal) else emptyNode
      ),

      // Action progress (if active) - in its own card
      actionProgress(skill, onStopAction),

      // Action selector based on skill type
      div(
        cls := "velor-action-selector",
        div(cls := "velor-action-selector-title", "Actions"),
        if Skill.isGathering(skill) then
          ActionList.forGathering(skill, onStartAction)
        else if Skill.isProcessing(skill) then
          ActionList.forProcessing(skill, onStartAction)
        else
          div(cls := "velor-text-muted", styleAttr := "padding: 1rem;", "Coming soon...")
      ),

      // Skill bonus modal
      SkillBonusModal(skill, modalOpenVar.signal, () => modalOpenVar.set(false))
    )

  private def perkBonuses(stateSignal: Signal[SkillState]): HtmlElement =
    div(
      cls := "velor-perk-bonuses",
      div(
        cls := "velor-perk-item",
        span(cls := "velor-perk-label", "2x Chance"),
        span(cls := "velor-perk-value", 
          child.text <-- stateSignal.map(s => f"${VelorIdleLogic.calculateDoubleChance(s.level, isGathering = false) * 100}%.0f%%")
        )
      ),
      div(
        cls := "velor-perk-item",
        span(cls := "velor-perk-label", "Recycle"),
        span(cls := "velor-perk-value",
          child.text <-- stateSignal.map(s => f"${VelorIdleLogic.calculateRecycleChance(s.level) * 100}%.0f%%")
        )
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

    // Determine if this skill is currently active
    val isActiveSignal = activeSignal.map:
      case ActiveAction.Gathering(skill, _) if skill == viewingSkill => true
      case ActiveAction.Processing(skill, _) if skill == viewingSkill => true
      case _ => false

    // Get action details when active
    val actionDetailsSignal = activeSignal.map:
      case ActiveAction.Gathering(skill, action) if skill == viewingSkill =>
        Some((action.icon, action.name, action.timeSeconds, action.xpGain, action.output))
      case ActiveAction.Processing(skill, action) if skill == viewingSkill =>
        Some((action.icon, action.name, action.timeSeconds, action.xpGain, action.output))
      case _ => None

    div(
      cls := "velor-action-container",
      // Action info row - show skill icon when idle, action details when active
      div(
        cls := "velor-action-info",
        div(
          cls := "velor-action-name",
          child.text <-- actionDetailsSignal.map:
            case Some((icon, name, _, _, _)) => s"$icon $name"
            case None => s"${Skill.icon(viewingSkill)} Idle"
        ),
        div(
          cls := "velor-action-time",
          child.text <-- isActiveSignal.combineWith(actionDetailsSignal, progressSignal).map:
            case (true, Some((_, _, timeSeconds, _, _)), p) =>
              val remaining = timeSeconds * (1.0 - p)
              f"${remaining}%.1fs"
            case _ => "Select action"
        )
      ),
      // Progress bar - always visible, empty when idle
      div(
        cls := "velor-action-bar-wrapper",
        div(
          cls := "velor-action-bar",
          div(
            cls := "velor-action-bar-fill",
            styleAttr <-- isActiveSignal.combineWith(progressSignal).map:
              case (true, p) => s"width: ${(p * 100).toInt}%"
              case _ => "width: 0%"
          )
        ),
        // Only show floating rewards when this skill is active
        div(
          cls := "velor-floating-rewards-wrapper",
          display <-- isActiveSignal.map(if _ then "block" else "none"),
          FloatingRewards.container()
        )
      ),
      // Footer - always show same structure for consistent layout
      div(
        cls := "velor-action-footer",
        div(
          cls := "velor-action-rewards",
          child.text <-- actionDetailsSignal.combineWith(VelorIdleState.inventorySignal).map:
            case (Some((_, _, _, xpGain, output)), inv) =>
              val count = inv.getCount(output)
              val countStr = if count > 0 then s" [$count]" else ""
              s"+$xpGain XP · ${Item.icon(output)} ${Item.displayName(output)}$countStr"
            case _ => "—"
        ),
        button(
          cls <-- isActiveSignal.map(active => 
            if active then "velor-stop-btn" else "velor-stop-btn disabled"
          ),
          disabled <-- isActiveSignal.map(!_),
          "Stop",
          onClick --> { _ => onStopAction() }
        )
      )
    )

  private def formatNumber(n: Long): String = VelorUtils.formatNumber(n)

