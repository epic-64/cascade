# Trader - Single Player Trading Game

## Overview

Trader is a single-player economic simulation game where players navigate a network of 9 cities, buying goods where supply is high (cheap) and selling where demand is high (expensive). The game emphasizes strategic route planning, inventory management, and timing market cycles around seasonal changes.

**Core Loop:**
1. View market prices in current city
2. Buy items where supply is high
3. Travel to a city with high demand (pay upfront travel costs)
4. Sell items for profit
5. Upgrade carriage capacity as wealth grows
6. Adapt to seasonal supply/demand shifts every 5 turns

## Game Mechanics

### Starting Conditions
- **Gold:** 100
- **Carriage Capacity:** 200kg
- **Starting City:** Riverdale (central hub)
- **Turn:** 1
- **Season:** Spring

### Cities (9 Total)

The map is arranged in a 3x3 grid with varying distances:

```
  Northport ------- Ironforge ------- Crystalpeak
      |                 |                  |
      |                 |                  |
  Wheatholm ------- Riverdale ------- Silkwood
      |                 |                  |
      |                 |                  |
  Saltmarsh ------- Vineyard -------- Timberfall
```

**City Specializations:**
| City | High Supply (Cheap) | High Demand (Expensive) |
|------|---------------------|-------------------------|
| Northport | Fish, Salt | Gems, Silk |
| Ironforge | Iron, Coal | Wheat, Wine |
| Crystalpeak | Gems | Iron, Wheat |
| Wheatholm | Wheat, Livestock | Gems, Silk |
| Riverdale | (Balanced) | (Balanced) |
| Silkwood | Silk, Spices | Iron, Coal |
| Saltmarsh | Salt, Fish | Lumber, Livestock |
| Vineyard | Wine, Wheat | Gems, Spices |
| Timberfall | Lumber, Livestock | Salt, Fish |

### Items (10 Tradeable Goods)

| Item | Base Price | Weight (kg) | Notes |
|------|------------|-------------|-------|
| Wheat | 5 | 20 | Staple food, high volume |
| Iron | 15 | 50 | Heavy, industrial |
| Coal | 8 | 40 | Fuel, pairs with Iron |
| Silk | 40 | 5 | Luxury, light |
| Gems | 80 | 2 | High value, very light |
| Wine | 20 | 15 | Moderate luxury |
| Salt | 6 | 25 | Preservative, heavy |
| Fish | 4 | 15 | Perishable feel, cheap |
| Lumber | 10 | 60 | Very heavy, bulky |
| Livestock | 25 | 100 | Most heavy, high base price |

### Pricing Formula

The market price for an item in a city is determined by supply, demand, and seasonal modifiers:

```
marketPrice = basePrice × supplyModifier × demandModifier × seasonModifier
```

**Important:** The buy and sell price are the **same** within a city. Profit comes from traveling to a different city with better prices, not from buying and selling in the same location.

**Supply Modifier:** (affects local availability)
- Abundant: 0.6 (40% discount - lots of supply)
- Normal: 1.0
- Scarce: 1.5 (50% markup - limited supply)

**Demand Modifier:** (affects local desire for goods)
- Low: 0.7 (30% discount - nobody wants it)
- Normal: 1.0
- High: 1.4 (40% markup - everyone wants it)

**Season Modifier:** (affects certain goods)
- Spring: Wheat +20%, Wine -10%
- Summer: Fish +30%, Salt -20%
- Autumn: Wine +30%, Wheat +10%
- Winter: Coal +40%, Livestock +20%

**Combined Example:**
- Silk base price: 40 gold
- In Wheatholm (abundant supply): 40 × 0.6 = 24 gold
- In Northport (high demand): 40 × 1.4 = 56 gold
- Potential profit: 32 gold per unit (minus travel costs)

### Travel System

**Distance Matrix (in "travel units"):**
Adjacent cities = 1 unit, diagonal = 1.5 units, two steps away = 2 units, etc.

```
Travel Cost = distanceUnits × 5 gold + (cargoWeight × 0.02 gold)
```

