package shared.Territory

import upickle.default.ReadWriter

// ============================================================================
// Tile Types
// ============================================================================

enum TileType derives ReadWriter:
  case Empty
  case WheatField(level: Int) // level determines production rate

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

  def level: Int = tileType match
    case TileType.WheatField(lvl) => lvl
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

  def allTilesFilledWithWheat: Boolean =
    unlockedTiles.nonEmpty && unlockedTiles.forall(_.isWheatField)

  def totalIncomeRate: Double =
    unlockedTiles.map(TerritoryLogic.productionRate).sum

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
  val MaxTiles: Int = 16

  // Production rate per level (wheat per second)
  def productionRate(tile: Tile): Double = tile.tileType match
    case TileType.WheatField(level) => level * 0.5 // 0.5/s at level 1, 1.0/s at level 2, etc.
    case _ => 0.0

  // Cost to build a wheat field on an empty tile
  def buildCost: Int = 10

  // Cost to level up a wheat field
  def levelUpCost(currentLevel: Int): Int =
    currentLevel * 20 // Level 1→2 costs 20, 2→3 costs 40, etc.

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
      case Some(tile) if game.wheat < buildCost => Left(s"Not enough wheat (need $buildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.WheatField(1))
        Right(game.copy(
          tiles = game.tiles.updated(tileId, updatedTile),
          wheat = game.wheat - buildCost
        ))

  // Level up a wheat field
  def levelUpWheatField(game: TerritoryGame, tileId: Int): Either[String, TerritoryGame] =
    game.tiles.get(tileId) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
        case TileType.WheatField(level) =>
          val cost = levelUpCost(level)
          if game.wheat < cost then
            Left(s"Not enough wheat (need $cost)")
          else
            val updatedTile = tile.copy(tileType = TileType.WheatField(level + 1))
            Right(game.copy(
              tiles = game.tiles.updated(tileId, updatedTile),
              wheat = game.wheat - cost
            ))
        case _ => Left("Tile is not a wheat field")

  // Abdicate: reset tiles, gain gold based on income rate
  def abdicate(game: TerritoryGame, currentTimeMillis: Long): Either[String, TerritoryGame] =
    if !game.allTilesFilledWithWheat then
      Left("Must fill all unlocked tiles with wheat fields before abdicating")
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

