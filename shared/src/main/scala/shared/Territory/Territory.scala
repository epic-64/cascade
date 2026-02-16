package shared.Territory

import upickle.default.ReadWriter

// ============================================================================
// Tile Types
// ============================================================================

enum TileType derives ReadWriter:
  case Empty
  case WheatField(level: Int) // level determines production rate
  case Farm(level: Int)       // boosts nearby wheat fields

// ============================================================================
// Tile
// ============================================================================

case class Tile(
    id: Int,
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

  def isBuilding: Boolean = isWheatField || isFarm

  def level: Int = tileType match
    case TileType.WheatField(lvl) => lvl
    case TileType.Farm(lvl) => lvl
    case _ => 0

// ============================================================================
// Game State
// ============================================================================

case class TerritoryGame(
    tiles: Map[Int, Tile],
    wheat: Double, // Can be fractional for smooth accumulation
    gold: Int,
    lastTickTime: Long, // Timestamp in milliseconds for offline progress
    totalAbdications: Int
) derives ReadWriter:

  def unlockedTiles: List[Tile] =
    tiles.values.filter(_.unlocked).toList.sortBy(_.id)

  def lockedTiles: List[Tile] =
    tiles.values.filterNot(_.unlocked).toList.sortBy(_.id)

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
  val InitialTileCount: Int = 4
  val MaxTiles: Int = 64
  val FarmBoostPerLevel: Double = 0.25 // 25% boost per farm level

  // Grid size calculation based on tile count (always 8x8 for 64 tiles)
  def gridSize(unlockedCount: Int): Int = 8

  // Convert tile ID to (row, col) based on current grid size
  def tilePosition(tileId: Int, gridWidth: Int): (Int, Int) =
    (tileId / gridWidth, tileId % gridWidth)

  // Get adjacent tile IDs (including diagonals) within radius 1
  def adjacentTileIds(tileId: Int, gridWidth: Int): Set[Int] =
    val (row, col) = tilePosition(tileId, gridWidth)
    val adjacent = for
      dr <- -1 to 1
      dc <- -1 to 1
      if !(dr == 0 && dc == 0) // exclude self
      newRow = row + dr
      newCol = col + dc
      if newRow >= 0 && newRow < gridWidth && newCol >= 0 && newCol < gridWidth
    yield newRow * gridWidth + newCol
    adjacent.toSet

  // Base production rate per level (wheat per second) - without bonuses
  def baseProductionRate(tile: Tile): Double = tile.tileType match
    case TileType.WheatField(level) => level * 0.5 // 0.5/s at level 1, 1.0/s at level 2, etc.
    case _ => 0.0

  // Calculate farm bonus multiplier for a wheat field at given position
  def farmBonusMultiplier(game: TerritoryGame, tileId: Int): Double =
    val gSize = gridSize(game.unlockedTiles.size)
    val adjacentIds = adjacentTileIds(tileId, gSize)
    val farmBonus = adjacentIds.flatMap(game.tiles.get).collect:
      case tile if tile.isFarm => tile.level * FarmBoostPerLevel
    .sum
    1.0 + farmBonus

  // Production rate for a specific tile (with farm bonuses applied)
  def productionRate(game: TerritoryGame, tile: Tile): Double =
    val base = baseProductionRate(tile)
    if base > 0 then base * farmBonusMultiplier(game, tile.id)
    else 0.0

  // Legacy method for backwards compatibility
  def productionRate(tile: Tile): Double = baseProductionRate(tile)

  // Total production rate for the game (all wheat fields with bonuses)
  def totalProductionRate(game: TerritoryGame): Double =
    game.unlockedTiles.map(tile => productionRate(game, tile)).sum

  // Cost to build a wheat field on an empty tile
  def wheatFieldBuildCost: Int = 10

  // Cost to build a farm on an empty tile
  def farmBuildCost: Int = 25

  // Legacy alias
  def buildCost: Int = wheatFieldBuildCost

  // Cost to level up a wheat field
  def wheatFieldLevelUpCost(currentLevel: Int): Int =
    currentLevel * 20 // Level 1→2 costs 20, 2→3 costs 40, etc.

  // Cost to level up a farm
  def farmLevelUpCost(currentLevel: Int): Int =
    currentLevel * 30 // Level 1→2 costs 30, 2→3 costs 60, etc.

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
    val initialTiles = (0 until MaxTiles).map: id =>
      id -> Tile(
        id = id,
        tileType = TileType.Empty,
        unlocked = id < InitialTileCount
      )
    .toMap

    TerritoryGame(
      tiles = initialTiles,
      wheat = 50.0, // Start with some wheat to build first field
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
  def buildWheatField(game: TerritoryGame, tileId: Int): Either[String, TerritoryGame] =
    game.tiles.get(tileId) match
      case None => Left("Tile not found")
      case Some(tile) if !tile.unlocked => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty => Left("Tile is not empty")
      case Some(tile) if game.wheat < wheatFieldBuildCost => Left(s"Not enough wheat (need $wheatFieldBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.WheatField(1))
        Right(game.copy(
          tiles = game.tiles.updated(tileId, updatedTile),
          wheat = game.wheat - wheatFieldBuildCost
        ))

  // Build a farm on an empty tile (requires at least one wheat field)
  def buildFarm(game: TerritoryGame, tileId: Int): Either[String, TerritoryGame] =
    game.tiles.get(tileId) match
      case None => Left("Tile not found")
      case Some(tile) if !tile.unlocked => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField => Left("Build a wheat field first")
      case Some(tile) if game.wheat < farmBuildCost => Left(s"Not enough wheat (need $farmBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Farm(1))
        Right(game.copy(
          tiles = game.tiles.updated(tileId, updatedTile),
          wheat = game.wheat - farmBuildCost
        ))

  // Level up a wheat field
  def levelUpWheatField(game: TerritoryGame, tileId: Int): Either[String, TerritoryGame] =
    game.tiles.get(tileId) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
        case TileType.WheatField(level) =>
          val cost = wheatFieldLevelUpCost(level)
          if game.wheat < cost then
            Left(s"Not enough wheat (need $cost)")
          else
            val updatedTile = tile.copy(tileType = TileType.WheatField(level + 1))
            Right(game.copy(
              tiles = game.tiles.updated(tileId, updatedTile),
              wheat = game.wheat - cost
            ))
        case _ => Left("Tile is not a wheat field")

  // Level up a farm
  def levelUpFarm(game: TerritoryGame, tileId: Int): Either[String, TerritoryGame] =
    game.tiles.get(tileId) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
        case TileType.Farm(level) =>
          val cost = farmLevelUpCost(level)
          if game.wheat < cost then
            Left(s"Not enough wheat (need $cost)")
          else
            val updatedTile = tile.copy(tileType = TileType.Farm(level + 1))
            Right(game.copy(
              tiles = game.tiles.updated(tileId, updatedTile),
              wheat = game.wheat - cost
            ))
        case _ => Left("Tile is not a farm")

  // Abdicate: reset tiles, gain gold based on income rate
  def abdicate(game: TerritoryGame, currentTimeMillis: Long): Either[String, TerritoryGame] =
    if !game.allTilesFilled then
      Left("Must fill all unlocked tiles with buildings before abdicating")
    else
      val goldReward = abdicationReward(game.totalIncomeRate)
      val resetTiles = game.tiles.map:
        case (id, tile) if tile.unlocked =>
          id -> tile.copy(tileType = TileType.Empty)
        case (id, tile) =>
          id -> tile

      Right(game.copy(
        tiles = resetTiles,
        wheat = 50.0, // Reset wheat, give starting amount
        gold = game.gold + goldReward,
        lastTickTime = currentTimeMillis,
        totalAbdications = game.totalAbdications + 1
      ))

  // Unlock next tile with gold
  def unlockTile(game: TerritoryGame): Either[String, TerritoryGame] =
    val nextTile = game.lockedTiles.headOption
    nextTile match
      case None => Left("No more tiles to unlock")
      case Some(tile) =>
        val cost = tileUnlockCost(game.unlockedTiles.size)
        if game.gold < cost then
          Left(s"Not enough gold (need $cost)")
        else
          val updatedTile = tile.copy(unlocked = true)
          Right(game.copy(
            tiles = game.tiles.updated(tile.id, updatedTile),
            gold = game.gold - cost
          ))

