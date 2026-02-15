package shared.Trader

import scala.util.Random

object TraderLogic:
  // Distance matrix based on grid positions
  // Cities are on a 3x3 grid, distance calculated as Manhattan distance with diagonals = 1.5
  private def distance(from: City, to: City): Double =
    val (r1, c1) = from.position
    val (r2, c2) = to.position
    val rowDiff = math.abs(r1 - r2)
    val colDiff = math.abs(c1 - c2)
    // Each step is 1 unit, diagonals count as 1.5
    val diagonals = math.min(rowDiff, colDiff)
    val straights = math.max(rowDiff, colDiff) - diagonals
    straights + diagonals * 1.5

  def travelCost(from: City, to: City, cargoWeight: Int): Int =
    val dist = distance(from, to)
    val baseCost = dist * 5
    val cargoCost = cargoWeight * 0.02
    math.ceil(baseCost + cargoCost).toInt

  /** Calculate the market price for an item in a city.
    * This is the unified price - what you pay to buy AND what you receive when selling.
    * Price is affected by:
    * - Base item price
    * - City's supply level (abundant = cheaper, scarce = expensive)
    * - City's demand level (high = expensive, low = cheaper)
    * - Current season modifiers
    */
  def calculatePrice(city: City, item: Item, season: Season): Int =
    val base = Item.basePrice(item)
    val supplyMod = city.market.supply.get(item).map(SupplyLevel.modifier).getOrElse(1.0)
    val demandMod = city.market.demand.get(item).map(DemandLevel.modifier).getOrElse(1.0)
    val seasonMod = Season.modifier(season, item)
    math.ceil(base * supplyMod * demandMod * seasonMod).toInt

  // Buy and sell use the same price - profit comes from trading between cities
  def calculateBuyPrice(city: City, item: Item, season: Season): Int =
    calculatePrice(city, item, season)

  def calculateSellPrice(city: City, item: Item, season: Season): Int =
    calculatePrice(city, item, season)

  def buyItem(game: TraderGame, item: Item, qty: Int): Either[String, TraderGame] =
    if qty <= 0 then return Left("Quantity must be positive")

    val price = calculateBuyPrice(game.currentCity, item, game.season) * qty
    val weight = Item.weight(item) * qty

    if price > game.player.gold then
      Left(s"Not enough gold. Need $price, have ${game.player.gold}")
    else if weight > game.player.availableCapacity then
      Left(s"Not enough capacity. Need ${weight}kg, have ${game.player.availableCapacity}kg")
    else
      val newPlayer = game.player.copy(
        gold = game.player.gold - price,
        inventory = game.player.inventory.add(item, qty)
      )
      val logEntry = s"Bought $qty ${item.toString} for ${price}g"
      Right(game.copy(
        player = newPlayer,
        log = (logEntry :: game.log).take(TraderGame.MaxLogEntries)
      ))

  def sellItem(game: TraderGame, item: Item, qty: Int): Either[String, TraderGame] =
    if qty <= 0 then return Left("Quantity must be positive")

    val currentQty = game.player.inventory.getQuantity(item)
    if currentQty < qty then
      Left(s"Not enough ${item.toString}. Have $currentQty, trying to sell $qty")
    else
      val price = calculateSellPrice(game.currentCity, item, game.season) * qty
      game.player.inventory.remove(item, qty) match
        case Some(newInventory) =>
          val newPlayer = game.player.copy(
            gold = game.player.gold + price,
            inventory = newInventory
          )
          val logEntry = s"Sold $qty ${item.toString} for ${price}g"
          Right(game.copy(
            player = newPlayer,
            log = (logEntry :: game.log).take(TraderGame.MaxLogEntries)
          ))
        case None =>
          Left(s"Failed to remove items from inventory")

  def travel(game: TraderGame, destination: CityId): Either[String, TraderGame] =
    travel(game, destination, Random)

  /** Travel with explicit RNG for testability */
  def travel(game: TraderGame, destination: CityId, rng: Random): Either[String, TraderGame] =
    if destination == game.player.currentCity then
      Left("Already in this city")
    else
      val fromCity = game.currentCity
      val toCity = game.cities(destination)
      val cost = travelCost(fromCity, toCity, game.player.inventory.totalWeight)

      if cost > game.player.gold then
        Left(s"Not enough gold for travel. Need ${cost}g, have ${game.player.gold}g")
      else
        val newPlayer = game.player.copy(
          gold = game.player.gold - cost,
          currentCity = destination
        )
        val logEntry = s"Traveled to ${toCity.name} (-${cost}g)"
        val gameAfterTravel = game.copy(
          player = newPlayer,
          visitedCities = game.visitedCities + destination,
          log = (logEntry :: game.log).take(TraderGame.MaxLogEntries)
        )
        
        // Check for bandit encounter
        val risk = TraderRisk.assessRisk(game.player.inventory)
        val gameAfterEncounter = TraderRisk.rollEncounter(risk, rng) match
          case Some(outcome) =>
            TraderRisk.applyEncounter(gameAfterTravel, outcome, game.player.currentCity, destination, rng)
          case None =>
            gameAfterTravel
        
        Right(advanceTurn(gameAfterEncounter))

  def upgradeCarriage(game: TraderGame): Either[String, TraderGame] =
    if game.player.carriageLevel >= 8 then
      Left("Carriage is already at maximum level")
    else
      val cost = game.player.upgradesCost
      if cost > game.player.gold then
        Left(s"Not enough gold. Need ${cost}g, have ${game.player.gold}g")
      else
        val newPlayer = game.player.copy(
          gold = game.player.gold - cost,
          carriageLevel = game.player.carriageLevel + 1
        )
        val newCapacity = newPlayer.carriageCapacity
        val logEntry = s"Upgraded carriage to level ${newPlayer.carriageLevel} (${newCapacity}kg capacity)"
        Right(game.copy(
          player = newPlayer,
          log = (logEntry :: game.log).take(TraderGame.MaxLogEntries)
        ))

  private def advanceTurn(game: TraderGame): TraderGame =
    val newTurn = game.turn + 1
    // Season changes every 5 turns
    if newTurn % 5 == 1 && newTurn > 1 then
      changeSeason(game.copy(turn = newTurn))
    else
      game.copy(turn = newTurn)

  private def changeSeason(game: TraderGame): TraderGame =
    val newSeason = Season.next(game.season)
    val logEntry = s"Season changed to ${newSeason.toString}! Markets have shifted."
    // Shuffle market conditions for all cities
    val newCities = game.cities.map { (id, city) =>
      id -> city.copy(market = shuffleMarket(city, id))
    }
    game.copy(
      season = newSeason,
      cities = newCities,
      visitedCities = Set(game.player.currentCity), // Reset - only current city is known
      log = (logEntry :: game.log).take(TraderGame.MaxLogEntries)
    )

  private def shuffleMarket(city: City, cityId: CityId): CityMarket =
    val (highSupply, highDemand) = citySpecialization(cityId)

    val supply = Item.all.map { item =>
      val level =
        if highSupply.contains(item) then SupplyLevel.Abundant
        else randomSupplyLevel()
      item -> level
    }.toMap

    val demand = Item.all.map { item =>
      val level =
        if highDemand.contains(item) then DemandLevel.High
        else randomDemandLevel()
      item -> level
    }.toMap

    CityMarket(supply, demand)

  private def randomSupplyLevel(): SupplyLevel =
    val rand = Random.nextDouble()
    if rand < 0.33 then SupplyLevel.Abundant
    else if rand < 0.66 then SupplyLevel.Normal
    else SupplyLevel.Scarce

  private def randomDemandLevel(): DemandLevel =
    val rand = Random.nextDouble()
    if rand < 0.33 then DemandLevel.Low
    else if rand < 0.66 then DemandLevel.Normal
    else DemandLevel.High

  // City specializations from the design doc
  // Note: Spices mentioned in design doc but not in Item enum, so omitted
  private def citySpecialization(cityId: CityId): (Set[Item], Set[Item]) = cityId match
    case CityId.Northport   => (Set(Item.Fish, Item.Salt), Set(Item.Gems, Item.Silk))
    case CityId.Ironforge   => (Set(Item.Iron, Item.Coal), Set(Item.Wheat, Item.Wine))
    case CityId.Crystalpeak => (Set(Item.Gems), Set(Item.Iron, Item.Wheat))
    case CityId.Wheatholm   => (Set(Item.Wheat, Item.Livestock), Set(Item.Gems, Item.Silk))
    case CityId.Riverdale   => (Set.empty, Set.empty) // Balanced
    case CityId.Silkwood    => (Set(Item.Silk), Set(Item.Iron, Item.Coal))
    case CityId.Saltmarsh   => (Set(Item.Salt, Item.Fish), Set(Item.Lumber, Item.Livestock))
    case CityId.Vineyard    => (Set(Item.Wine, Item.Wheat), Set(Item.Gems))
    case CityId.Timberfall  => (Set(Item.Lumber, Item.Livestock), Set(Item.Salt, Item.Fish))

  def newGame(): TraderGame =
    val cities = Map(
      CityId.Northport   -> City(CityId.Northport, "Northport", CityMarket(Map.empty, Map.empty), (0, 0)),
      CityId.Ironforge   -> City(CityId.Ironforge, "Ironforge", CityMarket(Map.empty, Map.empty), (0, 1)),
      CityId.Crystalpeak -> City(CityId.Crystalpeak, "Crystalpeak", CityMarket(Map.empty, Map.empty), (0, 2)),
      CityId.Wheatholm   -> City(CityId.Wheatholm, "Wheatholm", CityMarket(Map.empty, Map.empty), (1, 0)),
      CityId.Riverdale   -> City(CityId.Riverdale, "Riverdale", CityMarket(Map.empty, Map.empty), (1, 1)),
      CityId.Silkwood    -> City(CityId.Silkwood, "Silkwood", CityMarket(Map.empty, Map.empty), (1, 2)),
      CityId.Saltmarsh   -> City(CityId.Saltmarsh, "Saltmarsh", CityMarket(Map.empty, Map.empty), (2, 0)),
      CityId.Vineyard    -> City(CityId.Vineyard, "Vineyard", CityMarket(Map.empty, Map.empty), (2, 1)),
      CityId.Timberfall  -> City(CityId.Timberfall, "Timberfall", CityMarket(Map.empty, Map.empty), (2, 2))
    )

    // Initialize markets based on specializations
    val citiesWithMarkets = cities.map { (id, city) =>
      id -> city.copy(market = shuffleMarket(city, id))
    }

    val player = Player(
      gold = 100,
      inventory = Inventory.empty,
      carriageLevel = 1,
      currentCity = CityId.Riverdale
    )

    TraderGame(
      player = player,
      cities = citiesWithMarkets,
      turn = 1,
      season = Season.Spring,
      visitedCities = Set(CityId.Riverdale), // Start with knowledge of starting city
      log = List("Welcome to Trader! Buy low, sell high, and build your fortune.")
    )

  // Calculate all travel options from current city
  def getTravelOptions(game: TraderGame): List[(CityId, Int)] =
    val currentCity = game.currentCity
    val cargoWeight = game.player.inventory.totalWeight
    CityId.values.toList
      .filter(_ != game.player.currentCity)
      .map { destId =>
        val destCity = game.cities(destId)
        destId -> travelCost(currentCity, destCity, cargoWeight)
      }
      .sortBy(_._2)

  /** Get travel options with risk assessment for UI display */
  def getTravelOptionsWithRisk(game: TraderGame): (List[(CityId, Int)], RiskAssessment) =
    val options = getTravelOptions(game)
    val risk = TraderRisk.assessRisk(game.player.inventory)
    (options, risk)

  /** Get the cheapest items in a city (good for buying).
    * Returns up to 2 items with prices significantly below base price.
    */
  def getCheapestItems(city: City, season: Season, limit: Int = 2): List[(Item, Int)] =
    Item.all
      .map(item => item -> calculatePrice(city, item, season))
      .filter((item, price) => price < Item.basePrice(item)) // Only items below base price
      .sortBy((item, price) => price.toDouble / Item.basePrice(item)) // Sort by discount ratio
      .take(limit)

  /** Get the most expensive items in a city (good for selling).
    * Returns up to 2 items with prices significantly above base price.
    */
  def getMostExpensiveItems(city: City, season: Season, limit: Int = 2): List[(Item, Int)] =
    Item.all
      .map(item => item -> calculatePrice(city, item, season))
      .filter((item, price) => price > Item.basePrice(item)) // Only items above base price
      .sortBy((item, price) => -price.toDouble / Item.basePrice(item)) // Sort by markup ratio (descending)
      .take(limit)

