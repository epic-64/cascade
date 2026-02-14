# UI Consistency Strategy - ColorRush & DrawingGame

## Overview

This document analyzes the layout, styles, and user flows of ColorRush and DrawingGame, identifies inconsistencies and missing flows, and proposes a unified design strategy.

---

## Current State Analysis

### ColorRush User Flow

```
1. Lobby Setup (tabbed: Join/Create)
   ├─ Join Tab: gameId + playerName → Join Game
   └─ Create Tab: playerName → Create Game (generates code)
           ↓
2. Waiting Area
   ├─ Shows lobby code
   ├─ Player list
   ├─ Round selector (host only, presumably)
   └─ Start Game button
           ↓
3. Game Area
   ├─ Round info (round X of Y, target color)
   ├─ Color grid (clickable colors)
   └─ Players sidebar (scores)
           ↓
4. Round Winner Announcement (modal, auto-dismiss 2s)
           ↓
5. Game Winner Announcement (modal, click to dismiss)
           ↓
   Return to Lobby (page reload)
```

### DrawingGame User Flow

```
1. Lobby Setup (tabbed: Join/Create)
   ├─ Join Tab: lobbyId + playerName → Join Lobby
   └─ Create Tab: apiKey + playerName → Create Lobby
           ↓
2. Waiting Room
   ├─ Shows lobby code
   ├─ Player list (simpler style)
   └─ Start Game button
           ↓
3. Drawing Area
   ├─ Prompt display
   ├─ Timer
   ├─ Canvas (512x512)
   ├─ Color picker + Clear + Submit
   └─ Return to Lobby button
           ↓
4. Gallery Area (multi-phase)
   ├─ Phase 1: Drawings revealed
   ├─ Phase 2: AI captions revealed one-by-one
   ├─ Phase 3: AI vote revealed
   ├─ Phase 4: Player voting
   └─ Phase 5: Round complete + summary
           ↓
5. Next Round or Return to Lobby
```

---

## Identified Inconsistencies

### 1. **Naming Conventions**

| Aspect | ColorRush | DrawingGame | Recommendation |
|--------|-----------|-------------|----------------|
| Game identifier | `gameId` | `lobbyId` | Use `gameId` consistently |
| Player ID var | `colorRushPlayerId` | `currentPlayerId` | Use `currentPlayerId` |
| Session key | `colorRush` | `drawing` | Keep as-is (game-specific) |
| Waiting container | `waitingArea` | `waitingRoom` | Use `waitingArea` |
| Players container class | `.players` | `.players-list` | Use `.players` |

### 2. **CSS Class Inconsistencies**

| Element | ColorRush | DrawingGame | Recommendation |
|---------|-----------|-------------|----------------|
| Lobby setup container | `.lobby-setup` | `.lobby-setup` | ✅ Consistent |
| Waiting area | `.waiting-area-container` | `.waiting-room` | Unify to `.waiting-area` |
| Player cards | `.player-card` | `div` with inline styles | Use `.player-card` |
| Start button | `.start-button` | `#startGameBtn` (ID styling) | Use `.btn.btn-success` |
| Game controls | `.game-controls` | `.game-controls` | ✅ Consistent |
| Return button | `.secondary-button` | `.btn.btn-secondary` | Use `.btn.btn-secondary` |

### 3. **Button Styling**

ColorRush defines multiple button styles:
- `.start-button` - Custom green gradient
- `.secondary-button` - Custom secondary style
- `.close-winner-button` - Custom purple gradient

DrawingGame uses:
- `#startGameBtn` - Custom green (ID selector)
- `.btn.btn-secondary` - Base style
- `#submitDrawingBtn` - Custom green (ID selector)
- `#clearBtn` - Custom danger style (ID selector)
- `#nextRoundBtn` - Custom purple (ID selector)

**Problem**: Both games define buttons with IDs when base `.btn` classes exist.

**Recommendation**: Refactor to use base classes:
```css
/* Use these existing patterns */
.btn.btn-success   /* green primary action */
.btn.btn-secondary /* secondary action */
.btn.btn-primary   /* purple primary */
.btn.btn-danger    /* destructive action */
```

### 4. **Layout Approach**

| Aspect | ColorRush | DrawingGame |
|--------|-----------|-------------|
| Desktop layout | CSS Grid (2-column) | Flexbox (single column) |
| Responsive breakpoint | 900px | 768px |
| Max container width | Implicit via grid | 1200px explicit |
| Sidebar on desktop | Yes (sticky players) | No |

**Recommendation**: DrawingGame should adopt a similar responsive sidebar pattern for the player list during gameplay, or both games should use a consistent single-column layout.

### 5. **Timer Display**

