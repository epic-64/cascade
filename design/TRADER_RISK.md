# Trader Risk System

## Problem Statement

The current game has a severe balancing issue: **high value-per-kg items (especially Gems) dominate all other strategies.**

| Item | Base Price | Weight | Value/kg |
|------|------------|--------|----------|
| Gems | 80g | 2kg | **40 g/kg** |
| Silk | 40g | 5kg | 8 g/kg |
| Livestock | 25g | 100kg | 0.25 g/kg |
| Wheat | 5g | 20kg | 0.25 g/kg |

With Gems at 40 g/kg vs Wheat at 0.25 g/kg, there's a **160x difference** in value density. This means:
- Players can fill their cart with Gems and make massive profits
- Cart upgrades become irrelevant (you never need more capacity)
- Bulk goods like Wheat, Lumber, and Livestock are never worth trading
- The game reduces to a single optimal strategy: "buy Gems, sell Gems"

## Solution: The Risk System

Introduce a **risk of cargo loss** during travel that scales with the **total value per kg** of your cargo. This creates a risk/reward tradeoff where:
- **High-value cargo** = High risk of bandits/theft
- **Bulk goods** = Safe but lower margins
- **Mixed cargo** = Balanced approach

### Thematic Justification

> *"The roads between cities are dangerous. Bandits target merchants carrying precious cargo, while carts full of wheat pass unmolested. A wise trader balances profit against peril."*

### Core Mechanic

When traveling between cities, there's a chance of a **bandit encounter** that can result in cargo loss. The probability and severity scale with your cargo's value density.

```
cargoValuePerKg = totalCargoValue / totalCargoWeight
riskScore = (cargoValuePerKg / 10) ^ 1.5   // Exponential scaling
encounterChance = min(riskScore * 0.1, 0.8) // Cap at 80%
```

### Risk Tiers

| Cargo Value/kg | Risk Score | Encounter Chance | Description |
|----------------|------------|------------------|-------------|
| 0-5 g/kg | 0-1.1 | 0-11% | Safe - bulk goods |
| 5-10 g/kg | 1.1-3.2 | 11-32% | Low risk |
| 10-20 g/kg | 3.2-8.9 | 32-80%* | Moderate risk |
| 20+ g/kg | 8.9+ | 80%* | High risk (capped) |

*Capped at 80% - there's always a chance to get through

### Encounter Resolution

When an encounter triggers, the outcome is determined by a second roll:

| Roll (d100) | Outcome | Effect |
|-------------|---------|--------|
| 1-20 | **Escape** | No loss, but nerve-wracking |
| 21-60 | **Toll** | Lose 10-20% of cargo value (gold or goods) |
| 61-85 | **Robbery** | Lose 30-50% of highest-value items |
| 86-100 | **Devastating Loss** | Lose 50-80% of all cargo |

### Example Scenarios

**Scenario A: Gems Only**
- Carrying 100kg of Gems (50 units at 2kg each)
- Total value: 50 × 80g = 4,000g
- Value/kg: 40 g/kg
- Risk score: (40/10)^1.5 = 8
- Encounter chance: **80%** (capped)

**Scenario B: Wheat Only**
- Carrying 200kg of Wheat (10 units at 20kg each)
- Total value: 10 × 5g = 50g
- Value/kg: 0.25 g/kg
- Risk score: (0.25/10)^1.5 = 0.004
- Encounter chance: **0.04%** (essentially safe)

**Scenario C: Mixed Cargo**
- Carrying 10 Gems (20kg, 800g) + 180kg Wheat (9 units, 45g)
- Total: 200kg, 845g value
- Value/kg: 4.2 g/kg
- Risk score: (4.2/10)^1.5 = 0.27
- Encounter chance: **2.7%** (very manageable)

### Strategic Implications

