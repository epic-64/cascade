package client.components.laminar

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*

/** Laminar-based skill tree modal for TileKingdom.
  *
  * Displays the skill tree with branches and allows unlocking/switching/refunding skills.
  * Uses a dedicated SkillTreeState snapshot to avoid re-rendering on every game tick.
  */
object SkillTree:

  /** Callbacks for skill actions */
  case class Actions(
    onUnlock: Skill => Unit,
    onSwitch: Skill => Unit,
    onRefund: Skill => Unit,
    onClose: () => Unit
  )

  /** Snapshot of only the fields that matter for skill tree rendering.
    * Using .distinct on this avoids re-rendering when unrelated game state changes. */
  private case class SkillTreeState(
    hasSailed: Boolean,
    skillPoints: Int,
    unlockedSkills: Set[Skill],
    gold: Int,
    isFreshAbdication: Boolean
  )

  private object SkillTreeState:
    def from(game: TileKingdomGame): SkillTreeState =
      SkillTreeState(
        hasSailed = game.hasSailed,
        skillPoints = game.skillPoints,
        unlockedSkills = game.unlockedSkills,
        gold = game.gold,
        isFreshAbdication = game.isFreshAbdication
      )

  /** Format number for display */
  private def formatNumber(n: Double): String =
    if n >= 1_000_000 then f"${n / 1_000_000}%.1fM"
    else if n >= 1_000 then f"${n / 1_000}%.1fk"
    else if n == n.toInt then n.toInt.toString
    else f"$n%.1f"

  /** Signal that only emits when skill-relevant state actually changes */
  private val skillTreeStateSignal: Signal[SkillTreeState] =
    TileKingdomState.gameSignal.map(SkillTreeState.from).distinct

  /** The skill tree modal element */
  def apply(actions: Actions): HtmlElement =
    div(
      idAttr := "tile-kingdom-skill-tree-modal",
      cls := "skill-tree-modal",
      div(
        cls := "skill-tree-modal-content",
        // Header
        div(
          cls := "skill-tree-header",
          h3("🌳 Skill Tree"),
          div(
            idAttr := "skill-tree-points",
            cls := "skill-tree-points",
            child.text <-- skillTreeStateSignal.map(s => s"⭐ ${s.skillPoints} skill points")
          ),
          button(
            cls := "skill-tree-close-btn",
            "✕",
            onClick --> { _ => actions.onClose() }
          )
        ),
        // Body
        div(
          idAttr := "skill-tree-body",
          cls := "skill-tree-body",
          children <-- skillTreeStateSignal.map: state =>
            if !state.hasSailed then List(renderLockedMessage())
            else renderBranches(state, actions)
        )
      )
    )

  /** Render the locked message when player hasn't sailed yet */
  private def renderLockedMessage(): HtmlElement =
    div(
      cls := "skill-tree-locked",
      div(cls := "locked-icon", "🔒"),
      div(cls := "locked-text", "Sail at least once to unlock the skill tree"),
      div(cls := "locked-hint", "Reach 25 tiles and click ⛵ Sail")
    )

  /** Render all skill branches */
  private def renderBranches(state: SkillTreeState, actions: Actions): List[HtmlElement] =
    Skill.allBranches.map(branchName => renderBranch(branchName, state, actions)).toList

  /** Render a single skill branch */
  private def renderBranch(branchName: String, state: SkillTreeState, actions: Actions): HtmlElement =
    val skills = Skill.branchSkills(branchName)
    val skillsByTier = skills.groupBy(Skill.tier).toList.sortBy(_._1)

    div(
      cls := "skill-branch",
      div(
        cls := "skill-branch-header",
        span(cls := "branch-emoji", Skill.branchEmoji(branchName)),
        span(cls := "branch-name", branchName)
      ),
      div(
        cls := "skill-branch-nodes",
        skillsByTier.flatMap: (tier, skillsAtTier) =>
          val isDualTrack = skillsAtTier.exists(s => Skill.mutuallyExclusive(s).isDefined)
          if isDualTrack then
            List(renderDualTrack(skillsAtTier, state, actions))
          else
            skillsAtTier.map(skill => renderSkillNode(skill, state, actions)).toList
      )
    )

  /** Render a dual track (mutually exclusive skills) with OR separator */
  private def renderDualTrack(skills: Seq[Skill], state: SkillTreeState, actions: Actions): HtmlElement =
    div(
      cls := "skill-dual-track",
      skills.zipWithIndex.flatMap: (skill, idx) =>
        val separator = if idx > 0 then Some(div(cls := "skill-or-separator", "OR")) else None
        separator.toList :+ renderSkillNode(skill, state, actions)
    )

  /** Render a single skill node */
  private def renderSkillNode(skill: Skill, state: SkillTreeState, actions: Actions): HtmlElement =
    val isUnlocked = state.unlockedSkills.contains(skill)
    val canUnlock = canUnlockFromState(state, skill)
    val isExcluded = Skill.mutuallyExclusive(skill).exists(state.unlockedSkills.contains)
    val canSwitch = canSwitchFromState(state, skill)
    val canRefund = canRefundFromState(state, skill)
    val cost = Skill.cost(skill)
    val description = Skill.description(skill)
    val goldCost = cost * TileKingdomLogic.SkillRefundGoldCost

    val nodeCls =
      if isUnlocked then "skill-node unlocked"
      else if isExcluded && canSwitch then "skill-node switchable"
      else if isExcluded then "skill-node excluded"
      else if canUnlock then "skill-node available"
      else "skill-node locked"

    div(
      cls := nodeCls,
      div(cls := "skill-node-cost", s"${cost}⭐"),
      div(cls := "skill-node-desc", description),
      renderSkillActions(skill, state, actions, isUnlocked, canUnlock, isExcluded, canSwitch, canRefund, goldCost)
    )

  /** Check if a skill can be unlocked from the snapshot state */
  private def canUnlockFromState(state: SkillTreeState, skill: Skill): Boolean =
    if state.unlockedSkills.contains(skill) then false
    else if state.skillPoints < Skill.cost(skill) then false
    else if Skill.mutuallyExclusive(skill).exists(state.unlockedSkills.contains) then false
    else
      val standardPrereqMet = Skill.prerequisite(skill).forall(state.unlockedSkills.contains)
      val alternativePrereqMet = Skill.alternativePrerequisites(skill) match
        case Some(alternatives) => alternatives.exists(state.unlockedSkills.contains)
        case None => true
      standardPrereqMet && alternativePrereqMet

  /** Check if a skill can be switched to from the snapshot state */
  private def canSwitchFromState(state: SkillTreeState, toSkill: Skill): Boolean =
    if !state.hasSailed then false
    else if !state.isFreshAbdication then false
    else if state.unlockedSkills.contains(toSkill) then false
    else Skill.mutuallyExclusive(toSkill).exists(state.unlockedSkills.contains)

  /** Check if a skill can be refunded from the snapshot state */
  private def canRefundFromState(state: SkillTreeState, skill: Skill): Boolean =
    if !state.hasSailed then false
    else if !state.isFreshAbdication then false
    else if !state.unlockedSkills.contains(skill) then false
    else if state.gold < Skill.cost(skill) * TileKingdomLogic.SkillRefundGoldCost then false
    else
      // Check no other unlocked skill has this skill as a prerequisite
      val dependents = state.unlockedSkills.filter: other =>
        Skill.prerequisite(other).contains(skill) ||
          Skill.mutuallyExclusive(other).flatMap(Skill.prerequisite).contains(skill)
      dependents.isEmpty

  /** Render the action buttons/status for a skill node */
  private def renderSkillActions(
      skill: Skill,
      state: SkillTreeState,
      actions: Actions,
      isUnlocked: Boolean,
      canUnlock: Boolean,
      isExcluded: Boolean,
      canSwitch: Boolean,
      canRefund: Boolean,
      goldCost: Int
  ): HtmlElement =
    if isUnlocked && state.hasSailed then
      val refundReason =
        if !state.isFreshAbdication then Some("Abdicate first")
        else if state.gold < goldCost then Some(s"Need ${formatNumber(goldCost)} 💰")
        else if !canRefund then Some("Has dependents")
        else None

      div(
        cls := "skill-node-actions",
        div(cls := "skill-node-status", "✓ Unlocked"),
        refundReason match
          case None =>
            button(
              cls := "skill-node-btn refund-btn",
              s"Refund (${formatNumber(goldCost)} 💰)",
              onClick.stopPropagation --> { _ => actions.onRefund(skill) }
            )
          case Some(reason) =>
            button(
              cls := "skill-node-btn refund-btn disabled",
              disabled := true,
              title := reason,
              s"Refund (${formatNumber(goldCost)} 💰)"
            )
      )
    else if isUnlocked then
      div(cls := "skill-node-status", "✓ Unlocked")
    else if isExcluded && canSwitch then
      button(
        cls := "skill-node-btn switch-btn",
        "Switch",
        onClick.stopPropagation --> { _ => actions.onSwitch(skill) }
      )
    else if isExcluded then
      div(cls := "skill-node-status", "✗ Excluded")
    else if canUnlock then
      button(
        cls := "skill-node-btn",
        "Unlock",
        onClick.stopPropagation --> { _ => actions.onUnlock(skill) }
      )
    else
      div(cls := "skill-node-status", "🔒")

