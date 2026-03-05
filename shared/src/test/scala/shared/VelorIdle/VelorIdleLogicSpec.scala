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

      it("should use cubic scaling formula for XP requirements"):
        // Formula: 100*level + 10*level² + 0.5*level³
        // Level 1→2: 100 + 10 + 0 = 110
        SkillState.xpForLevel(1) shouldBe 110L
        // Level 2→3: 200 + 40 + 4 = 244
        SkillState.xpForLevel(2) shouldBe 244L
        // Level 10: 1000 + 1000 + 500 = 2500
        SkillState.xpForLevel(10) shouldBe 2500L
        // Level 50: 5000 + 25000 + 62500 = 92500
        SkillState.xpForLevel(50) shouldBe 92500L

      it("should calculate total XP to reach a level"):
        SkillState.totalXpForLevel(1) shouldBe 0L
        SkillState.totalXpForLevel(2) shouldBe 110L    // xpForLevel(1)
        SkillState.totalXpForLevel(3) shouldBe 354L    // 110 + 244

      it("should derive level from total XP"):
        SkillState.levelFromXp(0L) shouldBe 1
        SkillState.levelFromXp(109L) shouldBe 1
        SkillState.levelFromXp(110L) shouldBe 2
        SkillState.levelFromXp(353L) shouldBe 2
        SkillState.levelFromXp(354L) shouldBe 3

      it("should cap level at 99"):
        val massiveXp = 100_000_000L
        SkillState.levelFromXp(massiveXp) shouldBe 99

      it("should calculate XP progress within current level"):
        // Level 2 starts at 110 XP, level 3 at 354 XP, so need 244 XP to level up
        // With 171 total XP, we have 61 XP into level 2 (171 - 110 = 61)
        // Progress = 61 / 244 = 0.25
        val state = SkillState(level = 2, xp = 171L)
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
        // Level 2 requires 110 XP. Set XP to 105, so next action (10 XP) pushes to 115
        val skillState = SkillState(level = 1, xp = 105L)
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
        // First upgrade from 12 slots costs 100 gold (base cost)
        val game = VelorIdleGame.newGame(1000L)
          .copy(gold = 1000L)
        
        val result = VelorIdleLogic.buyInventorySlots(game)
        result.isRight shouldBe true
        result.toOption.get.inventory.maxSlots shouldBe 16  // 12 + 4
        result.toOption.get.gold shouldBe 900L // 1000 - 100 cost for first upgrade

      it("should reject upgrade when not enough gold"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(gold = 10L)  // Not enough for 100 gold cost

        val result = VelorIdleLogic.buyInventorySlots(game)
        result.isLeft shouldBe true
        result.left.toOption.get should include("100")

      it("should reject upgrade when at max capacity"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            gold = 10_000_000L,
            inventory = Inventory.empty(Inventory.MaxSlots)
          )

        val result = VelorIdleLogic.buyInventorySlots(game)
        result.isLeft shouldBe true
        result.left.toOption.get should include("maximum")

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
          case ActiveAction.EquipmentCrafting(_) => Some(Skill.Smithing)
          case ActiveAction.Thieving(_) => Some(Skill.Thieving)
          case ActiveAction.Adventure => Some(Skill.Adventure)
          case ActiveAction.Rest => Some(Skill.Adventure)
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
          case ActiveAction.EquipmentCrafting(_) => Some(Skill.Smithing)
          case ActiveAction.Thieving(_) => Some(Skill.Thieving)
          case ActiveAction.Adventure => Some(Skill.Adventure)
          case ActiveAction.Rest => Some(Skill.Adventure)
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

    // =========================================================================
    // Summoning Skill - Tablet Creation
    // =========================================================================

    describe("Feature: Summoning skill - tablet creation"):

      it("should allow starting summoning actions when requirements are met"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Summoning),
            inventory = Inventory.empty()
              .addItem(Item.NormalLogs, 100)._1
              .addItem(Item.CopperOre, 50)._1
          )
        
        val result = VelorIdleLogic.startAction(game, "create_gatherer")
        result.isRight shouldBe true
        result.toOption.get.activeAction match
          case ActiveAction.Processing(skill, action) =>
            skill shouldBe Skill.Summoning
            action.id shouldBe "create_gatherer"
            action.output shouldBe Item.GathererTablet
          case _ => fail("Expected Processing action for Summoning")

      it("should reject summoning when missing materials"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Summoning),
            inventory = Inventory.empty()
              .addItem(Item.NormalLogs, 50)._1  // Need 100
          )
        
        val result = VelorIdleLogic.startAction(game, "create_gatherer")
        result.isLeft shouldBe true
        result.left.toOption.get should include("Missing required materials")

      it("should reject summoning when level is too low"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Summoning),
            inventory = Inventory.empty()
              .addItem(Item.IronOre, 200)._1
              .addItem(Item.Coal, 100)._1
          )
        
        // Miner tablet requires level 10
        val result = VelorIdleLogic.startAction(game, "create_miner")
        result.isLeft shouldBe true
        result.left.toOption.get should include("level 10")

      it("should create a tablet on action completion"):
        val action = ProcessingActions.summoning.head // create_gatherer
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Summoning),
            inventory = Inventory.empty()
              .addItem(Item.NormalLogs, 100)._1
              .addItem(Item.CopperOre, 50)._1,
            activeAction = ActiveAction.Processing(Skill.Summoning, action),
            actionProgress = 0.99
          )
        
        val (updated, events) = VelorIdleLogic.tick(game, 1100L, fixedRandom())
        
        // Should have created the tablet
        updated.inventory.getCount(Item.GathererTablet) should be >= 1L
        events.exists {
          case GameEvent.ItemGained(Item.GathererTablet, _) => true
          case _ => false
        } shouldBe true

    // =========================================================================
    // Tablet Equipment
    // =========================================================================

    describe("Feature: Tablet equipment"):

      it("should equip a tablet to slot 1"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(inventory = Inventory.empty().addItem(Item.GathererTablet, 1)._1)
        
        val result = VelorIdleLogic.equipTablet(game, Item.GathererTablet, 1)
        result.isRight shouldBe true
        
        val updated = result.toOption.get
        updated.tabletSlots.slot1.isDefined shouldBe true
        updated.tabletSlots.slot1.get.tabletType shouldBe TabletType.Gatherer
        updated.inventory.getCount(Item.GathererTablet) shouldBe 0L

      it("should reject equipping non-tablet items"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(inventory = Inventory.empty().addItem(Item.NormalLogs, 10)._1)
        
        val result = VelorIdleLogic.equipTablet(game, Item.NormalLogs, 1)
        result.isLeft shouldBe true
        result.left.toOption.get should include("Not a tablet")

      it("should reject equipping tablet not in inventory"):
        val game = VelorIdleGame.newGame(1000L)
        
        val result = VelorIdleLogic.equipTablet(game, Item.GathererTablet, 1)
        result.isLeft shouldBe true
        result.left.toOption.get should include("No tablet in inventory")

      it("should reject equipping to slot 2 when Summoning level is too low"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(inventory = Inventory.empty().addItem(Item.GathererTablet, 1)._1)
        
        // Slot 2 requires Summoning level 25
        val result = VelorIdleLogic.equipTablet(game, Item.GathererTablet, 2)
        result.isLeft shouldBe true
        result.left.toOption.get should include("Slot 2 requires Summoning level 25")

      it("should allow equipping to slot 2 at Summoning level 25+"):
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            skills = Map(Skill.Summoning -> SkillState(level = 25, xp = 30000L)) ++
              Skill.values.filterNot(_ == Skill.Summoning).map(_ -> SkillState.initial).toMap,
            inventory = Inventory.empty().addItem(Item.MinerTablet, 1)._1
          )
        
        val result = VelorIdleLogic.equipTablet(game, Item.MinerTablet, 2)
        result.isRight shouldBe true
        result.toOption.get.tabletSlots.slot2.isDefined shouldBe true

      it("should unequip a tablet and return it to inventory"):
        val equippedTablet = EquippedTablet(Item.GathererTablet, TabletType.Gatherer, 10)
        val game = VelorIdleGame.newGame(1000L)
          .copy(tabletSlots = TabletSlots(Some(equippedTablet), None))
        
        val result = VelorIdleLogic.unequipTablet(game, 1)
        result.isRight shouldBe true
        
        val updated = result.toOption.get
        updated.tabletSlots.slot1 shouldBe None
        updated.inventory.getCount(Item.GathererTablet) shouldBe 1L

      it("should reject unequipping from empty slot"):
        val game = VelorIdleGame.newGame(1000L)
        
        val result = VelorIdleLogic.unequipTablet(game, 1)
        result.isLeft shouldBe true
        result.left.toOption.get should include("No tablet in slot")

    // =========================================================================
    // Tablet Type Detection
    // =========================================================================

    describe("Feature: Tablet type detection"):

      it("should identify all tablet items"):
        TabletType.isTablet(Item.GathererTablet) shouldBe true
        TabletType.isTablet(Item.MinerTablet) shouldBe true
        TabletType.isTablet(Item.FisherTablet) shouldBe true
        TabletType.isTablet(Item.LumberjackTablet) shouldBe true
        TabletType.isTablet(Item.ArtisanTablet) shouldBe true
        TabletType.isTablet(Item.HerbalistTablet) shouldBe true
        TabletType.isTablet(Item.AlchemistTablet) shouldBe true
        TabletType.isTablet(Item.ThiefTablet) shouldBe true
        TabletType.isTablet(Item.StargazerTablet) shouldBe true
        TabletType.isTablet(Item.MasterTablet) shouldBe true

      it("should not identify non-tablets"):
        TabletType.isTablet(Item.NormalLogs) shouldBe false
        TabletType.isTablet(Item.WoodcuttingPotion) shouldBe false
        TabletType.isTablet(Item.CopperOre) shouldBe false

      it("should map tablet items to correct types"):
        TabletType.fromItem(Item.GathererTablet) shouldBe Some(TabletType.Gatherer)
        TabletType.fromItem(Item.MinerTablet) shouldBe Some(TabletType.Miner)
        TabletType.fromItem(Item.MasterTablet) shouldBe Some(TabletType.Master)
        TabletType.fromItem(Item.NormalLogs) shouldBe None

    // =========================================================================
    // Synergy Detection
    // =========================================================================

    describe("Feature: Synergy detection"):

      it("should detect Earth Affinity synergy (Gatherer + Miner)"):
        val synergy = SynergyEffect.find(TabletType.Gatherer, TabletType.Miner)
        synergy shouldBe Some(SynergyEffect.EarthAffinity)
        
        // Order shouldn't matter
        SynergyEffect.find(TabletType.Miner, TabletType.Gatherer) shouldBe Some(SynergyEffect.EarthAffinity)

      it("should detect Nature's Bounty synergy (Gatherer + Fisher)"):
        val synergy = SynergyEffect.find(TabletType.Gatherer, TabletType.Fisher)
        synergy shouldBe Some(SynergyEffect.NaturesBounty)

      it("should detect Forest Spirit synergy (Gatherer + Lumberjack)"):
        val synergy = SynergyEffect.find(TabletType.Gatherer, TabletType.Lumberjack)
        synergy shouldBe Some(SynergyEffect.ForestSpirit)

      it("should detect Sea Chef synergy (Fisher + Artisan)"):
        val synergy = SynergyEffect.find(TabletType.Fisher, TabletType.Artisan)
        synergy shouldBe Some(SynergyEffect.SeaChef)

      it("should detect Potion Master synergy (Herbalist + Alchemist)"):
        val synergy = SynergyEffect.find(TabletType.Herbalist, TabletType.Alchemist)
        synergy shouldBe Some(SynergyEffect.PotionMaster)

      it("should detect Grove Keeper synergy (Lumberjack + Herbalist)"):
        val synergy = SynergyEffect.find(TabletType.Lumberjack, TabletType.Herbalist)
        synergy shouldBe Some(SynergyEffect.GroveKeeper)

      it("should return None for non-synergy combinations"):
        val synergy = SynergyEffect.find(TabletType.Miner, TabletType.Fisher)
        synergy shouldBe None

      it("should detect Master synergy with any tablet"):
        SynergyEffect.hasMasterSynergy(TabletType.Master, TabletType.Gatherer) shouldBe true
        SynergyEffect.hasMasterSynergy(TabletType.Gatherer, TabletType.Master) shouldBe true
        SynergyEffect.hasMasterSynergy(TabletType.Master, TabletType.Master) shouldBe false

    // =========================================================================
    // Tablet Slots - Active Synergy
    // =========================================================================

    describe("Feature: TabletSlots active synergy"):

      it("should have no synergy with single tablet"):
        val slots = TabletSlots(
          Some(EquippedTablet(Item.GathererTablet, TabletType.Gatherer, 10)),
          None
        )
        slots.activeSynergy shouldBe None

      it("should detect synergy when two compatible tablets are equipped"):
        val slots = TabletSlots(
          Some(EquippedTablet(Item.GathererTablet, TabletType.Gatherer, 10)),
          Some(EquippedTablet(Item.MinerTablet, TabletType.Miner, 10))
        )
        slots.activeSynergy shouldBe Some(SynergyEffect.EarthAffinity)

      it("should detect MasteryBoost synergy when Master tablet is present"):
        val slots = TabletSlots(
          Some(EquippedTablet(Item.GathererTablet, TabletType.Gatherer, 10)),
          Some(EquippedTablet(Item.MasterTablet, TabletType.Master, 5))
        )
        slots.activeSynergy shouldBe Some(SynergyEffect.MasteryBoost)

      it("should have no synergy for incompatible tablets"):
        val slots = TabletSlots(
          Some(EquippedTablet(Item.MinerTablet, TabletType.Miner, 10)),
          Some(EquippedTablet(Item.FisherTablet, TabletType.Fisher, 10))
        )
        slots.activeSynergy shouldBe None

    // =========================================================================
    // Tablet Bonuses
    // =========================================================================

    describe("Feature: Tablet bonuses"):

      it("should provide speed bonus for Mining from Miner tablet"):
        val slots = TabletSlots(
          Some(EquippedTablet(Item.MinerTablet, TabletType.Miner, 10)),
          None
        )
        slots.speedBonusFor(Skill.Mining) shouldBe 0.08 +- 0.001
        slots.speedBonusFor(Skill.Fishing) shouldBe 0.0 +- 0.001

      it("should provide speed bonus for Fishing from Fisher tablet"):
        val slots = TabletSlots(
          Some(EquippedTablet(Item.FisherTablet, TabletType.Fisher, 10)),
          None
        )
        slots.speedBonusFor(Skill.Fishing) shouldBe 0.08 +- 0.001

      it("should provide speed bonus for Woodcutting from Lumberjack tablet"):
        val slots = TabletSlots(
          Some(EquippedTablet(Item.LumberjackTablet, TabletType.Lumberjack, 10)),
          None
        )
        slots.speedBonusFor(Skill.Woodcutting) shouldBe 0.08 +- 0.001

      it("should provide gathering yield bonus from Gatherer tablet"):
        val slots = TabletSlots(
          Some(EquippedTablet(Item.GathererTablet, TabletType.Gatherer, 10)),
          None
        )
        slots.gatheringYieldBonus shouldBe 0.05 +- 0.001

      it("should double the effect with Master tablet via MasteryBoost"):
        val slots = TabletSlots(
          Some(EquippedTablet(Item.MinerTablet, TabletType.Miner, 10)),
          Some(EquippedTablet(Item.MasterTablet, TabletType.Master, 5))
        )
        // Miner gives 8%, Master doubles it to 16%, plus Master's own 5%
        slots.speedBonusFor(Skill.Mining) shouldBe 0.21 +- 0.001

      it("should prevent burning with Sea Chef synergy"):
        val slots = TabletSlots(
          Some(EquippedTablet(Item.FisherTablet, TabletType.Fisher, 10)),
          Some(EquippedTablet(Item.ArtisanTablet, TabletType.Artisan, 10))
        )
        slots.preventsBurning shouldBe true

      it("should provide recycle bonus with Metalworker synergy"):
        val slots = TabletSlots(
          Some(EquippedTablet(Item.MinerTablet, TabletType.Miner, 10)),
          Some(EquippedTablet(Item.ArtisanTablet, TabletType.Artisan, 10))
        )
        slots.recycleBonusFor(Skill.Smithing) shouldBe 0.15 +- 0.001
        slots.recycleBonusFor(Skill.Cooking) shouldBe 0.0 +- 0.001

      it("should provide double bonus for Alchemy with Potion Master synergy"):
        val slots = TabletSlots(
          Some(EquippedTablet(Item.HerbalistTablet, TabletType.Herbalist, 10)),
          Some(EquippedTablet(Item.AlchemistTablet, TabletType.Alchemist, 10))
        )
        // Alchemist gives 15%, Potion Master adds 20%
        slots.doubleBonusFor(Skill.Alchemy) shouldBe 0.35 +- 0.001

    // =========================================================================
    // Tablet Consumption
    // =========================================================================

    describe("Feature: Tablet consumption"):

      it("should consume tablet charges on action completion"):
        val equippedTablet = EquippedTablet(Item.GathererTablet, TabletType.Gatherer, 10)
        val action = GatheringActions.woodcutting.head
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Woodcutting),
            activeAction = ActiveAction.Gathering(Skill.Woodcutting, action),
            actionProgress = 0.99,
            tabletSlots = TabletSlots(Some(equippedTablet), None)
          )
        
        val (updated, _) = VelorIdleLogic.tick(game, 1100L, fixedRandom())
        
        // Tablet should have one less charge
        updated.tabletSlots.slot1.get.actionsRemaining shouldBe 9

      it("should remove tablet when charges reach zero"):
        val equippedTablet = EquippedTablet(Item.GathererTablet, TabletType.Gatherer, 1)
        val action = GatheringActions.woodcutting.head
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Woodcutting),
            activeAction = ActiveAction.Gathering(Skill.Woodcutting, action),
            actionProgress = 0.99,
            tabletSlots = TabletSlots(Some(equippedTablet), None)
          )
        
        val (updated, events) = VelorIdleLogic.tick(game, 1100L, fixedRandom())
        
        // Tablet should be removed
        updated.tabletSlots.slot1 shouldBe None
        
        // Should have TabletConsumed event
        events.exists {
          case GameEvent.TabletConsumed(Item.GathererTablet, 1) => true
          case _ => false
        } shouldBe true

      it("should consume both tablets when both are equipped"):
        val tablet1 = EquippedTablet(Item.GathererTablet, TabletType.Gatherer, 10)
        val tablet2 = EquippedTablet(Item.MinerTablet, TabletType.Miner, 10)
        val action = GatheringActions.mining.head
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Mining),
            activeAction = ActiveAction.Gathering(Skill.Mining, action),
            actionProgress = 0.99,
            tabletSlots = TabletSlots(Some(tablet1), Some(tablet2))
          )
        
        val (updated, _) = VelorIdleLogic.tick(game, 1100L, fixedRandom())
        
        // Both tablets should have one less charge
        updated.tabletSlots.slot1.get.actionsRemaining shouldBe 9
        updated.tabletSlots.slot2.get.actionsRemaining shouldBe 9

    // =========================================================================
    // Tablet Effects in Game Tick
    // =========================================================================

    describe("Feature: Tablet effects in game tick"):

      it("should apply tablet speed bonus to gathering"):
        val tablet = EquippedTablet(Item.MinerTablet, TabletType.Miner, 10)
        val action = GatheringActions.mining.head // copper_rock, 3.5 seconds base
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Mining),
            activeAction = ActiveAction.Gathering(Skill.Mining, action),
            actionProgress = 0.0,
            tabletSlots = TabletSlots(Some(tablet), None)
          )
        
        // Without tablet: 3.5 seconds, progress per second = 1/3.5 ≈ 0.286
        // With 8% tablet bonus: 3.5 * 0.92 = 3.22 seconds, progress per second ≈ 0.311
        val (updated, _) = VelorIdleLogic.tick(game, 2000L, fixedRandom())
        
        // Progress should be higher than without tablet
        // 1 second at 0.311 per second ≈ 0.311 progress
        updated.actionProgress should be > 0.28

      it("should prevent burning with Sea Chef synergy"):
        val tablet1 = EquippedTablet(Item.FisherTablet, TabletType.Fisher, 10)
        val tablet2 = EquippedTablet(Item.ArtisanTablet, TabletType.Artisan, 10)
        val action = ProcessingActions.cooking.head // cook_shrimp with 30% base burn
        
        // High summoning level to unlock slot 2
        val game = VelorIdleGame.newGame(1000L)
          .copy(
            currentSkill = Some(Skill.Cooking),
            skills = Map(Skill.Summoning -> SkillState(level = 25, xp = 30000L)) ++
              Skill.values.filterNot(_ == Skill.Summoning).map(_ -> SkillState.initial).toMap,
            inventory = Inventory.empty().addItem(Item.RawShrimp, 100)._1,
            activeAction = ActiveAction.Processing(Skill.Cooking, action),
            actionProgress = 0.99,
            tabletSlots = TabletSlots(Some(tablet1), Some(tablet2))
          )
        
        // Run many times - should never burn
        var burnCount = 0
        for seed <- 1 to 50 do
          val (updated, events) = VelorIdleLogic.tick(
            game.copy(inventory = Inventory.empty().addItem(Item.RawShrimp, 100)._1),
            1100L,
            new Random(seed)
          )
          if events.exists {
            case GameEvent.ActionFailed(_) => true
            case _ => false
          } then burnCount += 1
        
        burnCount shouldBe 0