1. **Gems are still profitable** but now come with significant risk
2. **Bulk goods become viable** as safe, steady income
3. **Mixed strategies emerge** - dilute high-value goods with bulk
4. **Cart upgrades matter** - more capacity means more bulk "padding"
5. **Route planning** becomes crucial - shorter routes = fewer risk checks

## Additional Features

### Risk Mitigation

Players can reduce risk through various means:

1. **Guards (Future)** - Hire guards that reduce encounter chance
2. **Insurance (Future)** - Pay premium to recover losses
3. **Safe Routes** - Some city pairs might have lower base risk
4. **Reputation (Future)** - High reputation = bandits avoid you

### UI Elements

- **Risk Indicator** when selecting travel destination
  - Shows current cargo value/kg and encounter chance
  - Color-coded: Green (<20%), Yellow (20-50%), Red (>50%)
- **Encounter Dialog** when bandits strike
  - Dramatic reveal of outcome
  - Clear display of losses
- **Travel Log** records all encounters

### Risk Preview

Before traveling, show:
```
┌─────────────────────────────────────────┐
│  Travel to Wheatholm                    │
│  ───────────────────────────────────────│
│  Distance: 1 unit                       │
│  Travel Cost: 9g                        │
│  ───────────────────────────────────────│
│  ⚠️ CARGO RISK                          │
│  Cargo Value: 800g                      │
│  Cargo Weight: 20kg                     │
│  Value/kg: 40 g/kg                      │
│  Encounter Chance: 80% (HIGH)           │
│  ───────────────────────────────────────│
│  [Travel Anyway]  [Cancel]              │
└─────────────────────────────────────────┘
```

## Data Models

### New Types

```scala
// Encounter outcomes
enum EncounterOutcome derives ReadWriter:
  case Escaped                           // Got away clean
  case Toll(goldLost: Int)              // Paid off the bandits
  case Robbery(itemsLost: Map[Item, Int]) // Lost some cargo
  case DevastatingLoss(itemsLost: Map[Item, Int], goldLost: Int) // Major loss

// Risk calculation result
case class RiskAssessment(
  cargoValue: Int,
  cargoWeight: Int,
  valuePerKg: Double,
  riskScore: Double,
  encounterChance: Double // 0.0 to 1.0
) derives ReadWriter

// Encounter event for logging/display
case class BanditEncounter(
  outcome: EncounterOutcome,
  route: (CityId, CityId),
  turn: Int
) derives ReadWriter
```

### Updated Game State

```scala
case class TraderGame(
  player: Player,
  cities: Map[CityId, City],
  turn: Int,
  season: Season,
  visitedCities: Set[CityId],
  log: List[String],
  // New fields
  lastEncounter: Option[BanditEncounter], // For UI display
  totalLossesToBandits: Int              // Lifetime stat
) derives ReadWriter
```

### Logic Functions

```scala
object TraderRisk:
  /** Calculate the risk assessment for current cargo */
  def assessRisk(game: TraderGame): RiskAssessment = 
    val inventory = game.player.inventory
    val totalValue = inventory.items.map { (item, qty) =>
      TraderLogic.calculatePrice(game.currentCity, item, game.season) * qty
    }.sum
    val totalWeight = inventory.totalWeight
    
    if totalWeight == 0 then
      RiskAssessment(0, 0, 0.0, 0.0, 0.0)
    else
      val valuePerKg = totalValue.toDouble / totalWeight
      val riskScore = math.pow(valuePerKg / 10, 1.5)
      val encounterChance = math.min(riskScore * 0.1, 0.8)
      RiskAssessment(totalValue, totalWeight, valuePerKg, riskScore, encounterChance)
  
  /** Roll for a bandit encounter during travel */
  def rollEncounter(risk: RiskAssessment, rng: Random): Option[EncounterOutcome] =
    if rng.nextDouble() > risk.encounterChance then None
    else Some(resolveEncounter(risk, rng))
  
  /** Determine the outcome of a bandit encounter */
  private def resolveEncounter(risk: RiskAssessment, rng: Random): EncounterOutcome =
    val roll = rng.nextInt(100)
    if roll < 20 then EncounterOutcome.Escaped
    else if roll < 60 then
      val lossPercent = 0.10 + rng.nextDouble() * 0.10 // 10-20%
      EncounterOutcome.Toll((risk.cargoValue * lossPercent).toInt)
    else if roll < 85 then
      // Robbery: lose 30-50% of highest-value items
      ???
    else
      // Devastating loss: lose 50-80% of all cargo
      ???
  
  /** Apply encounter outcome to game state */
  def applyEncounter(game: TraderGame, encounter: BanditEncounter): TraderGame = ???
```

