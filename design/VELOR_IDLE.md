# Velor Idle - Design Document

## Overview

Velor Idle is a minimalistic idle RPG inspired by Melvor Idle (itself a RuneScape-like experience). The core gameplay loop involves switching between various skills/jobs, gathering resources, and processing them into higher-value goods. Unlike most idle games, Velor features **linear progression** rather than exponential growth.

**Core Philosophy:**
- Simple, satisfying progression
- No exponential scaling - every level feels meaningful
- Mobile-first design that also works well on desktop
- Minimalistic UI with clear feedback

---

## Game Mechanics

### Skills System (10 Skills)

Each skill allows the player to perform a repeatable action that generates resources and XP.

| Skill | Type | Primary Output | Secondary Output |
|-------|------|----------------|------------------|
| **Woodcutting** | Gathering | Logs (various) | Bird nests (rare) |
| **Mining** | Gathering | Ores (various) | Gems (rare) |
| **Fishing** | Gathering | Fish (various) | Junk/treasure (rare) |
| **Herbalism** | Gathering | Herbs (various) | Seeds (rare) |
| **Cooking** | Processing | Cooked food | Burnt food (fail) |
| **Smithing** | Processing | Bars, equipment | - |
| **Alchemy** | Processing | Potions | - |
| **Summoning** | Processing | Rune Tablets | Shards (rare) |
| **Thieving** | Active | Gold, items | Stun on fail |
| **Astrology** | Passive | Stardust, buffs | - |

### Gathering Skills

#### Woodcutting
| Tree | Level Required | XP | Time (sec) | Output |
|------|----------------|-----|------------|--------|
| Normal Tree | 1 | 10 | 3.0 | Normal Logs |
| Oak Tree | 10 | 25 | 4.0 | Oak Logs |
| Willow Tree | 25 | 45 | 5.0 | Willow Logs |
| Maple Tree | 40 | 80 | 6.0 | Maple Logs |
| Yew Tree | 55 | 140 | 7.0 | Yew Logs |
| Magic Tree | 70 | 250 | 8.0 | Magic Logs |

#### Mining
| Rock | Level Required | XP | Time (sec) | Output |
|------|----------------|-----|------------|--------|
| Copper Rock | 1 | 12 | 3.5 | Copper Ore |
| Tin Rock | 1 | 12 | 3.5 | Tin Ore |
| Iron Rock | 15 | 30 | 4.5 | Iron Ore |
| Coal Rock | 30 | 50 | 5.5 | Coal |
| Gold Rock | 45 | 90 | 6.5 | Gold Ore |
| Mithril Rock | 60 | 160 | 7.5 | Mithril Ore |

#### Fishing
| Fish | Level Required | XP | Time (sec) | Output |
|------|----------------|-----|------------|--------|
| Shrimp | 1 | 8 | 2.5 | Raw Shrimp |
| Sardine | 10 | 18 | 3.0 | Raw Sardine |
| Trout | 20 | 40 | 4.0 | Raw Trout |
| Salmon | 35 | 70 | 5.0 | Raw Salmon |
| Lobster | 50 | 110 | 6.0 | Raw Lobster |
| Swordfish | 65 | 180 | 7.0 | Raw Swordfish |

#### Herbalism
| Herb | Level Required | XP | Time (sec) | Output |
|------|----------------|-----|------------|--------|
| Guam | 1 | 9 | 3.0 | Guam Leaf |
| Marrentill | 10 | 20 | 3.5 | Marrentill |
| Tarromin | 20 | 35 | 4.0 | Tarromin |
| Harralander | 35 | 60 | 4.5 | Harralander |
| Ranarr | 50 | 100 | 5.5 | Ranarr Weed |
| Irit | 60 | 150 | 6.0 | Irit Leaf |
| Kwuarm | 70 | 200 | 6.5 | Kwuarm |
| Cadantine | 80 | 280 | 7.0 | Cadantine |

### Processing Skills

All processing skills share two bonus mechanics:

**Double Chance** - Chance to produce 2x output from a single action
- Base: 0%
- Increases with skill level: +0.5% per level (up to ~50% at level 99)
- Can be boosted by tablets, potions, and skill perks

**Recycle Chance** - Chance to keep input materials after crafting
- Base: 0%
- Increases with skill level: +0.3% per level (up to ~30% at level 99)
- Can be boosted by tablets, potions, and skill perks

#### Cooking
Takes raw fish → cooked fish. Burn chance decreases with level.
- Burn chance: `max(5%, 50% - (level - requiredLevel) * 2%)`
- Cooked food heals HP in combat (future feature)

