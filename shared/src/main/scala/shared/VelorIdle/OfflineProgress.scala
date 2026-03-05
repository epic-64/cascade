package shared.VelorIdle

import scala.util.Random

/** Offline progression calculator.
  *
  * When the player returns after being away, this calculates what they would
  * have gained during that time and applies it in bulk.
  *
  * == Accuracy Notes ==
  * - Gathering/Processing/Thieving: XP gains are exact (actions × XP per action)
  * - Combat: Simulated tick-by-tick using real game logic (100% accurate)
  * - Rare drops use expected value for non-combat (may vary slightly from real gameplay)
  * - Efficiency/yield bonuses are calculated once at start for non-combat
  * - Potion and tablet effects are NOT applied (they'd expire quickly anyway)
  * - Inventory overflow is ignored (items may exceed normal limits)
  *
  * == Maintenance Requirements ==
  * When modifying gameplay systems, update this file if you:
  * - Add a new ActiveAction type (add a new case in calculateOfflineProgress)
  * - Change XP formulas in SkillState/ActionState (formulas are referenced directly)
  * - Add new bonus calculations to VelorIdleLogic (may need to include here)
  * - Change how processing/gathering/thieving works (update respective process* methods)
  * - Combat changes are automatically picked up (uses VelorIdleLogic.tick directly)
  *
  * == Not Supported ==
  * - Astrology (not implemented yet)
  */
