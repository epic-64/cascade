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

      case ActiveAction.Thieving(action) =>
        processThievingTick(game, action, elapsedSeconds, currentTime, random)

      case ActiveAction.Adventure =>
        // Adventure combat is processed by AdventureCombat.tick
        AdventureCombat.tick(game, currentTime, random)

      case ActiveAction.Rest =>
        // Rest action - regenerate HP and Mana
        processRestTick(game, elapsedSeconds, currentTime)

  /** Process rest tick - regenerate HP and Mana for adventure */
  private def processRestTick(
    game: VelorIdleGame,
    elapsedSeconds: Double,
    currentTime: Long
  ): (VelorIdleGame, Vector[GameEvent]) =
    val advState = game.adventureState
    val maxHp = advState.maxHp
    val maxMana = advState.maxMana

    // Use round to avoid losing small increments due to truncation
    val hpRegen = (AdventureState.HpRegenPerSecond * elapsedSeconds).round.toInt
    val manaRegen = (AdventureState.ManaRegenPerSecond * elapsedSeconds).round.toInt

    val newHp = (advState.currentHp + hpRegen).min(maxHp)
    val newMana = (advState.currentMana + manaRegen).min(maxMana)

    val newAdvState = advState.copy(currentHp = newHp, currentMana = newMana)
    
    // Auto-switch to IDLE when fully recovered
    val isFullyRecovered = newHp >= maxHp && newMana >= maxMana
    val newActiveAction = if isFullyRecovered then ActiveAction.Idle else game.activeAction
    
    val newGame = game.copy(
      adventureState = newAdvState,
      activeAction = newActiveAction,
      lastTickTime = currentTime
    )
    
    (newGame, Vector.empty)

  private def processGatheringTick(
    game: VelorIdleGame,
    skill: Skill,
    action: GatheringAction,
    elapsedSeconds: Double,
    currentTime: Long,
    random: Random
  ): (VelorIdleGame, Vector[GameEvent]) =
    val skillState = game.skills.getOrElse(skill, SkillState.initial)
    val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)

    // Calculate effective action time (reduced by efficiency perks - now action level based)
    val efficiencyBonus = calculateEfficiencyBonus(actionState.level)
    val potionSpeedBonus = game.potionSlots.speedBonusFor(skill)
    val tabletSpeedBonus = game.tabletSlots.speedBonusFor(skill)
    val effectiveTime = action.timeSeconds * (1.0 - efficiencyBonus) * (1.0 - potionSpeedBonus) * (1.0 - tabletSpeedBonus)

    // Calculate new progress
    val progressPerSecond = 1.0 / effectiveTime
    val newProgress = game.actionProgress + (elapsedSeconds * progressPerSecond)

    if newProgress >= 1.0 then
      // Action complete - grant rewards
      val (updatedGame, events) = completeGatheringAction(game, action, skill, skillState, random)

      // Consume potion charge
      val gameWithPotionConsumed = updatedGame.copy(
        potionSlots = updatedGame.potionSlots.consumeAction
      )
      val potionEvents = checkPotionExpired(game.potionSlots, gameWithPotionConsumed.potionSlots)

      // Consume tablet charges
      val (gameWithTabletsConsumed, tabletEvents) = consumeTablets(gameWithPotionConsumed)

      // Check if we have overflow progress for another action
      val overflowProgress = newProgress - 1.0
      val finalProgress = if overflowProgress > 0 && overflowProgress < 1.0 then overflowProgress else 0.0

      (gameWithTabletsConsumed.copy(actionProgress = finalProgress, lastTickTime = currentTime), events ++ potionEvents ++ tabletEvents)
    else
      (game.copy(actionProgress = newProgress, lastTickTime = currentTime), Vector.empty)

  /** Check if potion expired and generate event if so */
  private def checkPotionExpired(before: PotionSlots, after: PotionSlots): Vector[GameEvent] =
    (before.activePotion, after.activePotion) match
      case (Some(active), None) => Vector(GameEvent.PotionExpired(active.potion))
      case _ => Vector.empty

  /** Consume tablet charges and generate events for tablets that ran out */
  private def consumeTablets(game: VelorIdleGame): (VelorIdleGame, Vector[GameEvent]) =
    val before = game.tabletSlots
    val after = before.consumeAction

    val events = Vector(
      (before.slot1, after.slot1, 1),
      (before.slot2, after.slot2, 2)
    ).collect:
      case (Some(tablet), None, slot) => GameEvent.TabletConsumed(tablet.item, slot)

    (game.copy(tabletSlots = after), events)

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
      .pipe(grantGatheredItems(action, skill, skillState, actionState, random))
      .pipe(checkRareDrop(action.rareOutput, skillState.level, random))

    (result.game, result.events)

  private def grantXp(skill: Skill, skillState: SkillState, baseXpGain: Int)(update: GameUpdate): GameUpdate =
    // Apply potion bonus
    val potionBonus = update.game.potionSlots.xpBonusFor(skill)
    val xpGain = (baseXpGain * (1.0 + potionBonus)).toInt
    val newXp = skillState.xp + xpGain
    val oldLevel = skillState.level
    val newLevel = SkillState.levelFromXp(newXp)
    val newSkillState = skillState.copy(xp = newXp, level = newLevel)
    
    val withXp = update
      .mapGame(g => g.copy(skills = g.skills.updated(skill, newSkillState)))
      .addEvent(GameEvent.XpGained(skill, xpGain))
    
    if newLevel > oldLevel then
      val withLevelUp = withXp.addEvent(GameEvent.LevelUp(skill, newLevel))
      // Award skill points for Adventure level ups
      if skill == Skill.Adventure then
        val levelsGained = newLevel - oldLevel
        withLevelUp
          .mapGame(g => 
            val newCombatSkillState = SkillTreeLogic.awardPoints(g.adventureState.combatSkillState, levelsGained)
            g.copy(adventureState = g.adventureState.copy(combatSkillState = newCombatSkillState))
          )
          .addEvent(GameEvent.SkillPointsGained(levelsGained))
      else withLevelUp
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

  private def grantGatheredItems(action: GatheringAction, skill: Skill, skillState: SkillState, actionState: ActionState, random: Random)(update: GameUpdate): GameUpdate =
    // Base yield bonus from action level + tablet bonuses
    val baseYieldBonus = calculateYieldBonus(actionState.level)
    val tabletYieldBonus = update.game.tabletSlots.gatheringYieldBonus
    val herbalismBonus = if skill == Skill.Herbalism then update.game.tabletSlots.herbalismYieldBonus else 0.0
    val totalYieldBonus = baseYieldBonus + tabletYieldBonus + herbalismBonus

    val baseCount = 1
    val bonusCount = if random.nextDouble() < totalYieldBonus then 1 else 0

    // Double chance from skill level + tablet bonuses
    val baseDoubleChance = calculateDoubleChance(skillState.level, isGathering = true)
    val tabletDoubleBonus = update.game.tabletSlots.doubleBonusFor(skill)
    val totalDoubleChance = baseDoubleChance + tabletDoubleBonus
    val doubleCount = if random.nextDouble() < totalDoubleChance then baseCount + bonusCount else 0
    val totalCount = baseCount + bonusCount + doubleCount

    val (newInv, overflow) = update.game.inventory.addItem(action.output, totalCount)
    var withItems = update
      .mapGame(_.copy(inventory = newInv))
      .addEvent(GameEvent.ItemGained(action.output, totalCount))
    
    // Grove Keeper synergy: chance to find herbs while woodcutting
    if skill == Skill.Woodcutting && update.game.tabletSlots.hasGroveKeeper then
      if random.nextDouble() < 0.15 then // 15% chance to find a herb
        val herbs = Vector(Item.GuamLeaf, Item.Marrentill, Item.Tarromin)
        val herb = herbs(random.nextInt(herbs.length))
        val (invWithHerb, _) = withItems.game.inventory.addItem(herb, 1)
        withItems = withItems
          .mapGame(_.copy(inventory = invWithHerb))
          .addEvent(GameEvent.ItemGained(herb, 1))

    if overflow > 0 then withItems.addEvent(GameEvent.InventoryFull)
    else withItems

  private def checkRareDrop(rareOutput: Option[(Item, Double)], skillLevel: Int, random: Random)(update: GameUpdate): GameUpdate =
    rareOutput match
      case Some((rareItem, baseChance)) =>
        // Apply secondary chance bonus from job level
        val secondaryBonus = calculateSecondaryChance(skillLevel)
        val totalChance = baseChance + secondaryBonus
        if random.nextDouble() < totalChance then
          val (newInv, _) = update.game.inventory.addItem(rareItem, 1)
          update
            .mapGame(_.copy(inventory = newInv))
            .addEvent(GameEvent.ItemGained(rareItem, 1))
            .addEvent(GameEvent.RareDrop(rareItem))
        else update
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
    val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)

    // Check if we still have ingredients
    if !canProcess(game, action) then
      // Stop action - out of materials
      (game.copy(
        activeAction = ActiveAction.Idle,
        actionProgress = 0.0,
        lastTickTime = currentTime
      ), Vector(GameEvent.OutOfMaterials))

    else
      // Calculate effective action time (now action level based + potion bonus + tablet bonus)
      val efficiencyBonus = calculateEfficiencyBonus(actionState.level)
      val potionSpeedBonus = game.potionSlots.speedBonusFor(skill)
      val tabletSpeedBonus = game.tabletSlots.speedBonusFor(skill)
      val effectiveTime = action.timeSeconds * (1.0 - efficiencyBonus) * (1.0 - potionSpeedBonus) * (1.0 - tabletSpeedBonus)

      val progressPerSecond = 1.0 / effectiveTime
      val newProgress = game.actionProgress + (elapsedSeconds * progressPerSecond)

      if newProgress >= 1.0 then
        val (updatedGame, events) = completeProcessingAction(game, action, skill, skillState, random)
        
        // Consume potion charge
        val gameWithPotionConsumed = updatedGame.copy(
          potionSlots = updatedGame.potionSlots.consumeAction
        )
        val potionEvents = checkPotionExpired(game.potionSlots, gameWithPotionConsumed.potionSlots)
        
        // Consume tablet charges
        val (gameWithTabletsConsumed, tabletEvents) = consumeTablets(gameWithPotionConsumed)

        val overflowProgress = newProgress - 1.0
        val finalProgress = if overflowProgress > 0 && overflowProgress < 1.0 then overflowProgress else 0.0
        (gameWithTabletsConsumed.copy(actionProgress = finalProgress, lastTickTime = currentTime), events ++ potionEvents ++ tabletEvents)
      else
        (game.copy(actionProgress = newProgress, lastTickTime = currentTime), Vector.empty)

  private def completeProcessingAction(
    game: VelorIdleGame,
    action: ProcessingAction,
    skill: Skill,
    skillState: SkillState,
    random: Random
  ): (VelorIdleGame, Vector[GameEvent]) =
    // Consume inputs, checking for recycle (skill level bonus + tablet bonus)
    val baseRecycleChance = calculateRecycleChance(skillState.level)
    val tabletRecycleBonus = game.tabletSlots.recycleBonusFor(skill)
    val totalRecycleChance = baseRecycleChance + tabletRecycleBonus
    val (gameAfterConsume, allConsumed) = consumeInputs(game, action.inputs, totalRecycleChance, random)

    if !allConsumed then
      (gameAfterConsume, Vector(GameEvent.OutOfMaterials))
    else
      val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)
      val result = GameUpdate(gameAfterConsume, Vector.empty)
        .pipe(grantXp(skill, skillState, action.xpGain))
        .pipe(grantActionXp(action.id, actionState, action.xpGain))
        .pipe(processOutput(action, skill, skillState, random))

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

  private def processOutput(action: ProcessingAction, skill: Skill, skillState: SkillState, random: Random)(update: GameUpdate): GameUpdate =
    // Check for burn (cooking) - Sea Chef synergy prevents all burning
    val preventsBurn = update.game.tabletSlots.preventsBurning
    val didBurn = !preventsBurn && action.burnChance.exists { baseBurn =>
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
      // Success - check for double chance (skill level bonus + tablet bonus)
      val baseDoubleChance = calculateDoubleChance(skillState.level, isGathering = false)
      val tabletDoubleBonus = update.game.tabletSlots.doubleBonusFor(skill)
      val totalDoubleChance = baseDoubleChance + tabletDoubleBonus
      val baseCount = action.outputCount
      val doubleCount = if random.nextDouble() < totalDoubleChance then baseCount else 0
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
  // Thieving Tick
  // ============================================================================

  private def processThievingTick(
    game: VelorIdleGame,
    action: ThievingAction,
    elapsedSeconds: Double,
    currentTime: Long,
    random: Random
  ): (VelorIdleGame, Vector[GameEvent]) =
    val skillState = game.skills.getOrElse(Skill.Thieving, SkillState.initial)
    val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)

    // Calculate effective action time
    val efficiencyBonus = calculateEfficiencyBonus(actionState.level)
    val potionSpeedBonus = game.potionSlots.speedBonusFor(Skill.Thieving)
    val tabletSpeedBonus = game.tabletSlots.speedBonusFor(Skill.Thieving)
    val effectiveTime = action.timeSeconds * (1.0 - efficiencyBonus) * (1.0 - potionSpeedBonus) * (1.0 - tabletSpeedBonus)

    val progressPerSecond = 1.0 / effectiveTime
    val newProgress = game.actionProgress + (elapsedSeconds * progressPerSecond)

    if newProgress >= 1.0 then
      val (updatedGame, events) = completeThievingAction(game, action, skillState, currentTime, random)

      // Consume potion charge
      val gameWithPotionConsumed = updatedGame.copy(
        potionSlots = updatedGame.potionSlots.consumeAction
      )
      val potionEvents = checkPotionExpired(game.potionSlots, gameWithPotionConsumed.potionSlots)

      // Consume tablet charges
      val (gameWithTabletsConsumed, tabletEvents) = consumeTablets(gameWithPotionConsumed)

      val overflowProgress = newProgress - 1.0
      val finalProgress = if overflowProgress > 0 && overflowProgress < 1.0 then overflowProgress else 0.0
      (gameWithTabletsConsumed.copy(actionProgress = finalProgress, lastTickTime = currentTime), events ++ potionEvents ++ tabletEvents)
    else
      (game.copy(actionProgress = newProgress, lastTickTime = currentTime), Vector.empty)

  private def completeThievingAction(
    game: VelorIdleGame,
    action: ThievingAction,
    skillState: SkillState,
    currentTime: Long,
    random: Random
  ): (VelorIdleGame, Vector[GameEvent]) =
    val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)
    
    // Calculate success rate - increases with level difference
    val levelBonus = (skillState.level - action.levelRequired) * 0.005 // 0.5% per level above requirement
    val tabletBonus = if game.tabletSlots.equippedTypes.contains(TabletType.Thief) then 0.10 else 0.0
    val effectiveSuccessRate = (action.baseSuccessRate + levelBonus + tabletBonus).min(0.95) // Cap at 95%

    if random.nextDouble() < effectiveSuccessRate then
      // Success!
      val result = GameUpdate(game, Vector.empty)
        .pipe(grantXp(Skill.Thieving, skillState, action.xpGain))
        .pipe(grantActionXp(action.id, actionState, action.xpGain))
        .pipe(grantThievingLoot(action, random))
      (result.game, result.events)
    else
      // Failure - no loot, no XP
      (game, Vector(GameEvent.ThievingFailed("Caught!")))

  private def grantThievingLoot(action: ThievingAction, random: Random)(update: GameUpdate): GameUpdate =
    // Grant gold
    val goldAmount = action.goldMin + random.nextInt(action.goldMax - action.goldMin + 1)
    val withGold = update
      .mapGame(g => g.copy(gold = g.gold + goldAmount))
      .addEvent(GameEvent.GoldGained(goldAmount))

    // Check loot table for item drops
    action.lootTable.foldLeft(withGold) { case (acc, (item, chance)) =>
      if random.nextDouble() < chance then
        val (newInv, overflow) = acc.game.inventory.addItem(item, 1)
        val withItem = acc
          .mapGame(_.copy(inventory = newInv))
          .addEvent(GameEvent.ItemGained(item, 1))
        if overflow > 0 then withItem.addEvent(GameEvent.InventoryFull)
        else withItem
      else acc
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

  // Action-level perks (calculated from action level)
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

  // Job-level perks (calculated from skill level)
  private val secondaryChanceTiers = Vector(
    PerkTier(10, 0.02),  // 2% at level 10
    PerkTier(30, 0.03),  // +3% at level 30 = 5% total
    PerkTier(50, 0.05),  // +5% at level 50 = 10% total
    PerkTier(70, 0.05),  // +5% at level 70 = 15% total
    PerkTier(90, 0.05)   // +5% at level 90 = 20% total
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

  /** Efficiency bonus (reduces action time) - now based on ACTION level */
  def calculateEfficiencyBonus(level: Int): Double =
    calculateTieredBonus(level, efficiencyTiers)

  /** Yield bonus (chance for extra resource) - based on ACTION level */
  def calculateYieldBonus(level: Int): Double =
    calculateTieredBonus(level, yieldTiers)

  /** Secondary chance (rare item drop bonus) - based on JOB level */
  def calculateSecondaryChance(level: Int): Double =
    calculateTieredBonus(level, secondaryChanceTiers)

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
  def startAction(game: VelorIdleGame, actionId: String, currentTime: Long = System.currentTimeMillis()): Either[String, VelorIdleGame] =
    game.currentSkill match
      case None => Left("No skill selected")
      case Some(skill) if Skill.isGathering(skill) => startGathering(game, actionId)
      case Some(skill) if Skill.isProcessing(skill) => startProcessing(game, actionId)
      case Some(Skill.Thieving) => startThieving(game, actionId)
      case Some(Skill.Adventure) => startAdventure(game, actionId, currentTime)
      case Some(skill) => Left(s"${Skill.displayName(skill)} actions not yet implemented")

  /** Start adventure combat with an enemy */
  def startAdventure(game: VelorIdleGame, enemyId: String, currentTime: Long): Either[String, VelorIdleGame] =
    AdventureCombat.startCombat(game, enemyId, currentTime)

  /** Restart adventure combat with the same enemy (after death) */
  def restartAdventure(game: VelorIdleGame, currentTime: Long): Either[String, VelorIdleGame] =
    AdventureCombat.restartCombat(game, currentTime)

  /** Use an adventure combat skill */
  def useAdventureSkill(game: VelorIdleGame, slotIndex: Int, currentTime: Long): Either[String, VelorIdleGame] =
    AdventureCombat.useSkill(game, slotIndex, currentTime)

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

  /** Start a thieving action */
  def startThieving(game: VelorIdleGame, actionId: String): Either[String, VelorIdleGame] =
    game.currentSkill match
      case None => Left("No skill selected")
      case Some(skill) if skill != Skill.Thieving => Left("Not thieving skill")
      case Some(_) =>
        val actions = ThievingActions.targets
        actions.find(_.id == actionId) match
          case None => Left("Target not found")
          case Some(action) =>
            val skillState = game.skills.getOrElse(Skill.Thieving, SkillState.initial)
            if skillState.level < action.levelRequired then
              Left(s"Requires Thieving level ${action.levelRequired}")
            else
              Right(game.copy(
                activeAction = ActiveAction.Thieving(action),
                actionProgress = 0.0
              ))

  /** Stop the current action */
  def stopAction(game: VelorIdleGame): VelorIdleGame =
    val clearedGame = game.activeAction match
      case ActiveAction.Adventure => AdventureCombat.stopCombat(game)
      case _ => game
    clearedGame.copy(
      activeAction = ActiveAction.Idle,
      actionProgress = 0.0
    )

  /** Start resting to regenerate HP and Mana */
  def startRest(game: VelorIdleGame): VelorIdleGame =
    // Clear any combat state when starting rest
    val clearedGame = AdventureCombat.stopCombat(game)
    clearedGame.copy(
      currentSkill = Some(Skill.Adventure),
      activeAction = ActiveAction.Rest,
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

  /** Sell all except one of a specific item */
  def sellAllExceptOne(game: VelorIdleGame, item: Item): Either[String, VelorIdleGame] =
    val count = game.inventory.getCount(item)
    if count <= 1 then Left("Not enough to sell")
    else sellItem(game, item, count - 1)

  /** Toggle an item's junk status */
  def toggleJunk(game: VelorIdleGame, item: Item): VelorIdleGame =
    if game.junkItems.contains(item) then
      game.copy(junkItems = game.junkItems - item)
    else
      game.copy(junkItems = game.junkItems + item)

  /** Set an item's junk status explicitly */
  def setJunk(game: VelorIdleGame, item: Item, isJunk: Boolean): VelorIdleGame =
    if isJunk then
      game.copy(junkItems = game.junkItems + item)
    else
      game.copy(junkItems = game.junkItems - item)

  /** Sell all items marked as junk */
  def sellAllJunk(game: VelorIdleGame): Either[String, (VelorIdleGame, Long, Int)] =
    val junkInInventory = game.inventory.slots.flatten
      .filter(stack => game.junkItems.contains(stack.item))
    
    if junkInInventory.isEmpty then
      Left("No junk items to sell")
    else
      var currentGame = game
      var totalGold = 0L
      var itemsSold = 0
      
      for stack <- junkInInventory do
        val goldGain = Item.sellValue(stack.item) * stack.count
        val (newInventory, _) = currentGame.inventory.removeItem(stack.item, stack.count)
        currentGame = currentGame.copy(
          inventory = newInventory,
          gold = currentGame.gold + goldGain
        )
        totalGold += goldGain
        itemsSold += 1
      
      Right((currentGame, totalGold, itemsSold))


  /** Drink a potion from inventory to activate its effect */
  def drinkPotion(game: VelorIdleGame, potion: Item): Either[String, VelorIdleGame] =
    if !PotionEffect.isPotion(potion) then
      Left("Not a potion")
    else if game.inventory.getCount(potion) <= 0 then
      Left("No potion in inventory")
    else
      ActivePotion.fromItem(potion) match
        case None => Left("Invalid potion")
        case Some(activePotion) =>
          val (newInventory, _) = game.inventory.removeItem(potion, 1)
          Right(game.copy(
            inventory = newInventory,
            potionSlots = PotionSlots(Some(activePotion))
          ))

  /** Remove the currently active potion (waste remaining charges) */
  def removeActivePotion(game: VelorIdleGame): VelorIdleGame =
    game.copy(potionSlots = PotionSlots.empty)

  // ============================================================================
  // Tablet Management
  // ============================================================================

  /** Equip a tablet to a slot (1 or 2). Returns error if slot is locked or not a tablet. */
  def equipTablet(game: VelorIdleGame, tablet: Item, slot: Int): Either[String, VelorIdleGame] =
    if !TabletType.isTablet(tablet) then
      Left("Not a tablet")
    else if game.inventory.getCount(tablet) <= 0 then
      Left("No tablet in inventory")
    else if slot < 1 || slot > 2 then
      Left("Invalid slot")
    else
      val summoningLevel = game.skills.getOrElse(Skill.Summoning, SkillState.initial).level
      if slot == 2 && !game.tabletSlots.isSlot2Unlocked(summoningLevel) then
        Left("Slot 2 requires Summoning level 25")
      else
        EquippedTablet.fromItem(tablet) match
          case None => Left("Invalid tablet")
          case Some(equipped) =>
            val (newInventory, _) = game.inventory.removeItem(tablet, 1)
            val newSlots = slot match
              case 1 => game.tabletSlots.copy(slot1 = Some(equipped))
              case 2 => game.tabletSlots.copy(slot2 = Some(equipped))
            Right(game.copy(
              inventory = newInventory,
              tabletSlots = newSlots
            ))

  /** Unequip a tablet from a slot, returning it to inventory */
  def unequipTablet(game: VelorIdleGame, slot: Int): Either[String, VelorIdleGame] =
    if slot < 1 || slot > 2 then
      Left("Invalid slot")
    else
      val maybeTablet = slot match
        case 1 => game.tabletSlots.slot1
        case 2 => game.tabletSlots.slot2

      maybeTablet match
        case None => Left("No tablet in slot")
        case Some(equipped) =>
          val (newInventory, overflow) = game.inventory.addItem(equipped.item, 1)
          if overflow > 0 then
            Left("Inventory full")
          else
            val newSlots = slot match
              case 1 => game.tabletSlots.copy(slot1 = None)
              case 2 => game.tabletSlots.copy(slot2 = None)
            Right(game.copy(
              inventory = newInventory,
              tabletSlots = newSlots
            ))

  // ============================================================================
  // Shop
  // ============================================================================

  /** Items available for purchase in the shop */
  case class ShopItem(item: Item, buyPrice: Int)

  val shopItems: Vector[ShopItem] = Vector(
    ShopItem(Item.Vial, 10)  // Vials for alchemy
  )

  /** Buy an item from the shop */
  def buyItem(game: VelorIdleGame, item: Item, count: Int): Either[String, VelorIdleGame] =
    shopItems.find(_.item == item) match
      case None => Left("Item not for sale")
      case Some(shopItem) =>
        val totalCost = shopItem.buyPrice * count
        if game.gold < totalCost then
          Left(s"Need $totalCost gold (you have ${game.gold})")
        else if game.inventory.isFull then
          Left("Inventory full")
        else
          val (newInventory, overflow) = game.inventory.addItem(item, count)
          if overflow > 0 then
            Left("Not enough inventory space")
          else
            Right(game.copy(
              gold = game.gold - totalCost,
              inventory = newInventory
            ))

  /** Buy additional inventory slots (+4 slots per purchase) */
  def buyInventorySlots(game: VelorIdleGame): Either[String, VelorIdleGame] =
    Inventory.nextUpgradeCost(game.inventory.maxSlots) match
      case None => Left("Inventory is at maximum capacity")
      case Some(cost) =>
        if game.gold < cost then
          Left(s"Need $cost gold (you have ${game.gold})")
        else
          val newMaxSlots = (game.inventory.maxSlots + 4).min(Inventory.MaxSlots)
          val additionalSlots = newMaxSlots - game.inventory.maxSlots
          val newSlots = game.inventory.slots ++ Vector.fill(additionalSlots)(None)
          Right(game.copy(
            gold = game.gold - cost,
            inventory = game.inventory.copy(slots = newSlots, maxSlots = newMaxSlots)
          ))

  // ============================================================================
  // Combat Skill Tree Actions
  // ============================================================================

  /** Check if Adventure level changed and award skill points if so.
    * Call this after any operation that might change Adventure level.
    * Returns (updatedGame, skillPointsAwarded)
    */
  def checkAndAwardAdventureSkillPoints(game: VelorIdleGame, oldLevel: Int): (VelorIdleGame, Int) =
    val newLevel = game.skills.getOrElse(Skill.Adventure, SkillState.initial).level
    if newLevel > oldLevel then
      val levelsGained = newLevel - oldLevel
      val newCombatSkillState = SkillTreeLogic.awardPoints(game.adventureState.combatSkillState, levelsGained)
      val updatedGame = game.copy(
        adventureState = game.adventureState.copy(combatSkillState = newCombatSkillState)
      )
      (updatedGame, levelsGained)
    else
      (game, 0)

  /** Allocate a skill point to a combat skill */
  def allocateCombatSkillPoint(game: VelorIdleGame, skillId: String): Either[String, VelorIdleGame] =
    SkillTreeLogic.allocatePoint(game.adventureState.combatSkillState, skillId).map { newCombatSkillState =>
      game.copy(
        adventureState = game.adventureState.copy(combatSkillState = newCombatSkillState)
      )
    }

  /** Deallocate a skill point from a combat skill (costs gold) */
  def deallocateCombatSkillPoint(game: VelorIdleGame, skillId: String): Either[String, VelorIdleGame] =
    val refundCost = SkillTreeLogic.RefundCostGold
    if game.gold < refundCost then
      Left(s"Refund costs $refundCost gold")
    else
      SkillTreeLogic.deallocatePoint(game.adventureState.combatSkillState, skillId).map { newCombatSkillState =>
        game.copy(
          gold = game.gold - refundCost,
          adventureState = game.adventureState.copy(combatSkillState = newCombatSkillState)
        )
      }

  /** Bind a combat skill to a slot (1-4) */
  def bindCombatSkill(game: VelorIdleGame, skillId: String, slot: Int): Either[String, VelorIdleGame] =
    SkillTreeLogic.bindSkill(game.adventureState.combatSkillState, skillId, slot).map { newCombatSkillState =>
      game.copy(
        adventureState = game.adventureState.copy(combatSkillState = newCombatSkillState)
      )
    }

  /** Unbind a combat skill from a slot */
  def unbindCombatSkill(game: VelorIdleGame, slot: Int): Either[String, VelorIdleGame] =
    SkillTreeLogic.unbindSkill(game.adventureState.combatSkillState, slot).map { newCombatSkillState =>
      game.copy(
        adventureState = game.adventureState.copy(combatSkillState = newCombatSkillState)
      )
    }

  /** Reset all combat skill points (full refund) */
  def resetCombatSkillPoints(game: VelorIdleGame): VelorIdleGame =
    val newCombatSkillState = SkillTreeLogic.resetPoints(game.adventureState.combatSkillState)
    game.copy(
      adventureState = game.adventureState.copy(combatSkillState = newCombatSkillState)
    )

  /** Award combat skill points to the player */
  def awardCombatSkillPoints(game: VelorIdleGame, points: Int): VelorIdleGame =
    val newCombatSkillState = SkillTreeLogic.awardPoints(game.adventureState.combatSkillState, points)
    game.copy(
      adventureState = game.adventureState.copy(combatSkillState = newCombatSkillState)
    )

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
  case PotionDrunk(potion: Item)
  case PotionExpired(potion: Item)
  case TabletEquipped(tablet: Item, slot: Int)
  case TabletUnequipped(tablet: Item, slot: Int)
  case TabletConsumed(tablet: Item, slot: Int)
  case ThievingSuccess(goldAmount: Long)
  case ThievingFailed(reason: String)
  // Adventure events
  case AdventureEnemyDefeated(enemyId: String)
  case AdventurePlayerDied
  case SkillPointsGained(points: Int)