**Example Distances from Riverdale:**
| Destination | Distance | Base Cost | With 200kg cargo |
|-------------|----------|-----------|------------------|
| Wheatholm | 1 | 5g | 9g |
| Northport | 1.5 | 7.5g | 11.5g |
| Crystalpeak | 1.5 | 7.5g | 11.5g |
| Ironforge | 1 | 5g | 9g |
| Silkwood | 1 | 5g | 9g |
| Saltmarsh | 1.5 | 7.5g | 11.5g |
| Vineyard | 1 | 5g | 9g |
| Timberfall | 1.5 | 7.5g | 11.5g |

Travel costs must be paid upfront. If the player cannot afford travel, they must sell items first.

### Carriage Upgrades

| Level | Capacity | Upgrade Cost | Cumulative Cost |
|-------|----------|--------------|-----------------|
| 1 (Start) | 200kg | - | - |
| 2 | 250kg | 100g | 100g |
| 3 | 300kg | 200g | 300g |
| 4 | 350kg | 400g | 700g |
| 5 | 400kg | 800g | 1,500g |
| 6 | 450kg | 1,600g | 3,100g |
| 7 | 500kg | 3,200g | 6,300g |
| 8 (Max) | 550kg | 6,400g | 12,700g |

Formula: `upgradeCost = 100 × 2^(currentLevel - 1)`

### Seasons & Turns

- **Turn Duration:** Only **travel** advances the turn counter. Buying and selling are instant market actions.
- **Season Length:** 5 turns (i.e., 5 travels)
- **Season Order:** Spring → Summer → Autumn → Winter → (repeat)
- **Season Change Effects:**
  - All cities randomize supply/demand levels (within their specialization constraints)
  - Seasonal price modifiers apply
  - Player receives notification of new season
  - **Market knowledge is reset** (see Market Discovery below)

### Market Discovery

Players must **visit cities to discover their market conditions**. This adds an exploration element to the game.

- **Unknown Markets:** Cities not yet visited this season show "??" for their cheap/expensive items on the map
- **Visited Cities:** Once you travel to a city, its market info is revealed:
  - Up to 2 cheapest items (good for buying)
  - Up to 2 most expensive items (good for selling)
- **Season Reset:** When a new season begins, all market knowledge is reset except for your current city
- **Current City:** You always know the market conditions of the city you're currently in

**Map Display:**
- 📍 Current city (highlighted, market known)
- 👁 Visited cities (market info visible)
- Unvisited cities show "Market unknown" with "??" placeholders

### Win/Loss Conditions

This is a sandbox game with no explicit win condition. Potential goals:
- **Wealth Milestones:** Reach 1,000g, 5,000g, 10,000g
- **Upgrade Goal:** Max out carriage capacity
- **Trade Volume:** Complete X successful trades
- **Bankruptcy:** If gold reaches 0 and no sellable inventory, game over

## Data Models

### Core Types

```scala
// Items
enum Item:
  case Wheat, Iron, Coal, Silk, Gems, Wine, Salt, Fish, Lumber, Livestock

object Item:
  def basePrice(item: Item): Int = item match
    case Wheat => 5
    case Iron => 15
    case Coal => 8
    case Silk => 40
    case Gems => 80
    case Wine => 20
    case Salt => 6
    case Fish => 4
    case Lumber => 10
    case Livestock => 25

  def weight(item: Item): Int = item match
    case Wheat => 20
    case Iron => 50
    case Coal => 40
    case Silk => 5
    case Gems => 2
    case Wine => 15
    case Salt => 25
    case Fish => 15
    case Lumber => 60
    case Livestock => 100

// Supply/Demand levels
enum SupplyLevel:
  case Abundant, Normal, Scarce

enum DemandLevel:
  case Low, Normal, High

// Seasons
enum Season:
  case Spring, Summer, Autumn, Winter

// City
enum CityId:
  case Northport, Ironforge, Crystalpeak, Wheatholm, Riverdale, 
       Silkwood, Saltmarsh, Vineyard, Timberfall

case class CityMarket(
  supply: Map[Item, SupplyLevel],
  demand: Map[Item, DemandLevel]
) derives ReadWriter

case class City(
  id: CityId,
  name: String,
  market: CityMarket,
  position: (Int, Int) // Grid position for distance calculation
) derives ReadWriter

// Player state
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
      Some(copy(items = items.updated(item, current - qty)))
    else None

case class Player(
  gold: Int,
  inventory: Inventory,
  carriageLevel: Int,
  currentCity: CityId
) derives ReadWriter:
  def carriageCapacity: Int = 200 + (carriageLevel - 1) * 50
  def availableCapacity: Int = carriageCapacity - inventory.totalWeight

// Game state
case class TraderGame(
  player: Player,
  cities: Map[CityId, City],
  turn: Int,
  season: Season,
  visitedCities: Set[CityId], // Cities visited this season (market info revealed)
  log: List[String] // Recent actions/events
) derives ReadWriter:
  def isCityVisited(cityId: CityId): Boolean = 
    cityId == player.currentCity || visitedCities.contains(cityId)
```

