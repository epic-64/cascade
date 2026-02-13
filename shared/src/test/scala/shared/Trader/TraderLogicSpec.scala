package shared.Trader

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class TraderLogicSpec extends AnyFunSpec with Matchers:

  describe("Trader Game Logic"):

    describe("Feature: New game initialization"):

      it("should create a game with 100 starting gold"):
        val game = TraderLogic.newGame()
        game.player.gold shouldBe 100

      it("should start player at Riverdale"):
        val game = TraderLogic.newGame()
        game.player.currentCity shouldBe CityId.Riverdale

      it("should start with carriage level 1 and 200kg capacity"):
        val game = TraderLogic.newGame()
        game.player.carriageLevel shouldBe 1
        game.player.carriageCapacity shouldBe 200

      it("should start with empty inventory"):
        val game = TraderLogic.newGame()
        game.player.inventory.items shouldBe empty

      it("should start on turn 1 in Spring"):
        val game = TraderLogic.newGame()
        game.turn shouldBe 1
        game.season shouldBe Season.Spring

      it("should initialize all 9 cities"):
        val game = TraderLogic.newGame()
        game.cities.size shouldBe 9
        CityId.values.foreach(id => game.cities.contains(id) shouldBe true)

    describe("Feature: Buying items"):

      it("should deduct gold when buying an item"):
        val game = TraderLogic.newGame()
        val result = TraderLogic.buyItem(game, Item.Wheat, 1)
        result.isRight shouldBe true
        result.toOption.get.player.gold should be < 100

      it("should add item to inventory when buying"):
        val game = TraderLogic.newGame()
        val result = TraderLogic.buyItem(game, Item.Wheat, 2)
        result.isRight shouldBe true
        result.toOption.get.player.inventory.getQuantity(Item.Wheat) shouldBe 2

      it("should refuse purchase when not enough gold"):
        val game = TraderLogic.newGame().copy(
          player = TraderLogic.newGame().player.copy(gold = 5)
        )
        // With only 5 gold, cannot buy even 1 Iron (base 15g)
        val result = TraderLogic.buyItem(game, Item.Iron, 1)
        result.isLeft shouldBe true
        result.left.toOption.get should include ("gold")

      it("should refuse purchase when exceeding carriage capacity"):
        val game = TraderLogic.newGame().copy(
          player = TraderLogic.newGame().player.copy(gold = 10000)
        )
        // Livestock weighs 100kg each, try to buy 3 (300kg) with 200kg capacity
        val result = TraderLogic.buyItem(game, Item.Livestock, 3)
        result.isLeft shouldBe true
        result.left.toOption.get should include ("capacity")

      it("should advance turn after buying"):
        val game = TraderLogic.newGame()
        val result = TraderLogic.buyItem(game, Item.Wheat, 1)
        result.toOption.get.turn shouldBe 2

    describe("Feature: Selling items"):

      it("should increase gold when selling an item"):
        val game = TraderLogic.newGame()
        val withItem = game.copy(
          player = game.player.copy(inventory = Inventory.empty.add(Item.Wheat, 5))
        )
        val result = TraderLogic.sellItem(withItem, Item.Wheat, 1)
        result.isRight shouldBe true
        result.toOption.get.player.gold should be > 100

      it("should remove item from inventory when selling"):
        val game = TraderLogic.newGame()
        val withItem = game.copy(
          player = game.player.copy(inventory = Inventory.empty.add(Item.Wheat, 5))
        )
        val result = TraderLogic.sellItem(withItem, Item.Wheat, 2)
        result.toOption.get.player.inventory.getQuantity(Item.Wheat) shouldBe 3

      it("should refuse sale when not enough items in inventory"):
        val game = TraderLogic.newGame()
        val withItem = game.copy(
          player = game.player.copy(inventory = Inventory.empty.add(Item.Wheat, 2))
        )
        val result = TraderLogic.sellItem(withItem, Item.Wheat, 5)
        result.isLeft shouldBe true
        result.left.toOption.get should include ("Wheat")

      it("should refuse sale of items not in inventory"):
        val game = TraderLogic.newGame()
        val result = TraderLogic.sellItem(game, Item.Silk, 1)
        result.isLeft shouldBe true

    describe("Feature: Traveling between cities"):

      it("should update current city when traveling"):
        val game = TraderLogic.newGame()
        val result = TraderLogic.travel(game, CityId.Ironforge)
        result.isRight shouldBe true
        result.toOption.get.player.currentCity shouldBe CityId.Ironforge

      it("should deduct travel cost from gold"):
        val game = TraderLogic.newGame()
        val result = TraderLogic.travel(game, CityId.Ironforge)
        result.toOption.get.player.gold should be < 100

      it("should refuse travel to current city"):
        val game = TraderLogic.newGame()
        val result = TraderLogic.travel(game, CityId.Riverdale)
        result.isLeft shouldBe true
        result.left.toOption.get should include ("Already")

      it("should refuse travel when cannot afford cost"):
        val game = TraderLogic.newGame().copy(
          player = TraderLogic.newGame().player.copy(gold = 1)
        )
        val result = TraderLogic.travel(game, CityId.Crystalpeak)
        result.isLeft shouldBe true
        result.left.toOption.get should include ("gold")

      it("should charge more for farther cities"):
        val game = TraderLogic.newGame()
        val nearResult = TraderLogic.travel(game, CityId.Ironforge)
        val nearCost = 100 - nearResult.toOption.get.player.gold

        val farResult = TraderLogic.travel(game, CityId.Crystalpeak)
        val farCost = 100 - farResult.toOption.get.player.gold

        farCost should be > nearCost

      it("should charge more when carrying cargo"):
        val game = TraderLogic.newGame()
        val emptyResult = TraderLogic.travel(game, CityId.Ironforge)
        val emptyCost = 100 - emptyResult.toOption.get.player.gold

        val loadedGame = game.copy(
          player = game.player.copy(inventory = Inventory.empty.add(Item.Livestock, 2))
        )
        val loadedResult = TraderLogic.travel(loadedGame, CityId.Ironforge)
        val loadedCost = 100 - loadedResult.toOption.get.player.gold

        loadedCost should be > emptyCost

    describe("Feature: Carriage upgrades"):

      it("should increase capacity when upgrading"):
        val game = TraderLogic.newGame().copy(
          player = TraderLogic.newGame().player.copy(gold = 200)
        )
        val result = TraderLogic.upgradeCarriage(game)
        result.toOption.get.player.carriageLevel shouldBe 2
        result.toOption.get.player.carriageCapacity shouldBe 250

      it("should deduct gold when upgrading"):
        val game = TraderLogic.newGame().copy(
          player = TraderLogic.newGame().player.copy(gold = 200)
        )
        val result = TraderLogic.upgradeCarriage(game)
        result.toOption.get.player.gold shouldBe 100

      it("should refuse upgrade when not enough gold"):
        val game = TraderLogic.newGame().copy(
          player = TraderLogic.newGame().player.copy(gold = 50)
        )
        val result = TraderLogic.upgradeCarriage(game)
        result.isLeft shouldBe true
        result.left.toOption.get should include ("gold")

      it("should refuse upgrade at max level"):
        val game = TraderLogic.newGame().copy(
          player = TraderLogic.newGame().player.copy(gold = 10000, carriageLevel = 8)
        )
        val result = TraderLogic.upgradeCarriage(game)
        result.isLeft shouldBe true
        result.left.toOption.get should include ("max")

      it("should double upgrade cost each level"):
        val player1 = Player(1000, Inventory.empty, 1, CityId.Riverdale)
        player1.upgradesCost shouldBe 100

        val player2 = player1.copy(carriageLevel = 2)
        player2.upgradesCost shouldBe 200

        val player3 = player1.copy(carriageLevel = 3)
        player3.upgradesCost shouldBe 400

    describe("Feature: Price calculation"):

      it("should calculate buy price based on supply"):
        val city = City(
          CityId.Wheatholm, "Wheatholm",
          CityMarket(
            Map(Item.Wheat -> SupplyLevel.Abundant),
            Map.empty
          ),
          (1, 0)
        )
        val abundantPrice = TraderLogic.calculateBuyPrice(city, Item.Wheat, Season.Spring)

        val normalCity = city.copy(market = CityMarket(
          Map(Item.Wheat -> SupplyLevel.Normal),
          Map.empty
        ))
        val normalPrice = TraderLogic.calculateBuyPrice(normalCity, Item.Wheat, Season.Spring)

        abundantPrice should be < normalPrice

      it("should calculate sell price based on demand"):
        val city = City(
          CityId.Northport, "Northport",
          CityMarket(
            Map.empty,
            Map(Item.Silk -> DemandLevel.High)
          ),
          (0, 0)
        )
        val highDemandPrice = TraderLogic.calculateSellPrice(city, Item.Silk, Season.Spring)

        val normalCity = city.copy(market = CityMarket(
          Map.empty,
          Map(Item.Silk -> DemandLevel.Normal)
        ))
        val normalPrice = TraderLogic.calculateSellPrice(normalCity, Item.Silk, Season.Spring)

        highDemandPrice should be > normalPrice

      it("should apply seasonal modifiers"):
        val city = City(
          CityId.Riverdale, "Riverdale",
          CityMarket(Map.empty, Map.empty),
          (1, 1)
        )
        val winterCoalPrice = TraderLogic.calculateBuyPrice(city, Item.Coal, Season.Winter)
        val springCoalPrice = TraderLogic.calculateBuyPrice(city, Item.Coal, Season.Spring)

        winterCoalPrice should be > springCoalPrice

    describe("Feature: Season changes"):

      it("should change season after turn 5"):
        var game = TraderLogic.newGame()
        game.season shouldBe Season.Spring

        // Buy 5 times to advance to turn 6
        (1 to 5).foreach { _ =>
          TraderLogic.buyItem(game, Item.Wheat, 1) match
            case Right(g) => game = g
            case Left(_) =>
              // If we can't buy, travel instead
              TraderLogic.travel(game, CityId.Ironforge) match
                case Right(g) => game = g
                case Left(_) => fail("Could not advance turn")
        }

        game.season shouldBe Season.Summer

      it("should cycle through all seasons"):
        Season.next(Season.Spring) shouldBe Season.Summer
        Season.next(Season.Summer) shouldBe Season.Autumn
        Season.next(Season.Autumn) shouldBe Season.Winter
        Season.next(Season.Winter) shouldBe Season.Spring

    describe("Feature: Inventory management"):

      it("should track total weight correctly"):
        val inv = Inventory.empty
          .add(Item.Wheat, 2)   // 40kg
          .add(Item.Iron, 1)    // 50kg

        inv.totalWeight shouldBe 90

      it("should calculate available capacity correctly"):
        val player = Player(
          gold = 100,
          inventory = Inventory.empty.add(Item.Wheat, 5), // 100kg
          carriageLevel = 1, // 200kg capacity
          currentCity = CityId.Riverdale
        )

        player.availableCapacity shouldBe 100

    describe("Feature: Travel options"):

      it("should list all cities except current"):
        val game = TraderLogic.newGame()
        val options = TraderLogic.getTravelOptions(game)

        options.map(_._1) should not contain CityId.Riverdale
        options.size shouldBe 8

      it("should sort travel options by cost"):
        val game = TraderLogic.newGame()
        val options = TraderLogic.getTravelOptions(game)

        val costs = options.map(_._2)
        costs shouldBe costs.sorted

