package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Dedicated skill training screen - shown when player enters a skill */
object SkillTrainingView:

  def apply(onStartAction: String => Unit, onStopAction: () => Unit): HtmlElement =
    // Get current skill once at creation time - this view is recreated when navigating away and back
    val initialSkill = VelorIdleState.current.currentSkill
    
    div(
      cls := "velor-view velor-view-fill velor-training-view",
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
        else if skill == Skill.Thieving then thievingPerkBonuses(skillStateSignal)
        else emptyNode
      ),

      // Action progress (if active) - in its own card
      actionProgress(skill, onStopAction),


      // Action selector based on skill type
      div(
        cls := "velor-action-selector",
        div(cls := "velor-action-selector-title", if skill == Skill.Thieving then "Targets" else "Actions"),
        if Skill.isGathering(skill) then
          ActionList.forGathering(skill, onStartAction)
        else if Skill.isProcessing(skill) then
          ActionList.forProcessing(skill, onStartAction)
        else if skill == Skill.Thieving then
          ActionList.forThieving(onStartAction)
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

  private def thievingPerkBonuses(stateSignal: Signal[SkillState]): HtmlElement =
    // Get efficiency from active action
    val efficiencySignal = VelorIdleState.activeActionSignal.combineWith(VelorIdleState.gameSignal).map:
      case (ActiveAction.Thieving(action), game) =>
        val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)
        Some(VelorIdleLogic.calculateEfficiencyBonus(actionState.level))
      case _ => None

    div(
      cls := "velor-perk-bonuses",
      // Success bonus from level
      div(
        cls := "velor-perk-item",
        span(cls := "velor-perk-label", "Success ↑"),
        span(cls := "velor-perk-value",
          child.text <-- stateSignal.map { s =>
            f"+${s.level * 0.5}%.1f%%"
          }
        )
      ),
      // Separator
      span(cls := "velor-perk-separator", "·"),
      // Action bonuses (efficiency)
      div(
        cls := "velor-perk-item",
        span(cls := "velor-perk-label", "Speed"),
        span(cls := "velor-perk-value",
          child.text <-- efficiencySignal.map:
            case Some(eff) => f"${eff * 100}%.0f%%"
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
      case (ActiveAction.EquipmentCrafting(action), game) if skill == Skill.Smithing =>
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
    // Helper to get action XP rate info
    def getXpRateInfo(game: VelorIdleGame, currentProgress: Double): Option[(Double, Double)] =
      game.activeAction match
        case ActiveAction.Gathering(_, action) =>
          val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)
          val efficiency = VelorIdleLogic.calculateEfficiencyBonus(actionState.level)
          val effectiveTime = action.timeSeconds * (1.0 - efficiency)
          Some((action.xpGain.toDouble, effectiveTime))
        case ActiveAction.Processing(_, action) =>
          val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)
          val efficiency = VelorIdleLogic.calculateEfficiencyBonus(actionState.level)
          val effectiveTime = action.timeSeconds * (1.0 - efficiency)
          Some((action.xpGain.toDouble, effectiveTime))
        case ActiveAction.Thieving(action) =>
          val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)
          val efficiency = VelorIdleLogic.calculateEfficiencyBonus(actionState.level)
          val effectiveTime = action.timeSeconds * (1.0 - efficiency)
          Some((action.xpGain.toDouble, effectiveTime))
        case ActiveAction.EquipmentCrafting(action) =>
          val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)
          val efficiency = VelorIdleLogic.calculateEfficiencyBonus(actionState.level)
          val effectiveTime = action.timeSeconds * (1.0 - efficiency)
          Some((action.xpGain.toDouble, effectiveTime))
        case _ => None

    // ETA to next level
    val etaSignal = stateSignal.combineWith(VelorIdleState.gameSignal, VelorIdleState.actionProgressSignal).map:
      case (skillState, game, currentProgress) =>
        if skillState.level >= 99 then "MAX"
        else
          getXpRateInfo(game, currentProgress) match
            case Some((xpPerAction, effectiveTime)) if xpPerAction > 0 =>
              val seconds = calculateSecondsToSkillLevel(skillState.xp, skillState.level + 1, xpPerAction, effectiveTime, currentProgress)
              formatEta(seconds)
            case _ => "—"
    .map(formatEtaString).distinct

    // ETA to level 99
    val eta99Signal = stateSignal.combineWith(VelorIdleState.gameSignal, VelorIdleState.actionProgressSignal).map:
      case (skillState, game, currentProgress) =>
        if skillState.level >= 99 then "MAX"
        else
          getXpRateInfo(game, currentProgress) match
            case Some((xpPerAction, effectiveTime)) if xpPerAction > 0 =>
              val seconds = calculateSecondsToSkillLevel(skillState.xp, 99, xpPerAction, effectiveTime, currentProgress)
              formatEta(seconds)
            case _ => "—"
    .map(formatEtaString).distinct

    div(
      cls := "velor-xp-bar-container",
      div(
        cls := "velor-xp-bar-label",
        span(child.text <-- stateSignal.map(s => s"XP: ${s.xp}")),
        span(cls := "velor-xp-eta", child.text <-- etaSignal.map(e => s"$e")),
        span(cls := "velor-xp-eta velor-xp-eta-99", child.text <-- eta99Signal.map(e => s"$e")),
        span(child.text <-- stateSignal.map { s =>
          if s.level >= 99 then "MAX"
          else
            val nextLevelXp = SkillState.totalXpForLevel(s.level + 1)
            s"Next: $nextLevelXp"
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

  // Format ETA to string, rounding to nearest second for display stability
  private def formatEtaString(eta: String): String = eta

  private def formatEta(seconds: Long): String =
    if seconds < 0 then "0s"
    else if seconds < 60 then s"${seconds}s"
    else if seconds < 3600 then
      val m = seconds / 60
      val s = seconds % 60
      f"${m}m ${s}s"
    else if seconds < 86400 then
      val h = seconds / 3600
      val m = (seconds % 3600) / 60
      f"${h}h ${m}m"
    else
      val d = seconds / 86400
      val h = (seconds % 86400) / 3600
      f"${d}d ${h}h"

  /** Calculate seconds to reach a target level for a skill */
  private def calculateSecondsToSkillLevel(
    currentXp: Long,
    targetLevel: Int,
    xpPerAction: Double,
    effectiveTime: Double,
    currentProgress: Double
  ): Long =
    if xpPerAction <= 0 then Long.MaxValue
    else
      val xpNeeded = SkillState.totalXpForLevel(targetLevel) - currentXp
      if xpNeeded <= 0 then 0L
      else
        val actionsNeeded = Math.ceil(xpNeeded / xpPerAction)
        val remainingInCurrentAction = effectiveTime * (1.0 - currentProgress)
        val secondsRemaining = remainingInCurrentAction + (actionsNeeded - 1) * effectiveTime
        secondsRemaining.toLong

  /** Calculate seconds to reach a target level for an action */
  private def calculateSecondsToActionLevel(
    currentXp: Long,
    targetLevel: Int,
    xpPerAction: Double,
    effectiveTime: Double,
    currentProgress: Double,
    actionUnlockLevel: Int
  ): Long =
    if xpPerAction <= 0 then Long.MaxValue
    else
      val xpNeeded = ActionState.totalXpForLevel(targetLevel, actionUnlockLevel) - currentXp
      if xpNeeded <= 0 then 0L
      else
        val actionsNeeded = Math.ceil(xpNeeded / xpPerAction)
        val remainingInCurrentAction = effectiveTime * (1.0 - currentProgress)
        val secondsRemaining = remainingInCurrentAction + (actionsNeeded - 1) * effectiveTime
        secondsRemaining.toLong

  private def actionProgress(viewingSkill: Skill, onStopAction: () => Unit): HtmlElement =
    val activeSignal = VelorIdleState.activeActionSignal
    val progressSignal = VelorIdleState.actionProgressSignal
    val skillStateSignal = VelorIdleState.skillStateSignal(viewingSkill)
    val actionModalOpenVar = Var(false)

    // Determine if this skill is currently active
    val isActiveSignal = activeSignal.map:
      case ActiveAction.Gathering(skill, _) if skill == viewingSkill => true
      case ActiveAction.Processing(skill, _) if skill == viewingSkill => true
      case ActiveAction.EquipmentCrafting(_) if viewingSkill == Skill.Smithing => true
      case ActiveAction.Thieving(_) if viewingSkill == Skill.Thieving => true
      case _ => false

    // Get action details when active (including action id for yield calculation)
    // Tuple: (actionId, icon, name, timeSeconds, xpGain, output, hasRareOutput, levelRequired)
    val actionDetailsSignal = activeSignal.map:
      case ActiveAction.Gathering(skill, action) if skill == viewingSkill =>
        Some((action.id, action.icon, action.name, action.timeSeconds, action.xpGain, action.output, true, action.levelRequired))
      case ActiveAction.Processing(skill, action) if skill == viewingSkill =>
        Some((action.id, action.icon, action.name, action.timeSeconds, action.xpGain, action.output, false, action.levelRequired))
      case ActiveAction.EquipmentCrafting(action) if viewingSkill == Skill.Smithing =>
        Some((action.id, action.icon, action.name, action.timeSeconds, action.xpGain, Item.BronzeBar, false, action.levelRequired))
      case ActiveAction.Thieving(action) if viewingSkill == Skill.Thieving =>
        Some((action.id, action.icon, action.name, action.timeSeconds, action.xpGain, Item.Gem, false, action.levelRequired))
      case _ => None

    div(
      cls := "velor-action-container",
      
      // === Action Header (mirrors skill header) ===
      div(
        cls := "velor-training-header",
        div(
          cls := "velor-training-title",
          child <-- actionDetailsSignal.map:
            case Some((_, icon, _, _, _, _, _, _)) =>
              span(cls := "velor-training-icon", icon)
            case None =>
              span(cls := "velor-training-icon", Skill.icon(viewingSkill))
          ,
          child.text <-- actionDetailsSignal.map:
            case Some((_, _, name, _, _, _, _, _)) => name
            case None => "Idle"
          ,
          span(
            cls := "velor-training-level",
            child.text <-- actionDetailsSignal.combineWith(VelorIdleState.gameSignal).map:
              case (Some((actionId, _, _, _, _, _, _, _)), game) =>
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
      actionXpProgressBar(actionDetailsSignal),
      
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
        // Footer with time and stop button
        div(
          cls := "velor-action-footer",
          div(
            cls := "velor-action-time",
            child.text <-- isActiveSignal.combineWith(actionDetailsSignal, progressSignal, VelorIdleState.gameSignal).map:
              case (true, Some((actionId, _, _, baseTime, _, _, _, _)), p, game) =>
                val actionState = game.actionLevels.getOrElse(actionId, ActionState.initial)
                val efficiency = VelorIdleLogic.calculateEfficiencyBonus(actionState.level)
                val effectiveTime = baseTime * (1.0 - efficiency)
                val remaining = effectiveTime * (1.0 - p)
                f"$remaining%.1fs"
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

  private def actionXpProgressBar(actionDetailsSignal: Signal[Option[(String, String, String, Double, Int, Item, Boolean, Int)]]): HtmlElement =
    // Action state signal
    val actionStateSignal = actionDetailsSignal.combineWith(VelorIdleState.gameSignal).map:
      case (Some((actionId, _, _, _, _, _, _, _)), game) =>
        Some(game.actionLevels.getOrElse(actionId, ActionState.initial))
      case _ => None

    // Action unlock level signal (for XP calculations)
    val actionUnlockLevelSignal = actionDetailsSignal.map:
      case Some((_, _, _, _, _, _, _, levelRequired)) => levelRequired
      case _ => 1

    // ETA signal for action level up - uses progressSignal for real-time updates
    val etaSignal = actionDetailsSignal.combineWith(VelorIdleState.gameSignal, VelorIdleState.actionProgressSignal).map:
      case (Some((actionId, _, _, timeSeconds, actionXpGain, _, _, levelRequired)), game, currentProgress) =>
        val actionState = game.actionLevels.getOrElse(actionId, ActionState.initial)
        if actionState.level >= 99 then "MAX"
        else
          val efficiency = VelorIdleLogic.calculateEfficiencyBonus(actionState.level)
          val effectiveTime = timeSeconds * (1.0 - efficiency)
          val seconds = calculateSecondsToActionLevel(actionState.xp, actionState.level + 1, actionXpGain.toDouble, effectiveTime, currentProgress, levelRequired)
          if seconds == Long.MaxValue then "—" else formatEta(seconds)
      case _ => "—"
    .distinct

    // ETA signal for action level 99
    val eta99Signal = actionDetailsSignal.combineWith(VelorIdleState.gameSignal, VelorIdleState.actionProgressSignal).map:
      case (Some((actionId, _, _, timeSeconds, actionXpGain, _, _, levelRequired)), game, currentProgress) =>
        val actionState = game.actionLevels.getOrElse(actionId, ActionState.initial)
        if actionState.level >= 99 then "MAX"
        else
          val efficiency = VelorIdleLogic.calculateEfficiencyBonus(actionState.level)
          val effectiveTime = timeSeconds * (1.0 - efficiency)
          val seconds = calculateSecondsToActionLevel(actionState.xp, 99, actionXpGain.toDouble, effectiveTime, currentProgress, levelRequired)
          if seconds == Long.MaxValue then "—" else formatEta(seconds)
      case _ => "—"
    .distinct

    div(
      cls := "velor-xp-bar-container",
      div(
        cls := "velor-xp-bar-label",
        span(child.text <-- actionStateSignal.map:
          case Some(s) => s"XP: ${s.xp}"
          case None => "—"
        ),
        span(cls := "velor-xp-eta", child.text <-- etaSignal.map(e => s"$e")),
        span(cls := "velor-xp-eta velor-xp-eta-99", child.text <-- eta99Signal.map(e => s"$e")),
        span(child.text <-- actionStateSignal.combineWith(actionUnlockLevelSignal).map:
          case (Some(s), _) if s.level >= 99 => "MAX"
          case (Some(s), unlockLevel) => s"Next: ${ActionState.totalXpForLevel(s.level + 1, unlockLevel)}"
          case _ => ""
        )
      ),
      div(
        cls := "velor-xp-bar",
        div(
          cls := "velor-xp-bar-fill",
          styleAttr <-- actionStateSignal.combineWith(actionUnlockLevelSignal).map:
            case (Some(s), unlockLevel) => s"width: ${(ActionState.xpProgress(s, unlockLevel) * 100).toInt}%"
            case _ => "width: 0%"
        )
      )
    )

  private def formatNumber(n: Long): String = VelorUtils.formatNumber(n)