### Pure State Transitions

```scala
object TraderLogic:
  def buyItem(game: TraderGame, item: Item, qty: Int): Either[String, TraderGame] = ???
  def sellItem(game: TraderGame, item: Item, qty: Int): Either[String, TraderGame] = ???
  def travel(game: TraderGame, destination: CityId): Either[String, TraderGame] = ???
  def upgradeCarriage(game: TraderGame): Either[String, TraderGame] = ???
  def calculatePrice(city: City, item: Item, season: Season): Int = ???
  def travelCost(from: City, to: City, cargoWeight: Int): Int = ???
  def advanceTurn(game: TraderGame): TraderGame = ???
  def changeSeason(game: TraderGame): TraderGame = ???
  def newGame(): TraderGame = ???
  
  // Market discovery helpers
  def getCheapestItems(city: City, season: Season, limit: Int = 2): List[(Item, Int)] = ???
  def getMostExpensiveItems(city: City, season: Season, limit: Int = 2): List[(Item, Int)] = ???
```

## UI Design

### Main Layout

```
┌─────────────────────────────────────────────────────────────────┐
│  TRADER                              Turn: 12  Season: Summer   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                       CITY MAP                           │   │
│  │                                                          │   │
│  │    [Northport]────[Ironforge]────[Crystalpeak]          │   │
│  │         │              │               │                 │   │
│  │    [Wheatholm]────[RIVERDALE]────[Silkwood]             │   │
│  │         │              │               │                 │   │
│  │    [Saltmarsh]────[Vineyard]─────[Timberfall]           │   │
│  │                                                          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────┐  ┌─────────────────────────────────┐  │
│  │  YOUR CARRIAGE      │  │  RIVERDALE MARKET               │  │
│  │  ───────────────────│  │  ───────────────────────────────│  │
│  │  Gold: 100          │  │  Item      Buy    Sell   Stock  │  │
│  │  Capacity: 200kg    │  │  Wheat      5      4       -    │  │
│  │  Used: 0kg          │  │  Iron      18     16       -    │  │
│  │  ───────────────────│  │  Silk      52     48       -    │  │
│  │  Inventory:         │  │  Gems      96     88       -    │  │
│  │  (empty)            │  │  Wine      22     20       -    │  │
│  │                     │  │  ...                            │  │
│  │  [Upgrade: 100g]    │  │                                 │  │
│  └─────────────────────┘  └─────────────────────────────────┘  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  TRAVEL TO                          Cost (with cargo)    │   │
│  │  [Wheatholm - 9g] [Ironforge - 9g] [Silkwood - 9g]      │   │
│  │  [Northport - 12g] [Vineyard - 9g] [Crystalpeak - 12g]  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  LOG                                                     │   │
│  │  > Bought 3 Wheat for 15g                               │   │
│  │  > Traveled to Riverdale (-9g)                          │   │
│  │  > Season changed to Summer! Markets have shifted.      │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Buy/Sell Interaction

When clicking an item row in the market:
- Show quantity selector (input or +/- buttons)
- Show total cost/profit
- Show weight impact
- Confirm button

### Color Scheme (using existing CSS variables)

- Background: `--color-background`
- Cards: `--color-card`
- Primary actions: `--color-primary`
- Gold/money: `--color-warning` (yellow/gold)
- Profitable trades: `--color-success` (green)
- Unprofitable/warnings: `--color-error` (red)
- Current city highlight: `--color-accent`

## File Structure

```
shared/src/main/scala/shared/Trader/
├── Trader.scala          # ✅ Core types and data models
└── TraderLogic.scala     # ✅ Pure game logic functions