| Aspect | ColorRush | DrawingGame |
|--------|-----------|-------------|
| Timer element | None (round-based) | `.timer` (60s countdown) |
| Urgent state | N/A | `.timer.urgent` at ≤10s |
| Gallery timer | N/A | `.gallery-timer` |

ColorRush is click-based (no timer), DrawingGame is time-based. No issue, just different game mechanics.

### 6. **Modal/Announcement Patterns**

**ColorRush:**
- Round winner: `.winner-announcement` (auto-dismiss)
- Game winner: `.game-winner-announcement` (click overlay to dismiss)

**DrawingGame:**
- Round complete: `.round-summary` (inline, below gallery)
- No game-over modal

**Inconsistency**: ColorRush uses modals for results, DrawingGame uses inline summaries.

**Recommendation**: Both should use inline summaries for round results, with an optional game-over modal.

---

## Missing User Flows

### 1. **Error Handling UI**

Both games log errors to console but lack user-facing error states:

- [ ] Connection failed state
- [ ] Reconnection in progress state
- [ ] API error display (DrawingGame: OpenAI failures)
- [ ] Invalid game code feedback
- [ ] Game full feedback
- [ ] Game already started feedback

**Recommendation**: Add a shared `.alert` toast/banner system:
```html
<div id="errorBanner" class="alert alert-warning hidden">
  <span id="errorMessage"></span>
  <button class="alert-dismiss">×</button>
</div>
```

### 2. **Loading States**

Missing loading indicators for:
- [ ] WebSocket connecting
- [ ] Joining game
- [ ] Creating game
- [ ] DrawingGame: AI processing drawings
- [ ] DrawingGame: Submitting drawing

**Recommendation**: Add loading states to buttons:
```scala
def setButtonLoading(id: String, loading: Boolean): Unit =
  getElementById(id).foreach: btn =>
    val button = btn.asInstanceOf[HTMLButtonElement]
    button.disabled = loading
    if loading then
      button.dataset("originalText") = button.textContent
      button.textContent = "Loading..."
    else
      button.textContent = button.dataset("originalText")
```

### 3. **Reconnection UI Feedback**

Current flow: Session exists → hide lobby → attempt reconnect → success/failure

Missing:
- [ ] "Reconnecting..." visual indicator
- [ ] Reconnection attempt count
- [ ] Manual retry button on failure
- [ ] Clear session option

**Recommendation**:
```html
<div id="reconnecting" class="reconnecting-overlay hidden">
  <div class="reconnecting-content">
    <div class="spinner"></div>
    <p>Reconnecting to game...</p>
    <button id="cancelReconnect">Cancel</button>
  </div>
</div>
```

### 4. **Leave Game Confirmation**

Both games have "Return to Lobby" which reloads the page and clears session.

Missing:
- [ ] Confirmation dialog before leaving active game
- [ ] Different behavior for host vs player

**Recommendation**:
```scala
def returnToLobby(): Unit =
  if isGameActive then
    if window.confirm("Leave the game? You may not be able to rejoin.") then
      clearSession()
      window.location.reload()
  else
    clearSession()
    window.location.reload()
```

### 5. **Player List Updates**

**ColorRush**: Shows player cards with name + score + wins
**DrawingGame**: Shows simple text "name - X pts"

Missing in both:
- [ ] Player connection status indicator (online/reconnecting)
- [ ] Host badge
- [ ] "You" indicator for current player
- [ ] Player join/leave animations

### 6. **Game Settings Sync**

**ColorRush**: Round selector syncs to server via `ConfigureMessage`

**DrawingGame**: No settings (rounds are implicit)

Missing in DrawingGame:
- [ ] Number of rounds selector
- [ ] Drawing time selector (30/45/60 seconds)
- [ ] Voting time selector

### 7. **Spectator Mode**

Neither game supports spectators.

Missing:
- [ ] Watch-only join option
- [ ] Spectator count display
- [ ] Spectator-appropriate UI (no controls)

---

## Proposed Design Strategy

### Phase 1: CSS Unification

1. **Move shared game styles to base.css**:
   - `.lobby-setup`, `.waiting-area`, `.game-area`, `.game-controls`
   - `.player-card`, `.players` (list container)
   - `.lobby-code`, `.round-info`
   - Modal/announcement patterns

2. **Remove ID-based button styling**:
   - Replace `#startGameBtn` with `.btn.btn-success.btn-block`
   - Replace `#submitDrawingBtn` with `.btn.btn-success`
   - Replace `#clearBtn` with `.btn.btn-danger`
   - Replace `.start-button` with `.btn.btn-success.btn-block`
   - Replace `.secondary-button` with `.btn.btn-secondary`

3. **Standardize naming**:
   - `.waiting-room` → `.waiting-area`
   - `.players-list` → `.players`

### Phase 2: ScalaJS Component Extraction

