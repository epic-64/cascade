package shared.VelorIdle

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random

class VelorIdleLogicSpec extends AnyFunSpec with Matchers:

  // Fixed-seed random for deterministic tests
  def fixedRandom(seed: Int = 42): Random = new Random(seed)

  describe("Velor Idle Game Logic"):

    // =========================================================================
    // XP and Leveling
    // =========================================================================

    describe("Feature: XP calculations"):

      it("should require N*100 XP to go from level N to N+1"):
        SkillState.xpForLevel(1) shouldBe 100L
        SkillState.xpForLevel(2) shouldBe 200L
        SkillState.xpForLevel(10) shouldBe 1000L
        SkillState.xpForLevel(50) shouldBe 5000L

      it("should calculate total XP to reach a level"):
        SkillState.totalXpForLevel(1) shouldBe 0L
        SkillState.totalXpForLevel(2) shouldBe 100L    // 1*100
        SkillState.totalXpForLevel(3) shouldBe 300L   // 1*100 + 2*100
        SkillState.totalXpForLevel(4) shouldBe 600L   // 1*100 + 2*100 + 3*100

      it("should derive level from total XP"):
        SkillState.levelFromXp(0L) shouldBe 1
        SkillState.levelFromXp(99L) shouldBe 1
        SkillState.levelFromXp(100L) shouldBe 2
        SkillState.levelFromXp(299L) shouldBe 2
        SkillState.levelFromXp(300L) shouldBe 3

      it("should cap level at 99"):
        val massiveXp = 100_000_000L
        SkillState.levelFromXp(massiveXp) shouldBe 99

      it("should calculate XP progress within current level"):
        val state = SkillState(level = 2, xp = 150L) // 50 XP into level 2
        // Level 2 needs 200 XP to reach level 3, we have 50 XP into level 2
        val progress = SkillState.xpProgress(state)
        progress shouldBe 0.25 +- 0.01

      it("should show full progress at level 99"):
        val state = SkillState(level = 99, xp = 1_000_000L)
        SkillState.xpProgress(state) shouldBe 1.0

    // =========================================================================
    // New Game Initialization
    // =========================================================================

    describe("Feature: New game initialization"):

      it("should create a game with all skills at level 1"):
        val game = VelorIdleGame.newGame(1000L)
        Skill.values.foreach { skill =>
          game.skills(skill).level shouldBe 1
          game.skills(skill).xp shouldBe 0L
        }

      it("should start with empty inventory of 12 slots"):
        val game = VelorIdleGame.newGame(1000L)
        game.inventory.maxSlots shouldBe 12
        game.inventory.usedSlots shouldBe 0

      it("should start with 0 gold"):
        val game = VelorIdleGame.newGame(1000L)
        game.gold shouldBe 0L

      it("should start in idle state"):
        val game = VelorIdleGame.newGame(1000L)
        game.activeAction shouldBe ActiveAction.Idle
        game.currentSkill shouldBe None

    // =========================================================================
    // Skill Selection
    // =========================================================================

    describe("Feature: Skill selection"):

      it("should allow selecting a skill"):
        val game = VelorIdleGame.newGame(1000L)
        val updated = VelorIdleLogic.selectSkill(game, Skill.Woodcutting)
        updated.currentSkill shouldBe Some(Skill.Woodcutting)

      it("should allow changing selected skill"):
        val game = VelorIdleGame.newGame(1000L)
        val step1 = VelorIdleLogic.selectSkill(game, Skill.Woodcutting)
        val step2 = VelorIdleLogic.selectSkill(step1, Skill.Mining)
        step2.currentSkill shouldBe Some(Skill.Mining)

    // =========================================================================
    // Starting Actions
    // =========================================================================

    describe("Feature: Unified startAction"):

      it("should dispatch to gathering for gathering skills"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(currentSkill = Some(Skill.Woodcutting))
        
        val result = VelorIdleLogic.startAction(game, "normal_tree")
        result.isRight shouldBe true
        result.toOption.get.activeAction match
          case ActiveAction.Gathering(skill, action) =>
            skill shouldBe Skill.Woodcutting
            action.id shouldBe "normal_tree"
          case _ => fail("Expected Gathering action")

      it("should dispatch to processing for processing skills"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Cooking),
            inventory = Inventory.empty().addItem(Item.RawShrimp, 5)._1
          )
        
        val result = VelorIdleLogic.startAction(game, "cook_shrimp")
        result.isRight shouldBe true
        result.toOption.get.activeAction match
          case ActiveAction.Processing(skill, action) =>
            skill shouldBe Skill.Cooking
            action.id shouldBe "cook_shrimp"
          case _ => fail("Expected Processing action")

      it("should reject when no skill is selected"):
        val game = VelorIdleGame.newGame(1000L)
        val result = VelorIdleLogic.startAction(game, "normal_tree")
        result.isLeft shouldBe true
        result.left.toOption.get should include("No skill selected")

    describe("Feature: Starting gathering actions"):

      it("should start a gathering action when requirements are met"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(currentSkill = Some(Skill.Woodcutting))
        
        val result = VelorIdleLogic.startGathering(game, "normal_tree")
        result.isRight shouldBe true
        result.toOption.get.activeAction match
          case ActiveAction.Gathering(skill, action) =>
            skill shouldBe Skill.Woodcutting
            action.id shouldBe "normal_tree"
          case _ => fail("Expected Gathering action")

      it("should reject gathering without skill selected"):
        val game = VelorIdleGame.newGame(1000L)
        val result = VelorIdleLogic.startGathering(game, "normal_tree")
        result.isLeft shouldBe true
        result.left.toOption.get should include("No skill selected")

      it("should reject gathering for non-gathering skill"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(currentSkill = Some(Skill.Cooking))
        
        val result = VelorIdleLogic.startGathering(game, "normal_tree")
        result.isLeft shouldBe true
        result.left.toOption.get should include("Not a gathering skill")

      it("should reject action requiring higher level"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(currentSkill = Some(Skill.Woodcutting))
        
        // Oak tree requires level 10
        val result = VelorIdleLogic.startGathering(game, "oak_tree")
        result.isLeft shouldBe true
        result.left.toOption.get should include("level 10")

    describe("Feature: Starting processing actions"):

      it("should start a processing action when requirements are met"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Cooking),
            inventory = Inventory.empty().addItem(Item.RawShrimp, 5)._1
          )
        
        val result = VelorIdleLogic.startProcessing(game, "cook_shrimp")
        result.isRight shouldBe true
        result.toOption.get.activeAction match
          case ActiveAction.Processing(skill, action) =>
            skill shouldBe Skill.Cooking
            action.id shouldBe "cook_shrimp"
          case _ => fail("Expected Processing action")

      it("should reject processing without ingredients"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(currentSkill = Some(Skill.Cooking))
        
        val result = VelorIdleLogic.startProcessing(game, "cook_shrimp")
        result.isLeft shouldBe true
        result.left.toOption.get should include("Missing required materials")

      it("should reject processing for non-processing skill"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(currentSkill = Some(Skill.Woodcutting))
        
        val result = VelorIdleLogic.startProcessing(game, "cook_shrimp")
        result.isLeft shouldBe true
        result.left.toOption.get should include("Not a processing skill")

    // =========================================================================
    // Stopping Actions
    // =========================================================================

    describe("Feature: Stopping actions"):

      it("should stop an active action"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Woodcutting),
            activeAction = ActiveAction.Gathering(
              Skill.Woodcutting,
              GatheringActions.woodcutting.head
            ),
            actionProgress = 0.5
          )
        
        val stopped = VelorIdleLogic.stopAction(game)
        stopped.activeAction shouldBe ActiveAction.Idle
        stopped.actionProgress shouldBe 0.0

    // =========================================================================
    // Tick - Gathering
    // =========================================================================

    describe("Feature: Game tick for gathering"):

      it("should advance progress over time"):
        val action = GatheringActions.woodcutting.head // 3.0 seconds
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Woodcutting),
            activeAction = ActiveAction.Gathering(Skill.Woodcutting, action),
            actionProgress = 0.0
          )
        
        // Advance 1.5 seconds
        val (updated, events) = VelorIdleLogic.tick(game, 2500L, fixedRandom())
        updated.actionProgress shouldBe 0.5 +- 0.01

      it("should grant XP and item on action completion"):
        val action = GatheringActions.woodcutting.head // 3.0 seconds, 10 XP
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Woodcutting),
            activeAction = ActiveAction.Gathering(Skill.Woodcutting, action),
            actionProgress = 0.99
          )
        
        // Advance just enough to complete
        val (updated, events) = VelorIdleLogic.tick(game, 1100L, fixedRandom())
        
        // Should have gained XP
        updated.skills(Skill.Woodcutting).xp should be >= 10L
        
        // Should have item in inventory
        updated.inventory.getCount(Item.NormalLogs) should be >= 1L
        
        // Should have XP and Item events
        events.exists(_.isInstanceOf[GameEvent.XpGained]) shouldBe true
        events.exists(_.isInstanceOf[GameEvent.ItemGained]) shouldBe true

      it("should trigger level up event when crossing threshold"):
        val action = GatheringActions.woodcutting.head // 10 XP per action
        // Set XP to 95, so next action (10 XP) pushes to 105, crossing level 2 at 100
        val skillState = SkillState(level = 1, xp = 95L)
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Woodcutting),
            skills = Map(Skill.Woodcutting -> skillState) ++ 
              Skill.values.filterNot(_ == Skill.Woodcutting).map(_ -> SkillState.initial).toMap,
            activeAction = ActiveAction.Gathering(Skill.Woodcutting, action),
            actionProgress = 0.99
          )
        
        val (updated, events) = VelorIdleLogic.tick(game, 1100L, fixedRandom())
        
        updated.skills(Skill.Woodcutting).level shouldBe 2
        events.exists {
          case GameEvent.LevelUp(Skill.Woodcutting, 2) => true
          case _ => false
        } shouldBe true

      it("should not advance progress when idle"):
        val game = VelorIdleGame.newGame(1000L)
        val (updated, events) = VelorIdleLogic.tick(game, 5000L, fixedRandom())
        updated.actionProgress shouldBe 0.0
        events shouldBe empty

    // =========================================================================
    // Tick - Processing
    // =========================================================================

    describe("Feature: Game tick for processing"):

      it("should consume ingredients on completion"):
        val action = ProcessingActions.cooking.head // cook_shrimp
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Cooking),
            inventory = Inventory.empty().addItem(Item.RawShrimp, 5)._1,
            activeAction = ActiveAction.Processing(Skill.Cooking, action),
            actionProgress = 0.99
          )
        
        // Use a random that won't trigger recycle
        val rng = new Random(12345)
        val (updated, events) = VelorIdleLogic.tick(game, 1100L, rng)
        
        // Should have consumed 1 raw shrimp (unless recycled)
        updated.inventory.getCount(Item.RawShrimp) should be <= 4L

      it("should stop when out of materials"):
        val action = ProcessingActions.cooking.head // cook_shrimp requires 1 raw shrimp
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Cooking),
            inventory = Inventory.empty(), // No shrimp!
            activeAction = ActiveAction.Processing(Skill.Cooking, action),
            actionProgress = 0.5
          )
        
        val (updated, events) = VelorIdleLogic.tick(game, 2000L, fixedRandom())
        
        updated.activeAction shouldBe ActiveAction.Idle
        events should contain(GameEvent.OutOfMaterials)

    // =========================================================================
    // Perk Calculations
    // =========================================================================

    describe("Feature: Perk calculations"):

      it("should calculate efficiency bonus at tier thresholds"):
        VelorIdleLogic.calculateEfficiencyBonus(1) shouldBe 0.0 +- 0.001
        VelorIdleLogic.calculateEfficiencyBonus(9) shouldBe 0.0 +- 0.001
        VelorIdleLogic.calculateEfficiencyBonus(10) shouldBe 0.05 +- 0.001
        VelorIdleLogic.calculateEfficiencyBonus(39) shouldBe 0.05 +- 0.001
        VelorIdleLogic.calculateEfficiencyBonus(40) shouldBe 0.10 +- 0.001
        VelorIdleLogic.calculateEfficiencyBonus(69) shouldBe 0.10 +- 0.001
        VelorIdleLogic.calculateEfficiencyBonus(70) shouldBe 0.15 +- 0.001
        VelorIdleLogic.calculateEfficiencyBonus(99) shouldBe 0.15 +- 0.001

      it("should calculate yield bonus at tier thresholds"):
        VelorIdleLogic.calculateYieldBonus(1) shouldBe 0.0 +- 0.001
        VelorIdleLogic.calculateYieldBonus(19) shouldBe 0.0 +- 0.001
        VelorIdleLogic.calculateYieldBonus(20) shouldBe 0.10 +- 0.001
        VelorIdleLogic.calculateYieldBonus(49) shouldBe 0.10 +- 0.001
        VelorIdleLogic.calculateYieldBonus(50) shouldBe 0.20 +- 0.001
        VelorIdleLogic.calculateYieldBonus(79) shouldBe 0.20 +- 0.001
        VelorIdleLogic.calculateYieldBonus(80) shouldBe 0.30 +- 0.001

      it("should calculate gathering double chance (mastery)"):
        VelorIdleLogic.calculateDoubleChance(1, isGathering = true) shouldBe 0.0 +- 0.001
        VelorIdleLogic.calculateDoubleChance(30, isGathering = true) shouldBe 0.05 +- 0.001
        VelorIdleLogic.calculateDoubleChance(60, isGathering = true) shouldBe 0.10 +- 0.001
        VelorIdleLogic.calculateDoubleChance(90, isGathering = true) shouldBe 0.15 +- 0.001

      it("should calculate processing double chance (includes level scaling)"):
        // Processing has base 0.5% per level plus tier bonuses
        val level50 = VelorIdleLogic.calculateDoubleChance(50, isGathering = false)
        // 50 * 0.005 = 0.25 base + 0.05 (tier 20) + 0.05 (tier 50) = 0.35
        level50 shouldBe 0.35 +- 0.01

      it("should calculate recycle chance"):
        // Base 0.3% per level plus tier bonuses
        val level60 = VelorIdleLogic.calculateRecycleChance(60)
        // 60 * 0.003 = 0.18 base + 0.05 (tier 30) + 0.05 (tier 60) = 0.28
        level60 shouldBe 0.28 +- 0.01

    // =========================================================================
    // Inventory Operations
    // =========================================================================

    describe("Feature: Inventory operations"):

      it("should add items to empty inventory"):
        val inv = Inventory.empty()
        val (updated, overflow) = inv.addItem(Item.NormalLogs, 10)
        
        overflow shouldBe 0L
        updated.getCount(Item.NormalLogs) shouldBe 10L
        updated.usedSlots shouldBe 1

      it("should stack same items"):
        val inv = Inventory.empty()
        val (step1, _) = inv.addItem(Item.NormalLogs, 5)
        val (step2, _) = step1.addItem(Item.NormalLogs, 3)
        
        step2.getCount(Item.NormalLogs) shouldBe 8L
        step2.usedSlots shouldBe 1

      it("should use new slot for different items"):
        val inv = Inventory.empty()
        val (step1, _) = inv.addItem(Item.NormalLogs, 5)
        val (step2, _) = step1.addItem(Item.CopperOre, 3)
        
        step2.getCount(Item.NormalLogs) shouldBe 5L
        step2.getCount(Item.CopperOre) shouldBe 3L
        step2.usedSlots shouldBe 2

      it("should report overflow when inventory is full"):
        val smallInv = Inventory.empty(slots = 2)
        val (step1, _) = smallInv.addItem(Item.NormalLogs, 5)
        val (step2, _) = step1.addItem(Item.CopperOre, 3)
        // Inventory now full (2 slots)
        val (step3, overflow) = step2.addItem(Item.IronOre, 10)
        
        overflow shouldBe 10L
        step3.getCount(Item.IronOre) shouldBe 0L

      it("should remove items from inventory"):
        val inv = Inventory.empty()
        val (withItems, _) = inv.addItem(Item.NormalLogs, 10)
        val (afterRemove, removed) = withItems.removeItem(Item.NormalLogs, 3)
        
        removed shouldBe 3L
        afterRemove.getCount(Item.NormalLogs) shouldBe 7L

      it("should remove slot when count reaches zero"):
        val inv = Inventory.empty()
        val (withItems, _) = inv.addItem(Item.NormalLogs, 5)
        val (afterRemove, _) = withItems.removeItem(Item.NormalLogs, 5)
        
        afterRemove.getCount(Item.NormalLogs) shouldBe 0L
        afterRemove.usedSlots shouldBe 0

      it("should only remove available amount"):
        val inv = Inventory.empty()
        val (withItems, _) = inv.addItem(Item.NormalLogs, 5)
        val (afterRemove, removed) = withItems.removeItem(Item.NormalLogs, 100)
        
        removed shouldBe 5L // Only 5 available
        afterRemove.getCount(Item.NormalLogs) shouldBe 0L

    // =========================================================================
    // Selling Items
    // =========================================================================

    describe("Feature: Selling items"):

      it("should add gold when selling items"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(inventory = Inventory.empty().addItem(Item.NormalLogs, 10)._1)
        
        val result = VelorIdleLogic.sellItem(game, Item.NormalLogs, 5)
        result.isRight shouldBe true
        // Normal logs sell for 2 gold each
        result.toOption.get.gold shouldBe 10L
        result.toOption.get.inventory.getCount(Item.NormalLogs) shouldBe 5L

      it("should sell all of an item"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(inventory = Inventory.empty().addItem(Item.NormalLogs, 10)._1)
        
        val result = VelorIdleLogic.sellAll(game, Item.NormalLogs)
        result.isRight shouldBe true
        result.toOption.get.gold shouldBe 20L // 10 * 2
        result.toOption.get.inventory.getCount(Item.NormalLogs) shouldBe 0L

      it("should reject selling items not in inventory"):
        val game = VelorIdleGame.newGame(1000L)
        val result = VelorIdleLogic.sellItem(game, Item.NormalLogs, 1)
        result.isLeft shouldBe true

    // =========================================================================
    // Inventory Upgrades
    // =========================================================================

    describe("Feature: Inventory upgrades"):

      it("should upgrade inventory when enough gold"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(gold = 1000L)
        
        val result = VelorIdleLogic.upgradeInventory(game, 16)
        result.isRight shouldBe true
        result.toOption.get.inventory.maxSlots shouldBe 16
        result.toOption.get.gold shouldBe 500L // 1000 - 500 cost

      it("should reject upgrade when not enough gold"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(gold = 100L)
        
        val result = VelorIdleLogic.upgradeInventory(game, 16)
        result.isLeft shouldBe true
        result.left.toOption.get should include("500")

      it("should reject invalid upgrade target"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(gold = 10000L)
        
        // 13 is not a valid upgrade target
        val result = VelorIdleLogic.upgradeInventory(game, 13)
        result.isLeft shouldBe true

    // =========================================================================
    // UI State Behaviors
    // =========================================================================

    describe("Feature: UI state detection"):

      it("should detect when a skill is actively being trained"):
        val action = GatheringActions.woodcutting.head
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Woodcutting),
            activeAction = ActiveAction.Gathering(Skill.Woodcutting, action)
          )
        
        game.activeAction match
          case ActiveAction.Gathering(skill, _) => skill shouldBe Skill.Woodcutting
          case _ => fail("Expected Gathering action")

      it("should detect idle state when no action is running"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(currentSkill = Some(Skill.Woodcutting))
        
        game.activeAction shouldBe ActiveAction.Idle

      it("should distinguish between viewing skill and active skill"):
        val action = GatheringActions.woodcutting.head
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Mining), // Viewing Mining
            activeAction = ActiveAction.Gathering(Skill.Woodcutting, action) // But Woodcutting is active
          )
        
        // The viewing skill (currentSkill) is different from the active skill
        game.currentSkill shouldBe Some(Skill.Mining)
        game.activeAction match
          case ActiveAction.Gathering(skill, _) => skill shouldBe Skill.Woodcutting
          case _ => fail("Expected Gathering action")
        
        // This is the key check - viewing skill != active skill
        val viewingSkill = game.currentSkill
        val activeSkill = game.activeAction match
          case ActiveAction.Gathering(s, _) => Some(s)
          case ActiveAction.Processing(s, _) => Some(s)
          case ActiveAction.Idle => None
        
        viewingSkill should not be activeSkill

      it("should match viewing skill with active skill when same"):
        val action = GatheringActions.woodcutting.head
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Woodcutting),
            activeAction = ActiveAction.Gathering(Skill.Woodcutting, action)
          )
        
        val viewingSkill = game.currentSkill
        val activeSkill = game.activeAction match
          case ActiveAction.Gathering(s, _) => Some(s)
          case ActiveAction.Processing(s, _) => Some(s)
          case ActiveAction.Idle => None
        
        viewingSkill shouldBe activeSkill

      it("should allow selecting a different skill while action is running"):
        val action = GatheringActions.woodcutting.head
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Woodcutting),
            activeAction = ActiveAction.Gathering(Skill.Woodcutting, action),
            actionProgress = 0.5
          )
        
        // Select a different skill
        val updatedGame = VelorIdleLogic.selectSkill(game, Skill.Mining)
        
        // Viewing skill changed
        updatedGame.currentSkill shouldBe Some(Skill.Mining)
        
        // But action is still running for Woodcutting
        updatedGame.activeAction match
          case ActiveAction.Gathering(skill, _) => skill shouldBe Skill.Woodcutting
          case _ => fail("Action should still be running")
        
        // Progress preserved
        updatedGame.actionProgress shouldBe 0.5

      it("should provide action details when action is active"):
        val action = GatheringActions.woodcutting.head
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Woodcutting),
            activeAction = ActiveAction.Gathering(Skill.Woodcutting, action)
          )
        
        game.activeAction match
          case ActiveAction.Gathering(_, a) =>
            a.id shouldBe "normal_tree"
            a.name shouldBe "Normal Tree"
            a.xpGain shouldBe 10
            a.timeSeconds shouldBe 3.0
            a.output shouldBe Item.NormalLogs
          case _ => fail("Expected Gathering action")

      it("should provide no action details when idle"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(currentSkill = Some(Skill.Woodcutting))
        
        game.activeAction match
          case ActiveAction.Idle => succeed
          case _ => fail("Expected Idle state")

    // =========================================================================
    // Action Continuity (scroll position preservation)
    // =========================================================================

    describe("Feature: Action continuity across ticks"):

      it("should preserve active action through multiple ticks"):
        val action = GatheringActions.woodcutting.head
        var game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Woodcutting),
            activeAction = ActiveAction.Gathering(Skill.Woodcutting, action),
            actionProgress = 0.0
          )
        
        // Simulate multiple ticks
        for i <- 1 to 5 do
          val (newGame, _) = VelorIdleLogic.tick(game, game.lastTickTime + 500, fixedRandom())
          game = newGame
        
        // Action should still be the same (not recreated)
        game.activeAction match
          case ActiveAction.Gathering(skill, a) =>
            skill shouldBe Skill.Woodcutting
            a.id shouldBe action.id
          case _ => fail("Action should still be running")

      it("should maintain skill selection across action completion"):
        val action = GatheringActions.woodcutting.head // 3 seconds
        var game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Woodcutting),
            activeAction = ActiveAction.Gathering(Skill.Woodcutting, action),
            actionProgress = 0.99
          )
        
        // Complete the action
        val (newGame, events) = VelorIdleLogic.tick(game, game.lastTickTime + 100, fixedRandom())
        
        // Action completed (XP gained)
        events.exists(_.isInstanceOf[GameEvent.XpGained]) shouldBe true
        
        // But skill selection is preserved
        newGame.currentSkill shouldBe Some(Skill.Woodcutting)
        
        // And action continues (auto-repeat)
        newGame.activeAction match
          case ActiveAction.Gathering(skill, _) => skill shouldBe Skill.Woodcutting
          case _ => fail("Action should auto-continue")