## Balance Tuning

The constants in the risk formula can be adjusted:

| Parameter | Current | Effect of Increase |
|-----------|---------|-------------------|
| Base divisor (10) | 10 | Lower encounter rates overall |
| Exponent (1.5) | 1.5 | Steeper penalty for high-value cargo |
| Encounter multiplier (0.1) | 0.1 | Higher base encounter rates |
| Encounter cap (0.8) | 0.8 | Higher maximum risk |

### Recommended Starting Values

For initial playtesting, use conservative values:
- Divisor: 10
- Exponent: 1.5  
- Multiplier: 0.1
- Cap: 0.8

These can be exposed as game settings for difficulty levels:

| Difficulty | Multiplier | Cap |
|------------|------------|-----|
| Easy | 0.05 | 0.5 |
| Normal | 0.1 | 0.8 |
| Hard | 0.15 | 0.9 |

## Implementation Phases

### Phase 1: Risk Calculation & Display ✅
**Goal:** Show risk information without affecting gameplay

**Tasks:**
1. ✅ Add `RiskAssessment` and `EncounterOutcome` types to `Trader.scala`
2. ✅ Implement `assessRisk()` in new `TraderRisk.scala` (or in TraderLogic)
3. ✅ Add risk indicator to travel UI showing encounter chance
4. ✅ Color-code risk levels (green/yellow/red)
5. ✅ Write unit tests for risk calculation formula

**Deliverables:**
- Players see risk percentage when considering travel
- No actual risk events yet (informational only)
- Tests verify formula produces expected values

### Phase 2: Basic Encounters ✅
**Goal:** Implement the core risk/reward mechanic

**Tasks:**
1. ✅ Implement `rollEncounter()` function
2. ✅ Implement `resolveEncounter()` with all outcome types
3. ✅ Implement `applyEncounter()` to modify game state
4. ✅ Integrate encounter check into `travel()` function
5. ✅ Add encounter results to game log
6. ⬚ Update game state with `lastEncounter` and `totalLossesToBandits` (deferred)
7. ✅ Write unit tests for encounter resolution

**Deliverables:**
- Bandits can strike during travel
- Cargo/gold can be lost
- Losses recorded in log and stats

### Phase 3: Encounter UI ✅
**Goal:** Create engaging encounter experience

**Tasks:**
1. ✅ Create encounter dialog/modal showing outcome
2. ✅ Add dramatic reveal animation (optional)
3. ✅ Show itemized losses clearly
4. ✅ Add "Continue" button to dismiss
5. ✅ Update travel confirmation to show risk warning

**Deliverables:**
- Encounters feel impactful and dramatic
- Players clearly understand what they lost
- Risk is prominently displayed before travel

### Phase 3.5: Travel UI Overhaul ✅
**Goal:** Improve travel UX with city selection modal

**Tasks:**
1. ✅ Move risk indicator near the cart/carriage section
2. ✅ Remove the separate "Travel To" section at the bottom
3. ✅ Create city click modal with:
   - Travel cost
   - Risk factor (with current cargo)
   - Cheap items to buy there
   - Expensive items to sell there
   - Travel / Cancel buttons
