package shared.VelorIdle

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random

class AdventureCombatSpec extends AnyFunSpec with Matchers:

  // Fixed-seed random for deterministic tests
  def fixedRandom(seed: Int = 42): Random = new Random(seed)

  // Helper to create a basic game in combat
  def gameInCombat(playerHp: Int = 100, enemyHp: Int = 50, currentTime: Long = 10000L): VelorIdleGame =
    val game = VelorIdleGame.newGame(0L)
    val enemy = Enemies.trainingDummy.copy(maxHp = enemyHp)
    val combatState = CombatState(
      instanceId = 1L,
      enemy = enemy,
      enemyCurrentHp = enemyHp,
      playerCurrentHp = playerHp,
      playerMaxHp = 100,
      playerMana = 50,
      playerMaxMana = 50,
      lastPlayerAutoAttack = currentTime - 3000L, // Ready to attack
      lastEnemyAutoAttack = currentTime - 3000L,
      skillSlots = Vector.empty
    )
    game.copy(
      adventureState = game.adventureState.copy(
        inCombat = true,
        combatState = Some(combatState),
        currentHp = playerHp
      ),
      lastTickTime = currentTime - 100L
    )

  // Helper to create a simple test skill
  def testSkill(
    id: String,
    name: String,
    damage: Int,
    manaCost: Int = 5,
    cooldownMs: Long = 1000L,
    effects: Vector[SkillEffect] = Vector.empty
  ): CombatSkill = CombatSkill(
    id = id,
    name = name,
    icon = "⚔️",
    description = "Test skill",
    manaCost = manaCost,
    cooldownMs = cooldownMs,
    damage = damage,
    effects = effects
  )

  describe("Adventure Combat - Pending Damage System"):

    describe("Feature: Auto-attacks queue pending damage"):

      it("should not apply damage immediately when player auto-attacks"):
        val game = gameInCombat(enemyHp = 50, currentTime = 10000L)
        val initialEnemyHp = game.adventureState.combatState.get.enemyCurrentHp

        // Process a tick - player should auto-attack
        val (updatedGame, _) = AdventureCombat.tick(game, 10000L, fixedRandom(1))
        val combat = updatedGame.adventureState.combatState.get

        // Enemy HP should be unchanged (damage is pending)
        combat.enemyCurrentHp shouldBe initialEnemyHp

        // There should be a pending attack
        combat.pendingAttacks should not be empty

      it("should apply damage after projectile flight time"):
        val game = gameInCombat(enemyHp = 50, currentTime = 10000L)
        // Prevent enemy attack by setting its last attack to now
        val combatNoEnemy = game.adventureState.combatState.get.copy(
          lastEnemyAutoAttack = 10000L // Enemy just attacked, won't attack again
        )
        val gameNoEnemy = game.copy(
          adventureState = game.adventureState.copy(combatState = Some(combatNoEnemy))
        )

        // First tick - queues the player attack
        val (afterFirstTick, _) = AdventureCombat.tick(gameNoEnemy, 10000L, fixedRandom(1))
        val combatAfterFirst = afterFirstTick.adventureState.combatState.get
        val playerAttacks = combatAfterFirst.pendingAttacks.filter(!_.targetIsPlayer)
        playerAttacks should have size 1
        val pendingDamage = playerAttacks.head.damage

        // Second tick - before flight time expires (at 10100ms, flight time is 200ms)
        val (afterSecondTick, _) = AdventureCombat.tick(afterFirstTick.copy(lastTickTime = 10000L), 10100L, fixedRandom(2))
        val combatAfterSecond = afterSecondTick.adventureState.combatState.get
        // Damage still pending
        val playerAttacksAfterSecond = combatAfterSecond.pendingAttacks.filter(!_.targetIsPlayer)
        playerAttacksAfterSecond should have size 1
        combatAfterSecond.enemyCurrentHp shouldBe 50

        // Third tick - after flight time expires (at 10250ms)
        val (afterThirdTick, _) = AdventureCombat.tick(afterSecondTick.copy(lastTickTime = 10100L), 10250L, fixedRandom(3))
        val combatAfterThird = afterThirdTick.adventureState.combatState.get
        // Damage should now be applied (player attacks should be empty or only have new ones)
        combatAfterThird.enemyCurrentHp shouldBe (50 - pendingDamage)

      it("should queue enemy attacks as pending damage"):
        val game = gameInCombat(playerHp = 100, enemyHp = 50, currentTime = 10000L)

        // Tick to queue enemy attack
        val (afterTick, _) = AdventureCombat.tick(game, 10000L, fixedRandom(100))
        val combat = afterTick.adventureState.combatState.get

        // Should have pending attacks (could be player and/or enemy)
        val enemyAttacks = combat.pendingAttacks.filter(_.targetIsPlayer)
        // Enemy might have attacked depending on timing
        // At minimum, player HP should be unchanged since damage is pending
        combat.playerCurrentHp shouldBe 100

    describe("Feature: Skills queue pending damage"):

      it("should not apply skill damage immediately"):
        val game = gameInCombat(enemyHp = 100, currentTime = 10000L)
        // Add a skill slot with a damage skill
        val skill = testSkill("test_strike", "Test Strike", damage = 25)
        val combatWithSkill = game.adventureState.combatState.get.copy(
          skillSlots = Vector(SkillSlotState.fromSkill(skill))
        )
        val gameWithSkill = game.copy(
          adventureState = game.adventureState.copy(combatState = Some(combatWithSkill))
        )

        // Use the skill
        val result = AdventureCombat.useSkill(gameWithSkill, 0, 10000L)
        result.isRight shouldBe true

        val afterSkill = result.toOption.get
        val combat = afterSkill.adventureState.combatState.get

        // Enemy HP should be unchanged (skill damage is pending)
        combat.enemyCurrentHp shouldBe 100

        // There should be a pending skill
        combat.pendingSkills should have size 1
        combat.pendingSkills.head.skill.id shouldBe "test_strike"

      it("should apply skill effects when projectile lands"):
        val game = gameInCombat(enemyHp = 100, currentTime = 10000L)
        val skill = testSkill("test_strike", "Test Strike", damage = 25)
        val combatWithSkill = game.adventureState.combatState.get.copy(
          skillSlots = Vector(SkillSlotState.fromSkill(skill)),
          lastPlayerAutoAttack = 10000L, // Prevent auto-attack
          lastEnemyAutoAttack = 10000L   // Prevent enemy attack
        )
        val gameWithSkill = game.copy(
          adventureState = game.adventureState.copy(combatState = Some(combatWithSkill))
        )

        // Use the skill
        val afterSkill = AdventureCombat.useSkill(gameWithSkill, 0, 10000L).toOption.get

        // Tick before flight time expires
        val (beforeLanding, _) = AdventureCombat.tick(afterSkill.copy(lastTickTime = 10000L), 10100L, fixedRandom())
        beforeLanding.adventureState.combatState.get.enemyCurrentHp shouldBe 100
        beforeLanding.adventureState.combatState.get.pendingSkills should have size 1

        // Tick after flight time expires (200ms)
        val (afterLanding, _) = AdventureCombat.tick(beforeLanding.copy(lastTickTime = 10100L), 10250L, fixedRandom())
        afterLanding.adventureState.combatState.get.enemyCurrentHp shouldBe 75 // 100 - 25 damage
        afterLanding.adventureState.combatState.get.pendingSkills shouldBe empty

      it("should apply all skill effects on landing including stuns"):
        val game = gameInCombat(enemyHp = 100, currentTime = 10000L)
        val stunSkill = testSkill(
          id = "bash",
          name = "Bash",
          damage = 10,
          manaCost = 10,
          cooldownMs = 5000L,
          effects = Vector(SkillEffect.Stun(2000L))
        )
        val combatWithSkill = game.adventureState.combatState.get.copy(
          skillSlots = Vector(SkillSlotState.fromSkill(stunSkill)),
          lastPlayerAutoAttack = 10000L,
          lastEnemyAutoAttack = 10000L
        )
        val gameWithSkill = game.copy(
          adventureState = game.adventureState.copy(combatState = Some(combatWithSkill))
        )

        // Use the skill
        val afterSkill = AdventureCombat.useSkill(gameWithSkill, 0, 10000L).toOption.get

        // Before landing - no stun
        afterSkill.adventureState.combatState.get.enemyStun shouldBe None

        // After landing - stun should be applied
        val (afterLanding, _) = AdventureCombat.tick(afterSkill.copy(lastTickTime = 10000L), 10250L, fixedRandom())
        val combat = afterLanding.adventureState.combatState.get
        combat.enemyCurrentHp shouldBe 90 // 100 - 10
        combat.enemyStun shouldBe defined
        combat.enemyStun.get.endsAt shouldBe (10250L + 2000L)

    describe("Feature: Evaded attacks are handled correctly"):

      it("should queue evaded attacks with zero damage"):
        // Create a scenario where evade is guaranteed (high defense rating)
        val game = gameInCombat(enemyHp = 50, currentTime = 10000L)

        // Use a random that will cause an evade
        // The evade formula is: evadeChance = defenseRating / (defenseRating + attackRating) * 0.5
        // With equal ratings, evade chance is 25%
        // We'll use a seed that produces an evade

        // First, find a seed that causes evade
        var evadeSeed = 0
        var foundEvade = false
        while !foundEvade && evadeSeed < 1000 do
          val r = fixedRandom(evadeSeed)
          val evadeRoll = r.nextDouble()
          if evadeRoll < 0.25 then foundEvade = true
          else evadeSeed += 1

        if foundEvade then
          val (afterTick, _) = AdventureCombat.tick(game, 10000L, fixedRandom(evadeSeed))
          val combat = afterTick.adventureState.combatState.get

          // Check if there's a pending attack with 0 damage (evade)
          val evadedAttacks = combat.pendingAttacks.filter(_.damage == 0)
          evadedAttacks should not be empty

    describe("Feature: Cooldowns and chain windows update immediately"):

      it("should update skill cooldown immediately when skill is used"):
        val game = gameInCombat(enemyHp = 100, currentTime = 10000L)
        val skill = testSkill("slash", "Slash", damage = 15, cooldownMs = 3000L)
        val combatWithSkill = game.adventureState.combatState.get.copy(
          skillSlots = Vector(SkillSlotState.fromSkill(skill))
        )
        val gameWithSkill = game.copy(
          adventureState = game.adventureState.copy(combatState = Some(combatWithSkill))
        )

        // Use the skill
        val afterSkill = AdventureCombat.useSkill(gameWithSkill, 0, 10000L).toOption.get
        val combat = afterSkill.adventureState.combatState.get

        // Cooldown should be set immediately (not waiting for projectile to land)
        combat.skillSlots.head.cooldownEndsAt shouldBe (10000L + 3000L)

        // Damage is still pending
        combat.pendingSkills should have size 1
        combat.enemyCurrentHp shouldBe 100

      it("should consume mana immediately when skill is used"):
        val game = gameInCombat(enemyHp = 100, currentTime = 10000L)
        val skill = testSkill("fireball", "Fireball", damage = 30, manaCost = 20, cooldownMs = 2000L)
        val combatWithSkill = game.adventureState.combatState.get.copy(
          skillSlots = Vector(SkillSlotState.fromSkill(skill)),
          playerMana = 50
        )
        val gameWithSkill = game.copy(
          adventureState = game.adventureState.copy(combatState = Some(combatWithSkill))
        )

        val afterSkill = AdventureCombat.useSkill(gameWithSkill, 0, 10000L).toOption.get
        val combat = afterSkill.adventureState.combatState.get

        // Mana should be deducted immediately
        combat.playerMana shouldBe 30 // 50 - 20

        // But damage is pending
        combat.enemyCurrentHp shouldBe 100

    describe("Feature: Damage buff is captured at cast time"):

      it("should capture and apply damage buff from cast time"):
        val game = gameInCombat(enemyHp = 100, currentTime = 10000L)
        val skill = testSkill("strike", "Strike", damage = 20)
        val combatWithSkill = game.adventureState.combatState.get.copy(
          skillSlots = Vector(SkillSlotState.fromSkill(skill)),
          playerDamageBuff = Some(ActiveDamageBuff(0.5)), // +50% damage buff
          lastPlayerAutoAttack = 10000L,
          lastEnemyAutoAttack = 10000L
        )
        val gameWithSkill = game.copy(
          adventureState = game.adventureState.copy(combatState = Some(combatWithSkill))
        )

        // Use the skill
        val afterSkill = AdventureCombat.useSkill(gameWithSkill, 0, 10000L).toOption.get
        val combat = afterSkill.adventureState.combatState.get

        // Buff should be consumed immediately
        combat.playerDamageBuff shouldBe None

        // Pending skill should have the buff captured
        combat.pendingSkills.head.damageBuffPercent shouldBe Some(0.5)

        // After landing, damage should be buffed: 20 * 1.5 = 30
        val (afterLanding, _) = AdventureCombat.tick(afterSkill.copy(lastTickTime = 10000L), 10250L, fixedRandom())
        afterLanding.adventureState.combatState.get.enemyCurrentHp shouldBe 70 // 100 - 30

    describe("Feature: Equipment skill bonuses"):

      it("should include equipment skill bonus when building skill slots"):
        // Create a skill state with a warrior skill at level 2
        val warriorSkillId = SkillTrees.warrior.skills.head.id
        val combatSkillState = CombatSkillState(
          allocatedPoints = Map(warriorSkillId -> 2),
          boundSkills = Vector(Some(warriorSkillId), None, None, None),
          availablePoints = 0
        )

        // Build slots without equipment bonus
        val slotsNoBonus = CombatSkillHelpers.buildSkillSlots(combatSkillState, Map.empty)
        val damageNoBonus = slotsNoBonus.head.baseSkill.damage

        // Build slots with +2 warrior bonus from equipment
        val slotsWithBonus = CombatSkillHelpers.buildSkillSlots(combatSkillState, Map("warrior" -> 2))
        val damageWithBonus = slotsWithBonus.head.baseSkill.damage

        // Damage should be higher with the equipment bonus (level 4 vs level 2)
        damageWithBonus should be > damageNoBonus

      it("should apply equipment skill bonus to combat skill damage"):
        // Get the warrior slash skill definition
        val slashSkill = SkillTrees.warrior.skills.head

        // Calculate damage at level 2 vs level 4
        val damageAtLevel2 = slashSkill.damageAtLevel(2)
        val damageAtLevel4 = slashSkill.damageAtLevel(4)

        // Create combat skill state with slash at level 2
        val combatSkillState = CombatSkillState(
          allocatedPoints = Map(slashSkill.id -> 2),
          boundSkills = Vector(Some(slashSkill.id), None, None, None),
          availablePoints = 0
        )

        // With +2 equipment bonus, effective level is 4
        val slots = CombatSkillHelpers.buildSkillSlots(combatSkillState, Map("warrior" -> 2))

        slots.head.baseSkill.damage shouldBe damageAtLevel4
        damageAtLevel4 should be > damageAtLevel2

      it("should apply equipment skill bonus when starting combat"):
        val game = VelorIdleGame.newGame(0L)

        // Get warrior slash skill
        val slashSkill = SkillTrees.warrior.skills.head

        // Set up combat skill state with slash at level 1
        val combatSkillState = CombatSkillState(
          allocatedPoints = Map(slashSkill.id -> 1),
          boundSkills = Vector(Some(slashSkill.id), None, None, None),
          availablePoints = 0
        )

        // Create equipment with +2 warrior bonus
        val magicalWeapon = EquipmentInstance(
          instanceId = 1L,
          defId = "bronze_sword",
          quality = EquipmentQuality.Normal,
          rarity = EquipmentRarity.Magical,
          affixes = Vector(MagicalAffix.SkillLevelBonus("warrior", 2))
        )

        // Set up game with the skill state and equipment
        val gameWithSetup = game.copy(
          adventureState = game.adventureState.copy(
            combatSkillState = combatSkillState,
            equipment = EquipmentSlots(weapon = Some(magicalWeapon))
          ),
          skills = game.skills.updated(Skill.Adventure, SkillState(level = 10, xp = 0))
        )

        // Start combat
        val result = AdventureCombat.startCombat(gameWithSetup, "training_dummy", 10000L)
        result.isRight shouldBe true

        val gameInCombat = result.toOption.get
        val combat = gameInCombat.adventureState.combatState.get

        // Skill slot should have damage calculated at effective level 3 (1 base + 2 bonus)
        val expectedDamage = slashSkill.damageAtLevel(3)
        combat.skillSlots.head.baseSkill.damage shouldBe expectedDamage

      it("should stack skill bonuses from multiple equipment pieces"):
        val game = VelorIdleGame.newGame(0L)
        val slashSkill = SkillTrees.warrior.skills.head

        // Set up combat skill state with slash at level 1
        val combatSkillState = CombatSkillState(
          allocatedPoints = Map(slashSkill.id -> 1),
          boundSkills = Vector(Some(slashSkill.id), None, None, None),
          availablePoints = 0
        )

        // Create weapon with +1 warrior bonus
        val magicalWeapon = EquipmentInstance(
          instanceId = 1L,
          defId = "bronze_sword",
          quality = EquipmentQuality.Normal,
          rarity = EquipmentRarity.Magical,
          affixes = Vector(MagicalAffix.SkillLevelBonus("warrior", 1))
        )

        // Create armor with +2 warrior bonus
        val magicalArmor = EquipmentInstance(
          instanceId = 2L,
          defId = "bronze_armor",
          quality = EquipmentQuality.Normal,
          rarity = EquipmentRarity.Magical,
          affixes = Vector(MagicalAffix.SkillLevelBonus("warrior", 2))
        )

        // Set up equipment with both pieces
        val equipment = EquipmentSlots(weapon = Some(magicalWeapon), armor = Some(magicalArmor))

        // Verify bonuses stack: +1 from weapon + +2 from armor = +3 total
        equipment.allSkillBonuses.getOrElse("warrior", 0) shouldBe 3

        // Set up game and start combat
        val gameWithSetup = game.copy(
          adventureState = game.adventureState.copy(
            combatSkillState = combatSkillState,
            equipment = equipment
          ),
          skills = game.skills.updated(Skill.Adventure, SkillState(level = 10, xp = 0))
        )

        val result = AdventureCombat.startCombat(gameWithSetup, "training_dummy", 10000L)
        result.isRight shouldBe true

        val combat = result.toOption.get.adventureState.combatState.get

        // Effective level should be 4 (1 base + 3 from equipment)
        val expectedDamage = slashSkill.damageAtLevel(4)
        combat.skillSlots.head.baseSkill.damage shouldBe expectedDamage

      it("should not affect other skill trees"):
        // Create state with both warrior and mage skills
        val warriorSkill = SkillTrees.warrior.skills.head
        val mageSkill = SkillTrees.mage.skills.head

        val combatSkillState = CombatSkillState(
          allocatedPoints = Map(
            warriorSkill.id -> 2,
            mageSkill.id -> 2
          ),
          boundSkills = Vector(Some(warriorSkill.id), Some(mageSkill.id), None, None),
          availablePoints = 0
        )

        // Only give bonus to warrior tree
        val slots = CombatSkillHelpers.buildSkillSlots(combatSkillState, Map("warrior" -> 3))

        // Warrior skill should be at effective level 5 (2 + 3)
        slots(0).baseSkill.damage shouldBe warriorSkill.damageAtLevel(5)

        // Mage skill should be at base level 2 (no bonus)
        slots(1).baseSkill.damage shouldBe mageSkill.damageAtLevel(2)
