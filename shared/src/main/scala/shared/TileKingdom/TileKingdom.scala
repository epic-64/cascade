package shared.TileKingdom

import upickle.default.ReadWriter

// ============================================================================
// Resource System
// ============================================================================

enum Resource derives ReadWriter:
  case Wheat, Wood, Faith, Gold

case class Cost(amount: Int, resource: Resource) derives ReadWriter

case class Resources(
    wheat: Double = 0.0,
    wood: Double = 0.0,
    faith: Double = 0.0,
    gold: Int = 0
) derives ReadWriter:
  def get(resource: Resource): Double = resource match
    case Resource.Wheat => wheat
    case Resource.Wood  => wood
    case Resource.Faith => faith
    case Resource.Gold  => gold.toDouble

  def canAfford(cost: Cost): Boolean = get(cost.resource) >= cost.amount

  def canAfford(cost: Int, resource: Resource): Boolean = get(resource) >= cost

  def deduct(cost: Cost): Resources = deduct(cost.amount, cost.resource)

  def deduct(amount: Int, resource: Resource): Resources = resource match
    case Resource.Wheat => copy(wheat = wheat - amount)
    case Resource.Wood  => copy(wood = wood - amount)
    case Resource.Faith => copy(faith = faith - amount)
    case Resource.Gold  => copy(gold = gold - amount)

  def add(amount: Double, resource: Resource): Resources = resource match
    case Resource.Wheat => copy(wheat = wheat + amount)
    case Resource.Wood  => copy(wood = wood + amount)
    case Resource.Faith => copy(faith = faith + amount)
    case Resource.Gold  => copy(gold = gold + amount.toInt)

// ============================================================================
// Coordinate type for infinite grid
// ============================================================================

case class Coord(row: Int, col: Int) derives ReadWriter:
  def neighbors: Set[Coord] = neighborsWithinRadius(1)

  def neighborsWithinRadius(radius: Int): Set[Coord] =
    (for
      rowOffset <- -radius to radius
      colOffset <- -radius to radius
      if !(rowOffset == 0 && colOffset == 0)
    yield Coord(row + rowOffset, col + colOffset)).toSet

// ============================================================================
// Tile Types
// ============================================================================

enum TileType derives ReadWriter:
  case Empty
  case WheatField(level: Int) // level determines production rate
  case Farm(level: Int) // boosts nearby wheat fields
  case Woodcutter(level: Int) // produces wood
  case Bureau(level: Int) // auto-upgrades nearby buildings, costs wood
  case Temple(level: Int) // produces faith, costs wood
  case TownHall(politician: Option[Politician]) // has a slot for a politician

// ============================================================================
// Politician System
// ============================================================================

enum PoliticianEffect derives ReadWriter:
  case WheatProductionMultiplier(multiplier: Double) // e.g., 2.0 = 2x wheat production
  case WoodProductionMultiplier(multiplier: Double)
  case FaithProductionMultiplier(multiplier: Double)
  case AllProductionMultiplier(multiplier: Double)

case class Politician(
    id: String,
    name: String,
    title: String,
    effect: PoliticianEffect,
    emoji: String
) derives ReadWriter:
  def effectDescription: String = effect match
    case PoliticianEffect.WheatProductionMultiplier(m) => s"${(m * 100).toInt}% wheat production"
    case PoliticianEffect.WoodProductionMultiplier(m)  => s"${(m * 100).toInt}% wood production"
    case PoliticianEffect.FaithProductionMultiplier(m) => s"${(m * 100).toInt}% faith production"
    case PoliticianEffect.AllProductionMultiplier(m)   => s"${(m * 100).toInt}% all production"

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
    case _              => false

  def isWheatField: Boolean = tileType match
    case TileType.WheatField(_) => true
    case _                      => false

  def isFarm: Boolean = tileType match
    case TileType.Farm(_) => true
    case _                => false

  def isWoodcutter: Boolean = tileType match
    case TileType.Woodcutter(_) => true
    case _                      => false

  def isBureau: Boolean = tileType match
    case TileType.Bureau(_) => true
    case _                  => false

  def isTemple: Boolean = tileType match
    case TileType.Temple(_) => true
    case _                  => false

  def isTownHall: Boolean = tileType match
    case TileType.TownHall(_) => true
    case _                    => false

  def isBuilding: Boolean = isWheatField || isFarm || isWoodcutter || isBureau || isTemple || isTownHall

  def isUpgradeable: Boolean = isWheatField || isFarm || isWoodcutter || isTemple

  def level: Int = tileType match
    case TileType.WheatField(lvl) => lvl
    case TileType.Farm(lvl)       => lvl
    case TileType.Woodcutter(lvl) => lvl
    case TileType.Bureau(lvl)     => lvl
    case TileType.Temple(lvl)     => lvl
    case _                        => 0

  def upgradeCost: Option[Cost] = tileType match
    case TileType.WheatField(lvl) => Some(Cost(TileKingdomLogic.wheatFieldLevelUpCost(lvl), Resource.Wheat))
    case TileType.Farm(lvl)       => Some(Cost(TileKingdomLogic.farmLevelUpCost(lvl), Resource.Wheat))
    case TileType.Woodcutter(lvl) => Some(Cost(TileKingdomLogic.woodcutterLevelUpCost(lvl), Resource.Wheat))
    case TileType.Temple(lvl)     => Some(Cost(TileKingdomLogic.templeLevelUpCost(lvl), Resource.Wood))
    case _                        => None

