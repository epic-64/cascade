package shared.VelorIdle

import scala.util.Random

/** Combat logic for Adventure mode */
object AdventureCombat:

  // Global cooldown duration in milliseconds
  private val GlobalCooldownMs: Long = 1000L

  // Base auto-attack stats (without equipment)
  private val BaseAutoAttackDamage: Int = 5
  private val BaseAutoAttackSpeedMs: Long = 2000L

  // ============================================================================
  // Evade Calculation
  // ============================================================================

  /** Calculate evade chance based on attacker's attack rating vs defender's defense rating.
    * Formula: evadeChance = defenseRating / (defenseRating + attackRating) * 0.5
    * This gives a max of 50% evade when defense >> attack, and approaches 0% when attack >> defense.
    * When attack = defense, evade chance is 25%.
    */
  def calculateEvadeChance(attackRating: Int, defenseRating: Int): Double =
    if attackRating + defenseRating <= 0 then 0.0
    else (defenseRating.toDouble / (defenseRating + attackRating)) * 0.5

  /** Check if an attack is evaded */
  def rollEvade(attackRating: Int, defenseRating: Int, random: Random): Boolean =
    random.nextDouble() < calculateEvadeChance(attackRating, defenseRating)

  // ============================================================================
  // Combat Initialization
  // ============================================================================

  /** Start combat with an enemy */
  def startCombat(
    game: VelorIdleGame,
    enemyId: String,
    currentTime: Long
  ): Either[String, VelorIdleGame] =
    val adventureLevel = game.skills.getOrElse(Skill.Adventure, SkillState.initial).level

    Enemies.all.find(_.id == enemyId) match
      case None => Left(s"Unknown enemy: $enemyId")
      case Some(enemy) if enemy.levelRequired > adventureLevel =>
        Left(s"Requires Adventure level ${enemy.levelRequired}")
      case Some(enemy) =>
        val advState = game.adventureState

        // Use persisted player stats
        val maxHp = advState.maxHp
        val maxMana = advState.maxMana
        val currentHp = advState.currentHp.min(maxHp)
        val currentMana = advState.currentMana.min(maxMana)

        // Build 4 skill slots from combat skill state (skill tree system)
        val skillSlots = CombatSkillHelpers.buildSkillSlots(advState.combatSkillState)

        // Each combat instance gets a unique ID - UI uses this to detect new combats
        val instanceId = advState.nextCombatInstanceId

        val combatState = CombatState(
          instanceId = instanceId,
          enemy = enemy,
          enemyCurrentHp = enemy.maxHp,
          playerCurrentHp = currentHp,
          playerMaxHp = maxHp,
          playerMana = currentMana,
          playerMaxMana = maxMana,
          lastPlayerAutoAttack = currentTime,
          lastEnemyAutoAttack = currentTime,
          skillSlots = skillSlots
        )

        val newAdventureState = advState.copy(
          inCombat = true,
          combatState = Some(combatState),
          selectedEnemyId = Some(enemyId),
          nextCombatInstanceId = instanceId + 1
        )

        Right(game.copy(
          currentSkill = Some(Skill.Adventure),
          activeAction = ActiveAction.Adventure,
          adventureState = newAdventureState
        ))

  private val emptySkill = CombatSkill(
    id = "empty",
    name = "Empty",
    icon = "➖",
    description = "No skill equipped",
    manaCost = 0,
    cooldownMs = 0,
    damage = 0
  )

  // ============================================================================
  // Combat Tick - Single entry point for all combat processing
  // ============================================================================

  /** Process one tick of combat. Returns updated game and events. */
  def tick(
    game: VelorIdleGame,
    currentTime: Long,
    random: Random = Random
  ): (VelorIdleGame, Vector[GameEvent]) =
    game.adventureState.combatState match
      case None =>
        (game.copy(lastTickTime = currentTime), Vector.empty)
      case Some(combat) if combat.isPlayerDead =>
        (game.copy(lastTickTime = currentTime), Vector.empty)
      case Some(combat) if combat.isLoadingNextEnemy =>
        // Check if loading timer has expired
        combat.loadingNextEnemyUntil match
          case Some(until) if currentTime >= until =>
            // Timer expired - spawn next enemy
            restartCombat(game, currentTime) match
              case Right(restarted) => (restarted.copy(lastTickTime = currentTime), Vector.empty)
              case Left(_) =>
                // Fallback: clear combat (shouldn't happen)
                val clearedState = game.adventureState.copy(inCombat = false, combatState = None)
                (game.copy(adventureState = clearedState, lastTickTime = currentTime), Vector.empty)
          case _ =>
            // Still loading - just update tick time
            (game.copy(lastTickTime = currentTime), Vector.empty)
      case Some(combat) =>
        // Process combat and handle any resulting state changes
        val (updatedGame, events) = processCombatTick(game, combat, currentTime, random)
        (updatedGame.copy(lastTickTime = currentTime), events)

  /** Core combat processing - runs all combat systems and handles outcomes */
  private def processCombatTick(
    game: VelorIdleGame,
    combat: CombatState,
    currentTime: Long,
    random: Random
  ): (VelorIdleGame, Vector[GameEvent]) =
    val elapsedSeconds = (currentTime - game.lastTickTime).max(0) / 1000.0

    // Run all combat systems in order
    val (processedCombat, combatEvents) = runCombatSystems(combat, currentTime, elapsedSeconds, game.adventureState, random)

    // Check outcomes and handle state transitions
    if processedCombat.isEnemyDead then
      handleVictory(processedCombat, game, currentTime, random)
    else if processedCombat.isPlayerDead then
      handleDefeat(processedCombat, game, combatEvents)
    else
      finalizeCombatState(processedCombat, game, combatEvents)

  /** Run all combat systems: casting, DoTs, auto-attacks, regen, etc. */
  private def runCombatSystems(
    combat: CombatState,
    currentTime: Long,
    elapsedSeconds: Double,
    advState: AdventureState,
    random: Random
  ): (CombatState, Vector[CombatEvent]) =
    var c = combat
    var events = Vector.empty[CombatEvent]

    // 1. Process casting
    c.castingSkill match
      case Some(casting) if casting.isComplete(currentTime) =>
        val (newCombat, castEvents) = executeCastedSkill(c, casting.slotIndex, casting.skill, currentTime)
        c = newCombat.copy(castingSkill = None)
        events ++= castEvents
      case _ => ()

    // 2. Process DoTs
    val (afterDoTs, dotEvents) = processDoTs(c, currentTime)
    c = afterDoTs
    events ++= dotEvents

    // 3. Process auto-attacks (only if enemy not already dead from DoTs)
    if !c.isEnemyDead then
      val (afterAutos, autoEvents) = processAutoAttacks(c, currentTime, advState, random)
      c = afterAutos
      events ++= autoEvents

    // 4. Mana regen
    c = c.copy(playerMana = (c.playerMana + (AdventureState.ManaRegenPerSecond * elapsedSeconds).toInt).min(c.playerMaxMana))

    // 5. Process chain windows
    c = processChainWindows(c, currentTime)

    // 6. Process stuns
    c = c.enemyStun match
      case Some(stun) if currentTime >= stun.endsAt => c.copy(enemyStun = None)
      case _ => c

    // 7. Process shields
    c = c.playerShield match
      case Some(shield) if currentTime >= shield.endsAt => c.copy(playerShield = None)
      case _ => c

    // Update event tracking
    val finalCombat = c.copy(
      recentEvents = (c.recentEvents ++ events).takeRight(10),
      totalEventCount = c.totalEventCount + events.length
    )

    (finalCombat, events)

  // Duration to wait between enemy kills before next enemy spawns
  private val LoadingNextEnemyMs: Long = 1500L

  /** Handle victory: grant rewards and set loading state for next enemy */
  private def handleVictory(
    combat: CombatState,
    game: VelorIdleGame,
    currentTime: Long,
    random: Random
  ): (VelorIdleGame, Vector[GameEvent]) =
    val enemy = combat.enemy
    var events = Vector.empty[GameEvent]
    var g = game

    // Grant XP
    val skillState = g.skills.getOrElse(Skill.Adventure, SkillState.initial)
    val newXp = skillState.xp + enemy.xpReward
    val oldLevel = skillState.level
    val newLevel = SkillState.levelFromXp(newXp)
    g = g.copy(skills = g.skills.updated(Skill.Adventure, skillState.copy(xp = newXp, level = newLevel)))
    events :+= GameEvent.XpGained(Skill.Adventure, enemy.xpReward)
    if newLevel > oldLevel then events :+= GameEvent.LevelUp(Skill.Adventure, newLevel)

    // Grant gold
    val goldAmount = enemy.goldReward._1 + random.nextInt(enemy.goldReward._2 - enemy.goldReward._1 + 1)
    g = g.copy(gold = g.gold + goldAmount)
    events :+= GameEvent.GoldGained(goldAmount)

    // Grant loot
    enemy.lootTable.foreach { case (item, chance) =>
      if random.nextDouble() < chance then
        val (newInv, _) = g.inventory.addItem(item, 1)
        g = g.copy(inventory = newInv)
        events :+= GameEvent.ItemGained(item, 1)
    }

    events :+= GameEvent.AdventureEnemyDefeated(enemy.id)

    // Set loading state - next enemy will spawn after delay
    val loadingCombat = combat.copy(
      loadingNextEnemyUntil = Some(currentTime + LoadingNextEnemyMs)
    )
    val newAdvState = g.adventureState.copy(
      combatState = Some(loadingCombat),
      currentHp = combat.playerCurrentHp,
      currentMana = combat.playerMana
    )
    (g.copy(adventureState = newAdvState), events)

  /** Handle defeat: update combat state with death event */
  private def handleDefeat(
    combat: CombatState,
    game: VelorIdleGame,
    combatEvents: Vector[CombatEvent]
  ): (VelorIdleGame, Vector[GameEvent]) =
    val updatedCombat = combat.copy(
      recentEvents = (combat.recentEvents ++ combatEvents :+ CombatEvent.PlayerDied).takeRight(10),
      totalEventCount = combat.totalEventCount + combatEvents.length + 1
    )
    val newAdvState = game.adventureState.copy(
      combatState = Some(updatedCombat),
      currentHp = 0,
      currentMana = updatedCombat.playerMana
    )
    (game.copy(adventureState = newAdvState), Vector(GameEvent.AdventurePlayerDied))

  /** Finalize normal combat state (no victory/defeat) */
  private def finalizeCombatState(
    combat: CombatState,
    game: VelorIdleGame,
    combatEvents: Vector[CombatEvent]
  ): (VelorIdleGame, Vector[GameEvent]) =
    val newAdvState = game.adventureState.copy(
      combatState = Some(combat),
      currentHp = combat.playerCurrentHp,
      currentMana = combat.playerMana
    )
    (game.copy(adventureState = newAdvState), Vector.empty)

  // ============================================================================
  // Combat Systems
  // ============================================================================

  private def processDoTs(combat: CombatState, currentTime: Long): (CombatState, Vector[CombatEvent]) =
    var enemyHp = combat.enemyCurrentHp
    var playerHp = combat.playerCurrentHp
    var events = Vector.empty[CombatEvent]

    val updatedEnemyDoTs = combat.enemyDoTs.flatMap { dot =>
      if currentTime >= dot.lastTickTime + dot.tickIntervalMs then
        enemyHp -= dot.damagePerTick
        events :+= CombatEvent.EnemyDotTick(dot.damagePerTick, dot.name)
        val remaining = dot.ticksRemaining - 1
        if remaining > 0 then Some(dot.copy(ticksRemaining = remaining, lastTickTime = currentTime))
        else None
      else Some(dot)
    }

    val updatedPlayerDoTs = combat.playerDoTs.flatMap { dot =>
      if currentTime >= dot.lastTickTime + dot.tickIntervalMs then
        playerHp -= dot.damagePerTick
        events :+= CombatEvent.PlayerDotTick(dot.damagePerTick, dot.name)
        val remaining = dot.ticksRemaining - 1
        if remaining > 0 then Some(dot.copy(ticksRemaining = remaining, lastTickTime = currentTime))
        else None
      else Some(dot)
    }

    (combat.copy(
      enemyCurrentHp = enemyHp.max(0),
      playerCurrentHp = playerHp.max(0),
      enemyDoTs = updatedEnemyDoTs,
      playerDoTs = updatedPlayerDoTs
    ), events)

  private def processAutoAttacks(
    combat: CombatState,
    currentTime: Long,
    advState: AdventureState,
    random: Random
  ): (CombatState, Vector[CombatEvent]) =
    var c = combat
    var events = Vector.empty[CombatEvent]

    // Player auto-attack - using base auto-attack stats
    if currentTime >= c.lastPlayerAutoAttack + BaseAutoAttackSpeedMs then
      // Check if enemy evades (player attack rating vs enemy defense rating)
      val enemyEvades = rollEvade(advState.attackRating, c.enemy.defenseRating, random)
      if enemyEvades then
        c = c.copy(
          lastPlayerAutoAttack = currentTime,
          playerDamageBuff = None  // Buff is consumed even on evade
        )
        events :+= CombatEvent.EnemyEvaded
      else
        val damage = applyDamageBuff(BaseAutoAttackDamage, c.playerDamageBuff)
        c = c.copy(
          enemyCurrentHp = (c.enemyCurrentHp - damage).max(0),
          lastPlayerAutoAttack = currentTime,
          playerDamageBuff = None
        )
        events :+= CombatEvent.PlayerAutoAttack(damage)

    // Enemy auto-attack (only if not stunned/frozen and player not dead)
    val enemyStunned = c.enemyStun.exists(s => currentTime < s.endsAt)
    val enemyFrozen = c.enemyFreeze.exists(f => currentTime < f.endsAt)
    if !enemyStunned && !enemyFrozen && !c.isPlayerDead && currentTime >= c.lastEnemyAutoAttack + c.enemy.attackSpeedMs then
      // Check if player evades (enemy attack rating vs player defense rating)
      val playerEvades = rollEvade(c.enemy.attackRating, advState.defenseRating, random)
      if playerEvades then
        c = c.copy(lastEnemyAutoAttack = currentTime)
        events :+= CombatEvent.PlayerEvaded
      else
        val (newHp, newShield, shieldBroken) = applyDamageWithShield(c.playerCurrentHp, c.playerShield, c.enemy.attackDamage, currentTime)
        c = c.copy(
          playerCurrentHp = newHp.max(0),
          playerShield = newShield,
          lastEnemyAutoAttack = currentTime
        )
        events :+= CombatEvent.EnemyAutoAttack(c.enemy.attackDamage)
        if shieldBroken then events :+= CombatEvent.ShieldBroken

    (c, events)

  private def processChainWindows(combat: CombatState, currentTime: Long): CombatState =
    combat.copy(skillSlots = combat.skillSlots.map { slot =>
      if slot.isInChainWindow(currentTime) then slot
      else if slot.currentSkill.id != slot.baseSkill.id then
        slot.copy(currentSkill = slot.baseSkill, chainWindowEndsAt = 0L)
      else slot
    })

  private def applyDamageBuff(baseDamage: Int, buff: Option[ActiveDamageBuff]): Int =
    buff.map(b => (baseDamage * (1.0 + b.percent)).toInt).getOrElse(baseDamage)

  private def applyDamageWithShield(
    currentHp: Int,
    shield: Option[ActiveShield],
    damage: Int,
    currentTime: Long
  ): (Int, Option[ActiveShield], Boolean) =
    shield match
      case Some(s) if currentTime < s.endsAt =>
        if s.remainingAbsorb >= damage then (currentHp, Some(s.copy(remainingAbsorb = s.remainingAbsorb - damage)), false)
        else (currentHp - (damage - s.remainingAbsorb), None, true)
      case _ => (currentHp - damage, None, false)

  // ============================================================================
  // Skill Usage
  // ============================================================================

  /** Use a skill in the given slot */
  def useSkill(
    game: VelorIdleGame,
    slotIndex: Int,
    currentTime: Long
  ): Either[String, VelorIdleGame] =
    game.adventureState.combatState match
      case None => Left("Not in combat")
      case Some(combat) if combat.isCombatOver => Left("Combat is over")
      case Some(combat) =>
        validateAndExecuteSkill(game, combat, slotIndex, currentTime)

  private def validateAndExecuteSkill(
    game: VelorIdleGame,
    combat: CombatState,
    slotIndex: Int,
    currentTime: Long
  ): Either[String, VelorIdleGame] =
    if slotIndex < 0 || slotIndex >= combat.skillSlots.length then
      return Left(s"Invalid skill slot: $slotIndex")

    val slot = combat.skillSlots(slotIndex)
    val skill = slot.currentSkill
    val isChainSkill = slot.isInChainWindow(currentTime) && skill.id != slot.baseSkill.id

    // Validation
    if skill.id == "empty" then Left("No skill in this slot")
    else if combat.isCasting then Left("Already casting a skill")
    else if combat.isOnGlobalCooldown(currentTime) then
      Left(f"Global cooldown (${combat.globalCooldownRemainingMs(currentTime) / 1000.0}%.1fs)")
    else if slot.isOnCooldown(currentTime) && !isChainSkill then
      Left(f"${skill.name} on cooldown (${slot.cooldownRemainingMs(currentTime) / 1000.0}%.1fs)")
    else if combat.playerMana < skill.manaCost then
      Left(s"Not enough mana (${skill.manaCost} required)")
    else
      // Execute skill
      val newCombat = if skill.castTimeMs > 0 then
        combat.copy(
          playerMana = combat.playerMana - skill.manaCost,
          castingSkill = Some(CastingState(slotIndex, skill, currentTime, currentTime + skill.castTimeMs))
        )
      else
        val (executed, _) = executeSkill(combat, slotIndex, skill, currentTime)
        executed

      Right(game.copy(adventureState = game.adventureState.copy(combatState = Some(newCombat))))

  private def executeSkill(
    combat: CombatState,
    slotIndex: Int,
    skill: CombatSkill,
    currentTime: Long
  ): (CombatState, Vector[CombatEvent]) =
    var c = combat.copy(
      globalCooldownEndsAt = currentTime + GlobalCooldownMs,
      playerMana = combat.playerMana - skill.manaCost
    )
    var events = Vector.empty[CombatEvent]
    var totalDamage = 0

    // Apply base damage
    if skill.damage > 0 then
      val buffedDamage = applyDamageBuff(skill.damage, c.playerDamageBuff)
      c = c.copy(enemyCurrentHp = (c.enemyCurrentHp - buffedDamage).max(0), playerDamageBuff = None)
      totalDamage = buffedDamage

    // Apply effects
    skill.effects.foreach {
      case SkillEffect.Damage(amount) =>
        c = c.copy(enemyCurrentHp = (c.enemyCurrentHp - amount).max(0))
        totalDamage += amount
      case SkillEffect.DamageOverTime(dpt, ticks, interval) =>
        c = c.copy(enemyDoTs = c.enemyDoTs :+ ActiveDoT(skill.name, dpt, ticks, interval, currentTime))
      case SkillEffect.Stun(duration) =>
        c = c.copy(enemyStun = Some(ActiveStun(currentTime + duration)))
        events :+= CombatEvent.EnemyStunned(duration)
      case SkillEffect.Freeze(chancePercent, duration) =>
        // Random chance to freeze
        val roll = scala.util.Random.nextInt(100)
        if roll < chancePercent then
          c = c.copy(enemyFreeze = Some(ActiveFreeze(currentTime + duration)))
          events :+= CombatEvent.EnemyFrozen(duration)
      case SkillEffect.ConsumeFreeze(bonusDamagePercent) =>
        // If enemy is frozen, consume it for bonus damage
        c.enemyFreeze match
          case Some(freeze) if freeze.endsAt > currentTime =>
            val bonusDamage = (skill.damage * bonusDamagePercent).toInt
            c = c.copy(
              enemyCurrentHp = (c.enemyCurrentHp - bonusDamage).max(0),
              enemyFreeze = None  // Consume the freeze
            )
            totalDamage += bonusDamage
            events :+= CombatEvent.FreezeConsumed(bonusDamage)
          case _ => // No freeze to consume
      case SkillEffect.Heal(amount) =>
        val healed = amount.min(c.playerMaxHp - c.playerCurrentHp)
        c = c.copy(playerCurrentHp = c.playerCurrentHp + healed)
        events :+= CombatEvent.PlayerHealed(healed)
      case SkillEffect.LifeDrain(percent) =>
        val heal = (totalDamage * percent).toInt.min(c.playerMaxHp - c.playerCurrentHp)
        c = c.copy(playerCurrentHp = c.playerCurrentHp + heal)
        if heal > 0 then events :+= CombatEvent.PlayerHealed(heal)
      case SkillEffect.Shield(amount, duration) =>
        c = c.copy(playerShield = Some(ActiveShield(amount, currentTime + duration)))
        events :+= CombatEvent.ShieldApplied(amount)
      case SkillEffect.IncreaseNextDamage(percent) =>
        c = c.copy(playerDamageBuff = Some(ActiveDamageBuff(percent)))
        events :+= CombatEvent.DamageBuffApplied(percent)
    }

    events :+= CombatEvent.PlayerSkillUsed(skill.name, totalDamage)

    // Update skill slot
    val slot = c.skillSlots(slotIndex)
    val newSlot = skill.chainInto match
      case Some(chain) => slot.copy(currentSkill = chain.skill, cooldownEndsAt = currentTime + skill.cooldownMs, chainWindowEndsAt = currentTime + chain.windowMs)
      case None => slot.copy(currentSkill = slot.baseSkill, cooldownEndsAt = currentTime + skill.cooldownMs, chainWindowEndsAt = 0L)

    c = c.copy(
      skillSlots = c.skillSlots.updated(slotIndex, newSlot),
      recentEvents = (c.recentEvents ++ events).takeRight(10),
      totalEventCount = c.totalEventCount + events.length
    )

    (c, events)

  private def executeCastedSkill(
    combat: CombatState,
    slotIndex: Int,
    skill: CombatSkill,
    currentTime: Long
  ): (CombatState, Vector[CombatEvent]) =
    // Same as executeSkill but no mana cost (already paid) and no GCD
    var c = combat
    var events = Vector.empty[CombatEvent]
    var totalDamage = 0

    if skill.damage > 0 then
      val buffedDamage = applyDamageBuff(skill.damage, c.playerDamageBuff)
      c = c.copy(enemyCurrentHp = (c.enemyCurrentHp - buffedDamage).max(0), playerDamageBuff = None)
      totalDamage = buffedDamage

    skill.effects.foreach {
      case SkillEffect.Damage(amount) =>
        c = c.copy(enemyCurrentHp = (c.enemyCurrentHp - amount).max(0))
        totalDamage += amount
      case SkillEffect.DamageOverTime(dpt, ticks, interval) =>
        c = c.copy(enemyDoTs = c.enemyDoTs :+ ActiveDoT(skill.name, dpt, ticks, interval, currentTime))
      case SkillEffect.Stun(duration) =>
        c = c.copy(enemyStun = Some(ActiveStun(currentTime + duration)))
        events :+= CombatEvent.EnemyStunned(duration)
      case SkillEffect.Freeze(chancePercent, duration) =>
        val roll = scala.util.Random.nextInt(100)
        if roll < chancePercent then
          c = c.copy(enemyFreeze = Some(ActiveFreeze(currentTime + duration)))
          events :+= CombatEvent.EnemyFrozen(duration)
      case SkillEffect.ConsumeFreeze(bonusDamagePercent) =>
        c.enemyFreeze match
          case Some(freeze) if freeze.endsAt > currentTime =>
            val bonusDamage = (skill.damage * bonusDamagePercent).toInt
            c = c.copy(
              enemyCurrentHp = (c.enemyCurrentHp - bonusDamage).max(0),
              enemyFreeze = None
            )
            totalDamage += bonusDamage
            events :+= CombatEvent.FreezeConsumed(bonusDamage)
          case _ =>
      case SkillEffect.Heal(amount) =>
        val healed = amount.min(c.playerMaxHp - c.playerCurrentHp)
        c = c.copy(playerCurrentHp = c.playerCurrentHp + healed)
        events :+= CombatEvent.PlayerHealed(healed)
      case SkillEffect.LifeDrain(percent) =>
        val heal = (totalDamage * percent).toInt.min(c.playerMaxHp - c.playerCurrentHp)
        c = c.copy(playerCurrentHp = c.playerCurrentHp + heal)
        if heal > 0 then events :+= CombatEvent.PlayerHealed(heal)
      case SkillEffect.Shield(amount, duration) =>
        c = c.copy(playerShield = Some(ActiveShield(amount, currentTime + duration)))
        events :+= CombatEvent.ShieldApplied(amount)
      case SkillEffect.IncreaseNextDamage(percent) =>
        c = c.copy(playerDamageBuff = Some(ActiveDamageBuff(percent)))
        events :+= CombatEvent.DamageBuffApplied(percent)
    }

    events :+= CombatEvent.PlayerSkillUsed(skill.name, totalDamage)

    val slot = c.skillSlots(slotIndex)
    val newSlot = skill.chainInto match
      case Some(chain) => slot.copy(currentSkill = chain.skill, cooldownEndsAt = currentTime + skill.cooldownMs, chainWindowEndsAt = currentTime + chain.windowMs)
      case None => slot.copy(currentSkill = slot.baseSkill, cooldownEndsAt = currentTime + skill.cooldownMs, chainWindowEndsAt = 0L)

    c = c.copy(
      skillSlots = c.skillSlots.updated(slotIndex, newSlot),
      recentEvents = (c.recentEvents ++ events).takeRight(10),
      totalEventCount = c.totalEventCount + events.length
    )

    (c, events)

  // ============================================================================
  // Combat Control
  // ============================================================================

  def stopCombat(game: VelorIdleGame): VelorIdleGame =
    game.copy(adventureState = game.adventureState.copy(inCombat = false, combatState = None))

  def restartCombat(game: VelorIdleGame, currentTime: Long): Either[String, VelorIdleGame] =
    game.adventureState.selectedEnemyId match
      case None => Left("No enemy selected")
      case Some(enemyId) => startCombat(game, enemyId, currentTime)

