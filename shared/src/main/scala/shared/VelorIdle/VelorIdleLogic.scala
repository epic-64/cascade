package shared.VelorIdle

import scala.util.Random
import scala.util.chaining.*

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

      case ActiveAction.Gathering(skill, action) =>
        processGatheringTick(game, skill, action, elapsedSeconds, currentTime, random)

      case ActiveAction.Processing(skill, action) =>
        processProcessingTick(game, skill, action, elapsedSeconds, currentTime, random)

  private def processGatheringTick(
    game: VelorIdleGame,
    skill: Skill,
    action: GatheringAction,
    elapsedSeconds: Double,
    currentTime: Long,
    random: Random
  ): (VelorIdleGame, Vector[GameEvent]) =
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

  /** Accumulator for game state updates - allows chaining operations immutably */
  private case class GameUpdate(game: VelorIdleGame, events: Vector[GameEvent]):
    def addEvent(event: GameEvent): GameUpdate = copy(events = events :+ event)
    def addEvents(newEvents: Vector[GameEvent]): GameUpdate = copy(events = events ++ newEvents)
    def mapGame(f: VelorIdleGame => VelorIdleGame): GameUpdate = copy(game = f(game))

  private def completeGatheringAction(
    game: VelorIdleGame,
    action: GatheringAction,
    skill: Skill,
    skillState: SkillState,
    random: Random
  ): (VelorIdleGame, Vector[GameEvent]) =
    val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)
    val result = GameUpdate(game, Vector.empty)
      .pipe(grantXp(skill, skillState, action.xpGain))
      .pipe(grantActionXp(action.id, actionState, action.xpGain))
      .pipe(grantGatheredItems(action, skillState, actionState, random))
      .pipe(checkRareDrop(action.rareOutput, random))
    
    (result.game, result.events)

  private def grantXp(skill: Skill, skillState: SkillState, xpGain: Int)(update: GameUpdate): GameUpdate =
    val newXp = skillState.xp + xpGain
    val oldLevel = skillState.level
    val newLevel = SkillState.levelFromXp(newXp)
    val newSkillState = skillState.copy(xp = newXp, level = newLevel)
    
    val withXp = update
      .mapGame(g => g.copy(skills = g.skills.updated(skill, newSkillState)))
      .addEvent(GameEvent.XpGained(skill, xpGain))
    
    if newLevel > oldLevel then withXp.addEvent(GameEvent.LevelUp(skill, newLevel))
    else withXp

  private def grantActionXp(actionId: String, actionState: ActionState, xpGain: Int)(update: GameUpdate): GameUpdate =
    val newXp = actionState.xp + xpGain
    val oldLevel = actionState.level
    val newLevel = ActionState.levelFromXp(newXp)
    val newActionState = actionState.copy(xp = newXp, level = newLevel)
    
    val withXp = update
      .mapGame(g => g.copy(actionLevels = g.actionLevels.updated(actionId, newActionState)))
      .addEvent(GameEvent.ActionXpGained(actionId, xpGain))
    
    if newLevel > oldLevel then withXp.addEvent(GameEvent.ActionLevelUp(actionId, newLevel))
    else withXp

  private def grantGatheredItems(action: GatheringAction, skillState: SkillState, actionState: ActionState, random: Random)(update: GameUpdate): GameUpdate =
    val yieldBonus = calculateYieldBonus(actionState.level)
    val baseCount = 1
    val bonusCount = if random.nextDouble() < yieldBonus then 1 else 0
    val doubleChance = calculateDoubleChance(skillState.level, isGathering = true)
    val doubleCount = if random.nextDouble() < doubleChance then baseCount + bonusCount else 0
    val totalCount = baseCount + bonusCount + doubleCount

    val (newInv, overflow) = update.game.inventory.addItem(action.output, totalCount)
    val withItems = update
      .mapGame(_.copy(inventory = newInv))
      .addEvent(GameEvent.ItemGained(action.output, totalCount))
    
    if overflow > 0 then withItems.addEvent(GameEvent.InventoryFull)
    else withItems

  private def checkRareDrop(rareOutput: Option[(Item, Double)], random: Random)(update: GameUpdate): GameUpdate =
    rareOutput match
      case Some((rareItem, chance)) if random.nextDouble() < chance =>
        val (newInv, _) = update.game.inventory.addItem(rareItem, 1)
        update
          .mapGame(_.copy(inventory = newInv))
          .addEvent(GameEvent.ItemGained(rareItem, 1))
          .addEvent(GameEvent.RareDrop(rareItem))
      case _ => update


  // ============================================================================
  // Processing Tick
  // ============================================================================

  private def processProcessingTick(
    game: VelorIdleGame,
    skill: Skill,
    action: ProcessingAction,
    elapsedSeconds: Double,
    currentTime: Long,
    random: Random
  ): (VelorIdleGame, Vector[GameEvent]) =
    val skillState = game.skills.getOrElse(skill, SkillState.initial)

    // Check if we still have ingredients
    if !canProcess(game, action) then
      // Stop action - out of materials
      (game.copy(
        activeAction = ActiveAction.Idle,
        actionProgress = 0.0,
        lastTickTime = currentTime
      ), Vector(GameEvent.OutOfMaterials))

    else
      // Calculate effective action time
      val efficiencyBonus = calculateEfficiencyBonus(skillState.level)
      val effectiveTime = action.timeSeconds * (1.0 - efficiencyBonus)

      val progressPerSecond = 1.0 / effectiveTime
      val newProgress = game.actionProgress + (elapsedSeconds * progressPerSecond)

      if newProgress >= 1.0 then
        val (updatedGame, events) = completeProcessingAction(game, action, skill, skillState, random)
        val overflowProgress = newProgress - 1.0
        val finalProgress = if overflowProgress > 0 && overflowProgress < 1.0 then overflowProgress else 0.0
        (updatedGame.copy(actionProgress = finalProgress, lastTickTime = currentTime), events)
      else
        (game.copy(actionProgress = newProgress, lastTickTime = currentTime), Vector.empty)

  private def completeProcessingAction(
    game: VelorIdleGame,
    action: ProcessingAction,
    skill: Skill,
    skillState: SkillState,
    random: Random
  ): (VelorIdleGame, Vector[GameEvent]) =
    // Consume inputs, checking for recycle
    val recycleChance = calculateRecycleChance(skillState.level)
    val (gameAfterConsume, allConsumed) = consumeInputs(game, action.inputs, recycleChance, random)
    
    if !allConsumed then
      (gameAfterConsume, Vector(GameEvent.OutOfMaterials))
    else
      val result = GameUpdate(gameAfterConsume, Vector.empty)
        .pipe(grantXp(skill, skillState, action.xpGain))
        .pipe(processOutput(action, skillState, random))
      
      (result.game, result.events)

  private def consumeInputs(
    game: VelorIdleGame,
    inputs: Vector[(Item, Int)],
    recycleChance: Double,
    random: Random
  ): (VelorIdleGame, Boolean) =
    inputs.foldLeft((game, true)) { case ((currentGame, success), (item, count)) =>
      if !success then (currentGame, false)
      else
        val recycled = random.nextDouble() < recycleChance
        if recycled then (currentGame, true) // Keep materials
        else
          val (newInv, removed) = currentGame.inventory.removeItem(item, count)
          (currentGame.copy(inventory = newInv), removed >= count)
    }

  private def processOutput(action: ProcessingAction, skillState: SkillState, random: Random)(update: GameUpdate): GameUpdate =
    // Check for burn (cooking)
    val didBurn = action.burnChance.exists { baseBurn =>
      val levelDiff = skillState.level - action.levelRequired
      val effectiveBurn = (baseBurn - levelDiff * 0.02).max(0.05)
      random.nextDouble() < effectiveBurn
    }

    if didBurn then
      action.burnOutput match
        case Some(burnt) =>
          val (newInv, _) = update.game.inventory.addItem(burnt, 1)
          update
            .mapGame(_.copy(inventory = newInv))
            .addEvent(GameEvent.ItemGained(burnt, 1))
            .addEvent(GameEvent.ActionFailed("Burned!"))
        case None => update
    else
      // Success - check for double chance
      val doubleChance = calculateDoubleChance(skillState.level, isGathering = false)
      val baseCount = action.outputCount
      val doubleCount = if random.nextDouble() < doubleChance then baseCount else 0
      val totalCount = baseCount + doubleCount

      val (newInv, overflow) = update.game.inventory.addItem(action.output, totalCount)
      val withItems = update
        .mapGame(_.copy(inventory = newInv))
        .addEvent(GameEvent.ItemGained(action.output, totalCount))
      
      if overflow > 0 then withItems.addEvent(GameEvent.InventoryFull)
      else withItems


  /** Check if player has all required inputs for a processing action */
  def canProcess(game: VelorIdleGame, action: ProcessingAction): Boolean =
    action.inputs.forall { case (item, count) =>
      game.inventory.getCount(item) >= count
    }

  // ============================================================================
  // Perk Calculations
  // ============================================================================

  // ============================================================================
  // Perk System - Data-Driven Configuration
  // ============================================================================

  /** A perk tier that unlocks at a certain level */
  private case class PerkTier(levelRequired: Int, bonus: Double)

  /** Calculate total bonus from tier-based perks */
  private def calculateTieredBonus(level: Int, tiers: Vector[PerkTier]): Double =
    tiers.filter(_.levelRequired <= level).map(_.bonus).sum

  /** Calculate bonus with per-level scaling plus tiers */
  private def calculateScaledBonus(level: Int, perLevelBonus: Double, tiers: Vector[PerkTier]): Double =
    level * perLevelBonus + calculateTieredBonus(level, tiers)

  // Perk configurations - easy to adjust or extend
  private val efficiencyTiers = Vector(
    PerkTier(10, 0.05),  // 5% at level 10
    PerkTier(40, 0.05),  // +5% at level 40 = 10% total
    PerkTier(70, 0.05)   // +5% at level 70 = 15% total
  )

  private val yieldTiers = Vector(
    PerkTier(20, 0.10),  // 10% at level 20
    PerkTier(50, 0.10),  // +10% at level 50 = 20% total
    PerkTier(80, 0.10)   // +10% at level 80 = 30% total
  )

  private val gatheringMasteryTiers = Vector(
    PerkTier(30, 0.05),  // 5% at level 30
    PerkTier(60, 0.05),  // +5% at level 60 = 10% total
    PerkTier(90, 0.05)   // +5% at level 90 = 15% total
  )

  private val processingDoubleTiers = Vector(
    PerkTier(20, 0.05),  // 5% at level 20
    PerkTier(50, 0.05),  // +5% at level 50 = 10% total
    PerkTier(80, 0.05)   // +5% at level 80 = 15% total
  )

  private val recycleTiers = Vector(
    PerkTier(30, 0.05),  // 5% at level 30
    PerkTier(60, 0.05),  // +5% at level 60 = 10% total
    PerkTier(90, 0.05)   // +5% at level 90 = 15% total
  )

  /** Efficiency bonus (reduces action time) - gathering and processing */
  def calculateEfficiencyBonus(level: Int): Double =
    calculateTieredBonus(level, efficiencyTiers)

  /** Yield bonus (chance for extra resource) - gathering only */
  def calculateYieldBonus(level: Int): Double =
    calculateTieredBonus(level, yieldTiers)

  /** Double chance - gathering has mastery, processing has double perk + scaling */
  def calculateDoubleChance(level: Int, isGathering: Boolean): Double =
    if isGathering then
      calculateTieredBonus(level, gatheringMasteryTiers)
    else
      // Processing: 0.5% per level + tier bonuses
      calculateScaledBonus(level, 0.005, processingDoubleTiers)

  /** Recycle chance (keep inputs) - processing only */
  def calculateRecycleChance(level: Int): Double =
    // 0.3% per level + tier bonuses
    calculateScaledBonus(level, 0.003, recycleTiers)

  // ============================================================================
  // Player Actions
  // ============================================================================

  /** Select a skill to view/train - does not affect the currently running action */
  def selectSkill(game: VelorIdleGame, skill: Skill): VelorIdleGame =
    game.copy(currentSkill = Some(skill))

  /** Start an action - automatically dispatches to gathering or processing based on current skill */
  def startAction(game: VelorIdleGame, actionId: String): Either[String, VelorIdleGame] =
    game.currentSkill match
      case None => Left("No skill selected")
      case Some(skill) if Skill.isGathering(skill) => startGathering(game, actionId)
      case Some(skill) if Skill.isProcessing(skill) => startProcessing(game, actionId)
      case Some(skill) => Left(s"${Skill.displayName(skill)} actions not yet implemented")

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
                activeAction = ActiveAction.Gathering(skill, action),
                actionProgress = 0.0
              ))

  /** Start a processing action */
  def startProcessing(game: VelorIdleGame, actionId: String): Either[String, VelorIdleGame] =
    game.currentSkill match
      case None => Left("No skill selected")
      case Some(skill) if !Skill.isProcessing(skill) => Left("Not a processing skill")
      case Some(skill) =>
        val actions = ProcessingActions.forSkill(skill)
        actions.find(_.id == actionId) match
          case None => Left("Action not found")
          case Some(action) =>
            val skillState = game.skills.getOrElse(skill, SkillState.initial)
            if skillState.level < action.levelRequired then
              Left(s"Requires ${Skill.displayName(skill)} level ${action.levelRequired}")
            else if !canProcess(game, action) then
              Left("Missing required materials")
            else
              Right(game.copy(
                activeAction = ActiveAction.Processing(skill, action),
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
  case ActionXpGained(actionId: String, amount: Int)
  case ActionLevelUp(actionId: String, newLevel: Int)
  case ItemGained(item: Item, count: Long)
  case RareDrop(item: Item)
  case InventoryFull
  case GoldGained(amount: Long)
  case OutOfMaterials
  case ActionFailed(reason: String)

