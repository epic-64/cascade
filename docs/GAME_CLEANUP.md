# Game Cleanup System

## Overview

The Color Rush game server implements an automatic cleanup system to prevent "zombie" games from accumulating in memory.

## Features

### 1. Immediate Cleanup for GameOver Games
When a game reaches `GameOver` status and all players disconnect, the game is immediately removed from the server state.

**Implementation**: In `handlePlayerDisconnect`, after removing a player:
```scala
// Clean up game if it's GameOver and has no more connections
if updatedGame.status == shared.ColorRushGameStatus.GameOver && connections.isEmpty then
  cleanupGame(gameId)
```

This prevents the issue where:
- Game finishes at round 10/10
- Players see the winner and browser reloads
- Game remains in memory with status `GameOver`
- Players rejoin and game continues to 11/10

### 2. Periodic Cleanup for Empty Games
A scheduled task runs every 5 minutes to clean up any games that have no active connections, regardless of status.

**Implementation**: Uses `ScheduledExecutorService` to run cleanup task:
```scala
cleanupScheduler.scheduleAtFixedRate(
  cleanupTask,
  5, // initial delay in minutes
  5, // period in minutes
  java.util.concurrent.TimeUnit.MINUTES
)
```

This handles cases where:
- All players disconnect during a game
- Games are abandoned in `Waiting` or `Playing` state
- Network issues cause connections to drop

## Cleanup Process

The `cleanupGame(gameId)` method:
1. Removes game from `colorRushGames` map
2. Removes connection set from `gameConnections` map
3. Removes all player-to-game mappings from `playerToGame` map

## Configuration

- **Periodic cleanup interval**: 5 minutes (hardcoded)
- **Initial delay**: 5 minutes

## Logging

The system logs:
- When individual games are cleaned up: `"Cleaned up game $gameId"`
- Periodic cleanup results: `"Periodic cleanup: removed X empty game(s)"`
- Cleanup task initialization: `"Started periodic game cleanup task (runs every 5 minutes)"`

## Future Enhancements

Possible improvements:
- Make cleanup interval configurable via environment variable
- Add metrics tracking (games created vs games cleaned up)
- Implement game archival before deletion for analytics
- Add grace period before cleaning up empty games (e.g., 2 minutes)

