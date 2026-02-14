# Player List Consistency - Design Document

## Status: ✅ Implemented

## Overview

Unify the styling and structure of player lists across all game lobbies (ColorRush, DrawingGame, TugOfWar) to provide a consistent user experience.

## Current State

### ColorRush
- Uses `.player-card` class with full styling (border, background, flex layout)
- Players displayed as full-width cards (one per line)
- Generates HTML via string interpolation
- Shows `player.name` and `player.score pts (X wins)`

### DrawingGame  
- Simple plain div with text content
- No `.player-card` class usage
- Displays `${player.playerName} - ${player.score} pts`
- No border/background styling

### TugOfWar
- Team-based layout (Red/Blue teams)
- Uses span elements within team containers
- Includes team count in separate element
- Different UI paradigm due to team selection

## Desired Changes

### 1. Player "Bean" Style (All Games)
Players should be displayed as inline "beans" (pill-shaped badges) that flow horizontally, wrapping to new lines as needed, rather than taking up a full line each.

### 2. Player Count in Section Header
The "Players" heading should include the connected player count in parentheses.

**Current:** `h4(content = "Players")`  
**New:** `h4(content = "Players (3)")`

The count should update dynamically as players join/leave.

### 3. Unified Player Bean Component
Create a reusable component for rendering player beans that can be used across all games.

## Implementation Plan

### CSS Changes (`base.css`)

Add new `.players-container` and `.player-bean` classes:

```css
/* Players List Container - Bean Layout */
.players-container {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  padding: 0.75rem;
  background: rgba(24, 24, 27, 0.4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  min-height: 2.5rem;
}

/* Player Bean - Pill-shaped inline badge */
.player-bean {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  background: rgba(39, 39, 42, 0.7);
  border: 1px solid var(--border-medium);
  border-radius: 9999px; /* Full pill shape */
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-primary);
  transition: all 0.2s;
  white-space: nowrap;
}

.player-bean:hover {
  background: var(--bg-card-hover);
  border-color: var(--border-bright);
}

.player-bean .player-score {
  color: var(--text-secondary);
  font-weight: 400;
  font-size: 0.8rem;
}

/* Player status dot in bean */
.player-bean .status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-green);
  flex-shrink: 0;
}

.player-bean .status-dot.disconnected {
  background: var(--text-muted);
}
```

### ColorRush Changes (`ColorRushClient.scala`)

#### 1. Update `createWaitingArea()` - Line ~198
Change the players heading to use a dynamic ID for the count:

```scala
// Before:
h4(content = "Players"),
div(id = "playersList", cls = "players"),

// After:
h4(id = "playersHeading", content = "Players (0)"),
div(id = "playersList", cls = "players-container"),
```

#### 2. Update `updatePlayersList()` - Around line 395
Change from full-width cards to inline beans:

```scala
def updatePlayersList(players: Map[String, PlayerState], gameStatus: GameStatus): Unit =
  val playersArray = players.values.toSeq.sortBy(p => (-p.score, -p.roundsWon))
  val playerCount = playersArray.size
  
  // Update heading with count
  getElementById("playersHeading").foreach(_.textContent = s"Players ($playerCount)")
  
  val winner = playersArray.headOption
  val showCrown = gameStatus == GameStatus.GameOver
  
  val playersHTML = playersArray.map: player =>
    val crown = if showCrown && winner.contains(player) then "👑 " else ""
    s"""<span class="player-bean">$crown${player.name}</span>"""
  .mkString("")
  
  getElementById("playersList").foreach(_.innerHTML = playersHTML)
  getElementById("gamePlayers").foreach(_.innerHTML = playersHTML)
```

### DrawingGame Changes (`DrawingGameClient.scala`)

#### 1. Update `createWaitingRoom()` - Line ~226
Change the players heading to use a dynamic ID for the count:

```scala
// Before:
h4(content = "Players"),
div(id = "playersList", cls = "players"),

// After:
h4(id = "playersHeading", content = "Players (0)"),
div(id = "playersList", cls = "players-container"),
```

#### 2. Update `updateDrawingLobbyUI()` - Around line 641
Change from plain divs to bean elements:

```scala
getElementById("playersList").foreach: elem =>
  elem.innerHTML = ""
  val playerCount = lobby.players.size
  getElementById("playersHeading").foreach(_.textContent = s"Players ($playerCount)")
  
  lobby.players.values.foreach: player =>
    val statusClass = if player.connected then "" else " disconnected"
    val bean = span(cls = "player-bean")(
      span(cls = s"status-dot$statusClass"),
      span(content = player.playerName)
    )
    elem.appendChild(bean)
```

### TugOfWar Changes (`TugOfWarClient.scala`)

TugOfWar has a different paradigm (team selection) but we should still apply the bean style to team player lists.

#### 1. Update team player rendering - Around line 635
The existing code uses spans - update to use `.player-bean` class:

```scala
// Current:
elem.innerHTML = redPlayers.map: player =>
  s"""<span class="tow-player-name">${player.name}</span>"""
.mkString(" ")

// New:  
elem.innerHTML = redPlayers.map: player =>
  s"""<span class="player-bean">${player.name}</span>"""
.mkString("")
```

#### 2. Update team headers to include count
Instead of a separate count element, include count in header:

```scala
// Update header text dynamically
getElementById("towRedHeader").foreach(_.textContent = s"🔴 Red Team (${redPlayers.size})")
getElementById("towBlueHeader").foreach(_.textContent = s"🔵 Blue Team (${bluePlayers.size})")
```

This requires adding `id` attributes to the team headers.

## Files to Modify

1. **`jvm/src/main/resources/static/base.css`**
   - Add `.players-container` class
   - Add `.player-bean` class and variants
   
2. **`js/src/main/scala/client/ColorRushClient.scala`**
   - Update `createWaitingArea()` - add ID to heading, change container class
   - Update `updatePlayersList()` - generate bean HTML, update heading count

3. **`js/src/main/scala/client/DrawingGameClient.scala`**
   - Update `createWaitingRoom()` - add ID to heading, change container class
   - Update `updateDrawingLobbyUI()` - generate bean elements, update heading count

4. **`js/src/main/scala/client/TugOfWarClient.scala`**
   - Update `createTugOfWarWaitingArea()` - add IDs to team headers
   - Update team player rendering - use `.player-bean` class
   - Update header text to include player count

## Testing Considerations

1. Test player beans wrap correctly on narrow screens
2. Verify player count updates correctly when players join/leave
3. Test with long player names to ensure beans don't break layout
4. Verify hover states work correctly on beans
5. Test crown/winner indicator still displays correctly in ColorRush
6. Verify disconnected player status dot displays correctly in DrawingGame