// ============================================================================
// Game State
// ============================================================================

case class TileKingdomGame(
    tiles: Map[Coord, Tile],
    wheat: Double, // Can be fractional for smooth accumulation
    wood: Double, // Wood resource
    faith: Double, // Faith resource from temples
    gold: Int,
    lastTickTime: Long, // Timestamp in milliseconds for offline progress
    totalAbdications: Int,
    bureauBoosts: Map[Coord, Int] = Map.empty, // Number of faith boosts applied to each bureau
    upgradeCooldowns: Map[Coord, Long] = Map.empty, // Deprecated, kept for save compatibility
    politicianRoster: List[Politician] = List.empty, // Available politicians to assign
    lastPoliticianGeneration: Long = 0L // Timestamp of last politician generation
) derives ReadWriter:

  // Resource helpers
  def resources: Resources = Resources(wheat, wood, faith, gold)

  def canAfford(cost: Cost): Boolean = resources.canAfford(cost)

  def canAfford(amount: Int, resource: Resource): Boolean = resources.canAfford(amount, resource)

  def deduct(cost: Cost): TileKingdomGame = cost.resource match
    case Resource.Wheat => copy(wheat = wheat - cost.amount)
    case Resource.Wood  => copy(wood = wood - cost.amount)
    case Resource.Faith => copy(faith = faith - cost.amount)
    case Resource.Gold  => copy(gold = gold - cost.amount)

  def unlockedTiles: List[Tile] =
    tiles.values.filter(_.unlocked).toList.sortBy(t => (t.coord.row, t.coord.col))

  def lockedTiles: List[Tile] =
    tiles.values.filterNot(_.unlocked).toList.sortBy(t => (t.coord.row, t.coord.col))

  def allTilesFilled: Boolean =
    unlockedTiles.nonEmpty && unlockedTiles.forall(_.isBuilding)

  def hasWheatField: Boolean =
    unlockedTiles.exists(_.isWheatField)

  def totalIncomeRate: Double =
    TileKingdomLogic.totalWheatProductionRate(this) + TileKingdomLogic.totalWoodProductionRate(this)

  def nextTileUnlockCost: Int =
    TileKingdomLogic.tileUnlockCost(unlockedTiles.size)

  def abdicationGoldReward: Int =
    TileKingdomLogic.abdicationReward(totalIncomeRate)

// ============================================================================
// Game Logic
// ============================================================================

