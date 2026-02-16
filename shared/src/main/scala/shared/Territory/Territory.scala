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

  def neighborsWithinRadius(radius: Int): Set[Coord] =
    (for
      dr <- -radius to radius
      dc <- -radius to radius
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
  case Bureau(level: Int)     // auto-upgrades nearby buildings, costs wood

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

  def isBureau: Boolean = tileType match
    case TileType.Bureau(_) => true
    case _ => false

  def isBuilding: Boolean = isWheatField || isFarm || isWoodcutter || isBureau

  def isUpgradeable: Boolean = isWheatField || isFarm || isWoodcutter

  def level: Int = tileType match
    case TileType.WheatField(lvl) => lvl
    case TileType.Farm(lvl) => lvl
    case TileType.Woodcutter(lvl) => lvl
    case TileType.Bureau(lvl) => lvl
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
    totalAbdications: Int,
    upgradeCooldowns: Map[Coord, Long] = Map.empty // Timestamp when tile can be auto-upgraded again
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

  // Bureau constants
  val BureauIntervalSeconds: Int = 5      // Bureau attempts upgrade every 5 seconds
  val BureauRadius: Int = 2               // Bureau affects tiles within 2 tile radius
  val BureauUpgradeCooldownMs: Long = 60000 // 60 seconds cooldown after auto-upgrade
  val BureauWoodCostPerUpgrade: Int = 100  // Wood cost for each auto-upgrade
  val ForestGroupBonusPerTile: Double = 0.10 // 10% bonus per connected woodcutter

  // Initial 2x2 tiles at origin (center of infinite grid)
  val InitialUnlockedCoords: Set[Coord] = Set(
    Coord(0, 0), Coord(0, 1),
    Coord(1, 0), Coord(1, 1)
  )

  // Find all woodcutters in the same connected group as the given coord
  def findConnectedWoodcutters(game: TerritoryGame, startCoord: Coord): Set[Coord] =
    def floodFill(toVisit: Set[Coord], visited: Set[Coord]): Set[Coord] =
      if toVisit.isEmpty then visited
      else
        val current = toVisit.head
        val remaining = toVisit.tail
        if visited.contains(current) then floodFill(remaining, visited)
        else
          game.tiles.get(current) match
            case Some(tile) if tile.isWoodcutter =>
              val newNeighbors = current.neighbors.filterNot(visited.contains)
              floodFill(remaining ++ newNeighbors, visited + current)
            case _ =>
              floodFill(remaining, visited)
    floodFill(Set(startCoord), Set.empty)

  // Calculate forest group bonus multiplier for a woodcutter
  def forestGroupBonusMultiplier(game: TerritoryGame, coord: Coord): Double =
    val groupSize = findConnectedWoodcutters(game, coord).size
    1.0 + (groupSize - 1) * ForestGroupBonusPerTile // -1 because we don't count self for bonus

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

  // Wood production rate for a specific tile per second (with forest group bonus)
  def woodProductionRate(game: TerritoryGame, tile: Tile): Double =
    val base = woodProductionPerSecond(tile)
    if base > 0 then base * forestGroupBonusMultiplier(game, tile.coord)
    else 0.0

  // Legacy method for backwards compatibility
  def productionRate(tile: Tile): Double = productionPerSecond(tile)

  // Production per harvest for a specific tile (with farm bonuses applied)
  def productionPerHarvest(game: TerritoryGame, tile: Tile): Double =
    val base = baseWheatProductionRate(tile)
    if base > 0 then base * farmBonusMultiplier(game, tile.coord)
    else 0.0

  // Wood production per harvest for a specific tile (with forest group bonus)
  def woodProductionPerHarvest(game: TerritoryGame, tile: Tile): Double =
    val base = baseWoodProductionRate(tile)
    if base > 0 then base * forestGroupBonusMultiplier(game, tile.coord)
    else 0.0

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

  // Cost to build a bureau on an empty tile (costs wood, not wheat)
  def bureauBuildCost: Int = 500

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

  // Get upgrade cost for any upgradeable tile (returns wheat cost)
  def getUpgradeCost(tile: Tile): Option[Int] = tile.tileType match
    case TileType.WheatField(level) => Some(wheatFieldLevelUpCost(level))
    case TileType.Farm(level) => Some(farmLevelUpCost(level))
    case TileType.Woodcutter(level) => Some(woodcutterLevelUpCost(level))
    case _ => None

  // Legacy alias
  def levelUpCost(currentLevel: Int): Int = wheatFieldLevelUpCost(currentLevel)

  // Cost to unlock next tile (exponential, capped to prevent overflow)
  def tileUnlockCost(currentUnlockedCount: Int): Int =
    val tilesAfterInitial = math.max(0, currentUnlockedCount - InitialTileCount)
    if tilesAfterInitial == 0 then 100
    else if tilesAfterInitial >= 20 then 100_000_000 // Cap at 100 million
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

  // Build a bureau on an empty tile (costs wood, requires at least one wheat field)
  def buildBureau(game: TerritoryGame, coord: Coord): Either[String, TerritoryGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) if !tile.unlocked => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField => Left("Build a wheat field first")
      case Some(tile) if game.wood < bureauBuildCost => Left(s"Not enough wood (need $bureauBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Bureau(1))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wood = game.wood - bureauBuildCost
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

  // Bureau auto-upgrade: upgrade a tile, pay wheat cost + wood cost, set cooldown
  // Returns updated game and the coord that was upgraded (if any)
  def bureauAutoUpgrade(game: TerritoryGame, bureauCoord: Coord, currentTimeMillis: Long): Option[(TerritoryGame, Coord)] =
    game.tiles.get(bureauCoord) match
      case Some(bureauTile) if bureauTile.isBureau =>
        // Find upgradeable tiles within radius that aren't on cooldown
        val nearbyCoords = bureauCoord.neighborsWithinRadius(BureauRadius)
        val upgradeableCoords = nearbyCoords.filter: coord =>
          game.tiles.get(coord).exists: tile =>
            tile.isUpgradeable &&
              game.upgradeCooldowns.get(coord).forall(_ <= currentTimeMillis)
        
        // Try to upgrade the first one we can afford
        upgradeableCoords.flatMap(coord => game.tiles.get(coord).map(coord -> _)).find: (coord, tile) =>
          val wheatCost = getUpgradeCost(tile).getOrElse(0)
          val totalWoodCost = BureauWoodCostPerUpgrade
          game.wheat >= wheatCost && game.wood >= totalWoodCost
        .flatMap: (targetCoord, targetTile) =>
          val wheatCost = getUpgradeCost(targetTile).getOrElse(0)
          // Perform the upgrade based on tile type
          val upgradedTileType = targetTile.tileType match
            case TileType.WheatField(lvl) => TileType.WheatField(lvl + 1)
            case TileType.Farm(lvl) => TileType.Farm(lvl + 1)
            case TileType.Woodcutter(lvl) => TileType.Woodcutter(lvl + 1)
            case other => other
          
          val upgradedTile = targetTile.copy(tileType = upgradedTileType)
          val newCooldown = currentTimeMillis + BureauUpgradeCooldownMs
          
          val newGame = game.copy(
            tiles = game.tiles.updated(targetCoord, upgradedTile),
            wheat = game.wheat - wheatCost,
            wood = game.wood - BureauWoodCostPerUpgrade,
            upgradeCooldowns = game.upgradeCooldowns.updated(targetCoord, newCooldown)
          )
          Some((newGame, targetCoord))
      case _ => None

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
        totalAbdications = game.totalAbdications + 1,
        upgradeCooldowns = Map.empty // Reset cooldowns
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

  // Simple Perlin-like noise for continent generation
  private def noise2D(x: Double, y: Double, seed: Long): Double =
    val random = new scala.util.Random(seed ^ (x.toLong * 73856093L) ^ (y.toLong * 19349663L))
    random.nextDouble()

  private def smoothNoise(x: Double, y: Double, seed: Long): Double =
    val x0 = x.floor.toInt
    val y0 = y.floor.toInt
    val fx = x - x0
    val fy = y - y0
    
    // Get values at corners
    val v00 = noise2D(x0, y0, seed)
    val v10 = noise2D(x0 + 1, y0, seed)
    val v01 = noise2D(x0, y0 + 1, seed)
    val v11 = noise2D(x0 + 1, y0 + 1, seed)
    
    // Smooth interpolation
    val sx = fx * fx * (3 - 2 * fx)
    val sy = fy * fy * (3 - 2 * fy)
    
    val i0 = v00 * (1 - sx) + v10 * sx
    val i1 = v01 * (1 - sx) + v11 * sx
    i0 * (1 - sy) + i1 * sy

  private def perlinNoise(x: Double, y: Double, seed: Long, octaves: Int = 3): Double =
    var total = 0.0
    var frequency = 1.0
    var amplitude = 1.0
    var maxValue = 0.0
    
    for _ <- 0 until octaves do
      total += smoothNoise(x * frequency, y * frequency, seed) * amplitude
      maxValue += amplitude
      amplitude *= 0.5
      frequency *= 2
    
    total / maxValue

  // Dev tool: Unlock many tiles for free (creates continent-like shapes using Perlin noise)
  def unlockManyTiles(game: TerritoryGame, count: Int): TerritoryGame =
    val seed = System.currentTimeMillis()
    val noiseScale = 0.15 // Controls how "zoomed in" the noise is
    
    // Find center of current territory
    val existingCoords = game.tiles.keySet
    val centerRow = existingCoords.map(_.row).sum.toDouble / existingCoords.size
    val centerCol = existingCoords.map(_.col).sum.toDouble / existingCoords.size
    
    (1 to count).foldLeft(game): (currentGame, _) =>
      val available = unlockableCoords(currentGame)
      if available.isEmpty then currentGame
      else
        val currentCoords = currentGame.tiles.keySet
        
        // Score each candidate using Perlin noise + distance from center
        val scored = available.toList.map: coord =>
          val neighborCount = coord.neighbors.count(currentCoords.contains)
          
          // Use Perlin noise to create organic boundary
          val noiseVal = perlinNoise(coord.col * noiseScale, coord.row * noiseScale, seed)
          
          // Distance from center (normalized)
          val dist = math.sqrt(math.pow(coord.row - centerRow, 2) + math.pow(coord.col - centerCol, 2))
          val maxDist = math.sqrt(currentCoords.size.toDouble) * 1.5
          val normalizedDist = dist / maxDist
          
          // Threshold based on noise - tiles further out need higher noise to be included
          val threshold = 0.3 + normalizedDist * 0.4
          val noiseScore = if noiseVal > threshold then 1.0 else 0.3
          
          // Strongly prefer filling holes (high neighbor count)
          val holeFillingScore = neighborCount match
            case n if n >= 5 => 2.0  // Definitely fill holes
            case 4 => 1.5
            case 3 => 1.0
            case 2 => 0.8
            case _ => 0.5
          
          (coord, noiseScore * holeFillingScore)
        
        // Pick the best candidate
        val best = scored.maxBy(_._2)._1
        
        val newTile = Tile(coord = best, tileType = TileType.Empty, unlocked = true)
        currentGame.copy(tiles = currentGame.tiles.updated(best, newTile))