1. **Create shared UI components**:
   ```scala
   // client/components/LobbySetup.scala
   object LobbySetup:
     def render(
       title: String,
       subtitle: String,
       joinFields: Seq[InputField],
       createFields: Seq[InputField],
       onJoin: () => Unit,
       onCreate: () => Unit
     ): HTMLElement
   
   // client/components/WaitingArea.scala
   object WaitingArea:
     def render(
       lobbyCode: String,
       players: Seq[Player],
       settings: Option[HTMLElement],
       onStart: () => Unit
     ): HTMLElement
   
   // client/components/PlayerList.scala
   object PlayerList:
     def render(
       players: Seq[Player],
       currentPlayerId: Option[String],
       showScores: Boolean
     ): HTMLElement
   ```

2. **Create shared state utilities**:
   ```scala
   // client/components/GameState.scala
   trait GameClient:
     def showError(message: String): Unit
     def showLoading(elementId: String): Unit
     def hideLoading(elementId: String): Unit
     def showReconnecting(): Unit
     def hideReconnecting(): Unit
   ```

### Phase 3: Missing Flows Implementation

1. **Error handling system**:
   - Add error banner to base HTML structure
   - Create `showError(message, duration)` utility
   - Wire up WebSocket error handlers

2. **Loading states**:
   - Add spinner CSS to base.css
   - Create button loading helpers
   - Add to all async operations

3. **Reconnection UI**:
   - Add reconnecting overlay to base structure
   - Show during `checkForExistingSession()`
   - Hide on success/failure

4. **Leave confirmation**:
   - Add `beforeunload` event handler during active game
   - Add confirmation to Return to Lobby

### Phase 4: Player Experience Improvements

1. **Player list enhancements**:
   - Add connection status dots (green/yellow/gray)
   - Add "(Host)" badge
   - Add "(You)" indicator
   - Add join/leave animations

2. **Game settings for DrawingGame**:
   - Add rounds selector (3/5/10)
   - Add drawing time selector (30/45/60s)
   - Sync via WebSocket like ColorRush

---

## File Changes Summary

### CSS Files

**base.css additions:**
```css
/* Shared Game Styles */
.waiting-area { ... }
.player-card { ... }
.player-status { ... }
.lobby-code { ... }
.game-controls { ... }
.alert-toast { ... }
.reconnecting-overlay { ... }
.spinner { ... }
```

**color-rush.css removals:**
- Remove `.start-button` (use `.btn.btn-success`)
- Remove `.secondary-button` (use `.btn.btn-secondary`)
- Remove `.waiting-area-container` (use shared `.waiting-area`)

**ai-drawing.css removals:**
- Remove `#startGameBtn` styles (use `.btn.btn-success`)
- Remove `#submitDrawingBtn` styles (use `.btn.btn-success`)
- Remove `#clearBtn` styles (use `.btn.btn-danger`)
- Remove `.waiting-room` (use shared `.waiting-area`)
- Remove `.players-list` (use shared `.players`)

### ScalaJS Files

**New files:**
- `js/src/main/scala/client/components/ErrorBanner.scala`
- `js/src/main/scala/client/components/LoadingState.scala`
- `js/src/main/scala/client/components/ReconnectingOverlay.scala`
- `js/src/main/scala/client/components/PlayerCard.scala`

**Modified files:**
- `ColorRushClient.scala`: Use shared components, fix naming
- `DrawingGameClient.scala`: Use shared components, fix naming

---

## Priority Order

1. **High Priority** (User-facing bugs/confusion):
   - Error handling UI
   - Reconnection feedback
   - Loading states

2. **Medium Priority** (Consistency):
   - CSS class unification
   - Button styling standardization
   - Naming conventions

3. **Low Priority** (Polish):
   - Shared ScalaJS components
   - Player list enhancements
   - DrawingGame settings
   - Leave confirmation

---

## Testing Strategy

For each change:
1. Test ColorRush join/create/play/reconnect flow
2. Test DrawingGame join/create/play/reconnect flow
3. Test error scenarios (invalid code, full game, disconnection)
4. Test on mobile viewport
5. Test with reduced motion preference
6. Test with high contrast preference

---

## Open Questions

1. Should we unify the "lobby" vs "game" terminology?
   - ColorRush uses "game", DrawingGame uses "lobby"
   - **Recommendation**: Use "game" for the session, "lobby" for the waiting state

2. Should modals be replaced with inline UI?
   - ColorRush uses modals for results
   - DrawingGame uses inline summaries
   - **Recommendation**: Keep both patterns, but make them consistent within each game

3. Should we add a shared header showing game status?
   - Current: NavigationBar shows game name only
   - **Recommendation**: Add optional status area (players online, round number)

4. Should reconnection be automatic or manual?
   - Current: Automatic on page load, no retry
   - **Recommendation**: Add manual retry button, max 3 auto-retries