#### Smithing
Combines ores into bars, bars into equipment.
| Recipe | Level | Inputs | Output |
|--------|-------|--------|--------|
| Bronze Bar | 1 | 1 Copper + 1 Tin | Bronze Bar |
| Iron Bar | 15 | 1 Iron Ore | Iron Bar |
| Steel Bar | 30 | 1 Iron Ore + 2 Coal | Steel Bar |
| Gold Bar | 45 | 1 Gold Ore | Gold Bar |
| Mithril Bar | 60 | 1 Mithril Ore + 4 Coal | Mithril Bar |

#### Alchemy
Combines herbs + secondary ingredients into potions.
- Herbs obtained through Herbalism gathering skill
- Secondaries from various skills

#### Summoning
Create rune tablets by consuming large quantities of gathered/crafted resources. Tablets are consumable items that provide passive bonuses while equipped.

**Core Mechanics:**
- Each tablet requires a specific combination of resources
- Tablets are consumed slowly while actively skilling
- Two tablet slots available - equip different tablets for synergy bonuses
- Higher-tier tablets require more resources but provide stronger effects

**Tablet Types:**

| Tablet | Level | Recipe | Effect | Consumption |
|--------|-------|--------|--------|-------------|
| Gatherer Tablet | 1 | 100 Normal Logs + 50 Copper Ore | +5% gathering yield | 1 per 10 actions |
| Miner Tablet | 10 | 200 Iron Ore + 100 Coal | +8% mining speed | 1 per 10 actions |
| Fisher Tablet | 15 | 150 Raw Trout + 50 Raw Salmon | +8% fishing speed | 1 per 10 actions |
| Lumberjack Tablet | 20 | 200 Oak Logs + 100 Willow Logs | +8% woodcutting speed | 1 per 10 actions |
| Artisan Tablet | 25 | 50 Bronze Bars + 30 Iron Bars | +10% crafting success | 1 per 8 actions |
| Herbalist Tablet | 30 | 100 Guam + 50 Tarromin | +10% herb yield | 1 per 10 actions |
| Alchemist Tablet | 40 | 5 Potions (any) + 100 Herbs | +15% potion potency | 1 per 8 actions |
| Thief Tablet | 50 | 500 Gold + 50 Steel Bars | +10% thieving success | 1 per 5 actions |
| Stargazer Tablet | 60 | 200 Stardust + 100 Magic Logs | +20% stardust gain | 1 per 10 actions |
| Master Tablet | 75 | 10 of each lower tablet | +5% all skills | 1 per 5 actions |

**Synergy System:**

When two different tablets are equipped simultaneously, they can trigger a synergy effect that combines their bonuses or creates entirely new effects.

| Tablet 1 | Tablet 2 | Synergy Effect |
|----------|----------|----------------|
| Gatherer | Miner | "Earth Affinity" - 10% chance for double ore |
| Gatherer | Fisher | "Nature's Bounty" - 10% chance for double fish |
| Gatherer | Lumberjack | "Forest Spirit" - 10% chance for double logs |
| Miner | Artisan | "Metalworker" - +15% recycle chance when smithing |
| Fisher | Artisan | "Sea Chef" - Never burn fish when cooking |
| Herbalist | Alchemist | "Potion Master" - +20% double chance for potions |
| Artisan | Alchemist | "Efficient Brewer" - +15% recycle chance for alchemy |
| Thief | Stargazer | "Shadow Walker" - No stun on thieving failure |
| Lumberjack | Herbalist | "Grove Keeper" - Find herbs while woodcutting |
| Any | Master | "Mastery Boost" - Double the effect of the other tablet |

**Tablet Slots:**
- Slot 1 unlocked at Summoning level 1
- Slot 2 unlocked at Summoning level 25
- Synergies only activate when both slots are filled with compatible tablets

### Special Skills

