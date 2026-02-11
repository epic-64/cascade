# Plan: Unified Session Reconnection System

## Goal

Create a reusable session reconnection system that can be shared across all current and future games in Cascade.

## Current State Analysis

Both ColorRush and DrawingGame have nearly identical implementations spread across:
- **Client**: Session storage functions, reconnection flow, state variables
- **Server**: Player state model fields, rejoin handlers, disconnect handlers
- **Shared**: `canRejoin()`, `reconnectPlayer()`, `disconnectPlayer()` functions

The code is 90% duplicated with only naming differences.

---

## Proposed Architecture

### Phase 1: Shared Session Module (Client-Side)

Create a generic session manager in `js/src/main/scala/client/session/`:

```scala
// SessionManager.scala
trait GameSession:
  def playerId: String
  def gameId: String      // or lobbyId - generic identifier
  def playerName: String

object SessionManager:
  def save[T <: GameSession](key: String, session: T): Unit
  def load(key: String): Option[GameSession]
  def clear(key: String): Unit
  def exists(key: String): Boolean
```

**Benefits:**
- Single source of truth for localStorage logic
- Type-safe session data
- Consistent error handling with `Try`

### Phase 2: Reconnection Flow Abstraction (Client-Side)

Create reusable reconnection helpers:

```scala
// ReconnectionHelper.scala
trait ReconnectionConfig:
  def sessionKey: String
  def buildWebSocketUrl(gameId: String): String
  def buildRejoinMessage(session: GameSession): String  // JSON message
  def handleRejoinSuccess(playerId: String, gameId: String): Unit
  def handleRejoinFailure(reason: String): Unit
  def hideSetupUI(): Unit

object ReconnectionHelper:
  def checkAndReconnect(config: ReconnectionConfig): Unit
  def attemptReconnect(session: GameSession, config: ReconnectionConfig): Unit
```

**Benefits:**
- Standardized reconnection flow
- Game-specific behavior via configuration
- Reduces per-game boilerplate

### Phase 3: Shared Player State Model (Shared Module)

Extract common player fields to a shared trait:

```scala
// shared/src/main/scala/shared/session/PlayerConnection.scala
trait PlayerConnection:
  def connected: Boolean
  def disconnectedAt: Option[Long]

trait PlayerConnectionOps:
  def isWithinGracePeriod(player: PlayerConnection, gracePeriodMs: Long = 60000): Boolean =
    player.disconnectedAt match
      case Some(disconnectTime) =>
        (System.currentTimeMillis() - disconnectTime) < gracePeriodMs
      case None => player.connected
```

**Benefits:**
- Single implementation of grace period logic
- Shared across all game types
- Testable in isolation

### Phase 4: Server-Side Reconnection Handler (JVM Module)

Create a generic reconnection handler mixin:

```scala
// server/reconnection/ReconnectionSupport.scala
trait ReconnectionSupport[Player <: PlayerConnection, Game]:
  def getGame(gameId: String): Option[Game]
  def getPlayers(game: Game): Map[String, Player]
  def updatePlayer(game: Game, playerId: String, player: Player): Game
  def updateGame(gameId: String, game: Game): Unit
  def sendRejoinSuccess(channel: WsChannelActor, playerId: String, gameId: String): Unit
  def sendRejoinFailed(channel: WsChannelActor, reason: String): Unit
  def broadcastState(gameId: String): Unit
  
  // Provided implementation
  final def handleRejoin(channel: WsChannelActor, gameId: String, playerId: String): Unit =
    // Generic implementation that all games can use
```

**Benefits:**
- One tested implementation for all games
- Games implement abstract methods for their specifics
- Consistent logging and error handling

---

## Implementation Steps

### Step 1: Create Session Types (Shared)
**Effort: Small** ✅ COMPLETE
- [x] Create `shared/src/main/scala/shared/session/GameSession.scala`
- [x] Add `PlayerConnection` trait with `connected` and `disconnectedAt`
- [x] Add `PlayerConnectionOps` with `canRejoin` implementation