object TileKingdomLogic:

  // Constants
  val TickIntervalSeconds: Double = 0.25 // Tick four times per second
  val ProductionIntervalSeconds: Int = 10 // Wheat fields produce every 10 seconds
  val InitialTileCount: Int = 4
  val FarmBoostPerLevel: Double = 0.25 // 25% boost per farm level

  // Bureau constants
  val BureauIntervalSeconds: Int = 5 // Bureau attempts upgrade every 5 seconds
  val BureauRadius: Int = 2 // Bureau affects tiles within 2 tile radius
  val BureauWoodCostPerUpgrade: Int = 100 // Wood cost for each auto-upgrade
  val ForestGroupBonusPerTile: Double = 0.10 // 10% bonus per connected woodcutter

  // Temple constants
  val TempleBuildCost: Int = 200 // Wood cost to build a temple
  val FaithBoostCost: Int = 100 // Faith cost to boost a bureau
  val FaithBoostMultiplier: Double = 10.0 // Each boost increases bureau speed by 1000%

  // Town Hall constants
  val TownHallBuildCost: Int = 300 // Wood cost to build a town hall
  val TownHallInfluenceRadius: Int = 2 // Town Hall affects tiles within 2 tile radius
  val PoliticianGenerationIntervalSeconds: Int = 300 // 5 minutes = 300 seconds
  val MaxPoliticianRosterSize: Int = 3 // Maximum politicians in roster

  // Politician definitions
  val PoliticianPool: List[(String, String, PoliticianEffect, String)] = List(
    ("Farmer General", "Agricultural Expert", PoliticianEffect.WheatProductionMultiplier(2.0), "👨‍🌾"),
    ("Lumber Baron", "Forestry Minister", PoliticianEffect.WoodProductionMultiplier(2.0), "🪓"),
    ("High Priest", "Spiritual Leader", PoliticianEffect.FaithProductionMultiplier(2.0), "🙏"),
    ("Chancellor", "Economic Advisor", PoliticianEffect.AllProductionMultiplier(1.5), "📊"),
    ("Harvest Queen", "Fertility Goddess", PoliticianEffect.WheatProductionMultiplier(3.0), "👑"),
    ("Forest Warden", "Nature Guardian", PoliticianEffect.WoodProductionMultiplier(2.5), "🌲"),
    ("Oracle", "Divine Seer", PoliticianEffect.FaithProductionMultiplier(2.5), "🔮"),
    ("Grand Vizier", "Master Strategist", PoliticianEffect.AllProductionMultiplier(1.25), "🎭")
  )

  // Initial 2x2 tiles at origin (center of infinite grid)
  val InitialUnlockedCoords: Set[Coord] = Set(
    Coord(0, 0),
    Coord(0, 1),
    Coord(1, 0),
    Coord(1, 1)
  )

  // Find all woodcutters in the same connected group as the given coord
  def findConnectedWoodcutters(game: TileKingdomGame, startCoord: Coord): Set[Coord] =
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
  // Bonus escalates: 2 tiles = 10%, 3 tiles = 10+20=30%, 4 tiles = 10+20+30=60%, etc.
  def forestGroupBonusMultiplier(game: TileKingdomGame, coord: Coord): Double =
    val groupSize = findConnectedWoodcutters(game, coord).size
    val n = groupSize - 1 // Number of other woodcutters in group
    val totalBonus = n * (n + 1) / 2.0 * ForestGroupBonusPerTile // Triangular number * bonus per tile
    1.0 + totalBonus

  // Find all Town Halls that affect a given coord (within their influence radius)
  def townHallsAffecting(game: TileKingdomGame, coord: Coord): List[(Coord, Politician)] =
    game.tiles.toList.flatMap:
      case (townHallCoord, tile) => tile.tileType match
        case TileType.TownHall(Some(politician))
          if townHallCoord.neighborsWithinRadius(TownHallInfluenceRadius).contains(coord) =>
          Some((townHallCoord, politician))
        case _ => None

  // Calculate Town Hall bonus multiplier for wheat production at a given coord
  def townHallWheatMultiplier(game: TileKingdomGame, coord: Coord): Double =
    townHallsAffecting(game, coord).foldLeft(1.0): (acc, entry) =>
      val (_, politician) = entry
      politician.effect match
        case PoliticianEffect.WheatProductionMultiplier(m) => acc * m
        case PoliticianEffect.AllProductionMultiplier(m)   => acc * m
        case _ => acc

  // Calculate Town Hall bonus multiplier for wood production at a given coord
  def townHallWoodMultiplier(game: TileKingdomGame, coord: Coord): Double =
    townHallsAffecting(game, coord).foldLeft(1.0): (acc, entry) =>
      val (_, politician) = entry
      politician.effect match
        case PoliticianEffect.WoodProductionMultiplier(m) => acc * m
        case PoliticianEffect.AllProductionMultiplier(m)  => acc * m
        case _ => acc

  // Calculate Town Hall bonus multiplier for faith production at a given coord
  def townHallFaithMultiplier(game: TileKingdomGame, coord: Coord): Double =
    townHallsAffecting(game, coord).foldLeft(1.0): (acc, entry) =>
      val (_, politician) = entry
      politician.effect match
        case PoliticianEffect.FaithProductionMultiplier(m) => acc * m
        case PoliticianEffect.AllProductionMultiplier(m)   => acc * m
        case _ => acc

  // Generate a random politician
  def generatePolitician(seed: Long): Politician =
    val random = new scala.util.Random(seed)
    val (name, title, effect, emoji) = PoliticianPool(random.nextInt(PoliticianPool.size))
    Politician(
      id = s"politician_${seed}_${random.nextInt(10000)}",
      name = name,
      title = title,
      effect = effect,
      emoji = emoji
    )

  // Check and generate new politicians based on elapsed time
  def generateNewPoliticians(game: TileKingdomGame, currentTimeMillis: Long): TileKingdomGame =
    // Don't generate if roster is full
    if game.politicianRoster.size >= MaxPoliticianRosterSize then
      return game

    val intervalMs = PoliticianGenerationIntervalSeconds * 1000L
    val lastGen = if game.lastPoliticianGeneration == 0L then currentTimeMillis else game.lastPoliticianGeneration
    val elapsedSinceLastGen = currentTimeMillis - lastGen
    val newPoliticiansCount = (elapsedSinceLastGen / intervalMs).toInt

    if newPoliticiansCount > 0 then
      // Only generate up to the remaining space in roster
      val availableSlots = MaxPoliticianRosterSize - game.politicianRoster.size
      val actualNewCount = math.min(newPoliticiansCount, availableSlots)
      val newPoliticians = (0 until actualNewCount).map: i =>
        generatePolitician(currentTimeMillis + i)
      .toList
      game.copy(
        politicianRoster = game.politicianRoster ++ newPoliticians,
        lastPoliticianGeneration = lastGen + newPoliticiansCount * intervalMs
      )
    else game

  // Discard a politician from the roster
  def discardPolitician(game: TileKingdomGame, politicianId: String): TileKingdomGame =
    game.copy(politicianRoster = game.politicianRoster.filterNot(_.id == politicianId))

  // Base production per harvest (wheat per 10-second interval) - without bonuses
  def baseWheatProductionRate(tile: Tile): Double = tile.tileType match
    case TileType.WheatField(level) => level * 5.0 // 5 wheat at level 1, 10 at level 2, etc. (per 10s)
    case _                          => 0.0

  // Base wood production per harvest (wood per 10-second interval)
  def baseWoodProductionRate(tile: Tile): Double = tile.tileType match
    case TileType.Woodcutter(level) => level * 3.0 // 3 wood at level 1, 6 at level 2, etc. (per 10s)
    case _                          => 0.0

  // Base faith production per harvest (faith per 10-second interval)
  def baseFaithProductionRate(tile: Tile): Double = tile.tileType match
    case TileType.Temple(level) => level * 2.0 // 2 faith at level 1, 4 at level 2, etc. (per 10s)
    case _                      => 0.0

  // Legacy alias
  def baseProductionRate(tile: Tile): Double = baseWheatProductionRate(tile)

  // Production rate per second (for display and total income calculation)
  def productionPerSecond(tile: Tile): Double = baseWheatProductionRate(tile) / ProductionIntervalSeconds
  def woodProductionPerSecond(tile: Tile): Double = baseWoodProductionRate(tile) / ProductionIntervalSeconds
  def faithProductionPerSecond(tile: Tile): Double = baseFaithProductionRate(tile) / ProductionIntervalSeconds

  // Calculate farm bonus multiplier for a wheat field at given coord
  def farmBonusMultiplier(game: TileKingdomGame, coord: Coord): Double =
    val farmBonus = coord.neighbors.toList.flatMap(game.tiles.get).collect:
      case tile if tile.isFarm => tile.level * FarmBoostPerLevel
    .sum
    1.0 + farmBonus

  // Production rate for a specific tile per second (with farm bonuses and town hall bonuses applied)
  def productionRate(game: TileKingdomGame, tile: Tile): Double =
    val base = productionPerSecond(tile)
    if base > 0 then base * farmBonusMultiplier(game, tile.coord) * townHallWheatMultiplier(game, tile.coord)
    else 0.0

  // Wood production rate for a specific tile per second (with forest group bonus and town hall bonuses)
  def woodProductionRate(game: TileKingdomGame, tile: Tile): Double =
    val base = woodProductionPerSecond(tile)
    if base > 0 then base * forestGroupBonusMultiplier(game, tile.coord) * townHallWoodMultiplier(game, tile.coord)
    else 0.0

  // Faith production rate for a specific tile per second (with town hall bonuses)
  def faithProductionRate(game: TileKingdomGame, tile: Tile): Double =
    val base = faithProductionPerSecond(tile)
    if base > 0 then base * townHallFaithMultiplier(game, tile.coord)
    else 0.0

  // Legacy method for backwards compatibility
  def productionRate(tile: Tile): Double = productionPerSecond(tile)

  // Production per harvest for a specific tile (with farm bonuses and town hall bonuses applied)
  def productionPerHarvest(game: TileKingdomGame, tile: Tile): Double =
    val base = baseWheatProductionRate(tile)
    if base > 0 then base * farmBonusMultiplier(game, tile.coord) * townHallWheatMultiplier(game, tile.coord)
    else 0.0

  // Wood production per harvest for a specific tile (with forest group bonus and town hall bonuses)
  def woodProductionPerHarvest(game: TileKingdomGame, tile: Tile): Double =
    val base = baseWoodProductionRate(tile)
    if base > 0 then base * forestGroupBonusMultiplier(game, tile.coord) * townHallWoodMultiplier(game, tile.coord)
    else 0.0

  // Faith production per harvest for a specific tile (with town hall bonuses)
  def faithProductionPerHarvest(game: TileKingdomGame, tile: Tile): Double =
    val base = baseFaithProductionRate(tile)
    if base > 0 then base * townHallFaithMultiplier(game, tile.coord)
    else 0.0

  // Total wheat production rate for the game (all wheat fields with bonuses)
  def totalWheatProductionRate(game: TileKingdomGame): Double =
    game.unlockedTiles.map(tile => productionRate(game, tile)).sum

  // Total wood production rate
  def totalWoodProductionRate(game: TileKingdomGame): Double =
    game.unlockedTiles.map(tile => woodProductionRate(game, tile)).sum

  // Total faith production rate
  def totalFaithProductionRate(game: TileKingdomGame): Double =
    game.unlockedTiles.map(tile => faithProductionRate(game, tile)).sum

  // Cost to build a wheat field on an empty tile
  def wheatFieldBuildCost: Int = 10

  // Cost to build a farm on an empty tile
  def farmBuildCost: Int = 25

  // Cost to build a woodcutter on an empty tile
  def woodcutterBuildCost: Int = 20

  // Cost to build a bureau on an empty tile (costs wood, not wheat)
  def bureauBuildCost: Int = 500

  // Cost to build a temple on an empty tile (costs wood)
  def templeBuildCost: Int = TempleBuildCost

  // Cost to build a town hall on an empty tile (costs wood)
  def townHallBuildCost: Int = TownHallBuildCost

  // Legacy alias
  def buildCost: Int = wheatFieldBuildCost

  // Tier multiplier: 3x for every 10 levels (level 0-9: 1x, 10-19: 3x, 20-29: 9x, etc.)
  private def tierMultiplier(level: Int): Int =
    val tier = level / 10
    math.pow(3, tier).toInt

  // Cost to level up a wheat field
  def wheatFieldLevelUpCost(currentLevel: Int): Int =
    currentLevel * 20 * tierMultiplier(currentLevel) // Level 1→2 costs 20, 2→3 costs 40, etc.

  // Cost to level up a farm
  def farmLevelUpCost(currentLevel: Int): Int =
    currentLevel * 30 * tierMultiplier(currentLevel) // Level 1→2 costs 30, 2→3 costs 60, etc.

  // Cost to level up a woodcutter
  def woodcutterLevelUpCost(currentLevel: Int): Int =
    currentLevel * 25 * tierMultiplier(currentLevel) // Level 1→2 costs 25, 2→3 costs 50, etc.

  // Cost to level up a temple (costs wood)
  def templeLevelUpCost(currentLevel: Int): Int =
    currentLevel * 50 * tierMultiplier(currentLevel) // Level 1→2 costs 50 wood, 2→3 costs 100 wood, etc.

  // Cost to unlock next tile (linear with tier multiplier every 10 tiles)
  def tileUnlockCost(currentUnlockedCount: Int): Int =
    val tilesAfterInitial = math.max(0, currentUnlockedCount - InitialTileCount)
    if tilesAfterInitial == 0 then 100
    else
      val tier = tilesAfterInitial / 10
      val multiplier = math.pow(3, tier).toInt
      100 + tilesAfterInitial * 50 * multiplier

  // Gold reward for abdication based on total income rate
  def abdicationReward(totalIncomeRate: Double): Int =
    math.max(10, (totalIncomeRate * 20).toInt) // 20 gold per wheat/second

  // Create initial game state
  def newGame(currentTimeMillis: Long): TileKingdomGame =
    val initialTiles = InitialUnlockedCoords.map: coord =>
      coord -> Tile(
        coord = coord,
        tileType = TileType.Empty,
        unlocked = true
      )
    .toMap

    // Start with one politician in the roster
    val initialPolitician = generatePolitician(currentTimeMillis)

    TileKingdomGame(
      tiles = initialTiles,
      wheat = 50.0, // Start with some wheat to build first field
      wood = 0.0,
      faith = 0.0,
      gold = 0,
      lastTickTime = currentTimeMillis,
      totalAbdications = 0,
      politicianRoster = List(initialPolitician),
      lastPoliticianGeneration = currentTimeMillis
    )

  // Tick the game: accumulate wheat based on production rate
  def tick(game: TileKingdomGame, currentTimeMillis: Long): TileKingdomGame =
    val elapsedSeconds = (currentTimeMillis - game.lastTickTime) / 1000.0
    val wheatProduced = totalWheatProductionRate(game) * elapsedSeconds
    val woodProduced = totalWoodProductionRate(game) * elapsedSeconds
    val faithProduced = totalFaithProductionRate(game) * elapsedSeconds

    game.copy(
      wheat = game.wheat + wheatProduced,
      wood = game.wood + woodProduced,
      faith = game.faith + faithProduced,
      lastTickTime = currentTimeMillis
    )

  // Build a wheat field on an empty tile
  def buildWheatField(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                                           => Left("Tile not found")
      case Some(tile) if !tile.unlocked                   => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty                    => Left("Tile is not empty")
      case Some(tile) if game.wheat < wheatFieldBuildCost => Left(s"Not enough wheat (need $wheatFieldBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.WheatField(1))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wheat = game.wheat - wheatFieldBuildCost
        ))

  // Build a farm on an empty tile (requires at least one wheat field)
  def buildFarm(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                                     => Left("Tile not found")
      case Some(tile) if !tile.unlocked             => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty              => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField           => Left("Build a wheat field first")
      case Some(tile) if game.wheat < farmBuildCost => Left(s"Not enough wheat (need $farmBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Farm(1))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wheat = game.wheat - farmBuildCost
        ))

  // Build a woodcutter on an empty tile (requires at least one wheat field)
  def buildWoodcutter(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                                           => Left("Tile not found")
      case Some(tile) if !tile.unlocked                   => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty                    => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField                 => Left("Build a wheat field first")
      case Some(tile) if game.wheat < woodcutterBuildCost => Left(s"Not enough wheat (need $woodcutterBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Woodcutter(1))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wheat = game.wheat - woodcutterBuildCost
        ))

  // Build a bureau on an empty tile (costs wood, requires at least one wheat field)
  def buildBureau(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                                      => Left("Tile not found")
      case Some(tile) if !tile.unlocked              => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty               => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField            => Left("Build a wheat field first")
      case Some(tile) if game.wood < bureauBuildCost => Left(s"Not enough wood (need $bureauBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Bureau(1))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wood = game.wood - bureauBuildCost
        ))

  // Build a temple on an empty tile (costs wood, requires at least one wheat field)
  def buildTemple(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                                       => Left("Tile not found")
      case Some(tile) if !tile.unlocked               => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty                => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField             => Left("Build a wheat field first")
      case Some(tile) if game.wood < templeBuildCost  => Left(s"Not enough wood (need $templeBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Temple(1))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wood = game.wood - templeBuildCost
        ))

  // Build a town hall on an empty tile (costs wood, requires at least one wheat field)
  def buildTownHall(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                                         => Left("Tile not found")
      case Some(tile) if !tile.unlocked                 => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty                  => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField               => Left("Build a wheat field first")
      case Some(tile) if game.wood < townHallBuildCost  => Left(s"Not enough wood (need $townHallBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.TownHall(None))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wood = game.wood - townHallBuildCost
        ))

  // Assign a politician from the roster to a town hall (allows swapping)
  def assignPolitician(game: TileKingdomGame, politicianId: String, townHallCoord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(townHallCoord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
        case TileType.TownHall(existingPolitician) =>
          game.politicianRoster.find(_.id == politicianId) match
            case None => Left("Politician not found in roster")
            case Some(newPolitician) =>
              val updatedTile = tile.copy(tileType = TileType.TownHall(Some(newPolitician)))
              // Remove new politician from roster, add existing one back if present
              val updatedRoster = game.politicianRoster.filterNot(_.id == politicianId) ++ existingPolitician.toList
              Right(game.copy(
                tiles = game.tiles.updated(townHallCoord, updatedTile),
                politicianRoster = updatedRoster
              ))
        case _ => Left("Tile is not a town hall")

  // Remove a politician from a town hall back to the roster
  def removePolitician(game: TileKingdomGame, townHallCoord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(townHallCoord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
        case TileType.TownHall(Some(politician)) =>
          val updatedTile = tile.copy(tileType = TileType.TownHall(None))
          Right(game.copy(
            tiles = game.tiles.updated(townHallCoord, updatedTile),
            politicianRoster = game.politicianRoster :+ politician
          ))
        case TileType.TownHall(None) => Left("Town Hall has no politician")
        case _ => Left("Tile is not a town hall")

  // Level up a wheat field
  def levelUpWheatField(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
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
  def levelUpFarm(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
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
  def levelUpWoodcutter(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
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

  // Level up a temple (costs wood)
  def levelUpTemple(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
          case TileType.Temple(level) =>
            val cost = templeLevelUpCost(level)
            if game.wood < cost then
              Left(s"Not enough wood (need $cost)")
            else
              val updatedTile = tile.copy(tileType = TileType.Temple(level + 1))
              Right(game.copy(
                tiles = game.tiles.updated(coord, updatedTile),
                wood = game.wood - cost
              ))
          case _ => Left("Tile is not a temple")

  // Boost a bureau's speed with faith
  def boostBureau(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) if !tile.isBureau => Left("Tile is not a bureau")
      case Some(_) if game.faith < FaithBoostCost => Left(s"Not enough faith (need $FaithBoostCost)")
      case Some(_) =>
        val currentBoosts = game.bureauBoosts.getOrElse(coord, 0)
        Right(game.copy(
          faith = game.faith - FaithBoostCost,
          bureauBoosts = game.bureauBoosts.updated(coord, currentBoosts + 1)
        ))

  // Get bureau speed multiplier (1.0 = normal, 11.0 = 1 boost, 21.0 = 2 boosts, etc.)
  def bureauSpeedMultiplier(game: TileKingdomGame, bureauCoord: Coord): Double =
    val boosts = game.bureauBoosts.getOrElse(bureauCoord, 0)
    1.0 + boosts * FaithBoostMultiplier

  // Bureau auto-upgrade: upgrade the tile with lowest upgrade cost within radius
  // Returns updated game and the coord that was upgraded (if any)
  // Compares costs numerically - all resources are treated as equivalent
  def bureauAutoUpgrade(
                         game: TileKingdomGame,
                         bureauCoord: Coord,
                         currentTimeMillis: Long
  ): Option[(TileKingdomGame, Coord)] =
    game.tiles.get(bureauCoord) match
      case Some(bureauTile) if bureauTile.isBureau =>
        // Find upgradeable tiles within radius with their costs
        val nearbyCoords = bureauCoord.neighborsWithinRadius(BureauRadius)
        val upgradeableTiles = nearbyCoords
          .flatMap(coord => game.tiles.get(coord).map(coord -> _))
          .filter((_, tile) => tile.isUpgradeable)
          .flatMap((coord, tile) => tile.upgradeCost.map(cost => (coord, tile, cost)))

        // Must have wood for the bureau fee regardless of what we upgrade
        if game.wood < BureauWoodCostPerUpgrade then return None

        // Filter to only tiles we can afford (including bureau wood fee for wood-cost upgrades)
        val affordableTiles = upgradeableTiles.filter: (_, _, cost) =>
          val extraWoodNeeded = if cost.resource == Resource.Wood then BureauWoodCostPerUpgrade else 0
          game.canAfford(cost.amount + extraWoodNeeded, cost.resource)

        // Select the tile with the lowest upgrade cost (comparing numerically)
        affordableTiles.minByOption(_._3.amount).flatMap: (targetCoord, targetTile, cost) =>
          // Perform the upgrade based on tile type
          val upgradedTileType = targetTile.tileType match
            case TileType.WheatField(lvl) => TileType.WheatField(lvl + 1)
            case TileType.Farm(lvl)       => TileType.Farm(lvl + 1)
            case TileType.Woodcutter(lvl) => TileType.Woodcutter(lvl + 1)
            case TileType.Temple(lvl)     => TileType.Temple(lvl + 1)
            case other                    => other

          val upgradedTile = targetTile.copy(tileType = upgradedTileType)

          // Deduct upgrade cost and bureau fee
          val afterUpgradeCost = game.deduct(cost)
          val afterBureauFee = afterUpgradeCost.copy(wood = afterUpgradeCost.wood - BureauWoodCostPerUpgrade)

          val newGame = afterBureauFee.copy(
            tiles = game.tiles.updated(targetCoord, upgradedTile)
          )
          Some((newGame, targetCoord))
      case _ => None

  // Destroy a building on a tile (returns it to empty state, no refund)
  def destroyBuilding(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                         => Left("Tile not found")
      case Some(tile) if !tile.unlocked => Left("Tile is locked")
      case Some(tile) if tile.isEmpty   => Left("Tile is already empty")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Empty)
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile)
        ))

  // Abdicate: reset tiles, gain gold based on income rate
  def abdicate(game: TileKingdomGame, currentTimeMillis: Long): Either[String, TileKingdomGame] =
    if !game.allTilesFilled then
      Left("Must fill all unlocked tiles with buildings before abdicating")
    else
      val goldReward = abdicationReward(game.totalIncomeRate)

      // Collect politicians from town halls before resetting
      val politiciansFromTownHalls = game.tiles.values.flatMap: tile =>
        tile.tileType match
          case TileType.TownHall(Some(politician)) => Some(politician)
          case _ => None
      .toList

      val resetTiles = game.tiles.map:
        case (coord, tile) if tile.unlocked =>
          coord -> tile.copy(tileType = TileType.Empty)
        case (coord, tile) =>
          coord -> tile

      Right(game.copy(
        tiles = resetTiles,
        wheat = 50.0, // Reset wheat, give starting amount
        wood = 0.0, // Reset wood
        faith = 0.0, // Reset faith
        gold = game.gold + goldReward,
        lastTickTime = currentTimeMillis,
        totalAbdications = game.totalAbdications + 1,
        bureauBoosts = Map.empty, // Reset bureau boosts since bureaus are destroyed
        politicianRoster = game.politicianRoster ++ politiciansFromTownHalls // Return politicians to roster
      ))

  // Get all coords that can be unlocked (coords adjacent to unlocked tiles that aren't already tiles)
  def unlockableCoords(game: TileKingdomGame): Set[Coord] =
    val unlockedCoords = game.unlockedTiles.map(_.coord).toSet
    val allAdjacentToUnlocked = unlockedCoords.flatMap(_.neighbors)
    allAdjacentToUnlocked.filterNot(game.tiles.contains)

  // Unlock a specific tile with gold (must be adjacent to an unlocked tile)
  def unlockTile(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
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

  // Dev tool: Unlock many tiles for free (creates continent-like shapes)
  def unlockManyTiles(game: TileKingdomGame, count: Int): TileKingdomGame =
    val random = new scala.util.Random(System.currentTimeMillis())

    // Pick 3-5 random growth directions (angles in radians)
    val numDirections = 3 + random.nextInt(3)
    val growthDirections = (0 until numDirections).map: _ =>
      random.nextDouble() * 2 * math.Pi
    .toList

    // Each direction has a random "strength"
    val directionStrengths = growthDirections.map(_ => 0.5 + random.nextDouble() * 0.5)

    // Find center of current territory
    val startCoords = game.tiles.keySet
    val startCenterRow = startCoords.map(_.row).sum.toDouble / startCoords.size
    val startCenterCol = startCoords.map(_.col).sum.toDouble / startCoords.size

    (1 to count).foldLeft(game): (currentGame, i) =>
      val available = unlockableCoords(currentGame)
      if available.isEmpty then currentGame
      else
        val currentCoords = currentGame.tiles.keySet

        // Score each candidate
        val scored = available.toList.map: coord =>
          val neighborCount = coord.neighbors.count(currentCoords.contains)

          // Calculate angle from start center to this coord
          val dx = coord.col - startCenterCol
          val dy = coord.row - startCenterRow
          val angle = math.atan2(dy, dx)

          // Score based on alignment with growth directions
          val directionScore = growthDirections.zip(directionStrengths).map: (dir, strength) =>
            val angleDiff = math.abs(((angle - dir) + math.Pi) % (2 * math.Pi) - math.Pi)
            val alignment = math.cos(angleDiff) // 1.0 when aligned, -1.0 when opposite
            if alignment > 0 then alignment * strength else 0.0
          .max

          // Add some noise for organic feel
          val noise = random.nextDouble() * 0.3

          // Only fill holes when really necessary (7-8 neighbors)
          val holeScore = neighborCount match
            case 8 => 3.0 // Must fill
            case 7 => 2.0 // Should fill
            case _ => 0.0 // Don't prioritize filling

          // Prefer tiles on the edge (1-3 neighbors) for exploration
          val edgeBonus = neighborCount match
            case 1 => 0.8
            case 2 => 1.0
            case 3 => 0.9
            case _ => 0.5

          val finalScore = directionScore * edgeBonus + noise + holeScore
          (coord, finalScore)

        // Pick the best candidate
        val best = scored.maxBy(_._2)._1

        val newTile = Tile(coord = best, tileType = TileType.Empty, unlocked = true)
        currentGame.copy(tiles = currentGame.tiles.updated(best, newTile))
