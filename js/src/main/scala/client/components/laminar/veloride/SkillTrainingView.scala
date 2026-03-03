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
        // Perk bonuses based on skill type
        if Skill.isGathering(skill) then gatheringPerkBonuses(skill, skillStateSignal)
        else if Skill.isProcessing(skill) then processingPerkBonuses(skill, skillStateSignal)
        else emptyNode
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

  private def gatheringPerkBonuses(skill: Skill, stateSignal: Signal[SkillState]): HtmlElement =
    // Get action bonuses from active action (if any gathering action is active for this skill)
    val actionBonusesSignal = VelorIdleState.activeActionSignal.combineWith(VelorIdleState.gameSignal).map:
      case (ActiveAction.Gathering(s, action), game) if s == skill =>
        val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)
        Some((
          VelorIdleLogic.calculateEfficiencyBonus(actionState.level),
          VelorIdleLogic.calculateYieldBonus(actionState.level)
        ))
      case _ => None

    div(
      cls := "velor-perk-bonuses",
      // Job bonuses
      div(
        cls := "velor-perk-item",
        span(cls := "velor-perk-label", "Secondary"),
        span(cls := "velor-perk-value",
          child.text <-- stateSignal.map(s => f"${VelorIdleLogic.calculateSecondaryChance(s.level) * 100}%.0f%%")
        )
      ),
      div(
        cls := "velor-perk-item",
        span(cls := "velor-perk-label", "Mastery"),
        span(cls := "velor-perk-value",
          child.text <-- stateSignal.map(s => f"${VelorIdleLogic.calculateDoubleChance(s.level, isGathering = true) * 100}%.0f%%")
        )
      ),
      // Separator
      span(cls := "velor-perk-separator", "·"),
      // Action bonuses
      div(
        cls := "velor-perk-item",
        span(cls := "velor-perk-label", "Efficiency"),
        span(cls := "velor-perk-value",
          child.text <-- actionBonusesSignal.map:
            case Some((eff, _)) => f"${eff * 100}%.0f%%"
            case None => "—"
        )
      ),
      div(
        cls := "velor-perk-item",
        span(cls := "velor-perk-label", "Yield"),
        span(cls := "velor-perk-value",
          child.text <-- actionBonusesSignal.map:
            case Some((_, y)) => f"${y * 100}%.0f%%"
            case None => "—"
        )
      )
    )

  private def processingPerkBonuses(skill: Skill, stateSignal: Signal[SkillState]): HtmlElement =
    // Get efficiency from active action
    val efficiencySignal = VelorIdleState.activeActionSignal.combineWith(VelorIdleState.gameSignal).map:
      case (ActiveAction.Processing(s, action), game) if s == skill =>
        val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)
        Some(VelorIdleLogic.calculateEfficiencyBonus(actionState.level))
      case _ => None

    div(
      cls := "velor-perk-bonuses",
      // Job bonuses
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
      ),
      // Separator
      span(cls := "velor-perk-separator", "·"),
      // Action bonuses
      div(
        cls := "velor-perk-item",
        span(cls := "velor-perk-label", "Efficiency"),
        span(cls := "velor-perk-value",
          child.text <-- efficiencySignal.map:
            case Some(eff) => f"${eff * 100}%.0f%%"
            case None => "—"
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
    val skillStateSignal = VelorIdleState.skillStateSignal(viewingSkill)
    val actionModalOpenVar = Var(false)

    // Determine if this skill is currently active
    val isActiveSignal = activeSignal.map:
      case ActiveAction.Gathering(skill, _) if skill == viewingSkill => true
      case ActiveAction.Processing(skill, _) if skill == viewingSkill => true
      case _ => false

    // Get action details when active (including action id for yield calculation)
    val actionDetailsSignal = activeSignal.map:
      case ActiveAction.Gathering(skill, action) if skill == viewingSkill =>
        Some((action.id, action.icon, action.name, action.timeSeconds, action.xpGain, action.output, true))
      case ActiveAction.Processing(skill, action) if skill == viewingSkill =>
        Some((action.id, action.icon, action.name, action.timeSeconds, action.xpGain, action.output, false))
      case _ => None

    div(
      cls := "velor-action-container",
      
      // === Action Header (mirrors skill header) ===
      div(
        cls := "velor-training-header",
        div(
          cls := "velor-training-title",
          child <-- actionDetailsSignal.map:
            case Some((_, icon, name, _, _, _, _)) =>
              span(cls := "velor-training-icon", icon)
            case None =>
              span(cls := "velor-training-icon", Skill.icon(viewingSkill))
          ,
          child.text <-- actionDetailsSignal.map:
            case Some((_, _, name, _, _, _, _)) => name
            case None => "Idle"
          ,
          span(
            cls := "velor-training-level",
            child.text <-- actionDetailsSignal.combineWith(VelorIdleState.gameSignal).map:
              case (Some((actionId, _, _, _, _, _, _)), game) =>
                val actionState = game.actionLevels.getOrElse(actionId, ActionState.initial)
                s"Lv.${actionState.level}"
              case _ => ""
          ),
          // Help button for action bonuses (only show when action is active)
          child <-- isActiveSignal.map:
            case true =>
              button(
                cls := "velor-help-btn",
                "?",
                onClick --> { _ => actionModalOpenVar.set(true) }
              )
            case false => emptyNode
        )
      ),
      
      // === Action XP Bar (mirrors skill XP bar) ===
      child <-- actionDetailsSignal.combineWith(VelorIdleState.gameSignal).map:
        case (Some((actionId, _, _, _, _, _, _)), game) =>
          val actionState = game.actionLevels.getOrElse(actionId, ActionState.initial)
          actionXpProgressBar(actionState)
        case _ =>
          div(cls := "velor-xp-bar-container",
            div(cls := "velor-xp-bar-label", span("—"), span("")),
            div(cls := "velor-xp-bar", div(cls := "velor-xp-bar-fill", styleAttr := "width: 0%"))
          )
      ,
      
      // === Action Progress Addon (unique to action card) ===
      div(
        cls := "velor-action-progress-addon",
        // Progress bar with time remaining
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
          // Floating rewards
          div(
            cls := "velor-floating-rewards-wrapper",
            display <-- isActiveSignal.map(if _ then "block" else "none"),
            FloatingRewards.container()
          )
        ),
        // Footer with time, rewards, and stop button
        div(
          cls := "velor-action-footer",
          div(
            cls := "velor-action-time",
            child.text <-- isActiveSignal.combineWith(actionDetailsSignal, progressSignal, VelorIdleState.gameSignal).map:
              case (true, Some((actionId, _, _, baseTime, _, _, _)), p, game) =>
                val actionState = game.actionLevels.getOrElse(actionId, ActionState.initial)
                val efficiency = VelorIdleLogic.calculateEfficiencyBonus(actionState.level)
                val effectiveTime = baseTime * (1.0 - efficiency)
                val remaining = effectiveTime * (1.0 - p)
                f"$remaining%.1fs"
              case _ => "—"
          ),
          div(
            cls := "velor-action-rewards",
            child.text <-- actionDetailsSignal.combineWith(VelorIdleState.inventorySignal).map:
              case (Some((_, _, _, _, xpGain, output, _)), inv) =>
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
      ),
      
      // Action bonus modal - rendered once, visibility controlled by signal
      ActionBonusModal(
        actionDetailsSignal.map(_.map(t => (t._3, t._2))),  // (name, icon)
        actionDetailsSignal.map(_.map(_._1)),               // actionId
        actionModalOpenVar.signal,
        () => actionModalOpenVar.set(false)
      )
    )

  private def actionXpProgressBar(actionState: ActionState): HtmlElement =
    div(
      cls := "velor-xp-bar-container",
      div(
        cls := "velor-xp-bar-label",
        span(s"XP: ${formatNumber(actionState.xp)}"),
        span(
          if actionState.level >= 99 then "MAX"
          else s"Next: ${formatNumber(ActionState.totalXpForLevel(actionState.level + 1))}"
        )
      ),
      div(
        cls := "velor-xp-bar",
        div(
          cls := "velor-xp-bar-fill",
          styleAttr := s"width: ${(ActionState.xpProgress(actionState) * 100).toInt}%"
        )
      )
    )

  private def formatNumber(n: Long): String = VelorUtils.formatNumber(n)