object OfflineProgress:

  /** Duration of each simulated chunk in seconds */
  val ChunkDurationSeconds: Double = 30.0

  /** Maximum offline time to process (24 hours) */
  val MaxOfflineSeconds: Long = 24 * 60 * 60

  /** Result of offline progression calculation */
  case class OfflineResult(
    game: VelorIdleGame,
    secondsProcessed: Long,
    chunksProcessed: Int,
    xpGained: Map[Skill, Long],
    actionXpGained: Map[String, Long],
    itemsGained: Map[Item, Long],
    itemsConsumed: Map[Item, Long],
    goldGained: Long,
    skillLevelUps: Map[Skill, Int],
    actionLevelUps: Map[String, Int],
    events: Vector[GameEvent]
  )

  /** Calculate and apply offline progression.
    *
    * @param game Current game state
    * @param lastTickTime When the game was last updated
    * @param currentTime Current timestamp
    * @param random Random source for drops
    * @return Updated game state and summary of gains
    */
  def calculateOfflineProgress(
    game: VelorIdleGame,
    lastTickTime: Long,
    currentTime: Long,
    random: Random = Random
  ): OfflineResult =
    val elapsedSeconds = ((currentTime - lastTickTime) / 1000L).min(MaxOfflineSeconds)

    if elapsedSeconds < ChunkDurationSeconds then
      // Not enough time passed for offline processing
      return OfflineResult(game, 0, 0, Map.empty, Map.empty, Map.empty, Map.empty, 0, Map.empty, Map.empty, Vector.empty)

    game.activeAction match
      case ActiveAction.Idle =>
        OfflineResult(game, elapsedSeconds, 0, Map.empty, Map.empty, Map.empty, Map.empty, 0, Map.empty, Map.empty, Vector.empty)

      case ActiveAction.Gathering(skill, action) =>
        processGatheringOffline(game, skill, action, elapsedSeconds, random)

      case ActiveAction.Processing(skill, action) =>
        processProcessingOffline(game, skill, action, elapsedSeconds, random)

      case ActiveAction.Thieving(action) =>
        processThievingOffline(game, action, elapsedSeconds, random)

      case ActiveAction.EquipmentCrafting(action) =>
        processEquipmentCraftingOffline(game, action, elapsedSeconds, random)

      case ActiveAction.Adventure =>
        processAdventureOffline(game, lastTickTime, currentTime, elapsedSeconds, random)

      case ActiveAction.Rest =>
        processRestOffline(game, elapsedSeconds)

  // ============================================================================
  // Gathering Offline Processing
  // ============================================================================

  private def processGatheringOffline(
    game: VelorIdleGame,
    skill: Skill,
    action: GatheringAction,
    elapsedSeconds: Long,
    random: Random
  ): OfflineResult =
    val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)
    val skillState = game.skills.getOrElse(skill, SkillState.initial)

    // Calculate effective time per action (with efficiency bonus)
    val efficiency = VelorIdleLogic.calculateEfficiencyBonus(actionState.level)
    val effectiveTime = action.timeSeconds * (1.0 - efficiency)

    // Calculate number of actions that would complete
    val actionsCompleted = (elapsedSeconds / effectiveTime).toLong
    val chunksProcessed = (elapsedSeconds / ChunkDurationSeconds).toInt

    if actionsCompleted <= 0 then
      return OfflineResult(game, elapsedSeconds, 0, Map.empty, Map.empty, Map.empty, Map.empty, 0, Map.empty, Map.empty, Vector.empty)

    // Calculate XP gained
    val skillXpGained = actionsCompleted * action.xpGain
    val actionXpGained = actionsCompleted * action.xpGain

    // Calculate items gained (base + yield bonus)
    val yieldBonus = VelorIdleLogic.calculateYieldBonus(actionState.level)
    val avgItemsPerAction = 1.0 + yieldBonus
    val baseItems = (actionsCompleted * avgItemsPerAction).toLong

    // Roll for rare drops
    val rareItems = action.rareOutput match
      case Some((item, chance)) =>
        val expectedRare = (actionsCompleted * chance).toLong
        val extraRare = if random.nextDouble() < (actionsCompleted * chance - expectedRare) then 1L else 0L
        Map(item -> (expectedRare + extraRare)).filter(_._2 > 0)
      case None => Map.empty[Item, Long]

    val itemsGained = Map(action.output -> baseItems) ++ rareItems

    // Apply XP and calculate level ups
    var updatedGame = game
    var events = Vector.empty[GameEvent]

    // Apply skill XP
    val oldSkillLevel = skillState.level
    val newSkillXp = skillState.xp + skillXpGained
    val newSkillLevel = SkillState.levelFromXp(newSkillXp)
    val newSkillState = skillState.copy(xp = newSkillXp, level = newSkillLevel)
    updatedGame = updatedGame.copy(skills = updatedGame.skills.updated(skill, newSkillState))

    val skillLevelUps = if newSkillLevel > oldSkillLevel then
      events :+= GameEvent.LevelUp(skill, newSkillLevel)
      Map(skill -> (newSkillLevel - oldSkillLevel))
    else Map.empty[Skill, Int]

    // Apply action XP
    val oldActionLevel = actionState.level
    val newActionXp = actionState.xp + actionXpGained
    val newActionLevel = ActionState.levelFromXp(newActionXp, action.levelRequired)
    val newActionState = actionState.copy(xp = newActionXp, level = newActionLevel)
    updatedGame = updatedGame.copy(actionLevels = updatedGame.actionLevels.updated(action.id, newActionState))

    val actionLevelUps = if newActionLevel > oldActionLevel then
      events :+= GameEvent.ActionLevelUp(action.id, newActionLevel)
      Map(action.id -> (newActionLevel - oldActionLevel))
    else Map.empty[String, Int]

    // Add items to inventory
    itemsGained.foreach { case (item, count) =>
      val (newInv, _) = updatedGame.inventory.addItem(item, count)
      updatedGame = updatedGame.copy(inventory = newInv)
    }

    // Award combat skill points if Adventure leveled up
    if skill == Skill.Adventure && newSkillLevel > oldSkillLevel then
      val pointsGained = newSkillLevel - oldSkillLevel
      val newCombatSkillState = SkillTreeLogic.awardPoints(updatedGame.adventureState.combatSkillState, pointsGained)
      updatedGame = updatedGame.copy(
        adventureState = updatedGame.adventureState.copy(combatSkillState = newCombatSkillState)
      )

    OfflineResult(
      game = updatedGame,
      secondsProcessed = elapsedSeconds,
      chunksProcessed = chunksProcessed,
      xpGained = Map(skill -> skillXpGained),
      actionXpGained = Map(action.id -> actionXpGained),
      itemsGained = itemsGained,
      itemsConsumed = Map.empty, // Gathering doesn't consume items
      goldGained = 0,
      skillLevelUps = skillLevelUps,
      actionLevelUps = actionLevelUps,
      events = events
    )

  // ============================================================================
  // Processing Offline Processing
  // ============================================================================

  private def processProcessingOffline(
    game: VelorIdleGame,
    skill: Skill,
    action: ProcessingAction,
    elapsedSeconds: Long,
    random: Random
  ): OfflineResult =
    val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)
    val skillState = game.skills.getOrElse(skill, SkillState.initial)

    // Calculate effective time per action
    val efficiency = VelorIdleLogic.calculateEfficiencyBonus(actionState.level)
    val effectiveTime = action.timeSeconds * (1.0 - efficiency)

    // Calculate max actions based on time
    val maxActionsByTime = (elapsedSeconds / effectiveTime).toLong

    // Calculate max actions based on available materials
    val maxActionsByMaterials = action.inputs.map { case (item, countPerAction) =>
      game.inventory.getCount(item) / countPerAction
    }.minOption.getOrElse(0L)

    val actionsCompleted = maxActionsByTime.min(maxActionsByMaterials)
    val chunksProcessed = (elapsedSeconds / ChunkDurationSeconds).toInt

    if actionsCompleted <= 0 then
      return OfflineResult(game, elapsedSeconds, 0, Map.empty, Map.empty, Map.empty, Map.empty, 0, Map.empty, Map.empty, Vector.empty)

    // Calculate XP gained
    val skillXpGained = actionsCompleted * action.xpGain
    val actionXpGained = actionsCompleted * action.xpGain

    // Consume inputs and produce outputs
    var updatedGame = game

    // Remove input items (accounting for recycle chance) and track consumption
    val recycleChance = VelorIdleLogic.calculateRecycleChance(skillState.level)
    var itemsConsumed = Map.empty[Item, Long]
    action.inputs.foreach { case (item, countPerAction) =>
      val totalNeeded = actionsCompleted * countPerAction
      val recycled = (totalNeeded * recycleChance).toLong
      val actualConsumed = totalNeeded - recycled
      itemsConsumed = itemsConsumed.updated(item, actualConsumed)
      val (newInv, _) = updatedGame.inventory.removeItem(item, actualConsumed)
      updatedGame = updatedGame.copy(inventory = newInv)
    }

    // Add output items (accounting for burn chance for cooking)
    val outputCount = action.burnChance match
      case Some(baseBurn) =>
        val levelDiff = skillState.level - action.levelRequired
        val effectiveBurnChance = (baseBurn - levelDiff * 0.02).max(0.05)
        val burned = (actionsCompleted * effectiveBurnChance).toLong
        actionsCompleted - burned
      case None => actionsCompleted
    
    val itemsGained = Map(action.output -> (outputCount * action.outputCount))
    itemsGained.foreach { case (item, count) =>
      val (newInv, _) = updatedGame.inventory.addItem(item, count)
      updatedGame = updatedGame.copy(inventory = newInv)
    }

    // Apply skill XP
    val oldSkillLevel = skillState.level
    val newSkillXp = skillState.xp + skillXpGained
    val newSkillLevel = SkillState.levelFromXp(newSkillXp)
    val newSkillState = skillState.copy(xp = newSkillXp, level = newSkillLevel)
    updatedGame = updatedGame.copy(skills = updatedGame.skills.updated(skill, newSkillState))

    var events = Vector.empty[GameEvent]
    val skillLevelUps = if newSkillLevel > oldSkillLevel then
      events :+= GameEvent.LevelUp(skill, newSkillLevel)
      Map(skill -> (newSkillLevel - oldSkillLevel))
    else Map.empty[Skill, Int]

    // Apply action XP
    val oldActionLevel = actionState.level
    val newActionXp = actionState.xp + actionXpGained
    val newActionLevel = ActionState.levelFromXp(newActionXp, action.levelRequired)
    val newActionState = actionState.copy(xp = newActionXp, level = newActionLevel)
    updatedGame = updatedGame.copy(actionLevels = updatedGame.actionLevels.updated(action.id, newActionState))

    val actionLevelUps = if newActionLevel > oldActionLevel then
      events :+= GameEvent.ActionLevelUp(action.id, newActionLevel)
      Map(action.id -> (newActionLevel - oldActionLevel))
    else Map.empty[String, Int]

    OfflineResult(
      game = updatedGame,
      secondsProcessed = elapsedSeconds,
      chunksProcessed = chunksProcessed,
      xpGained = Map(skill -> skillXpGained),
      actionXpGained = Map(action.id -> actionXpGained),
      itemsGained = itemsGained,
      itemsConsumed = itemsConsumed,
      goldGained = 0,
      skillLevelUps = skillLevelUps,
      actionLevelUps = actionLevelUps,
      events = events
    )

  // ============================================================================
  // Thieving Offline Processing
  // ============================================================================

  private def processThievingOffline(
    game: VelorIdleGame,
    action: ThievingAction,
    elapsedSeconds: Long,
    random: Random
  ): OfflineResult =
    val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)
    val skillState = game.skills.getOrElse(Skill.Thieving, SkillState.initial)

    // Calculate effective time per action
    val efficiency = VelorIdleLogic.calculateEfficiencyBonus(actionState.level)
    val effectiveTime = action.timeSeconds * (1.0 - efficiency)

    val attemptsCompleted = (elapsedSeconds / effectiveTime).toLong
    val chunksProcessed = (elapsedSeconds / ChunkDurationSeconds).toInt

    if attemptsCompleted <= 0 then
      return OfflineResult(game, elapsedSeconds, 0, Map.empty, Map.empty, Map.empty, Map.empty, 0, Map.empty, Map.empty, Vector.empty)

    // Calculate success rate
    val levelBonus = (skillState.level - action.levelRequired) * 0.005
    val effectiveSuccessRate = (action.baseSuccessRate + levelBonus).min(0.95)

    // Calculate successful attempts
    val successfulAttempts = (attemptsCompleted * effectiveSuccessRate).toLong

    // Calculate XP (only for successful attempts)
    val skillXpGained = successfulAttempts * action.xpGain
    val actionXpGained = successfulAttempts * action.xpGain

    // Calculate gold (average of min/max)
    val avgGold = (action.goldMin + action.goldMax) / 2
    val goldGained = successfulAttempts * avgGold

    // Calculate loot drops
    var itemsGained = Map.empty[Item, Long]
    action.lootTable.foreach { case (item, chance) =>
      val expectedDrops = (successfulAttempts * chance).toLong
      if expectedDrops > 0 then
        itemsGained = itemsGained.updated(item, itemsGained.getOrElse(item, 0L) + expectedDrops)
    }

    var updatedGame = game
    var events = Vector.empty[GameEvent]

    // Apply gold
    updatedGame = updatedGame.copy(gold = updatedGame.gold + goldGained)

    // Apply items
    itemsGained.foreach { case (item, count) =>
      val (newInv, _) = updatedGame.inventory.addItem(item, count)
      updatedGame = updatedGame.copy(inventory = newInv)
    }

    // Apply skill XP
    val oldSkillLevel = skillState.level
    val newSkillXp = skillState.xp + skillXpGained
    val newSkillLevel = SkillState.levelFromXp(newSkillXp)
    val newSkillState = skillState.copy(xp = newSkillXp, level = newSkillLevel)
    updatedGame = updatedGame.copy(skills = updatedGame.skills.updated(Skill.Thieving, newSkillState))

    val skillLevelUps = if newSkillLevel > oldSkillLevel then
      events :+= GameEvent.LevelUp(Skill.Thieving, newSkillLevel)
      Map(Skill.Thieving -> (newSkillLevel - oldSkillLevel))
    else Map.empty[Skill, Int]

    // Apply action XP
    val oldActionLevel = actionState.level
    val newActionXp = actionState.xp + actionXpGained
    val newActionLevel = ActionState.levelFromXp(newActionXp, action.levelRequired)
    val newActionState = actionState.copy(xp = newActionXp, level = newActionLevel)
    updatedGame = updatedGame.copy(actionLevels = updatedGame.actionLevels.updated(action.id, newActionState))

    val actionLevelUps = if newActionLevel > oldActionLevel then
      events :+= GameEvent.ActionLevelUp(action.id, newActionLevel)
      Map(action.id -> (newActionLevel - oldActionLevel))
    else Map.empty[String, Int]

    OfflineResult(
      game = updatedGame,
      secondsProcessed = elapsedSeconds,
      chunksProcessed = chunksProcessed,
      xpGained = Map(Skill.Thieving -> skillXpGained),
      actionXpGained = Map(action.id -> actionXpGained),
      itemsGained = itemsGained,
      itemsConsumed = Map.empty, // Thieving doesn't consume items
      goldGained = goldGained,
      skillLevelUps = skillLevelUps,
      actionLevelUps = actionLevelUps,
      events = events
    )

  // ============================================================================
  // Equipment Crafting Offline Processing
  // ============================================================================

  private def processEquipmentCraftingOffline(
    game: VelorIdleGame,
    action: EquipmentCraftingAction,
    elapsedSeconds: Long,
    random: Random
  ): OfflineResult =
    val actionState = game.actionLevels.getOrElse(action.id, ActionState.initial)
    val skillState = game.skills.getOrElse(Skill.Smithing, SkillState.initial)

    // Calculate effective time per action
    val efficiency = VelorIdleLogic.calculateEfficiencyBonus(actionState.level)
    val effectiveTime = action.timeSeconds * (1.0 - efficiency)

    // Calculate max actions based on time
    val maxActionsByTime = (elapsedSeconds / effectiveTime).toLong

    // Calculate max actions based on available materials
    val maxActionsByMaterials = action.inputs.map { case (item, countPerAction) =>
      game.inventory.getCount(item) / countPerAction
    }.minOption.getOrElse(0L)

    val actionsCompleted = maxActionsByTime.min(maxActionsByMaterials)
    val chunksProcessed = (elapsedSeconds / ChunkDurationSeconds).toInt

    if actionsCompleted <= 0 then
      return OfflineResult(game, elapsedSeconds, 0, Map.empty, Map.empty, Map.empty, Map.empty, 0, Map.empty, Map.empty, Vector.empty)

    // Calculate XP gained
    val skillXpGained = actionsCompleted * action.xpGain
    val actionXpGained = actionsCompleted * action.xpGain

    var updatedGame = game
    var events = Vector.empty[GameEvent]

    // Consume inputs and track consumption
    val recycleChance = VelorIdleLogic.calculateRecycleChance(skillState.level)
    var itemsConsumed = Map.empty[Item, Long]
    action.inputs.foreach { case (item, countPerAction) =>
      val totalNeeded = actionsCompleted * countPerAction
      val recycled = (totalNeeded * recycleChance).toLong
      val actualConsumed = totalNeeded - recycled
      itemsConsumed = itemsConsumed.updated(item, actualConsumed)
      val (newInv, _) = updatedGame.inventory.removeItem(item, actualConsumed)
      updatedGame = updatedGame.copy(inventory = newInv)
    }

    // Create equipment items
    var advState = updatedGame.adventureState
    (0L until actionsCompleted).foreach { _ =>
      val instanceId = advState.nextEquipmentInstanceId
      EquipmentCrafting.createEquipment(action.outputDefId, instanceId, random).foreach { equipment =>
        updatedGame = updatedGame.copy(
          equipmentInventory = updatedGame.equipmentInventory :+ equipment
        )
        advState = advState.copy(nextEquipmentInstanceId = instanceId + 1)
      }
    }
    updatedGame = updatedGame.copy(adventureState = advState)

    // Apply skill XP
    val oldSkillLevel = skillState.level
    val newSkillXp = skillState.xp + skillXpGained
    val newSkillLevel = SkillState.levelFromXp(newSkillXp)
    val newSkillState = skillState.copy(xp = newSkillXp, level = newSkillLevel)
    updatedGame = updatedGame.copy(skills = updatedGame.skills.updated(Skill.Smithing, newSkillState))

    val skillLevelUps = if newSkillLevel > oldSkillLevel then
      events :+= GameEvent.LevelUp(Skill.Smithing, newSkillLevel)
      Map(Skill.Smithing -> (newSkillLevel - oldSkillLevel))
    else Map.empty[Skill, Int]

    // Apply action XP
    val oldActionLevel = actionState.level
    val newActionXp = actionState.xp + actionXpGained
    val newActionLevel = ActionState.levelFromXp(newActionXp, action.levelRequired)
    val newActionState = actionState.copy(xp = newActionXp, level = newActionLevel)
    updatedGame = updatedGame.copy(actionLevels = updatedGame.actionLevels.updated(action.id, newActionState))

    val actionLevelUps = if newActionLevel > oldActionLevel then
      events :+= GameEvent.ActionLevelUp(action.id, newActionLevel)
      Map(action.id -> (newActionLevel - oldActionLevel))
    else Map.empty[String, Int]

    OfflineResult(
      game = updatedGame,
      secondsProcessed = elapsedSeconds,
      chunksProcessed = chunksProcessed,
      xpGained = Map(Skill.Smithing -> skillXpGained),
      actionXpGained = Map(action.id -> actionXpGained),
      itemsGained = Map.empty, // Equipment goes to equipment inventory
      itemsConsumed = itemsConsumed,
      goldGained = 0,
      skillLevelUps = skillLevelUps,
      actionLevelUps = actionLevelUps,
      events = events
    )

  // ============================================================================
  // Adventure (Combat) Offline Processing - Tick by Tick Simulation
  // ============================================================================

  /** Tick interval for offline combat simulation in milliseconds */
  private val CombatTickIntervalMs: Long = 100L
  
  /** Maximum number of combat ticks to simulate (prevents infinite loops) */
  private val MaxCombatTicks: Int = 100_000 // ~2.7 hours at 100ms ticks

  private def processAdventureOffline(
    game: VelorIdleGame,
    lastTickTime: Long,
    currentTime: Long,
    elapsedSeconds: Long,
    random: Random
  ): OfflineResult =
    val chunksProcessed = (elapsedSeconds / ChunkDurationSeconds).toInt
    
    // Track gains across all combat
    var totalXpGained = 0L
    var totalGoldGained = 0L
    var itemsGained = Map.empty[Item, Long]
    var allEvents = Vector.empty[GameEvent]
    var enemiesDefeated = 0
    
    // Simulate combat tick by tick
    var currentGame = game
    var simulatedTime = lastTickTime
    var tickCount = 0
    
    while simulatedTime < currentTime && tickCount < MaxCombatTicks do
      val tickEndTime = (simulatedTime + CombatTickIntervalMs).min(currentTime)
      
      val (updatedGame, events) = VelorIdleLogic.tick(currentGame, tickEndTime, random)
      
      // Track XP gains
      events.foreach:
        case GameEvent.XpGained(skill, amount) if skill == Skill.Adventure =>
          totalXpGained += amount
        case GameEvent.GoldGained(amount) =>
          totalGoldGained += amount
        case GameEvent.ItemGained(item, count) =>
          itemsGained = itemsGained.updated(item, itemsGained.getOrElse(item, 0L) + count)
        case GameEvent.AdventureEnemyDefeated(_) =>
          enemiesDefeated += 1
        case _ => ()
      
      allEvents ++= events
      currentGame = updatedGame
      simulatedTime = tickEndTime
      tickCount += 1
      
      // If combat ended (player died or stopped), break out
      if currentGame.activeAction != ActiveAction.Adventure then
        // If player died and is now resting, let them fully recover
        if currentGame.activeAction == ActiveAction.Rest then
          val advState = currentGame.adventureState
          currentGame = currentGame.copy(
            adventureState = advState.copy(
              currentHp = advState.maxHp,
              currentMana = advState.maxMana
            ),
            activeAction = ActiveAction.Idle
          )
        simulatedTime = currentTime // Exit the loop
    
    // Calculate level ups
    val oldLevel = game.skills.getOrElse(Skill.Adventure, SkillState.initial).level
    val newLevel = currentGame.skills.getOrElse(Skill.Adventure, SkillState.initial).level
    val skillLevelUps = if newLevel > oldLevel then 
      Map(Skill.Adventure -> (newLevel - oldLevel)) 
    else Map.empty[Skill, Int]
    
    OfflineResult(
      game = currentGame.copy(lastTickTime = currentTime),
      secondsProcessed = elapsedSeconds,
      chunksProcessed = chunksProcessed,
      xpGained = if totalXpGained > 0 then Map(Skill.Adventure -> totalXpGained) else Map.empty,
      actionXpGained = Map.empty,
      itemsGained = itemsGained,
      itemsConsumed = Map.empty, // Combat doesn't consume items
      goldGained = totalGoldGained,
      skillLevelUps = skillLevelUps,
      actionLevelUps = Map.empty,
      events = allEvents
    )

  // ============================================================================
  // Rest Offline Processing
  // ============================================================================

  private def processRestOffline(
    game: VelorIdleGame,
    elapsedSeconds: Long
  ): OfflineResult =
    // Resting for any amount of time fully restores HP and mana
    val advState = game.adventureState
    val restoredGame = game.copy(
      adventureState = advState.copy(
        currentHp = advState.maxHp,
        currentMana = advState.maxMana,
        restManaRegenAccumulator = 0.0
      ),
      activeAction = ActiveAction.Idle
    )
    
    OfflineResult(
      game = restoredGame,
      secondsProcessed = elapsedSeconds,
      chunksProcessed = 0,
      xpGained = Map.empty,
      actionXpGained = Map.empty,
      itemsGained = Map.empty,
      itemsConsumed = Map.empty,
      goldGained = 0,
      skillLevelUps = Map.empty,
      actionLevelUps = Map.empty,
      events = Vector.empty
    )