### Step 2: Create Client Session Manager
**Effort: Small** ✅ COMPLETE
- [x] Create `js/src/main/scala/client/session/SessionManager.scala`
- [x] Implement `save`, `load`, `clear` functions
- [x] Add logging and error handling
- [x] Create `js/src/main/scala/client/session/ReconnectionHelper.scala`

### Step 3: Refactor ColorRush Client
**Effort: Medium**
- [ ] Replace inline session functions with `SessionManager` calls
- [ ] Create `ColorRushSession` case class extending `GameSession`
- [ ] Update `checkForExistingSession` to use shared helper
- [ ] Test reconnection still works

### Step 4: Refactor DrawingGame Client
**Effort: Medium**
- [ ] Apply same changes as ColorRush
- [ ] Create `DrawingGameSession` case class
- [ ] Test reconnection still works

### Step 5: Create Server Reconnection Support
**Effort: Medium** ✅ COMPLETE
- [x] Create `ReconnectionSupport` trait in `server/reconnection/`
- [x] Implement generic `handleRejoinRequest` logic
- [x] Implement generic `handleDisconnection` logic
- [x] Add `cleanupExpiredPlayers` helper

### Step 6: Refactor Server Handlers
**Effort: Medium**
- [ ] Update `ColorRushHandler` to mix in `ReconnectionSupport`
- [ ] Update `DrawingGameHandler` to mix in `ReconnectionSupport`
- [ ] Verify tests still pass

### Step 7: Update Shared Models
**Effort: Small**
- [ ] Make `ColorRushPlayer` extend `PlayerConnection`
- [ ] Make `DrawingPlayer` extend `PlayerConnection`
- [ ] Remove duplicated `canRejoin`/`reconnectPlayer` implementations

---

## File Changes Summary

### New Files
```
shared/src/main/scala/shared/session/
  ├── GameSession.scala          # Session data trait
  └── PlayerConnection.scala     # Connected state trait + ops

js/src/main/scala/client/session/
  ├── SessionManager.scala       # localStorage wrapper
  └── ReconnectionHelper.scala   # Reconnection flow helper

jvm/src/main/scala/server/reconnection/
  └── ReconnectionSupport.scala  # Server-side handler mixin
```

### Modified Files
```
js/src/main/scala/client/
  ├── ColorRushClient.scala      # Use SessionManager
  └── DrawingGameClient.scala    # Use SessionManager

jvm/src/main/scala/server/
  ├── ColorRushHandler.scala     # Mix in ReconnectionSupport
  └── DrawingGameHandler.scala   # Mix in ReconnectionSupport

shared/src/main/scala/shared/
  ├── ColorRush/ColorRush.scala  # PlayerState extends PlayerConnection
  └── DrawingGame/DrawingGame.scala # DrawingPlayer extends PlayerConnection
```

---

## Testing Strategy

1. **Unit tests** for `SessionManager` (mock localStorage)
2. **Unit tests** for `PlayerConnectionOps.canRejoin`
3. **Integration tests** per game verifying:
   - Session is saved on join
   - Session is loaded and reconnect attempted on page load
   - Session is cleared on rejoin failure
   - Grace period is respected

---

## Future Games

To add session reconnection to a new game:

1. Create a `NewGameSession` case class extending `GameSession`
2. Add `extends PlayerConnection` to the player state model
3. Mix `ReconnectionSupport` into the server handler
4. Call `SessionManager.save()` on join confirmation
5. Call `ReconnectionHelper.checkAndReconnect()` on initialization

Estimated effort per new game: **~30 minutes** (vs current ~2 hours of copy-paste)

---

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Breaking existing games | Incremental refactor with tests |
| Shared code coupling | Keep game-specific behavior in config objects |
| Migration complexity | Refactor one game first, then copy pattern |

## Estimated Total Effort

- Phase 1-2 (Client): 4-6 hours
- Phase 3-4 (Server): 4-6 hours  
- Testing & verification: 2-4 hours
- **Total: 10-16 hours**

