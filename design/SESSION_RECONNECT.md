# Session Reconnection Mechanism - Design Document

## Overview

This document describes the player session reconnection system used in Cascade games (ColorRush and DrawingGame) to handle page refreshes and WebSocket disconnections gracefully.

## Problem Statement

WebSocket connections are inherently fragile:
- Page refresh closes the connection
- Browser tab sleep/wake cycles
- Network interruptions
- Accidental navigation away and back

Without session persistence, players would lose their game progress and be unable to rejoin.

## Current Implementation

Both ColorRush and DrawingGame implement nearly identical reconnection logic across three layers:

### 1. Client-Side Session Storage

**Storage Keys:**
```
ColorRush:
  - colorRush.playerId
  - colorRush.gameId
  - colorRush.playerName

DrawingGame:
  - drawing.playerId
  - drawing.lobbyId
  - drawing.playerName
```

**Lifecycle:**
1. On successful join: `saveSession(playerId, gameId, playerName)` saves to `localStorage`
2. On page load: `checkForExistingSession()` loads session and attempts reconnect
3. On rejoin failure: `clearSession()` removes stale data
4. On WebSocket close: Session is NOT cleared (allows retry)

### 2. Client-Side Reconnection Flow

```
Page Load
    ↓
loadSession() → found? 
    ↓               ↓
   yes             no
    ↓               ↓
Hide lobby UI    Show lobby UI
    ↓
connectWebSocket(gameId)
    ↓
Send RejoinMessage(playerId, gameId)
    ↓
Wait for response
    ↓
JoinedMessage (success) ──or── RejoinFailed (failure)
    ↓                              ↓
Update UI, game continues     clearSession()
                                   ↓
                              Show lobby UI
```

### 3. Server-Side Player State

**Player State Model:**
```scala
case class PlayerState(
    playerId: String,
    name: String,
    score: Int,
    connected: Boolean = true,              // Track connection status
    disconnectedAt: Option[Long] = None     // Timestamp for grace period
)
```

**Server Logic:**
```scala
// On disconnect
def disconnectPlayer(playerId: String): Unit =
  player.copy(connected = false, disconnectedAt = Some(System.currentTimeMillis()))

// On rejoin attempt  
def canRejoin(playerId: String, gracePeriodMs: Long = 60000): Boolean =
  player.disconnectedAt match
    case Some(disconnectTime) =>
      (System.currentTimeMillis() - disconnectTime) < gracePeriodMs
    case None => true

// On successful rejoin
def reconnectPlayer(playerId: String): Unit =
  player.copy(connected = true, disconnectedAt = None)
```

### 4. WebSocket Messages

**Client → Server:**
```scala
// ColorRush
case class RejoinMessage(playerId: String, gameId: String)

// DrawingGame  
case class RejoinLobby(lobbyId: String, playerId: String)
```

**Server → Client:**
```scala
// ColorRush
case class JoinedMessage(playerId: String, gameId: String)
case class RejoinFailedMessage(reason: String)

// DrawingGame
case class LobbyCreated(lobbyId: String, playerId: String)  // Reused for rejoin
case class RejoinFailed(reason: String)
```

## Key Design Decisions

### Why localStorage over sessionStorage?
- `sessionStorage` clears on tab close
- `localStorage` persists across sessions
- Allows returning to a game even after closing the browser (within grace period)

### Why 60-second grace period?
- Long enough to handle page refreshes and network hiccups
- Short enough to not block game slots indefinitely
- Consistent with game cleanup intervals

### Why not remove player on disconnect?
- Removing players causes issues with scoring and game state
- Other players see "ghost" departures and rejoins
- Better UX: "Player reconnecting..." vs "Player left" + "Player joined"

## Shared Patterns

Both implementations follow the same patterns:

| Aspect | ColorRush | DrawingGame |
|--------|-----------|-------------|
| Storage keys | `colorRush.*` | `drawing.*` |
| Session data | (playerId, gameId, playerName) | (playerId, lobbyId, playerName) |
| Rejoin check on load | `checkForExistingSession()` | `checkForExistingDrawingSession()` |
| Rejoin attempt | `attemptRejoin()` | `attemptDrawingRejoin()` |
| Grace period | 60 seconds | 60 seconds |
| Player model field | `disconnectedAt: Option[Long]` | `disconnectedAt: Option[Long]` |
| Server methods | `canRejoin()`, `reconnectPlayer()`, `disconnectPlayer()` | Same |

## Limitations

1. **Single game per type**: A player can only have one active session per game type
2. **No offline queue**: Messages sent while disconnected are lost
3. **State not cached client-side**: Full state must be re-sent on reconnect
4. **No automatic retry**: Client must refresh page to retry reconnection

