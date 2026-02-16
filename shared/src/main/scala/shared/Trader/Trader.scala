package shared.Trader

import upickle.default.ReadWriter

// Items - the 10 tradeable goods
enum Item derives ReadWriter:
  case Wheat, Iron, Coal, Silk, Gems, Wine, Salt, Fish, Lumber, Livestock

object Item:
  def basePrice(item: Item): Int = item match
    case Item.Wheat     => 5
    case Item.Iron      => 15
    case Item.Coal      => 8
    case Item.Silk      => 40
    case Item.Gems      => 80
    case Item.Wine      => 20
    case Item.Salt      => 6
    case Item.Fish      => 4
    case Item.Lumber    => 10
    case Item.Livestock => 25

  def weight(item: Item): Int = item match
    case Item.Wheat     => 20
    case Item.Iron      => 50
    case Item.Coal      => 40
    case Item.Silk      => 5
    case Item.Gems      => 2
    case Item.Wine      => 15
    case Item.Salt      => 25
    case Item.Fish      => 15
    case Item.Lumber    => 60
    case Item.Livestock => 100

  val all: List[Item] = Item.values.toList

// Supply/Demand levels
enum SupplyLevel derives ReadWriter:
  case Abundant, Normal, Scarce

object SupplyLevel:
  def modifier(level: SupplyLevel): Double = level match
    case SupplyLevel.Abundant => 0.6
    case SupplyLevel.Normal   => 1.0
    case SupplyLevel.Scarce   => 1.5

enum DemandLevel derives ReadWriter:
  case Low, Normal, High

object DemandLevel:
  def modifier(level: DemandLevel): Double = level match
    case DemandLevel.Low    => 0.7
    case DemandLevel.Normal => 1.0
    case DemandLevel.High   => 1.4

// Seasons
enum Season derives ReadWriter:
  case Spring, Summer, Autumn, Winter

object Season:
  def modifier(season: Season, item: Item): Double = (season, item) match
    case (Season.Spring, Item.Wheat)     => 1.2
    case (Season.Spring, Item.Wine)      => 0.9
    case (Season.Summer, Item.Fish)      => 1.3
    case (Season.Summer, Item.Salt)      => 0.8
    case (Season.Autumn, Item.Wine)      => 1.3
    case (Season.Autumn, Item.Wheat)     => 1.1
    case (Season.Winter, Item.Coal)      => 1.4
    case (Season.Winter, Item.Livestock) => 1.2
    case _                               => 1.0

  def next(season: Season): Season = season match
    case Season.Spring => Season.Summer
    case Season.Summer => Season.Autumn
    case Season.Autumn => Season.Winter
    case Season.Winter => Season.Spring

// City identifiers
enum CityId derives ReadWriter:
  case Northport, Ironforge, Crystalpeak, Wheatholm, Riverdale,
    Silkwood, Saltmarsh, Vineyard, Timberfall

// City market conditions
case class CityMarket(
    supply: Map[Item, SupplyLevel],
    demand: Map[Item, DemandLevel]
) derives ReadWriter

// City data
case class City(
    id: CityId,
    name: String,
    market: CityMarket,
    position: (Int, Int) // Grid position (row, col) for distance calculation
) derives ReadWriter

// Player inventory
case class Inventory(
    items: Map[Item, Int] // Item -> quantity
) derives ReadWriter:
  def totalWeight: Int =
    items.map((item, qty) => Item.weight(item) * qty).sum

  def add(item: Item, qty: Int): Inventory =
    copy(items = items.updated(item, items.getOrElse(item, 0) + qty))

  def remove(item: Item, qty: Int): Option[Inventory] =
    val current = items.getOrElse(item, 0)
    if current >= qty then
      val newQty = current - qty
      val newItems = if newQty == 0 then items - item else items.updated(item, newQty)
      Some(copy(items = newItems))
    else None

  def getQuantity(item: Item): Int = items.getOrElse(item, 0)

object Inventory:
  val empty: Inventory = Inventory(Map.empty)

// Player state
case class Player(
    gold: Int,
    inventory: Inventory,
    carriageLevel: Int,
    currentCity: CityId
) derives ReadWriter:
  def carriageCapacity: Int = 200 + (carriageLevel - 1) * 50
  def availableCapacity: Int = carriageCapacity - inventory.totalWeight
  def upgradesCost: Int = 100 * math.pow(2, carriageLevel - 1).toInt

// Full game state
case class TraderGame(
    player: Player,
    cities: Map[CityId, City],
    turn: Int,
    season: Season,
    visitedCities: Set[CityId], // Cities visited this season (market info revealed)
    log: List[String], // Recent actions/events (newest first)
    lastEncounter: Option[BanditEncounter] = None // Most recent encounter for UI display
) derives ReadWriter:
  def currentCity: City = cities(player.currentCity)
  // Current city is always considered visited (you can see the market you're in)
  def isCityVisited(cityId: CityId): Boolean =
    cityId == player.currentCity || visitedCities.contains(cityId)

object TraderGame:
  val MaxLogEntries: Int = 20

// Risk system types

/** Possible outcomes when traveling - includes safe arrival and bandit encounters */
enum EncounterOutcome derives ReadWriter:
  case Unscathed // Safe arrival, no bandits
  case Escaped // Got away clean
  case Toll(goldLost: Int) // Paid off the bandits
  case Robbery(itemsLost: Map[Item, Int]) // Lost some cargo
  case DevastatingLoss(itemsLost: Map[Item, Int], goldLost: Int) // Major loss

/** Risk calculation result for current cargo */
case class RiskAssessment(
    cargoValue: Int,
    cargoWeight: Int,
    valuePerKg: Double,
    riskScore: Double,
    encounterChance: Double // 0.0 to 1.0
) derives ReadWriter

object RiskAssessment:
  val safe: RiskAssessment = RiskAssessment(0, 0, 0.0, 0.0, 0.0)

/** Encounter event for logging/display */
case class BanditEncounter(
    outcome: EncounterOutcome,
    fromCity: CityId,
    toCity: CityId,
    turn: Int
) derives ReadWriter
