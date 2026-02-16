package shared.Territory

import upickle.default.ReadWriter

// ============================================================================
// Coordinate type for infinite grid
// ============================================================================

case class Coord(row: Int, col: Int) derives ReadWriter:
  def neighbors: Set[Coord] =
    (for
      dr <- -1 to 1
      dc <- -1 to 1
      if !(dr == 0 && dc == 0)
    yield Coord(row + dr, col + dc)).toSet

// ============================================================================
// Tile Types
// ============================================================================

enum TileType derives ReadWriter:
  case Empty
  case WheatField(level: Int) // level determines production rate
  case Farm(level: Int)       // boosts nearby wheat fields
  case Woodcutter(level: Int) // produces wood

// ============================================================================
// Tile
// ============================================================================

case class Tile(
    coord: Coord,
    tileType: TileType,
    unlocked: Boolean
) derives ReadWriter:
  def isEmpty: Boolean = tileType match
    case TileType.Empty => true
    case _ => false

  def isWheatField: Boolean = tileType match
    case TileType.WheatField(_) => true
    case _ => false

  def isFarm: Boolean = tileType match
    case TileType.Farm(_) => true
    case _ => false

  def isWoodcutter: Boolean = tileType match
    case TileType.Woodcutter(_) => true
    case _ => false

  def isBuilding: Boolean = isWheatField || isFarm || isWoodcutter

  def level: Int = tileType match
    case TileType.WheatField(lvl) => lvl
    case TileType.Farm(lvl) => lvl
    case TileType.Woodcutter(lvl) => lvl
    case _ => 0

// ============================================================================
// Game State
// ============================================================================

case class TerritoryGame(
    tiles: Map[Coord, Tile],
    wheat: Double, // Can be fractional for smooth accumulation
    wood: Double,  // Wood resource
    gold: Int,
    lastTickTime: Long, // Timestamp in milliseconds for offline progress
    totalAbdications: Int
) derives ReadWriter:

  def unlockedTiles: List[Tile] =
    tiles.values.filter(_.unlocked).toList.sortBy(t => (t.coord.row, t.coord.col))

  def lockedTiles: List[Tile] =
    tiles.values.filterNot(_.unlocked).toList.sortBy(t => (t.coord.row, t.coord.col))

  def allTilesFilled: Boolean =
    unlockedTiles.nonEmpty && unlockedTiles.forall(_.isBuilding)

  def hasWheatField: Boolean =
    unlockedTiles.exists(_.isWheatField)

  def totalIncomeRate: Double =
    TerritoryLogic.totalProductionRate(this)

  def nextTileUnlockCost: Int =
    TerritoryLogic.tileUnlockCost(unlockedTiles.size)

  def abdicationGoldReward: Int =
    TerritoryLogic.abdicationReward(totalIncomeRate)

// ============================================================================
// Game Logic
// ============================================================================

