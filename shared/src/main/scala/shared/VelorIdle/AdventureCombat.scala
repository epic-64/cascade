package shared.VelorIdle

import scala.util.Random
import scala.util.chaining.*

/** Combat logic for Adventure mode */
object AdventureCombat:

  // Global cooldown duration in milliseconds
  val GlobalCooldownMs: Long = 1000L

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
        val weapon = advState.equippedWeapon
        val armor = advState.equippedArmor

        // Use persisted player stats
        val maxHp = advState.maxHp
        val maxMana = advState.maxMana
        val currentHp = advState.currentHp.min(maxHp)  // Cap at max in case equipment changed
        val currentMana = advState.currentMana.min(maxMana)

        // Build skill slots from weapon (slots 0-3) and armor (slots 4-7)
        val weaponSlots = weapon.skills.map(SkillSlotState.fromSkill)
        val armorSlots = armor.map(_.skills.map(SkillSlotState.fromSkill)).getOrElse(
          Vector.fill(4)(SkillSlotState.fromSkill(emptySkill))
        )

        val combatState = CombatState(
          enemy = enemy,
          enemyCurrentHp = enemy.maxHp,
          playerCurrentHp = currentHp,
          playerMaxHp = maxHp,
          playerMana = currentMana,
          playerMaxMana = maxMana,
          lastPlayerAutoAttack = currentTime,
          lastEnemyAutoAttack = currentTime,
          skillSlots = weaponSlots ++ armorSlots
        )

        val newAdventureState = advState.copy(
          inCombat = true,
          combatState = Some(combatState),
          selectedEnemyId = Some(enemyId)
        )

        Right(game.copy(
          currentSkill = Some(Skill.Adventure),
          activeAction = ActiveAction.Adventure,
          adventureState = newAdventureState
        ))

  /** Empty skill placeholder for armor slots when no armor equipped */
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
  // Combat Tick
  // ============================================================================

  /** Process one tick of combat. Returns updated game and events. */
  def tick(
    game: VelorIdleGame,
    currentTime: Long,
    random: Random = Random
  ): (VelorIdleGame, Vector[GameEvent]) =
    game.adventureState.combatState match
      case None =>
        // Not in combat - nothing to do (use Rest action for regen)
        (game.copy(lastTickTime = currentTime), Vector.empty)
      case Some(combat) if combat.isEnemyDead =>
        // Enemy is dead (killed by skill) - handle death and auto-restart
        val (_, endEvents, updatedGame) = handleEnemyDeath(combat, game, random, currentTime)
        (updatedGame.copy(lastTickTime = currentTime), endEvents)
      case Some(combat) if combat.isPlayerDead =>
        // Player died - nothing to do (use Rest action for regen)
        (game.copy(lastTickTime = currentTime), Vector.empty)
      case Some(combat) =>
        val elapsedMs = currentTime - game.lastTickTime
        val elapsedSeconds = elapsedMs / 1000.0

        // Process casting first - if a cast completes, execute the skill
        val (combat0, castEvents) = processCasting(combat, currentTime)
        
        // Process in order: DoTs, auto-attacks, mana regen, chain windows
        val (combat1, events1) = processDoTs(combat0, currentTime)
        val (combat2, events2) = processAutoAttacks(combat1, currentTime, game.adventureState.equippedWeapon, random)
        val combat3 = processManaRegen(combat2, elapsedSeconds)
        val combat4 = processChainWindows(combat3, currentTime)
        val combat5 = processStuns(combat4, currentTime)
        val combat6 = processShields(combat5, currentTime)

        // Check for combat end
        val (finalCombat, endEvents, updatedGame) =
          if combat6.isEnemyDead then
            handleEnemyDeath(combat6, game, random, currentTime)
          else if combat6.isPlayerDead then
            val newCount = combat6.totalEventCount + 1
            (combat6.copy(
              recentEvents = combat6.recentEvents :+ CombatEvent.PlayerDied,
              totalEventCount = newCount
            ), Vector(GameEvent.AdventurePlayerDied), game)
          else
            (combat6, Vector.empty, game)

        // If enemy died and combat was restarted, use the already-updated game from handleEnemyDeath
        // (which includes the new combat state). Otherwise, update combat events normally.
        val newGame = if combat6.isEnemyDead then
          updatedGame.copy(lastTickTime = currentTime)
        else
          val allCombatEvents = castEvents ++ events1 ++ events2
          val newEventCount = finalCombat.totalEventCount + allCombatEvents.length
          val finalCombatWithEvents = finalCombat.copy(
            recentEvents = (finalCombat.recentEvents ++ allCombatEvents).takeRight(10),
            totalEventCount = newEventCount
          )

          // Sync player HP/Mana back to AdventureState
          val newAdventureState = updatedGame.adventureState.copy(
            combatState = Some(finalCombatWithEvents),
            currentHp = finalCombatWithEvents.playerCurrentHp,
            currentMana = finalCombatWithEvents.playerMana
          )
          updatedGame.copy(adventureState = newAdventureState, lastTickTime = currentTime)

        (newGame, endEvents)


  private def processDoTs(combat: CombatState, currentTime: Long): (CombatState, Vector[CombatEvent]) =
    var enemyHp = combat.enemyCurrentHp
    var playerHp = combat.playerCurrentHp
    var events = Vector.empty[CombatEvent]

    // Process enemy DoTs (damage to enemy)
    val updatedEnemyDoTs = combat.enemyDoTs.flatMap { dot =>
      if currentTime >= dot.lastTickTime + dot.tickIntervalMs then
        enemyHp -= dot.damagePerTick
        events :+= CombatEvent.EnemyDotTick(dot.damagePerTick, dot.name)
        val remaining = dot.ticksRemaining - 1
        if remaining > 0 then
          Some(dot.copy(ticksRemaining = remaining, lastTickTime = currentTime))
        else None
      else Some(dot)
    }

    // Process player DoTs (damage to player)
    val updatedPlayerDoTs = combat.playerDoTs.flatMap { dot =>
      if currentTime >= dot.lastTickTime + dot.tickIntervalMs then
        playerHp -= dot.damagePerTick
        events :+= CombatEvent.PlayerDotTick(dot.damagePerTick, dot.name)
        val remaining = dot.ticksRemaining - 1
        if remaining > 0 then
          Some(dot.copy(ticksRemaining = remaining, lastTickTime = currentTime))
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
    weapon: Weapon,
    random: Random
  ): (CombatState, Vector[CombatEvent]) =
    var c = combat
    var events = Vector.empty[CombatEvent]

    // Player auto-attack
    if currentTime >= c.lastPlayerAutoAttack + weapon.attackSpeedMs then
      val damage = calculatePlayerDamage(weapon.attackDamage, c.playerDamageBuff)
      c = c.copy(
        enemyCurrentHp = (c.enemyCurrentHp - damage).max(0),
        lastPlayerAutoAttack = currentTime,
        playerDamageBuff = None  // Consume damage buff on auto-attack
      )
      events :+= CombatEvent.PlayerAutoAttack(damage)

    // Enemy auto-attack (only if not stunned)
    val enemyStunned = c.enemyStun.exists(s => currentTime < s.endsAt)
    if !enemyStunned && currentTime >= c.lastEnemyAutoAttack + c.enemy.attackSpeedMs then
      val (newHp, newShield, shieldBroken) = applyDamageWithShield(
        c.playerCurrentHp,
        c.playerShield,
        c.enemy.attackDamage,
        currentTime
      )
      c = c.copy(
        playerCurrentHp = newHp.max(0),
        playerShield = newShield,
        lastEnemyAutoAttack = currentTime
      )
      events :+= CombatEvent.EnemyAutoAttack(c.enemy.attackDamage)
      if shieldBroken then events :+= CombatEvent.ShieldBroken

    (c, events)

  private def calculatePlayerDamage(baseDamage: Int, buff: Option[ActiveDamageBuff]): Int =
    buff match
      case Some(b) => (baseDamage * (1.0 + b.percent)).toInt
      case None => baseDamage

  private def applyDamageWithShield(
    currentHp: Int,
    shield: Option[ActiveShield],
    damage: Int,
    currentTime: Long
  ): (Int, Option[ActiveShield], Boolean) =
    shield match
      case Some(s) if currentTime < s.endsAt =>
        if s.remainingAbsorb >= damage then
          (currentHp, Some(s.copy(remainingAbsorb = s.remainingAbsorb - damage)), false)
        else
          val overflow = damage - s.remainingAbsorb
          (currentHp - overflow, None, true)
      case _ =>
        (currentHp - damage, None, false)

  private def processCasting(combat: CombatState, currentTime: Long): (CombatState, Vector[CombatEvent]) =
    combat.castingSkill match
      case Some(casting) if casting.isComplete(currentTime) =>
        // Cast complete - execute the skill (no GCD after cast)
        val (newCombat, events) = executeCastedSkill(combat, casting.slotIndex, casting.skill, currentTime)
        (newCombat.copy(castingSkill = None), events)
      case _ =>
        (combat, Vector.empty)

  private def processManaRegen(combat: CombatState, elapsedSeconds: Double): CombatState =
    val regenAmount = (AdventureState.ManaRegenPerSecond * elapsedSeconds).toInt
    combat.copy(
      playerMana = (combat.playerMana + regenAmount).min(combat.playerMaxMana)
    )

  private def processChainWindows(combat: CombatState, currentTime: Long): CombatState =
    val updatedSlots = combat.skillSlots.map { slot =>
      if slot.isInChainWindow(currentTime) then slot
      else if slot.currentSkill.id != slot.baseSkill.id then
        // Chain window expired, revert to base skill
        slot.copy(currentSkill = slot.baseSkill, chainWindowEndsAt = 0L)
      else slot
    }
    combat.copy(skillSlots = updatedSlots)

  private def processStuns(combat: CombatState, currentTime: Long): CombatState =
    combat.enemyStun match
      case Some(stun) if currentTime >= stun.endsAt =>
        combat.copy(enemyStun = None)
      case _ => combat

  private def processShields(combat: CombatState, currentTime: Long): CombatState =
    combat.playerShield match
      case Some(shield) if currentTime >= shield.endsAt =>
        combat.copy(playerShield = None)
      case _ => combat

  private def handleEnemyDeath(
    combat: CombatState,
    game: VelorIdleGame,
    random: Random,
    currentTime: Long
  ): (CombatState, Vector[GameEvent], VelorIdleGame) =
    val enemy = combat.enemy
    var events = Vector.empty[GameEvent]
    var updatedGame = game

    // XP reward - actually grant it
    val skillState = updatedGame.skills.getOrElse(Skill.Adventure, SkillState.initial)
    val newXp = skillState.xp + enemy.xpReward
    val oldLevel = skillState.level
    val newLevel = SkillState.levelFromXp(newXp)
    val newSkillState = skillState.copy(xp = newXp, level = newLevel)
    updatedGame = updatedGame.copy(skills = updatedGame.skills.updated(Skill.Adventure, newSkillState))
    events :+= GameEvent.XpGained(Skill.Adventure, enemy.xpReward)
    if newLevel > oldLevel then
      events :+= GameEvent.LevelUp(Skill.Adventure, newLevel)

    // Gold reward - actually grant it
    val goldAmount = enemy.goldReward._1 + random.nextInt(enemy.goldReward._2 - enemy.goldReward._1 + 1)
    updatedGame = updatedGame.copy(gold = updatedGame.gold + goldAmount)
    events :+= GameEvent.GoldGained(goldAmount)

    // Loot - actually grant it
    enemy.lootTable.foreach { case (item, chance) =>
      if random.nextDouble() < chance then
        val (newInv, _) = updatedGame.inventory.addItem(item, 1)
        updatedGame = updatedGame.copy(inventory = newInv)
        events :+= GameEvent.ItemGained(item, 1)
    }

    events :+= GameEvent.AdventureEnemyDefeated(enemy.id)

    // Auto-restart combat with same enemy
    restartCombat(updatedGame, currentTime) match
      case Right(restarted) =>
        // Return the new combat state from the restarted game
        val newCombat = restarted.adventureState.combatState.getOrElse(combat)
        (newCombat, events, restarted)
      case Left(_) =>
        // Fallback: mark combat as over (shouldn't happen normally)
        val combatWithEvents = combat.copy(
          recentEvents = combat.recentEvents :+ CombatEvent.EnemyDied,
          totalEventCount = combat.totalEventCount + 1
        )
        (combatWithEvents, events, updatedGame)

  // ============================================================================
  // Skill Usage
  // ============================================================================

  /** Use a skill in the given slot (0-7). Slots 0-3 are weapon, 4-7 are armor. */
  def useSkill(
    game: VelorIdleGame,
    slotIndex: Int,
    currentTime: Long
  ): Either[String, VelorIdleGame] =
    game.adventureState.combatState match
      case None => Left("Not in combat")
      case Some(combat) if combat.isCombatOver => Left("Combat is over")
      case Some(combat) =>
        if slotIndex < 0 || slotIndex >= combat.skillSlots.length then
          Left(s"Invalid skill slot: $slotIndex")
        else
          val slot = combat.skillSlots(slotIndex)
          val skill = slot.currentSkill
          val isChainSkill = slot.isInChainWindow(currentTime) && skill.id != slot.baseSkill.id
          val isTrainingDummy = combat.enemy.id == "training_dummy"
          val effectiveManaCost = if isTrainingDummy then 0 else skill.manaCost

          // Check if skill is empty
          if skill.id == "empty" then
            Left("No skill in this slot")
          // Check if already casting
          else if combat.isCasting then
            Left("Already casting a skill")
          // Check global cooldown
          else if combat.isOnGlobalCooldown(currentTime) then
            val remaining = combat.globalCooldownRemainingMs(currentTime) / 1000.0
            Left(f"Global cooldown ($remaining%.1fs)")
          // Check cooldown - but allow chain skills during chain window
          else if slot.isOnCooldown(currentTime) && !isChainSkill then
            val remaining = slot.cooldownRemainingMs(currentTime) / 1000.0
            Left(f"${skill.name} on cooldown ($remaining%.1fs)")
          // Check mana (free against training dummy)
          else if combat.playerMana < effectiveManaCost then
            Left(s"Not enough mana (${skill.manaCost} required, ${combat.playerMana} available)")
          else
            // Check if skill has cast time
            if skill.castTimeMs > 0 then
              // Start casting - consume mana now, skill executes when cast completes
              val newCombat = combat.copy(
                playerMana = combat.playerMana - effectiveManaCost,
                castingSkill = Some(CastingState(
                  slotIndex = slotIndex,
                  skill = skill,
                  startedAt = currentTime,
                  completesAt = currentTime + skill.castTimeMs
                ))
              )
              val newAdventureState = game.adventureState.copy(combatState = Some(newCombat))
              Right(game.copy(adventureState = newAdventureState))
            else
              // Execute the skill immediately (with effective mana cost)
              val (newCombat, events) = executeSkill(combat, slotIndex, skill, currentTime, isTrainingDummy)
              val newAdventureState = game.adventureState.copy(combatState = Some(newCombat))
              Right(game.copy(adventureState = newAdventureState))

  private def executeSkill(
    combat: CombatState,
    slotIndex: Int,
    skill: CombatSkill,
    currentTime: Long,
    isTrainingDummy: Boolean = false
  ): (CombatState, Vector[CombatEvent]) =
    var c = combat
    var events = Vector.empty[CombatEvent]

    // Set global cooldown
    c = c.copy(globalCooldownEndsAt = currentTime + GlobalCooldownMs)    // Consume mana (free against training dummy)
    val effectiveManaCost = if isTrainingDummy then 0 else skill.manaCost
    c = c.copy(playerMana = c.playerMana - effectiveManaCost)

    // Apply base damage
    var totalDamage = skill.damage
    if skill.damage > 0 then
      val buffedDamage = calculatePlayerDamage(skill.damage, c.playerDamageBuff)
      c = c.copy(
        enemyCurrentHp = (c.enemyCurrentHp - buffedDamage).max(0),
        playerDamageBuff = None  // Consume buff
      )
      totalDamage = buffedDamage

    // Apply effects
    skill.effects.foreach {
      case SkillEffect.Damage(amount) =>
        c = c.copy(enemyCurrentHp = (c.enemyCurrentHp - amount).max(0))
        totalDamage += amount

      case SkillEffect.DamageOverTime(damagePerTick, ticks, interval) =>
        val dot = ActiveDoT(skill.name, damagePerTick, ticks, interval, currentTime)
        c = c.copy(enemyDoTs = c.enemyDoTs :+ dot)

      case SkillEffect.Stun(duration) =>
        c = c.copy(enemyStun = Some(ActiveStun(currentTime + duration)))
        events :+= CombatEvent.EnemyStunned(duration)

      case SkillEffect.Heal(amount) =>
        val healedAmount = amount.min(c.playerMaxHp - c.playerCurrentHp)
        c = c.copy(playerCurrentHp = c.playerCurrentHp + healedAmount)
        events :+= CombatEvent.PlayerHealed(healedAmount)

      case SkillEffect.LifeDrain(percent) =>
        val healAmount = (totalDamage * percent).toInt
        val actualHeal = healAmount.min(c.playerMaxHp - c.playerCurrentHp)
        c = c.copy(playerCurrentHp = c.playerCurrentHp + actualHeal)
        if actualHeal > 0 then events :+= CombatEvent.PlayerHealed(actualHeal)

      case SkillEffect.Shield(amount, duration) =>
        c = c.copy(playerShield = Some(ActiveShield(amount, currentTime + duration)))
        events :+= CombatEvent.ShieldApplied(amount)

      case SkillEffect.IncreaseNextDamage(percent) =>
        c = c.copy(playerDamageBuff = Some(ActiveDamageBuff(percent)))
        events :+= CombatEvent.DamageBuffApplied(percent)
    }

    events :+= CombatEvent.PlayerSkillUsed(skill.name, totalDamage)

    // Update skill slot: set cooldown and handle chain skill
    val slot = c.skillSlots(slotIndex)
    val newSlot = skill.chainInto match
      case Some(chain) =>
        slot.copy(
          currentSkill = chain.skill,
          cooldownEndsAt = currentTime + skill.cooldownMs,
          chainWindowEndsAt = currentTime + chain.windowMs
        )
      case None =>
        // If this was a chain skill, revert to base
        slot.copy(
          currentSkill = slot.baseSkill,
          cooldownEndsAt = currentTime + skill.cooldownMs,
          chainWindowEndsAt = 0L
        )

    c = c.copy(
      skillSlots = c.skillSlots.updated(slotIndex, newSlot),
      recentEvents = (c.recentEvents ++ events).takeRight(10),
      totalEventCount = c.totalEventCount + events.length
    )

    (c, events)

  /** Execute a skill after its cast time completes. No mana cost (already paid), no GCD. */
  private def executeCastedSkill(
    combat: CombatState,
    slotIndex: Int,
    skill: CombatSkill,
    currentTime: Long
  ): (CombatState, Vector[CombatEvent]) =
    var c = combat
    var events = Vector.empty[CombatEvent]

    // No GCD after cast completes
    // No mana cost - already consumed when cast started

    // Apply base damage
    var totalDamage = skill.damage
    if skill.damage > 0 then
      val buffedDamage = calculatePlayerDamage(skill.damage, c.playerDamageBuff)
      c = c.copy(
        enemyCurrentHp = (c.enemyCurrentHp - buffedDamage).max(0),
        playerDamageBuff = None  // Consume buff
      )
      totalDamage = buffedDamage

    // Apply effects
    skill.effects.foreach {
      case SkillEffect.Damage(amount) =>
        c = c.copy(enemyCurrentHp = (c.enemyCurrentHp - amount).max(0))
        totalDamage += amount

      case SkillEffect.DamageOverTime(damagePerTick, ticks, interval) =>
        val dot = ActiveDoT(skill.name, damagePerTick, ticks, interval, currentTime)
        c = c.copy(enemyDoTs = c.enemyDoTs :+ dot)

      case SkillEffect.Stun(duration) =>
        c = c.copy(enemyStun = Some(ActiveStun(currentTime + duration)))
        events :+= CombatEvent.EnemyStunned(duration)

      case SkillEffect.Heal(amount) =>
        val healedAmount = amount.min(c.playerMaxHp - c.playerCurrentHp)
        c = c.copy(playerCurrentHp = c.playerCurrentHp + healedAmount)
        events :+= CombatEvent.PlayerHealed(healedAmount)

      case SkillEffect.LifeDrain(percent) =>
        val healAmount = (totalDamage * percent).toInt
        val actualHeal = healAmount.min(c.playerMaxHp - c.playerCurrentHp)
        c = c.copy(playerCurrentHp = c.playerCurrentHp + actualHeal)
        if actualHeal > 0 then events :+= CombatEvent.PlayerHealed(actualHeal)

      case SkillEffect.Shield(amount, duration) =>
        c = c.copy(playerShield = Some(ActiveShield(amount, currentTime + duration)))
        events :+= CombatEvent.ShieldApplied(amount)

      case SkillEffect.IncreaseNextDamage(percent) =>
        c = c.copy(playerDamageBuff = Some(ActiveDamageBuff(percent)))
        events :+= CombatEvent.DamageBuffApplied(percent)
    }

    events :+= CombatEvent.PlayerSkillUsed(skill.name, totalDamage)

    // Update skill slot: set cooldown and handle chain skill
    val slot = c.skillSlots(slotIndex)
    val newSlot = skill.chainInto match
      case Some(chain) =>
        slot.copy(
          currentSkill = chain.skill,
          cooldownEndsAt = currentTime + skill.cooldownMs,
          chainWindowEndsAt = currentTime + chain.windowMs
        )
      case None =>
        // If this was a chain skill, revert to base
        slot.copy(
          currentSkill = slot.baseSkill,
          cooldownEndsAt = currentTime + skill.cooldownMs,
          chainWindowEndsAt = 0L
        )

    c = c.copy(
      skillSlots = c.skillSlots.updated(slotIndex, newSlot),
      recentEvents = (c.recentEvents ++ events).takeRight(10),
      totalEventCount = c.totalEventCount + events.length
    )

    (c, events)

  // ============================================================================
  // Combat Control
  // ============================================================================

  /** Stop combat and exit adventure mode */
  def stopCombat(game: VelorIdleGame): VelorIdleGame =
    val newAdventureState = game.adventureState.copy(
      inCombat = false,
      combatState = None
    )
    game.copy(adventureState = newAdventureState)

  /** Restart combat with the same enemy (after death or victory) */
  def restartCombat(game: VelorIdleGame, currentTime: Long): Either[String, VelorIdleGame] =
    game.adventureState.selectedEnemyId match
      case None => Left("No enemy selected")
      case Some(enemyId) => startCombat(game, enemyId, currentTime)

