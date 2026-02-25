package client.components.laminar

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*

/** Laminar-based skill tree modal for TileKingdom.
  *
  * Displays the skill tree with branches and allows unlocking/switching/refunding skills.
  */
object SkillTree:

  /** Callbacks for skill actions */
  case class Actions(
    onUnlock: Skill => Unit,
    onSwitch: Skill => Unit,
    onRefund: Skill => Unit,
    onClose: () => Unit
  )

  /** Format number for display */
  private def formatNumber(n: Double): String =
    if n >= 1_000_000 then f"${n / 1_000_000}%.1fM"
    else if n >= 1_000 then f"${n / 1_000}%.1fk"
    else if n == n.toInt then n.toInt.toString
    else f"$n%.1f"

  /** The skill tree modal element */
  def apply(actions: Actions): HtmlElement =
    import TileKingdomState.*

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
            child.text <-- skillPointsSignal.map(pts => s"⭐ $pts skill points")
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
          children <-- hasSailedSignal.combineWith(gameSignal).map:
            case (false, _) => List(renderLockedMessage())
            case (true, game) => renderBranches(game, actions)
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
  private def renderBranches(game: TileKingdomGame, actions: Actions): List[HtmlElement] =
    Skill.allBranches.map(branchName => renderBranch(branchName, game, actions)).toList

  /** Render a single skill branch */
  private def renderBranch(branchName: String, game: TileKingdomGame, actions: Actions): HtmlElement =
    val skills = Skill.branchSkills(branchName)
    val skillsByCost = skills.groupBy(Skill.cost).toList.sortBy(_._1)

    div(
      cls := "skill-branch",
      div(
        cls := "skill-branch-header",
        span(cls := "branch-emoji", Skill.branchEmoji(branchName)),
        span(cls := "branch-name", branchName)
      ),
      div(
        cls := "skill-branch-nodes",
        skillsByCost.flatMap: (cost, skillsAtLevel) =>
          val isDualTrack = skillsAtLevel.exists(s => Skill.mutuallyExclusive(s).isDefined)
          if isDualTrack then
            List(renderDualTrack(skillsAtLevel, game, actions))
          else
            skillsAtLevel.map(skill => renderSkillNode(skill, game, actions)).toList
      )
    )

  /** Render a dual track (mutually exclusive skills) with OR separator */
  private def renderDualTrack(skills: Seq[Skill], game: TileKingdomGame, actions: Actions): HtmlElement =
    div(
      cls := "skill-dual-track",
      skills.zipWithIndex.flatMap: (skill, idx) =>
        val separator = if idx > 0 then Some(div(cls := "skill-or-separator", "OR")) else None
        separator.toList :+ renderSkillNode(skill, game, actions)
    )

  /** Render a single skill node */
  private def renderSkillNode(skill: Skill, game: TileKingdomGame, actions: Actions): HtmlElement =
    val isUnlocked = game.hasSkill(skill)
    val canUnlock = game.canUnlockSkill(skill)
    val isExcluded = Skill.mutuallyExclusive(skill).exists(game.hasSkill)
    val canSwitch = TileKingdomLogic.canSwitchSkill(game, skill)
    val canRefund = game.canRefundSkill(skill)
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
      renderSkillActions(skill, game, actions, isUnlocked, canUnlock, isExcluded, canSwitch, canRefund, goldCost)
    )

  /** Render the action buttons/status for a skill node */
  private def renderSkillActions(
      skill: Skill,
      game: TileKingdomGame,
      actions: Actions,
      isUnlocked: Boolean,
      canUnlock: Boolean,
      isExcluded: Boolean,
      canSwitch: Boolean,
      canRefund: Boolean,
      goldCost: Int
  ): HtmlElement =
    if isUnlocked && game.hasSailed then
      val refundReason =
        if !game.isFreshAbdication then Some("Abdicate first")
        else if game.gold < goldCost then Some(s"Need ${formatNumber(goldCost)} 💰")
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