object TerritoryLogic:

  // Constants
  val TickIntervalSeconds: Int = 1
  val ProductionIntervalSeconds: Int = 10 // Wheat fields produce every 10 seconds
  val InitialTileCount: Int = 4
  val FarmBoostPerLevel: Double = 0.25 // 25% boost per farm level

  // Initial 2x2 tiles at origin (center of infinite grid)
  val InitialUnlockedCoords: Set[Coord] = Set(
    Coord(0, 0), Coord(0, 1),
    Coord(1, 0), Coord(1, 1)
  )

  // Base production per harvest (wheat per 10-second interval) - without bonuses
  def baseWheatProductionRate(tile: Tile): Double = tile.tileType match
    case TileType.WheatField(level) => level * 5.0 // 5 wheat at level 1, 10 at level 2, etc. (per 10s)
    case _ => 0.0

  // Base wood production per harvest (wood per 10-second interval)
  def baseWoodProductionRate(tile: Tile): Double = tile.tileType match
    case TileType.Woodcutter(level) => level * 3.0 // 3 wood at level 1, 6 at level 2, etc. (per 10s)
    case _ => 0.0

  // Legacy alias
  def baseProductionRate(tile: Tile): Double = baseWheatProductionRate(tile)

  // Production rate per second (for display and total income calculation)
  def productionPerSecond(tile: Tile): Double = baseWheatProductionRate(tile) / ProductionIntervalSeconds
  def woodProductionPerSecond(tile: Tile): Double = baseWoodProductionRate(tile) / ProductionIntervalSeconds

  // Calculate farm bonus multiplier for a wheat field at given coord
  def farmBonusMultiplier(game: TerritoryGame, coord: Coord): Double =
    val farmBonus = coord.neighbors.flatMap(game.tiles.get).collect:
      case tile if tile.isFarm => tile.level * FarmBoostPerLevel
    .sum
    1.0 + farmBonus

  // Production rate for a specific tile per second (with farm bonuses applied)
  def productionRate(game: TerritoryGame, tile: Tile): Double =
    val base = productionPerSecond(tile)
    if base > 0 then base * farmBonusMultiplier(game, tile.coord)
    else 0.0

  // Wood production rate for a specific tile per second
  def woodProductionRate(game: TerritoryGame, tile: Tile): Double =
    woodProductionPerSecond(tile) // No bonuses for wood currently

  // Legacy method for backwards compatibility
  def productionRate(tile: Tile): Double = productionPerSecond(tile)

  // Production per harvest for a specific tile (with farm bonuses applied)
  def productionPerHarvest(game: TerritoryGame, tile: Tile): Double =
    val base = baseWheatProductionRate(tile)
    if base > 0 then base * farmBonusMultiplier(game, tile.coord)
    else 0.0

  // Wood production per harvest for a specific tile
  def woodProductionPerHarvest(game: TerritoryGame, tile: Tile): Double =
    baseWoodProductionRate(tile)

  // Total production rate for the game (all wheat fields with bonuses)
  def totalProductionRate(game: TerritoryGame): Double =
    game.unlockedTiles.map(tile => productionRate(game, tile)).sum

  // Total wood production rate
  def totalWoodProductionRate(game: TerritoryGame): Double =
    game.unlockedTiles.map(tile => woodProductionRate(game, tile)).sum

  // Cost to build a wheat field on an empty tile
  def wheatFieldBuildCost: Int = 10

  // Cost to build a farm on an empty tile
  def farmBuildCost: Int = 25

  // Cost to build a woodcutter on an empty tile
  def woodcutterBuildCost: Int = 20

  // Legacy alias
  def buildCost: Int = wheatFieldBuildCost

  // Cost to level up a wheat field
  def wheatFieldLevelUpCost(currentLevel: Int): Int =
    currentLevel * 20 // Level 1→2 costs 20, 2→3 costs 40, etc.

  // Cost to level up a farm
  def farmLevelUpCost(currentLevel: Int): Int =
    currentLevel * 30 // Level 1→2 costs 30, 2→3 costs 60, etc.

  // Cost to level up a woodcutter
  def woodcutterLevelUpCost(currentLevel: Int): Int =
    currentLevel * 25 // Level 1→2 costs 25, 2→3 costs 50, etc.

  // Legacy alias
  def levelUpCost(currentLevel: Int): Int = wheatFieldLevelUpCost(currentLevel)

  // Cost to unlock next tile (exponential)
  def tileUnlockCost(currentUnlockedCount: Int): Int =
    val tilesAfterInitial = math.max(0, currentUnlockedCount - InitialTileCount)
    if tilesAfterInitial == 0 then 100
    else 100 * math.pow(2, tilesAfterInitial).toInt

  // Gold reward for abdication based on total income rate
  def abdicationReward(totalIncomeRate: Double): Int =
    math.max(10, (totalIncomeRate * 20).toInt) // 20 gold per wheat/second

  // Create initial game state
  def newGame(currentTimeMillis: Long): TerritoryGame =
    val initialTiles = InitialUnlockedCoords.map: coord =>
      coord -> Tile(
        coord = coord,
        tileType = TileType.Empty,
        unlocked = true
      )
    .toMap

    TerritoryGame(
      tiles = initialTiles,
      wheat = 50.0, // Start with some wheat to build first field
      wood = 0.0,
      gold = 0,
      lastTickTime = currentTimeMillis,
      totalAbdications = 0
    )

  // Tick the game: accumulate wheat based on production rate
  def tick(game: TerritoryGame, currentTimeMillis: Long): TerritoryGame =
    val elapsedSeconds = (currentTimeMillis - game.lastTickTime) / 1000.0
    val wheatProduced = game.totalIncomeRate * elapsedSeconds

    game.copy(
      wheat = game.wheat + wheatProduced,
      lastTickTime = currentTimeMillis
    )

  // Build a wheat field on an empty tile
  def buildWheatField(game: TerritoryGame, coord: Coord): Either[String, TerritoryGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) if !tile.unlocked => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty => Left("Tile is not empty")
      case Some(tile) if game.wheat < wheatFieldBuildCost => Left(s"Not enough wheat (need $wheatFieldBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.WheatField(1))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wheat = game.wheat - wheatFieldBuildCost
        ))

  // Build a farm on an empty tile (requires at least one wheat field)
  def buildFarm(game: TerritoryGame, coord: Coord): Either[String, TerritoryGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) if !tile.unlocked => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField => Left("Build a wheat field first")
      case Some(tile) if game.wheat < farmBuildCost => Left(s"Not enough wheat (need $farmBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Farm(1))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wheat = game.wheat - farmBuildCost
        ))

  // Build a woodcutter on an empty tile (requires at least one wheat field)
  def buildWoodcutter(game: TerritoryGame, coord: Coord): Either[String, TerritoryGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) if !tile.unlocked => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField => Left("Build a wheat field first")
      case Some(tile) if game.wheat < woodcutterBuildCost => Left(s"Not enough wheat (need $woodcutterBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Woodcutter(1))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wheat = game.wheat - woodcutterBuildCost
        ))

  // Level up a wheat field
  def levelUpWheatField(game: TerritoryGame, coord: Coord): Either[String, TerritoryGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
        case TileType.WheatField(level) =>
          val cost = wheatFieldLevelUpCost(level)
          if game.wheat < cost then
            Left(s"Not enough wheat (need $cost)")
          else
            val updatedTile = tile.copy(tileType = TileType.WheatField(level + 1))
            Right(game.copy(
              tiles = game.tiles.updated(coord, updatedTile),
              wheat = game.wheat - cost
            ))
        case _ => Left("Tile is not a wheat field")

  // Level up a farm
  def levelUpFarm(game: TerritoryGame, coord: Coord): Either[String, TerritoryGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
        case TileType.Farm(level) =>
          val cost = farmLevelUpCost(level)
          if game.wheat < cost then
            Left(s"Not enough wheat (need $cost)")
          else
            val updatedTile = tile.copy(tileType = TileType.Farm(level + 1))
            Right(game.copy(
              tiles = game.tiles.updated(coord, updatedTile),
              wheat = game.wheat - cost
            ))
        case _ => Left("Tile is not a farm")

  // Level up a woodcutter
  def levelUpWoodcutter(game: TerritoryGame, coord: Coord): Either[String, TerritoryGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
        case TileType.Woodcutter(level) =>
          val cost = woodcutterLevelUpCost(level)
          if game.wheat < cost then
            Left(s"Not enough wheat (need $cost)")
          else
            val updatedTile = tile.copy(tileType = TileType.Woodcutter(level + 1))
            Right(game.copy(
              tiles = game.tiles.updated(coord, updatedTile),
              wheat = game.wheat - cost
            ))
        case _ => Left("Tile is not a woodcutter")

  // Destroy a building on a tile (returns it to empty state, no refund)
  def destroyBuilding(game: TerritoryGame, coord: Coord): Either[String, TerritoryGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) if !tile.unlocked => Left("Tile is locked")
      case Some(tile) if tile.isEmpty => Left("Tile is already empty")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Empty)
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile)
        ))

  // Abdicate: reset tiles, gain gold based on income rate
  def abdicate(game: TerritoryGame, currentTimeMillis: Long): Either[String, TerritoryGame] =
    if !game.allTilesFilled then
      Left("Must fill all unlocked tiles with buildings before abdicating")
    else
      val goldReward = abdicationReward(game.totalIncomeRate)
      val resetTiles = game.tiles.map:
        case (coord, tile) if tile.unlocked =>
          coord -> tile.copy(tileType = TileType.Empty)
        case (coord, tile) =>
          coord -> tile

      Right(game.copy(
        tiles = resetTiles,
        wheat = 50.0, // Reset wheat, give starting amount
        wood = 0.0,   // Reset wood
        gold = game.gold + goldReward,
        lastTickTime = currentTimeMillis,
        totalAbdications = game.totalAbdications + 1
      ))

  // Get all coords that can be unlocked (coords adjacent to unlocked tiles that aren't already tiles)
  def unlockableCoords(game: TerritoryGame): Set[Coord] =
    val unlockedCoords = game.unlockedTiles.map(_.coord).toSet
    val allAdjacentToUnlocked = unlockedCoords.flatMap(_.neighbors)
    allAdjacentToUnlocked.filterNot(game.tiles.contains)

  // Unlock a specific tile with gold (must be adjacent to an unlocked tile)
  def unlockTile(game: TerritoryGame, coord: Coord): Either[String, TerritoryGame] =
    if game.tiles.contains(coord) then
      Left("Tile already exists")
    else if !unlockableCoords(game).contains(coord) then
      Left("Can only unlock tiles adjacent to your territory")
    else
      val cost = tileUnlockCost(game.unlockedTiles.size)
      if game.gold < cost then
        Left(s"Not enough gold (need $cost)")
      else
        val newTile = Tile(coord = coord, tileType = TileType.Empty, unlocked = true)
        Right(game.copy(
          tiles = game.tiles.updated(coord, newTile),
          gold = game.gold - cost
        ))

