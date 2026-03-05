package shared.VelorIdle

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random

class OfflineProgressSpec extends AnyFunSpec with Matchers:

  def fixedRandom(seed: Long = 12345L): Random = new Random(seed)

  describe("OfflineProgress"):

    describe("for Gathering actions"):

      it("should grant XP proportional to time elapsed"):
        val action = GatheringActions.woodcutting.head // Normal tree: 3.0s, 10 XP
        val game = VelorIdleGame.newGame(0L).copy(
          activeAction = ActiveAction.Gathering(Skill.Woodcutting, action)
        )
        
        // 5 minutes = 300 seconds, should complete ~100 actions at 3s each
        val result = OfflineProgress.calculateOfflineProgress(game, 0L, 300_000L, fixedRandom())
        
        result.xpGained.getOrElse(Skill.Woodcutting, 0L) should be >= 900L // At least 90 actions worth
        result.xpGained.getOrElse(Skill.Woodcutting, 0L) should be <= 1100L // At most 110 actions worth
        result.itemsGained.getOrElse(Item.NormalLogs, 0L) should be >= 90L

      it("should handle level-ups correctly"):
        val action = GatheringActions.woodcutting.head
        val game = VelorIdleGame.newGame(0L).copy(
          activeAction = ActiveAction.Gathering(Skill.Woodcutting, action),
          skills = Map(Skill.Woodcutting -> SkillState(level = 1, xp = 100)) // Close to level 2
        )
        
        // Enough time to level up
        val result = OfflineProgress.calculateOfflineProgress(game, 0L, 60_000L, fixedRandom())
        
        result.skillLevelUps.getOrElse(Skill.Woodcutting, 0) should be >= 1

      it("should not process if not enough time elapsed"):
        val action = GatheringActions.woodcutting.head
        val game = VelorIdleGame.newGame(0L).copy(
          activeAction = ActiveAction.Gathering(Skill.Woodcutting, action)
        )
        
        // Only 10 seconds - below chunk threshold
        val result = OfflineProgress.calculateOfflineProgress(game, 0L, 10_000L, fixedRandom())
        
        result.secondsProcessed shouldBe 0
        result.xpGained shouldBe empty

    describe("for Processing actions"):

      it("should be limited by available materials"):
        val action = ProcessingActions.cooking.head // Cook shrimp: needs raw shrimp
        val game = VelorIdleGame.newGame(0L).copy(
          activeAction = ActiveAction.Processing(Skill.Cooking, action),
          inventory = Inventory.empty().addItem(Item.RawShrimp, 10)._1
        )
        
        // 1 hour - but only 10 shrimp available
        val result = OfflineProgress.calculateOfflineProgress(game, 0L, 3600_000L, fixedRandom())
        
        // Should have processed at most 10 items (minus some burns)
        result.itemsGained.values.sum should be <= 10L

      it("should not grant anything if no materials available"):
        val action = ProcessingActions.cooking.head
        val game = VelorIdleGame.newGame(0L).copy(
          activeAction = ActiveAction.Processing(Skill.Cooking, action)
          // No raw shrimp in inventory
        )
        
        val result = OfflineProgress.calculateOfflineProgress(game, 0L, 3600_000L, fixedRandom())
        
        result.xpGained shouldBe empty
        result.itemsGained shouldBe empty

    describe("for Idle state"):

      it("should not grant any progress"):
        val game = VelorIdleGame.newGame(0L).copy(
          activeAction = ActiveAction.Idle
        )
        
        val result = OfflineProgress.calculateOfflineProgress(game, 0L, 3600_000L, fixedRandom())
        
        result.xpGained shouldBe empty
        result.itemsGained shouldBe empty
        result.goldGained shouldBe 0

    describe("for Adventure/Rest"):

      it("should restore HP and mana to full"):
        val game = VelorIdleGame.newGame(0L).copy(
          activeAction = ActiveAction.Rest,
          adventureState = AdventureState().copy(currentHp = 10, currentMana = 5)
        )
        
        val result = OfflineProgress.calculateOfflineProgress(game, 0L, 3600_000L, fixedRandom())
        
        result.game.adventureState.currentHp shouldBe result.game.adventureState.maxHp
        result.game.adventureState.currentMana shouldBe result.game.adventureState.maxMana
        result.game.activeAction shouldBe ActiveAction.Idle