4. ✅ Add travel animation between cities
5. ✅ Always show outcome feedback:
   - "Unscathed" for safe arrival
   - Existing encounter modals for bandit events

**Deliverables:**
- Cleaner UI with travel integrated into map
- Better information at decision point
- More engaging travel experience with animation

### Phase 4: Balance & Polish ⬚
**Goal:** Fine-tune the system for fun gameplay

**Tasks:**
1. ⬚ Playtest and adjust formula constants
2. ⬚ Add difficulty settings (optional)
3. ⬚ Track lifetime statistics (encounters, losses, escapes)
4. ⬚ Add achievements related to risk (e.g., "Escaped 10 encounters")
5. ⬚ Consider adding "safe route" mechanic between certain cities

**Deliverables:**
- Balanced gameplay where multiple strategies are viable
- Statistics give players feedback on their risk-taking
- Optional difficulty customization

### Phase 5: Advanced Features (Future) ⬚
**Goal:** Expand the risk system with more depth

**Tasks:**
1. ⬚ Guard hiring system (spend gold to reduce risk)
2. ⬚ Insurance system (pay premium, recover % of losses)
3. ⬚ Reputation system (successful trades reduce bandit attention)
4. ⬚ Special events (bandit king, safe passage tokens)
5. ⬚ Route danger levels (some paths are safer)

**Deliverables:**
- Multiple ways to manage risk
- Deeper strategic choices
- More replay variety

## Testing Strategy

### Unit Tests
- `assessRisk` returns 0 encounter chance for empty cargo
- `assessRisk` returns 0 encounter chance for low value-per-kg cargo
- `assessRisk` caps at 80% for extremely high value cargo
- `assessRisk` calculates correct risk for mixed cargo
- `resolveEncounter` distributes outcomes according to probability
- `applyEncounter` correctly removes items from inventory
- `applyEncounter` correctly deducts gold for toll
- Travel with high-risk cargo can trigger encounters
- Travel with low-risk cargo rarely triggers encounters

### Integration Tests
- Full trade cycle with encounter: buy gems → travel → encounter → reduced profit
- Verify bulk goods remain profitable despite lower margins
- Verify mixed cargo strategy reduces risk appropriately

### Balance Tests (Manual)
- Play 10 games using only Gems strategy - record win rate
- Play 10 games using only bulk goods - record win rate
- Play 10 games using mixed strategy - record win rate
- All strategies should be viable with different risk/reward profiles

## Open Questions

1. **Seeded RNG:** Should encounters use a seeded RNG for reproducibility/testing?

2. **Empty Cargo:** Should traveling with no cargo have any risk? (Currently 0%)

3. **Gold Counting:** Should carried gold count toward cargo value for risk calculation?

4. **City Safety:** Should some cities/routes be inherently safer?

5. **Encounter Frequency:** Should there be a cooldown after an encounter?

6. **Player Agency:** Should players have any way to fight back or negotiate?

## Appendix: Value/kg Reference

Current items sorted by value density:

| Item | Base Price | Weight | Value/kg | Risk at 200kg |
|------|------------|--------|----------|---------------|
| Gems | 80g | 2kg | 40.0 | 80% |
| Silk | 40g | 5kg | 8.0 | 23% |
| Wine | 20g | 15kg | 1.33 | 0.5% |
| Iron | 15g | 50kg | 0.3 | 0.02% |
| Lumber | 10g | 60kg | 0.17 | 0.01% |
| Coal | 8g | 40kg | 0.2 | 0.01% |
| Salt | 6g | 25kg | 0.24 | 0.01% |
| Wheat | 5g | 20kg | 0.25 | 0.01% |
| Fish | 4g | 15kg | 0.27 | 0.02% |
| Livestock | 25g | 100kg | 0.25 | 0.01% |

This shows that after the risk system:
- **Gems** go from "always optimal" to "high risk, high reward"
- **Silk** becomes a moderate risk option
- **All bulk goods** remain essentially risk-free

