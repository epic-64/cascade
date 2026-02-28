package shared.TileKingdom

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

class TileKingdomLogicSpec extends AnyFunSpec with Matchers with EitherValues:

  // Helper to create a game with specific tiles on the current island
  private def gameWithTiles(game: TileKingdomGame, tiles: Map[Coord, Tile]): TileKingdomGame =
    val updatedIsland = game.currentIsland.copy(tiles = game.currentIsland.tiles ++ tiles)
    game.copy(islands = game.islands.updated(game.currentIslandIndex, updatedIsland))

  // Helper to unlock a tile on the current island
  private def unlockTileAt(game: TileKingdomGame, coord: Coord): TileKingdomGame =
    val tile = game.currentIsland.tiles.getOrElse(coord, Tile(coord, TileType.Empty, unlocked = false))
    gameWithTiles(game, Map(coord -> tile.copy(unlocked = true)))

  describe("TileKingdomLogic"):

    describe("Feature: Creating a new game"):

      it("should start with initial resources and one island"):
        val game = TileKingdomLogic.newGame(1000L)

        game.wheat shouldBe 50.0
        game.wood shouldBe 0.0
        game.faith shouldBe 0.0
        game.stone shouldBe 0.0
        game.gold shouldBe TileKingdomLogic.StartingGold
        game.islands.size shouldBe 1
        game.currentIsland.tiles.size shouldBe 15 // 3x5 grid
        game.currentIsland.unlockedTiles.size shouldBe 1 // Only center tile

      it("should create an island with only the center tile unlocked"):
        val game = TileKingdomLogic.newGame(1000L)
        val centerTile = Coord(2, 1) // Center of 3x5 grid

        game.currentIsland.tiles(centerTile).unlocked shouldBe true
        game.currentIsland.tiles.values.count(_.unlocked) shouldBe 1
        game.currentIsland.tiles.values.foreach(_.isEmpty shouldBe true)

    describe("Feature: Building wheat fields"):

      it("should allow building a wheat field on an empty unlocked tile with enough wheat"):
        val game = TileKingdomLogic.newGame(1000L)
        val coord = Coord(2, 1) // Center tile (unlocked)

        val result = TileKingdomLogic.buildWheatField(game, coord)

        result.isRight shouldBe true
        result.value.tiles(coord).tileType shouldBe TileType.WheatField(1)
        result.value.wheat shouldBe <(game.wheat)

      it("should reject building on a locked tile"):
        val game = TileKingdomLogic.newGame(1000L)
        val lockedCoord = Coord(0, 0) // Not the center, so locked

        val result = TileKingdomLogic.buildWheatField(game, lockedCoord)

        result.isLeft shouldBe true

      it("should reject building when not enough resources"):
        val game = TileKingdomLogic.newGame(1000L).copy(wheat = 0)
        val coord = Coord(2, 1) // Center tile

        val result = TileKingdomLogic.buildWheatField(game, coord)

        result.isLeft shouldBe true
        result.left.value should include("wheat")

    describe("Feature: Building farms"):

      it("should require a wheat field before building a farm"):
        val game = TileKingdomLogic.newGame(1000L)
        val coord = Coord(2, 1)

        val result = TileKingdomLogic.buildFarm(game, coord)

        result.isLeft shouldBe true
        result.left.value should include("wheat field")

      it("should allow building a farm after building a wheat field"):
        val game = TileKingdomLogic.newGame(1000L)
        val wheatCoord = Coord(2, 1)
        val farmCoord = Coord(1, 1) // Adjacent to center
        
        // Build wheat field, then unlock adjacent tile for farm
        val gameWithWheatField = TileKingdomLogic.buildWheatField(game, wheatCoord).value
          .copy(wheat = 100, gold = 1000)
        val gameWithUnlockedFarmTile = TileKingdomLogic.unlockTile(gameWithWheatField, farmCoord).value

        val result = TileKingdomLogic.buildFarm(gameWithUnlockedFarmTile, farmCoord)

        result.isRight shouldBe true
        result.value.tiles(farmCoord).tileType shouldBe TileType.Farm(1)

    describe("Feature: Building woodcutters"):

      it("should require a farm before building a woodcutter"):
        val game = TileKingdomLogic.newGame(1000L)
        val coord = Coord(2, 1)

        val result = TileKingdomLogic.buildWoodcutter(game, coord)

        result.isLeft shouldBe true
        result.left.value should include("farm")

    describe("Feature: Leveling up tiles"):

      it("should increase the level of a wheat field"):
        val game = TileKingdomLogic.newGame(1000L)
        val coord = Coord(2, 1)
        val gameWithField = TileKingdomLogic.buildWheatField(game, coord).value
          .copy(wheat = 1000)

        val result = TileKingdomLogic.levelUp(gameWithField, coord)

        result.isRight shouldBe true
        result.value.tiles(coord).level shouldBe 2

      it("should deduct the upgrade cost"):
        val game = TileKingdomLogic.newGame(1000L)
        val coord = Coord(2, 1)
        val gameWithField = TileKingdomLogic.buildWheatField(game, coord).value
          .copy(wheat = 1000)
        val wheatBefore = gameWithField.wheat

        val result = TileKingdomLogic.levelUp(gameWithField, coord)

        result.value.wheat shouldBe <(wheatBefore)

      it("should reject leveling up an empty tile"):
        val game = TileKingdomLogic.newGame(1000L)
        val coord = Coord(2, 1)

        val result = TileKingdomLogic.levelUp(game, coord)

        result.isLeft shouldBe true

    describe("Feature: Production calculations"):

      it("should calculate wheat production based on wheat field level"):
        val game = TileKingdomLogic.newGame(1000L)
        val coord = Coord(2, 1)
        val gameWithField = TileKingdomLogic.buildWheatField(game, coord).value

        val tile = gameWithField.tiles(coord)
        val production = TileKingdomLogic.baseWheatProductionRate(tile)

        production shouldBe 5.0 // Level 1 = 5 wheat per 10s

      it("should scale production linearly with level"):
        val coord = Coord(0, 0)
        val level5Tile = Tile(coord = coord, tileType = TileType.WheatField(5), unlocked = true)

        val production = TileKingdomLogic.baseWheatProductionRate(level5Tile)

        production shouldBe 25.0 // Level 5 = 25 wheat per 10s

      it("should apply farm bonus multiplier to nearby wheat fields"):
        val game = TileKingdomLogic.newGame(1000L)
        val wheatCoord = Coord(2, 1)
        val farmCoord = Coord(1, 1)

        val gameWithBuildings = gameWithTiles(game, Map(
          wheatCoord -> Tile(wheatCoord, TileType.WheatField(1), unlocked = true),
          farmCoord -> Tile(farmCoord, TileType.Farm(1), unlocked = true)
        ))

        val multiplier = TileKingdomLogic.farmBonusMultiplier(gameWithBuildings, wheatCoord)

        multiplier shouldBe >(1.0)

    describe("Feature: Game tick"):

      it("should accumulate resources over time"):
        val game = TileKingdomLogic.newGame(1000L)
        val coord = Coord(2, 1)
        val gameWithField = TileKingdomLogic.buildWheatField(game, coord).value

        // Simulate 10 seconds passing
        val tickedGame = TileKingdomLogic.tick(gameWithField, gameWithField.lastTickTime + 10000)

        tickedGame.wheat shouldBe >(gameWithField.wheat)

      it("should not produce resources with no buildings"):
        val game = TileKingdomLogic.newGame(1000L)
        val initialWheat = game.wheat

        val tickedGame = TileKingdomLogic.tick(game, game.lastTickTime + 10000)

        tickedGame.wheat shouldBe initialWheat

    describe("Feature: Forest group bonus"):

      it("should give bonus to woodcutters in connected groups"):
        val game = TileKingdomLogic.newGame(1000L)
        val coord1 = Coord(2, 1)
        val coord2 = Coord(1, 1)

        val gameWithWoodcutters = gameWithTiles(game, Map(
          coord1 -> Tile(coord1, TileType.Woodcutter(1), unlocked = true),
          coord2 -> Tile(coord2, TileType.Woodcutter(1), unlocked = true)
        ))

        val multiplier = TileKingdomLogic.forestGroupBonusMultiplier(gameWithWoodcutters, coord1)

        multiplier shouldBe >(1.0)

      it("should not give bonus to isolated woodcutters"):
        val game = TileKingdomLogic.newGame(1000L)
        val coord = Coord(2, 1)

        val gameWithWoodcutter = gameWithTiles(game, Map(
          coord -> Tile(coord, TileType.Woodcutter(1), unlocked = true)
        ))

        val multiplier = TileKingdomLogic.forestGroupBonusMultiplier(gameWithWoodcutter, coord)

        multiplier shouldBe 1.0

    describe("Feature: Abdication"):

      it("should reset buildings and grant gold"):
        val game = TileKingdomLogic.newGame(1000L)
        val coord = Coord(2, 1)
        val gameWithField = TileKingdomLogic.buildWheatField(game, coord).value

        val result = TileKingdomLogic.abdicate(gameWithField, 2000L)

        result.isRight shouldBe true
        result.value.gold shouldBe >(0)
        result.value.tiles.values.foreach(_.isEmpty shouldBe true)
        result.value.totalAbdications shouldBe 1

    describe("Feature: Sailing (second tier prestige)"):

      it("should require minimum islands to sail"):
        val game = TileKingdomLogic.newGame(1000L)

        val result = TileKingdomLogic.sail(game, 2000L)

        result.isLeft shouldBe true
        result.left.value should include("islands")

      it("should reset everything and grant legacy points when sailing"):
        val game = TileKingdomLogic.newGame(1000L)
        // Create a game with 2 islands to meet sail requirements
        val secondIsland = Island.create(1)
        val bigGame = game.copy(
          islands = game.islands :+ secondIsland,
          gold = 1000
        )
        bigGame.islands.size should be >= TileKingdomLogic.SailMinIslands

        val result = TileKingdomLogic.sail(bigGame, 2000L)

        result.isRight shouldBe true
        val sailedGame = result.value
        sailedGame.gold shouldBe TileKingdomLogic.StartingGold
        sailedGame.islands.size shouldBe 1
        sailedGame.hasSailed shouldBe true

    describe("Feature: Unlocking tiles"):

      it("should allow unlocking adjacent tiles with gold"):
        val game = TileKingdomLogic.newGame(1000L).copy(gold = 1000)
        val adjacentCoord = Coord(1, 1) // Adjacent to center (2,1)

        val result = TileKingdomLogic.unlockTile(game, adjacentCoord)

        result.isRight shouldBe true
        result.value.tiles(adjacentCoord).unlocked shouldBe true
        result.value.gold shouldBe <(game.gold)

      it("should use tile points instead of gold when available"):
        val game = TileKingdomLogic.newGame(1000L).copy(gold = 1000, tilePoints = 1)
        val adjacentCoord = Coord(1, 1)
        val goldBefore = game.gold

        val result = TileKingdomLogic.unlockTile(game, adjacentCoord)

        result.isRight shouldBe true
        result.value.gold shouldBe goldBefore
        result.value.tilePoints shouldBe 0

      it("should reject unlocking non-adjacent tiles"):
        val game = TileKingdomLogic.newGame(1000L).copy(gold = 1000)
        val farCoord = Coord(0, 0) // Not adjacent to center

        val result = TileKingdomLogic.unlockTile(game, farCoord)

        result.isLeft shouldBe true

    describe("Feature: Destroying tiles"):

      it("should award a tile point when destroying a tile"):
        val game = TileKingdomLogic.newGame(1000L).copy(gold = 1000)
        // First unlock another tile so we have 2
        val gameWith2Tiles = TileKingdomLogic.unlockTile(game, Coord(1, 1)).value
        val coord = Coord(1, 1)

        val result = TileKingdomLogic.destroyTile(gameWith2Tiles, coord)

        result.isRight shouldBe true
        result.value.tilePoints shouldBe gameWith2Tiles.tilePoints + 1
        result.value.tiles(coord).unlocked shouldBe false

      it("should not allow destroying the last tile"):
        val game = TileKingdomLogic.newGame(1000L)
        val centerCoord = Coord(2, 1)

        val result = TileKingdomLogic.destroyTile(game, centerCoord)

        result.isLeft shouldBe true

    describe("Feature: Skill system"):

      it("should unlock a skill when player has enough skill points"):
        val game = TileKingdomLogic.newGame(1000L).copy(skillPoints = 1, hasSailed = true)

        val result = TileKingdomLogic.unlockSkill(game, Skill.Agriculture1A)

        result.isRight shouldBe true
        result.value.hasSkill(Skill.Agriculture1A) shouldBe true
        result.value.skillPoints shouldBe 0

      it("should reject unlocking a skill without enough skill points"):
        val game = TileKingdomLogic.newGame(1000L).copy(skillPoints = 0, hasSailed = true)

        val result = TileKingdomLogic.unlockSkill(game, Skill.Agriculture1A)

        result.isLeft shouldBe true

      it("should apply skill effects to production"):
        val game = TileKingdomLogic.newGame(1000L).copy(
          unlockedSkills = Set(Skill.Agriculture2A)
        )
        val coord = Coord(2, 1)
        val gameWithField = gameWithTiles(game, Map(
          coord -> Tile(coord, TileType.WheatField(1), unlocked = true)
        ))

        val multiplier = TileKingdomLogic.agriculture2AIntervalMultiplier(gameWithField)

        multiplier shouldBe <(1.0)

    describe("Feature: Bureau auto-upgrade"):

      it("should upgrade the cheapest tile within range"):
        val game = TileKingdomLogic.newGame(1000L)
        val bureauCoord = Coord(2, 1)
        val targetCoord = Coord(1, 1)

        val gameWithBuildings = gameWithTiles(game, Map(
          bureauCoord -> Tile(bureauCoord, TileType.Bureau(1), unlocked = true),
          targetCoord -> Tile(targetCoord, TileType.WheatField(1), unlocked = true)
        )).copy(wood = 1000, wheat = 1000)

        val result = TileKingdomLogic.bureauAutoUpgrade(gameWithBuildings, bureauCoord, 2000L)

        result shouldBe defined
        val (newGame, upgradedCoord) = result.get
        upgradedCoord shouldBe targetCoord
        newGame.tiles(targetCoord).level shouldBe 2

      it("should deduct wood cost for each upgrade"):
        val game = TileKingdomLogic.newGame(1000L)
        val bureauCoord = Coord(2, 1)
        val targetCoord = Coord(1, 1)

        val gameWithBuildings = gameWithTiles(game, Map(
          bureauCoord -> Tile(bureauCoord, TileType.Bureau(1), unlocked = true),
          targetCoord -> Tile(targetCoord, TileType.WheatField(1), unlocked = true)
        )).copy(wood = 1000, wheat = 1000)
        val woodBefore = gameWithBuildings.wood

        val result = TileKingdomLogic.bureauAutoUpgrade(gameWithBuildings, bureauCoord, 2000L)

        result.get._1.wood shouldBe <(woodBefore)

    describe("Feature: Politician system"):

      it("should generate politicians with unique IDs"):
        val pol1 = TileKingdomLogic.generatePolitician(1000L, 0.0)
        val pol2 = TileKingdomLogic.generatePolitician(1001L, 0.0)

        pol1.id should not be pol2.id

      it("should assign politician to town hall"):
        val pol = TileKingdomLogic.generatePolitician(1000L, 0.0)
        val game = TileKingdomLogic.newGame(1000L)
        val coord = Coord(2, 1)

        val gameWithTownHall = gameWithTiles(game, Map(
          coord -> Tile(coord, TileType.TownHall(List.empty), unlocked = true)
        )).copy(politicianRoster = List(pol))

        val result = TileKingdomLogic.assignPolitician(gameWithTownHall, pol.id, coord)

        result.isRight shouldBe true
        val newGame = result.value
        newGame.tiles(coord).tileType match
          case TileType.TownHall(pols) => pols.map(_.id) should contain(pol.id)
          case _ => fail("Expected town hall with politician")
        newGame.politicianRoster should not contain pol

      it("should remove politician from town hall back to roster"):
        val pol = TileKingdomLogic.generatePolitician(1000L, 0.0)
        val game = TileKingdomLogic.newGame(1000L)
        val coord = Coord(2, 1)

        val gameWithAssigned = gameWithTiles(game, Map(
          coord -> Tile(coord, TileType.TownHall(List(pol)), unlocked = true)
        )).copy(politicianRoster = List.empty)

        val result = TileKingdomLogic.removePolitician(gameWithAssigned, coord)

        result.isRight shouldBe true
        val newGame = result.value
        newGame.tiles(coord).tileType shouldBe TileType.TownHall(List.empty)
        newGame.politicianRoster should contain(pol)

      it("should hold 2 politicians when Management3A is unlocked"):
        val pol1 = TileKingdomLogic.generatePolitician(1000L, 0.0)
        val pol2 = TileKingdomLogic.generatePolitician(2000L, 0.0)
        val game = TileKingdomLogic.newGame(1000L)
        val coord = Coord(2, 1)

        val gameWithSkill = gameWithTiles(game, Map(
          coord -> Tile(coord, TileType.TownHall(List.empty), unlocked = true)
        )).copy(
          politicianRoster = List(pol1, pol2),
          hasSailed = true,
          unlockedSkills = Set(Skill.Management1A, Skill.Management2A, Skill.Management3A)
        )

        val afterFirst = TileKingdomLogic.assignPolitician(gameWithSkill, pol1.id, coord).value
        val afterSecond = TileKingdomLogic.assignPolitician(afterFirst, pol2.id, coord).value

        afterSecond.tiles(coord).tileType match
          case TileType.TownHall(pols) =>
            pols should have size 2
            pols.map(_.id) should contain allOf (pol1.id, pol2.id)
          case _ => fail("Expected town hall with 2 politicians")
        afterSecond.politicianRoster shouldBe empty

      it("should swap oldest politician when at capacity without Management3A"):
        val pol1 = TileKingdomLogic.generatePolitician(1000L, 0.0)
        val pol2 = TileKingdomLogic.generatePolitician(2000L, 0.0)
        val game = TileKingdomLogic.newGame(1000L)
        val coord = Coord(2, 1)

        val gameWithPol = gameWithTiles(game, Map(
          coord -> Tile(coord, TileType.TownHall(List(pol1)), unlocked = true)
        )).copy(politicianRoster = List(pol2))

        val result = TileKingdomLogic.assignPolitician(gameWithPol, pol2.id, coord).value

        result.tiles(coord).tileType match
          case TileType.TownHall(pols) =>
            pols should have size 1
            pols.head.id shouldBe pol2.id
          case _ => fail("Expected town hall with replaced politician")
        result.politicianRoster.map(_.id) should contain(pol1.id)

      it("should apply effects from both politicians in a dual-slot town hall"):
        val pol1 = Politician("p1", "Test1", "Title1", PoliticianEffect.WheatProductionMultiplier(2.0), "🌾")
        val pol2 = Politician("p2", "Test2", "Title2", PoliticianEffect.WheatProductionMultiplier(3.0), "🌾")
        val game = TileKingdomLogic.newGame(1000L)
        val townHallCoord = Coord(2, 1)
        val wheatCoord = Coord(1, 1)

        val gameWithDual = gameWithTiles(game, Map(
          townHallCoord -> Tile(townHallCoord, TileType.TownHall(List(pol1, pol2)), unlocked = true),
          wheatCoord -> Tile(wheatCoord, TileType.WheatField(1), unlocked = true)
        )).copy(unlockedSkills = Set(Skill.Management1A, Skill.Management2A, Skill.Management3A))

        val affecting = TileKingdomLogic.townHallsAffecting(gameWithDual, wheatCoord)
        affecting should have size 2
        val multiplier = TileKingdomLogic.townHallWheatMultiplier(gameWithDual, wheatCoord)
        multiplier shouldBe 6.0 // 2.0 * 3.0