#### Thieving
Pick targets to steal from. Each attempt:
- Success: Gain gold + possible item
- Failure: Stunned for X seconds (can't act)
- Stun duration decreases with level

| Target | Level | Success Rate | Loot | Stun (sec) |
|--------|-------|--------------|------|------------|
| Man | 1 | 80% | 3-10 gold | 5 |
| Farmer | 15 | 75% | 10-25 gold, seeds | 6 |
| Guard | 30 | 65% | 25-50 gold, keys | 8 |
| Knight | 50 | 55% | 50-100 gold | 10 |
| Noble | 70 | 45% | 100-250 gold, gems | 12 |

#### Astrology
Study constellations to gain permanent skill bonuses.
- Passive stardust accumulation
- Spend stardust to unlock constellation bonuses
- Bonuses: +2% XP, -3% action time, +5% resource yield

---

## Progression Systems

### XP and Leveling

**Linear XP Curve:**
- Level 1→2: 100 XP
- Level 2→3: 200 XP
- Level N→N+1: N × 100 XP
- Max Level: 99

**Total XP to 99:** `sum(1..98) * 100 = 485,100 XP`

This linear curve ensures each level takes proportionally longer, but growth never becomes exponential.

### Skill Perks (Milestones)

Every 10 levels, unlock a skill-specific perk:

**Gathering Skills:**

| Level | Perk Type | Effect |
|-------|-----------|--------|
| 10 | Efficiency I | -5% action time |
| 20 | Yield I | +10% resource chance |
| 30 | Mastery I | +5% double resource chance |
| 40 | Efficiency II | -10% action time (total) |
| 50 | Yield II | +20% resource chance (total) |
| 60 | Mastery II | +10% double resource chance (total) |
| 70 | Efficiency III | -15% action time (total) |
| 80 | Yield III | +30% resource chance (total) |
| 90 | Mastery III | +15% double resource chance (total) |

**Processing Skills:**

| Level | Perk Type | Effect |
|-------|-----------|--------|
| 10 | Efficiency I | -5% action time |
| 20 | Double I | +5% double output chance |
| 30 | Recycle I | +5% recycle chance |
| 40 | Efficiency II | -10% action time (total) |
| 50 | Double II | +10% double output chance (total) |
| 60 | Recycle II | +10% recycle chance (total) |
| 70 | Efficiency III | -15% action time (total) |
| 80 | Double III | +15% double output chance (total) |
| 90 | Recycle III | +15% recycle chance (total) |

### Skill Mastery (Late Game)

After reaching level 99, continue earning XP toward "mastery" levels:
- Mastery levels provide small additional bonuses
- Mastery XP curve: Level × 500 XP
- Max Mastery: 99

---

## Inventory System

### Slot-Based Inventory

- **Starting Slots:** 12
- **Max Slots:** 100+
- **Stack Size:** Infinite per slot
- Each unique item takes one slot

### Slot Upgrades

| Slots | Cost (Gold) | Cumulative Cost |
|-------|-------------|-----------------|
| 12→16 | 500 | 500 |
| 16→20 | 1,500 | 2,000 |
| 20→24 | 4,000 | 6,000 |
| 24→28 | 8,000 | 14,000 |
| 28→32 | 15,000 | 29,000 |
| 32→40 | 30,000 | 59,000 |
| 40→50 | 60,000 | 119,000 |
| 50→60 | 100,000 | 219,000 |

Buying more inventory slots is one of the primary gold sinks and motivations.

### Item Shop

Basic shop to sell gathered/crafted items for gold:
- Raw materials: Base value
- Processed goods: ~2-3x raw value
- Equipment: Higher value based on tier

---

## Economy

### Gold Sources
1. Selling items to shop
2. Thieving
3. Rare drops

### Gold Sinks
1. Inventory slot upgrades (primary)
2. Equipment upgrades
3. Potion ingredients
4. Bank storage (future)

### Item Values

| Item | Sell Value |
|------|------------|
| Normal Logs | 2 |
| Oak Logs | 5 |
| Copper Ore | 3 |
| Iron Ore | 8 |
| Raw Shrimp | 1 |
| Cooked Shrimp | 3 |
| Bronze Bar | 10 |
| Iron Bar | 25 |

---

## UI Design

### Mobile-First Layout

```
┌─────────────────────────────────────┐
│  🎮 Velor Idle          💰 1,234   │  <- Header
├─────────────────────────────────────┤
│                                     │
│  ┌───────────────────────────────┐  │
│  │  🪓 Woodcutting    Lv. 25    │  │  <- Current Skill Card
│  │  ═══════════════════════70%══ │  │  <- XP Progress
│  │                               │  │
│  │  [====▓▓▓▓▓▓▓▓▓▓▓===]  3.2s  │  │  <- Action Progress
│  │                               │  │
│  │  🌳 Cutting: Oak Tree         │  │  <- Current Action
│  │  +25 XP  |  🪵 Oak Logs       │  │  <- Rewards
│  └───────────────────────────────┘  │
│                                     │
│  ┌─────────┬─────────┬─────────┐   │
│  │ 🌳      │ ⛏️      │ 🎣      │   │  <- Skill Selector
│  │ Wood    │ Mine    │ Fish    │   │
│  │ Lv.25   │ Lv.18   │ Lv.12   │   │
│  ├─────────┼─────────┼─────────┤   │
│  │ 🍳      │ 🔨      │ 🧪      │   │
│  │ Cook    │ Smith   │ Herb    │   │
│  │ Lv.8    │ Lv.5    │ Lv.1    │   │
│  ├─────────┼─────────┼─────────┤   │
│  │ 🗡️      │ ⭐      │         │   │
│  │ Thieve  │ Astro   │         │   │
│  │ Lv.3    │ Lv.1    │         │   │
│  └─────────┴─────────┴─────────┘   │
│                                     │
│  [📦 Inventory]  [🏪 Shop]  [⚙️]  │  <- Bottom Nav
└─────────────────────────────────────┘
```

### Desktop Layout

On wider screens (>768px):
- Skill selector moves to left sidebar
- Current action panel expands
- Inventory visible alongside action

### Key UI Components

1. **Header Bar** - Game title, gold display, settings
2. **Skill Card** - Current skill details, XP, action progress
3. **Skill Selector** - Grid of all skills with levels
4. **Action Selector** - Within a skill, choose what to do
5. **Inventory Panel** - Grid of items with counts
6. **Shop Panel** - Buy/sell interface
7. **Settings Modal** - Audio, notifications, data management

### Visual Feedback

- Progress bars for actions and XP
- Floating numbers for XP/item gains
- Gentle animations on skill completion
- Toast notifications for milestones

---

## Technical Architecture

### Shared Domain Model (shared/)

```
shared/src/main/scala/shared/VelorIdle/
├── VelorIdle.scala       # Core domain types
├── VelorIdleLogic.scala  # Pure game logic
└── Items.scala           # Item definitions
```

### Client (js/)

```
js/src/main/scala/client/
├── VelorIdleClient.scala           # Main client entry
└── components/laminar/veloride/
    ├── VelorIdleState.scala        # Reactive state
    ├── Header.scala                # Top bar
    ├── SkillCard.scala             # Current skill display
    ├── SkillSelector.scala         # Skill grid
    ├── ActionSelector.scala        # Action picker
    ├── InventoryPanel.scala        # Item grid
    ├── ShopPanel.scala             # Buy/sell
    └── ProgressBar.scala           # Reusable progress
```

### Static Assets (jvm/)

```
jvm/src/main/resources/static/
├── velor-idle.html       # Entry point
├── velor-idle.css        # Game-specific styles
└── velor-idle-manifest.json  # PWA manifest
```

### State Model

```scala
case class VelorIdleGame(
  skills: Map[Skill, SkillState],
  inventory: Inventory,
  gold: Long,
  currentSkill: Option[Skill],
  currentAction: Option[Action],
  actionProgress: Double,        // 0.0 to 1.0
  lastTickTime: Long,
  settings: GameSettings
)

case class SkillState(
  level: Int,
  xp: Long,
  masteryLevel: Int = 0,
  masteryXp: Long = 0
)

case class Inventory(
  slots: Vector[Option[ItemStack]],
  maxSlots: Int
)

case class ItemStack(
  item: Item,
  count: Long
)
```

---

## Implementation Phases

### Phase 1: Foundation ✅
- [x] Create HTML entry point (`velor-idle.html`)
- [x] Create CSS file (`velor-idle.css`) with base styles
- [x] Create PWA manifest
- [x] Add route in `ClientMain.scala`
- [x] Create `VelorIdleClient.scala` with basic init

### Phase 2: Domain Model ✅
- [x] Define `Skill` enum with all 10 skills
- [x] Define `Item` enum with basic items
- [x] Define `Action` types per skill
- [x] Create `VelorIdleGame` state model
- [x] Create `SkillState` with XP calculations
- [x] Create `Inventory` with slot management
- [x] Implement XP-to-level calculations (linear curve)
- [x] Implement level-to-XP requirements

### Phase 3: Core Logic ✅
- [x] Implement `VelorIdleLogic.tick()` - advances game state
- [x] Implement action completion handling
- [x] Implement XP granting and level-ups
- [x] Implement item generation on action complete
- [x] Implement inventory add/remove logic
- [x] Implement gold transactions

### Phase 4: Basic UI ✅
- [x] Create `VelorIdleState.scala` with reactive Vars
- [x] Create `Header.scala` - title, gold display
- [x] Create `SkillSelector.scala` - grid of skills
- [x] Create `SkillCard.scala` - current skill display
- [x] Create `ProgressBar.scala` - reusable progress component
- [x] Wire up skill selection to change current skill

### Phase 5: Action System ✅
- [x] Create `ActionSelector.scala` - pick action within skill
- [x] Implement action progress tick (visual)
- [x] Implement action completion with rewards
- [x] Add floating XP/item notifications
- [x] Implement Woodcutting with all tree types
- [x] Implement Mining with all rock types
- [x] Implement Fishing with all fish types
- [x] Implement Herbalism with all herb types

### Phase 6: Inventory & Shop (Partial)
- [x] Create `InventoryPanel.scala` - item grid
- [x] Implement item stacking logic
- [ ] Create `ShopPanel.scala` - sell interface
- [x] Implement selling items for gold
- [ ] Implement inventory slot purchases
- [ ] Add inventory full handling

### Phase 7: Processing Skills
- [x] Implement Cooking (raw → cooked, burn chance)
- [x] Implement Smithing (ore → bar → equipment)
- [x] Implement Alchemy (herbs → potions)
- [x] Implement Summoning (resources → tablets)
- [x] Implement tablet equipment slots
- [x] Implement tablet consumption during actions
- [x] Implement synergy detection and effects
- [ ] Implement recipe selection UI
- [ ] Add ingredient checking/consuming

### Phase 8: Special Skills
- [ ] Implement Thieving (success/fail/stun)
- [ ] Implement Astrology (stardust, constellations)
- [ ] Create constellation UI

### Phase 9: Persistence (Partial)
- [x] Implement localStorage save/load
- [x] Add auto-save timer (every 30s)
- [ ] Implement offline progress calculation
- [ ] Create welcome-back modal showing offline gains

### Phase 10: Polish
- [ ] Add skill milestone notifications
- [ ] Implement settings modal
- [ ] Add sound effects (optional, with toggle)
- [ ] Add haptic feedback for mobile
- [ ] Performance optimization
- [ ] Add statistics tracking

### Phase 11: Advanced Features (Future)
- [ ] Equipment system
- [ ] Combat skill
- [ ] Bank storage (separate from inventory)
- [ ] Achievements system
- [ ] Mastery system post-99

---

## CSS Structure

### Color Scheme

```css
:root {
  /* Velor-specific colors */
  --velor-bg: #1a1a2e;
  --velor-card: #252540;
  --velor-accent: #4ade80;      /* Green for progress */
  --velor-xp: #fbbf24;          /* Yellow/gold for XP */
  --velor-skill-wood: #8b5a2b;
  --velor-skill-mine: #6b7280;
  --velor-skill-fish: #3b82f6;
  --velor-skill-herbalism: #16a34a;  /* Gathering herbs */
  --velor-skill-cook: #ef4444;
  --velor-skill-smith: #f97316;
  --velor-skill-alchemy: #22c55e;    /* Alchemy/potions */
  --velor-skill-summon: #06b6d4;     /* Summoning/tablets */
  --velor-skill-thieve: #8b5cf6;
  --velor-skill-astro: #a855f7;
}
```

### Component Classes

- `.velor-container` - Full viewport container
- `.velor-header` - Top bar
- `.velor-skill-card` - Main skill display
- `.velor-skill-grid` - 3x3 skill selector
- `.velor-skill-tile` - Individual skill in grid
- `.velor-action-list` - Scrollable action picker
- `.velor-action-item` - Single action option
- `.velor-progress-bar` - Reusable progress bar
- `.velor-inventory` - Item grid container
- `.velor-item-slot` - Single inventory slot
- `.velor-bottom-nav` - Mobile bottom navigation

---

## Testing Strategy

### Unit Tests (shared/)

- XP calculation functions
- Level requirement calculations
- Inventory slot management
- Item stacking logic
- Action completion rewards
- Gold transaction handling

### Integration Tests

- Full game tick cycle
- Save/load round-trip
- Offline progress calculation

### Manual Testing Checklist

- [ ] All skills selectable
- [ ] Actions start on selection
- [ ] XP bar fills correctly
- [ ] Level-up triggers at correct XP
- [ ] Items added to inventory
- [ ] Shop selling works
- [ ] Inventory upgrades work
- [ ] Save/load preserves state
- [ ] Offline progress calculated
- [ ] Mobile touch interactions
- [ ] Desktop mouse interactions

---

## Open Questions

1. **Combat system** - Should combat be a separate skill or a game mode?
2. **Equipment** - When to introduce? What effects?
3. **Multiplayer** - Any social features? Leaderboards?
4. **Prestige** - Should there be a prestige/reset system?

---

## References

- [Melvor Idle](https://melvoridle.com/) - Primary inspiration
- [RuneScape Wiki](https://runescape.wiki/) - Skill mechanics reference
- [Tile Kingdom](../js/src/main/scala/client/TileKingdomClient.scala) - Existing Laminar patterns

