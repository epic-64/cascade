package shared.Trader

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random

class TraderRiskSpec extends AnyFunSpec with Matchers:

  describe("Trader Risk System"):

    describe("Feature: Risk assessment calculation"):

      it("should return zero risk for empty cargo"):
        val inventory = Inventory.empty
        val risk = TraderRisk.assessRisk(inventory)
        
        risk.encounterChance shouldBe 0.0
        risk.cargoValue shouldBe 0
        risk.cargoWeight shouldBe 0

      it("should calculate low risk for bulk goods like Wheat"):
        // Wheat: 5g base price, 20kg weight = 0.25 g/kg
        val inventory = Inventory.empty.add(Item.Wheat, 10)
        val risk = TraderRisk.assessRisk(inventory)
        
        risk.valuePerKg shouldBe 0.25 +- 0.01
        risk.encounterChance should be < 0.01 // Essentially safe

      it("should calculate high risk for Gems"):
        // Gems: 80g base price, 2kg weight = 40 g/kg
        val inventory = Inventory.empty.add(Item.Gems, 10)
        val risk = TraderRisk.assessRisk(inventory)
        
        risk.valuePerKg shouldBe 40.0 +- 0.01
        risk.encounterChance shouldBe 0.8 // Capped at 80%

      it("should calculate moderate risk for Silk"):
        // Silk: 40g base price, 5kg weight = 8 g/kg
        // riskScore = (8/10)^1.5 ≈ 0.716, encounterChance ≈ 7.2%
        val inventory = Inventory.empty.add(Item.Silk, 10)
        val risk = TraderRisk.assessRisk(inventory)
        
        risk.valuePerKg shouldBe 8.0 +- 0.01
        risk.encounterChance should be > 0.05
        risk.encounterChance should be < 0.15

      it("should calculate reduced risk for mixed cargo"):
        // Mix: 10 Gems (20kg, 800g) + 180kg Wheat (9 units, 45g)
        // Total: 200kg, 845g = ~4.2 g/kg
        val inventory = Inventory.empty
          .add(Item.Gems, 10)
          .add(Item.Wheat, 9)
        val risk = TraderRisk.assessRisk(inventory)
        
        risk.cargoWeight shouldBe 200
        risk.valuePerKg should be > 4.0
        risk.valuePerKg should be < 5.0
        risk.encounterChance should be < 0.1 // Much safer than pure gems

      it("should cap encounter chance at 80%"):
        // Even with extremely valuable cargo, chance should not exceed 80%
        val inventory = Inventory.empty.add(Item.Gems, 100)
        val risk = TraderRisk.assessRisk(inventory)
        
        risk.encounterChance shouldBe 0.8

    describe("Feature: Risk level descriptions"):

      it("should return 'Safe' for very low risk cargo"):
        val inventory = Inventory.empty.add(Item.Wheat, 10)
        val risk = TraderRisk.assessRisk(inventory)
        
        TraderRisk.getRiskLevel(risk) shouldBe "Safe"

      it("should return 'Extreme' for very high risk cargo"):
        val inventory = Inventory.empty.add(Item.Gems, 50)
        val risk = TraderRisk.assessRisk(inventory)
        
        TraderRisk.getRiskLevel(risk) shouldBe "Extreme"

    describe("Feature: Encounter rolling"):

      it("should never trigger encounter with zero risk"):
        val risk = RiskAssessment.safe
        val rng = new Random(12345)
        
        // Roll many times - should never encounter
        val encounters = (1 to 100).flatMap(_ => TraderRisk.rollEncounter(risk, rng))
        encounters shouldBe empty

      it("should sometimes trigger encounters with high risk"):
        val inventory = Inventory.empty.add(Item.Gems, 50)
        val risk = TraderRisk.assessRisk(inventory)
        val rng = new Random(12345)
        
        // With 80% chance, most rolls should trigger
        val results = (1 to 100).map(_ => TraderRisk.rollEncounter(risk, rng))
        val encounters = results.flatten
        
        encounters.size should be > 50 // Should be around 80

      it("should produce different outcomes"):
        val inventory = Inventory.empty.add(Item.Gems, 50)
        val risk = TraderRisk.assessRisk(inventory)
        val rng = new Random(54321)
        
        val outcomes = (1 to 100).flatMap(_ => TraderRisk.rollEncounter(risk, rng))
        
        // Should have variety of outcomes
        outcomes.exists(_.isInstanceOf[EncounterOutcome.Escaped.type]) shouldBe true
        outcomes.exists(_.isInstanceOf[EncounterOutcome.Toll]) shouldBe true

    describe("Feature: Applying encounter outcomes"):

      def createGameWithCargo(gems: Int = 0, wheat: Int = 0, gold: Int = 1000): TraderGame =
        val player = Player(
          gold = gold,
          inventory = Inventory.empty
            .add(Item.Gems, gems)
            .add(Item.Wheat, wheat),
          carriageLevel = 5, // High capacity
          currentCity = CityId.Riverdale
        )
        TraderLogic.newGame().copy(player = player)

      it("should not affect cargo for Escaped outcome"):
        val game = createGameWithCargo(gems = 10, gold = 500)
        val rng = new Random(12345)
        
        val result = TraderRisk.applyEncounter(
          game, 
          EncounterOutcome.Escaped,
          CityId.Riverdale,
          CityId.Ironforge,
          rng
        )
        
        result.player.gold shouldBe 500
        result.player.inventory.getQuantity(Item.Gems) shouldBe 10
        result.log.head should include("escaped")

      it("should deduct gold for Toll outcome"):
        val game = createGameWithCargo(gems = 10, gold = 500)
        val rng = new Random(12345)
        
        val result = TraderRisk.applyEncounter(
          game,
          EncounterOutcome.Toll(100),
          CityId.Riverdale,
          CityId.Ironforge,
          rng
        )
        
        result.player.gold shouldBe 400
        result.player.inventory.getQuantity(Item.Gems) shouldBe 10 // Cargo unchanged
        result.log.head should include("toll")

      it("should not deduct more gold than player has for Toll"):
        val game = createGameWithCargo(gems = 10, gold = 50)
        val rng = new Random(12345)
        
        val result = TraderRisk.applyEncounter(
          game,
          EncounterOutcome.Toll(100), // More than player has
          CityId.Riverdale,
          CityId.Ironforge,
          rng
        )
        
        result.player.gold shouldBe 0 // Loses all gold, but not negative

      it("should remove high-value items for Robbery outcome"):
        val game = createGameWithCargo(gems = 10, wheat = 5, gold = 500)
        val rng = new Random(12345)
        
        val result = TraderRisk.applyEncounter(
          game,
          EncounterOutcome.Robbery(Map.empty), // Items calculated dynamically
          CityId.Riverdale,
          CityId.Ironforge,
          rng
        )
        
        // Gems should be targeted first (highest value/kg)
        result.player.inventory.getQuantity(Item.Gems) should be < 10
        result.player.gold shouldBe 500 // Gold unchanged for robbery
        result.log.head should include("robbed")

      it("should remove items and gold for Devastating Loss"):
        val game = createGameWithCargo(gems = 10, wheat = 5, gold = 500)
        val rng = new Random(12345)
        
        val result = TraderRisk.applyEncounter(
          game,
          EncounterOutcome.DevastatingLoss(Map.empty, 0), // Calculated dynamically
          CityId.Riverdale,
          CityId.Ironforge,
          rng
        )
        
        // Should lose significant cargo and some gold
        result.player.inventory.getQuantity(Item.Gems) should be < 10
        result.player.inventory.getQuantity(Item.Wheat) should be < 5
        result.player.gold should be < 500
        result.log.head should include("Devastating")

    describe("Feature: Travel with risk integration"):

      def createGameWithCargo(gems: Int = 0, wheat: Int = 0, gold: Int = 1000): TraderGame =
        val player = Player(
          gold = gold,
          inventory = Inventory.empty
            .add(Item.Gems, gems)
            .add(Item.Wheat, wheat),
          carriageLevel = 5,
          currentCity = CityId.Riverdale
        )
        TraderLogic.newGame().copy(player = player)

      it("should allow safe travel with bulk goods"):
        val game = createGameWithCargo(wheat = 10, gold = 100)
        
        // Use seeded RNG that would trigger encounter if chance was high
        val rng = new Random(99999)
        val result = TraderLogic.travel(game, CityId.Ironforge, rng)
        
        result.isRight shouldBe true
        // With wheat only (~0% risk), should arrive safely
        result.toOption.get.player.currentCity shouldBe CityId.Ironforge

      it("should sometimes trigger encounters with valuable cargo"):
        val game = createGameWithCargo(gems = 50, gold = 500)
        
        // Try many travels with different seeds to find one that triggers
        val encounterTriggered = (1 to 20).exists { seed =>
          val rng = new Random(seed)
          val result = TraderLogic.travel(game, CityId.Ironforge, rng)
          result.toOption.exists { g =>
            g.log.exists(_.contains("⚔️"))
          }
        }
        
        encounterTriggered shouldBe true

      it("should include risk info in getTravelOptionsWithRisk"):
        val game = createGameWithCargo(gems = 20)
        
        val (options, risk) = TraderLogic.getTravelOptionsWithRisk(game)
        
        options should not be empty
        risk.encounterChance shouldBe 0.8 // Gems = max risk
        risk.cargoValue shouldBe 1600 // 20 gems * 80g base price