js/src/main/scala/client/
└── TraderClient.scala    # ✅ Client app (UI, state, persistence)

jvm/src/main/resources/static/
├── trader.html           # ✅ HTML shell
└── trader.css            # ✅ Game-specific styles

shared/src/test/scala/shared/Trader/
└── TraderLogicSpec.scala # ✅ Unit tests for game logic (35 tests)
```

**Note:** The original design proposed separate files for UI, state, and storage,
but these were consolidated into `TraderClient.scala` for simplicity since the
game is single-player and doesn't require WebSocket complexity.

## Implementation Phases

### Phase 1: Core Models & Basic UI Shell ✅ COMPLETE
**Goal:** Establish data models and render static UI

**Tasks:**
1. ✅ Create `Trader.scala` with all enums and case classes
2. ✅ Create `TraderLogic.scala` with `newGame()` and `calculatePrice()`
3. ✅ Create `trader.html` with basic structure
4. ✅ Create `trader.css` extending base styles
5. ✅ Create `TraderClient.scala` with basic rendering
6. ✅ Add route in Cask server for `/trader`
7. ✅ Add to landing page

**Deliverables:**
- ✅ Player can see the map and their starting state
- ✅ Market prices displayed (read-only)
- ✅ No interactions yet

**Files Created:**
- `shared/src/main/scala/shared/Trader/Trader.scala`
- `shared/src/main/scala/shared/Trader/TraderLogic.scala`
- `shared/src/test/scala/shared/Trader/TraderLogicSpec.scala` (44 tests)
- `js/src/main/scala/client/TraderClient.scala`
- `jvm/src/main/resources/static/trader.html`
- `jvm/src/main/resources/static/trader.css`

### Phase 2: Single City Trading ✅ COMPLETE
**Goal:** Enable buying and selling in current city

**Tasks:**
1. ✅ Implement `buyItem()` and `sellItem()` in TraderLogic
2. ✅ Add buy/sell UI interactions
3. ✅ Implement inventory display
4. ✅ Add action log
5. ✅ Write unit tests for buy/sell logic

**Deliverables:**
- ✅ Player can buy items (gold decreases, inventory updates)
- ✅ Player can sell items (gold increases, inventory updates)
- ✅ Capacity limits enforced
- ✅ Actions logged

### Phase 3: Travel & Multi-City ✅ COMPLETE
**Goal:** Enable movement between cities

**Tasks:**
1. ✅ Implement distance calculation
2. ✅ Implement `travelCost()` function
3. ✅ Implement `travel()` function
4. ✅ Add travel UI with cost display
5. ✅ Different cities show different prices
6. ✅ Write unit tests for travel logic

**Deliverables:**
- ✅ Player can travel between cities
- ✅ Travel costs deducted
- ✅ Different cities have different market conditions
- ✅ Full trading loop possible

### Phase 4: Seasons & Upgrades ✅ COMPLETE
**Goal:** Complete game mechanics

**Tasks:**
1. ✅ Implement turn tracking
2. ✅ Implement `advanceTurn()` and `changeSeason()`
3. ✅ Implement seasonal supply/demand shuffling
4. ✅ Implement `upgradeCarriage()`
5. ✅ Add season change notifications (via log)
6. ✅ Add upgrade UI
7. ✅ Write unit tests

**Deliverables:**
- ✅ Turns advance only on travel (not buy/sell)
- ✅ Seasons change every 5 travels
- ✅ Markets shuffle on season change
- ✅ Player can upgrade carriage
- ✅ Full game loop complete

### Phase 5: Persistence & Polish ✅ MOSTLY COMPLETE
**Goal:** Save/load and UX improvements

**Tasks:**
1. ✅ Implement localStorage persistence (in TraderClient.scala)
2. ✅ Auto-save on each action
3. ✅ Load game on page refresh
4. ✅ Add "New Game" button with confirmation
5. ✅ Market discovery system (visited cities reveal market info)
6. ✅ Enhanced market UI showing base price, current price, and price factors
7. ✅ Color-coded prices (green = good deal, red = bad deal)
8. ✅ Price factor tags with +/- indicators (Supply, Demand, Season effects)
9. ⬚ Add milestone notifications (wealth goals)
10. ⬚ Add sound effects (optional)
11. ⬚ Mobile-responsive layout improvements

**Deliverables:**
- ✅ Game persists across browser sessions
- ✅ Clean restart option
- ✅ Map shows cheap/expensive items for visited cities
- ✅ Unknown cities show "??" until visited
- ✅ Market knowledge resets each season
- ⬚ Achievement feedback

### Phase 6: Server Integration (Future) ⬚ NOT STARTED
**Goal:** Enable login and cloud saves

**Tasks:**
1. ⬚ Add authentication (session/JWT)
2. ⬚ Create save/load API endpoints
3. ⬚ Database schema for game state
4. ⬚ Sync local and server state
5. ⬚ Leaderboard (optional)

**Deliverables:**
- ⬚ User accounts
- ⬚ Cloud save/load
- ⬚ Cross-device play

## Testing Strategy

### Unit Tests (TraderLogicSpec.scala) - 44 tests
- `buyItem` respects gold limits
- `buyItem` respects capacity limits
- `buyItem` does not advance turn
- `sellItem` requires items in inventory
- `travel` deducts correct cost
- `travel` prevents travel without funds
- `travel` advances turn
- `travel` marks destination city as visited
- `calculatePrice` applies supply, demand, and season modifiers
- `calculatePrice` returns same value for buy and sell
- `changeSeason` shuffles markets
- `changeSeason` resets visited cities
- `upgradeCarriage` costs escalate correctly
- `getCheapestItems` returns items below base price
- `getMostExpensiveItems` returns items above base price
- Starting city is visited by default

### Integration Tests
- Full trade cycle: buy → travel → sell → profit
- Season cycle: play through 20 turns
- Bankruptcy scenario: run out of gold

### Manual Testing Checklist
- [x] New game starts correctly
- [x] Buy/sell updates gold and inventory
- [x] Cannot exceed carriage capacity
- [x] Cannot spend more gold than available
- [x] Travel updates current city
- [x] Travel costs scale with distance and cargo
- [x] Seasons change every 5 travels
- [x] Markets shuffle on season change
- [x] Upgrades increase capacity
- [x] Game saves to localStorage
- [x] Game loads on refresh
- [x] Map shows market info for visited cities
- [x] Unvisited cities show "??" placeholders
- [x] Market knowledge resets on season change

## Open Questions

1. **Difficulty Scaling:** Should there be difficulty levels affecting starting gold, travel costs, or price volatility?

2. **Random Events:** Add random events like "bandits" (lose cargo) or "festival" (demand spike)?

3. **Multiple Save Slots:** Allow multiple game saves locally?

4. **Tutorial:** Add an interactive tutorial for first-time players?

5. **End Game:** Define explicit win conditions or keep as endless sandbox?

## Appendix: Distance Matrix

Full distance grid (in travel units):

| From\To | NP | IF | CP | WH | RD | SW | SM | VY | TF |
|---------|----|----|----|----|----|----|----|----|-----|
| Northport (NP) | 0 | 1 | 2 | 1 | 1.5 | 2 | 2 | 2.5 | 3 |
| Ironforge (IF) | 1 | 0 | 1 | 1.5 | 1 | 1.5 | 2 | 2 | 2.5 |
| Crystalpeak (CP) | 2 | 1 | 0 | 2 | 1.5 | 1 | 3 | 2.5 | 2 |
| Wheatholm (WH) | 1 | 1.5 | 2 | 0 | 1 | 2 | 1 | 1.5 | 2 |
| Riverdale (RD) | 1.5 | 1 | 1.5 | 1 | 0 | 1 | 1.5 | 1 | 1.5 |
| Silkwood (SW) | 2 | 1.5 | 1 | 2 | 1 | 0 | 2 | 1.5 | 1 |
| Saltmarsh (SM) | 2 | 2 | 3 | 1 | 1.5 | 2 | 0 | 1 | 2 |
| Vineyard (VY) | 2.5 | 2 | 2.5 | 1.5 | 1 | 1.5 | 1 | 0 | 1 |
| Timberfall (TF) | 3 | 2.5 | 2 | 2 | 1.5 | 1 | 2 | 1 | 0 |

