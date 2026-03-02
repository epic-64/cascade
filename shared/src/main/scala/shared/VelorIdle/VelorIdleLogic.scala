package shared.VelorIdle

import scala.util.Random

/** Pure game logic for Velor Idle - no side effects */
object VelorIdleLogic:

  // ============================================================================
  // Game Tick
  // ============================================================================

  /** Advance game state by elapsed time. Returns updated game and any events that occurred. */
  def tick(game: VelorIdleGame, currentTime: Long, random: Random = Random): (VelorIdleGame, Vector[GameEvent]) =
    val elapsed = (currentTime - game.lastTickTime).max(0)
    val elapsedSeconds = elapsed / 1000.0

    game.activeAction match
      case ActiveAction.Idle =>
        (game.copy(lastTickTime = currentTime), Vector.empty)

      case ActiveAction.Gathering(action) =>
        processGatheringTick(game, action, elapsedSeconds, currentTime, random)

  private def processGatheringTick(
    game: VelorIdleGame,
    action: GatheringAction,
    elapsedSeconds: Double,
    currentTime: Long,
    random: Random
  ): (VelorIdleGame, Vector[GameEvent]) =
    game.currentSkill match
      case None => (game.copy(lastTickTime = currentTime), Vector.empty)
      case Some(skill) =>
        val skillState = game.skills.getOrElse(skill, SkillState.initial)

        // Calculate effective action time (reduced by efficiency perks)
        val efficiencyBonus = calculateEfficiencyBonus(skillState.level)
        val effectiveTime = action.timeSeconds * (1.0 - efficiencyBonus)

        // Calculate new progress
        val progressPerSecond = 1.0 / effectiveTime
        val newProgress = game.actionProgress + (elapsedSeconds * progressPerSecond)

        if newProgress >= 1.0 then
          // Action complete - grant rewards
          val (updatedGame, events) = completeGatheringAction(game, action, skill, skillState, random)

          // Check if we have overflow progress for another action
          val overflowProgress = newProgress - 1.0
          val finalProgress = if overflowProgress > 0 && overflowProgress < 1.0 then overflowProgress else 0.0

          (updatedGame.copy(actionProgress = finalProgress, lastTickTime = currentTime), events)
        else
          (game.copy(actionProgress = newProgress, lastTickTime = currentTime), Vector.empty)

  private def completeGatheringAction(
    game: VelorIdleGame,
    action: GatheringAction,
    skill: Skill,
    skillState: SkillState,
    random: Random
  ): (VelorIdleGame, Vector[GameEvent]) =
    var events = Vector.empty[GameEvent]
    var updatedGame = game

    // Grant XP
    val xpGain = action.xpGain
    val newXp = skillState.xp + xpGain
    val oldLevel = skillState.level
    val newLevel = SkillState.levelFromXp(newXp)
    val newSkillState = skillState.copy(xp = newXp, level = newLevel)
    updatedGame = updatedGame.copy(skills = updatedGame.skills.updated(skill, newSkillState))
    events = events :+ GameEvent.XpGained(skill, xpGain)

    if newLevel > oldLevel then
      events = events :+ GameEvent.LevelUp(skill, newLevel)

    // Grant primary output
    val yieldBonus = calculateYieldBonus(skillState.level)
    val baseCount = 1
    val bonusCount = if random.nextDouble() < yieldBonus then 1 else 0
    val doubleChance = calculateDoubleChance(skillState.level, isGathering = true)
    val doubleCount = if random.nextDouble() < doubleChance then baseCount + bonusCount else 0
    val totalCount = baseCount + bonusCount + doubleCount

    val (inv1, overflow1) = updatedGame.inventory.addItem(action.output, totalCount)
    updatedGame = updatedGame.copy(inventory = inv1)
    events = events :+ GameEvent.ItemGained(action.output, totalCount)

    if overflow1 > 0 then
      events = events :+ GameEvent.InventoryFull

    // Check for rare output
    action.rareOutput.foreach { case (rareItem, chance) =>
      if random.nextDouble() < chance then
        val (inv2, overflow2) = updatedGame.inventory.addItem(rareItem, 1)
        updatedGame = updatedGame.copy(inventory = inv2)
        events = events :+ GameEvent.ItemGained(rareItem, 1)
        events = events :+ GameEvent.RareDrop(rareItem)
    }

    (updatedGame, events)

  // ============================================================================
  // Perk Calculations
  // ============================================================================

  /** Efficiency bonus (reduces action time) - gathering and processing */
  def calculateEfficiencyBonus(level: Int): Double =
    val tier1 = if level >= 10 then 0.05 else 0.0
    val tier2 = if level >= 40 then 0.05 else 0.0  // +5% more = 10% total
    val tier3 = if level >= 70 then 0.05 else 0.0  // +5% more = 15% total
    tier1 + tier2 + tier3

  /** Yield bonus (chance for extra resource) - gathering only */
  def calculateYieldBonus(level: Int): Double =
    val tier1 = if level >= 20 then 0.10 else 0.0
    val tier2 = if level >= 50 then 0.10 else 0.0  // +10% more = 20% total
    val tier3 = if level >= 80 then 0.10 else 0.0  // +10% more = 30% total
    tier1 + tier2 + tier3

  /** Double chance - gathering has mastery, processing has double perk */
  def calculateDoubleChance(level: Int, isGathering: Boolean): Double =
    if isGathering then
      // Mastery perk for gathering
      val tier1 = if level >= 30 then 0.05 else 0.0
      val tier2 = if level >= 60 then 0.05 else 0.0  // +5% more = 10% total
      val tier3 = if level >= 90 then 0.05 else 0.0  // +5% more = 15% total
      tier1 + tier2 + tier3
    else
      // Double perk for processing (also gets base scaling)
      val baseBonus = level * 0.005 // 0.5% per level, up to ~50% at 99
      val tier1 = if level >= 20 then 0.05 else 0.0
      val tier2 = if level >= 50 then 0.05 else 0.0  // +5% more = 10% total
      val tier3 = if level >= 80 then 0.05 else 0.0  // +5% more = 15% total
      baseBonus + tier1 + tier2 + tier3

  /** Recycle chance (keep inputs) - processing only */
  def calculateRecycleChance(level: Int): Double =
    val baseBonus = level * 0.003 // 0.3% per level, up to ~30% at 99
    val tier1 = if level >= 30 then 0.05 else 0.0
    val tier2 = if level >= 60 then 0.05 else 0.0  // +5% more = 10% total
    val tier3 = if level >= 90 then 0.05 else 0.0  // +5% more = 15% total
    baseBonus + tier1 + tier2 + tier3

  // ============================================================================
  // Player Actions
  // ============================================================================

  /** Select a skill to view/train */
  def selectSkill(game: VelorIdleGame, skill: Skill): VelorIdleGame =
    game.copy(
      currentSkill = Some(skill),
      activeAction = ActiveAction.Idle,
      actionProgress = 0.0
    )

  /** Start a gathering action */
  def startGathering(game: VelorIdleGame, actionId: String): Either[String, VelorIdleGame] =
    game.currentSkill match
      case None => Left("No skill selected")
      case Some(skill) if !Skill.isGathering(skill) => Left("Not a gathering skill")
      case Some(skill) =>
        val actions = GatheringActions.forSkill(skill)
        actions.find(_.id == actionId) match
          case None => Left("Action not found")
          case Some(action) =>
            val skillState = game.skills.getOrElse(skill, SkillState.initial)
            if skillState.level < action.levelRequired then
              Left(s"Requires ${Skill.displayName(skill)} level ${action.levelRequired}")
            else
              Right(game.copy(
                activeAction = ActiveAction.Gathering(action),
                actionProgress = 0.0
              ))

  /** Stop the current action */
  def stopAction(game: VelorIdleGame): VelorIdleGame =
    game.copy(
      activeAction = ActiveAction.Idle,
      actionProgress = 0.0
    )

  /** Sell items from inventory */
  def sellItem(game: VelorIdleGame, item: Item, count: Long): Either[String, VelorIdleGame] =
    if count <= 0 then Left("Invalid count")
    else
      val available = game.inventory.getCount(item)
      if available <= 0 then Left("Item not in inventory")
      else
        val toSell = count.min(available)
        val goldGain = Item.sellValue(item) * toSell
        val (newInventory, removed) = game.inventory.removeItem(item, toSell)
        Right(game.copy(
          inventory = newInventory,
          gold = game.gold + goldGain
        ))

  /** Sell all of a specific item */
  def sellAll(game: VelorIdleGame, item: Item): Either[String, VelorIdleGame] =
    val count = game.inventory.getCount(item)
    if count <= 0 then Left("Item not in inventory")
    else sellItem(game, item, count)

  /** Upgrade inventory slots */
  def upgradeInventory(game: VelorIdleGame, targetSlots: Int): Either[String, VelorIdleGame] =
    Inventory.upgradeCost(game.inventory.maxSlots, targetSlots) match
      case None => Left("Invalid upgrade")
      case Some(cost) =>
        if game.gold < cost then Left(s"Need $cost gold")
        else
          val newSlots = game.inventory.slots ++ Vector.fill(targetSlots - game.inventory.maxSlots)(None)
          Right(game.copy(
            gold = game.gold - cost,
            inventory = game.inventory.copy(slots = newSlots, maxSlots = targetSlots)
          ))

// ============================================================================
// Game Events (for UI feedback)
// ============================================================================

enum GameEvent:
  case XpGained(skill: Skill, amount: Int)
  case LevelUp(skill: Skill, newLevel: Int)
  case ItemGained(item: Item, count: Long)
  case RareDrop(item: Item)
  case InventoryFull
  case GoldGained(amount: Long)

