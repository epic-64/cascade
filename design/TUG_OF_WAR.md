# Tug of War - Design Document

## Overview
A real-time multiplayer tug of war game where two teams compete by spam-clicking to pull a rope indicator toward their side. Players join a lobby, pick a team (Left or Right), and then furiously click to help their team win.

## Game Concept

### Theme
Classic tug of war with a rope visualization. A rope indicator (marker/flag) sits at center. Each team's clicks pull the marker toward their side. First team to pull the marker past their goal line wins the round.

### Core Mechanic
- **Left Team (🔴 Red)**: Clicks decrease the position value (pull left)
- **Right Team (🔵 Blue)**: Clicks increase the position value (pull right)
- **Position**: Integer value starting at 0, range -100 to +100
- **Win Condition**: First team to reach their goal (-100 for Red, +100 for Blue) wins the round

## Game Flow

### 1. Lobby Phase (`Waiting`)
- Host creates a game lobby, receives a shareable game code
- Players join using the game code and enter their name
- **Team Selection**: Players must choose Red (Left) or Blue (Right) team
- Lobby shows connected players grouped by team
- Host configures game settings (rounds to win, optional time limit)
- Host can start game when at least 1 player is on each team

### 2. Playing Phase (`Playing`)
- Rope visualization displayed with marker at center (position = 0)
- Large click button for each player (styled with their team color)
- Real-time position updates broadcast via WebSocket
- Players spam-click to pull the rope toward their side
- Visual feedback: rope animates, marker moves, team strength indicators pulse

### 3. Round End Phase (`RoundEnd`)
- Winner team announced with celebration animation
- Score updated (winning team gets +1 round)
- Brief pause (3 seconds) before next round or game end
- Host can proceed to next round

### 4. Game Over Phase (`GameOver`)
- Overall winner announced (team with most rounds won)
- Final statistics displayed:
  - Total clicks per team
  - Clicks per player
  - Rounds won by each team
- Option to play again or return to lobby

## Technical Architecture

### Shared Data Models (`shared/src/main/scala/shared/TugOfWar/`)

```scala
// TugOfWar.scala
package shared.TugOfWar

import upickle.default.ReadWriter
import shared.session.{PlayerConnection, PlayerConnectionOps}

enum Team derives ReadWriter:
  case Red, Blue

enum GameStatus derives ReadWriter:
  case Waiting, Playing, RoundEnd, GameOver

case class TugOfWarGame(
  gameId: String,
  players: Map[String, PlayerState],
  ropePosition: Int,           // -100 to +100, 0 is center
  roundsToWin: Int,            // First to X rounds wins
  redRoundsWon: Int,
  blueRoundsWon: Int,
  currentRound: Int,
  status: GameStatus,
  roundStartTime: Option[Long],
  timeLimitSeconds: Option[Int] // Optional time limit per round
) derives ReadWriter

case class PlayerState(
  playerId: String,
  name: String,
  team: Option[Team],          // None until team is chosen
  clickCount: Int,             // Clicks this round
  totalClicks: Int,            // Clicks across all rounds
  connected: Boolean = true,
  disconnectedAt: Option[Long] = None
) extends PlayerConnection derives ReadWriter

case class RoundResult(
  winner: Team,
  redClicks: Int,
  blueClicks: Int,
  duration: Long
) derives ReadWriter

object TugOfWar:
  val WinPosition = 100        // Position to reach to win
  val StartPosition = 0
  val ClickPower = 1           // How much each click moves the rope
  
  def createGame(gameId: String, roundsToWin: Int = 3): TugOfWarGame
  def addPlayer(game: TugOfWarGame, playerId: String, playerName: String): TugOfWarGame
  def setPlayerTeam(game: TugOfWarGame, playerId: String, team: Team): TugOfWarGame
  def removePlayer(game: TugOfWarGame, playerId: String): TugOfWarGame
  def disconnectPlayer(game: TugOfWarGame, playerId: String): TugOfWarGame
  def reconnectPlayer(game: TugOfWarGame, playerId: String): Option[TugOfWarGame]
  def canRejoin(game: TugOfWarGame, playerId: String, gracePeriodMs: Long): Boolean
  def cleanupDisconnectedPlayers(game: TugOfWarGame, gracePeriodMs: Long): TugOfWarGame
  def configureGame(game: TugOfWarGame, roundsToWin: Int, timeLimitSeconds: Option[Int]): TugOfWarGame
  def canStart(game: TugOfWarGame): Boolean  // At least 1 player per team
  def startRound(game: TugOfWarGame): TugOfWarGame
  def handleClick(game: TugOfWarGame, playerId: String): TugOfWarGame
  def checkRoundEnd(game: TugOfWarGame): Option[Team]  // Returns winner if round ended
  def endRound(game: TugOfWarGame, winner: Team): TugOfWarGame
  def shouldEndGame(game: TugOfWarGame): Boolean
  def getGameWinner(game: TugOfWarGame): Option[Team]
  def getTeamClicks(game: TugOfWarGame, team: Team): Int
  def getTeamPlayers(game: TugOfWarGame, team: Team): Seq[PlayerState]
```

### WebSocket Messages (`shared/src/main/scala/shared/TugOfWar/GameMessages.scala`)

```scala
package shared.TugOfWar

import upickle.default.*

// Client -> Server messages
sealed trait ClientMessage

object ClientMessage:
  given ReadWriter[ClientMessage] = ReadWriter.merge(...)

case class JoinMessage(playerName: String) extends ClientMessage derives ReadWriter
case class RejoinMessage(playerId: String, gameId: String) extends ClientMessage derives ReadWriter
case class SelectTeamMessage(team: Team) extends ClientMessage derives ReadWriter
case class ConfigureMessage(roundsToWin: Int, timeLimitSeconds: Option[Int]) extends ClientMessage derives ReadWriter
case class StartMessage() extends ClientMessage derives ReadWriter
case class ClickMessage() extends ClientMessage derives ReadWriter  // Simple click, no payload needed
case class NextRoundMessage() extends ClientMessage derives ReadWriter
case class PingMessage() extends ClientMessage derives ReadWriter

// Server -> Client messages
sealed trait ServerMessage

object ServerMessage:
  given ReadWriter[ServerMessage] = ReadWriter.merge(...)

case class GameUpdateMessage(game: TugOfWarGame) extends ServerMessage derives ReadWriter
case class JoinedMessage(playerId: String, gameId: String) extends ServerMessage derives ReadWriter
case class RejoinFailedMessage(reason: String) extends ServerMessage derives ReadWriter
case class PositionUpdateMessage(position: Int) extends ServerMessage derives ReadWriter  // High-frequency updates
case class RoundEndMessage(winner: Team, result: RoundResult) extends ServerMessage derives ReadWriter
case class GameEndMessage(winner: Team, redRoundsWon: Int, blueRoundsWon: Int) extends ServerMessage derives ReadWriter
case class ErrorMessage(message: String) extends ServerMessage derives ReadWriter
```

### Server Handler (`jvm/src/main/scala/server/TugOfWarHandler.scala`)

Follows the pattern established by `ColorRushHandler`:

```scala
package server

import org.slf4j.{Logger, LoggerFactory}
import server.reconnection.ReconnectionSupport
import shared.TugOfWar.{TugOfWarGame, PlayerState}

object TugOfWarHandler extends ReconnectionSupport[PlayerState, TugOfWarGame]:
  // ReconnectionSupport implementation (same pattern as ColorRush)
  
  // WebSocket handling with high-frequency click processing
  def handleWebSocket(gameId: String): cask.WebsocketResult
  
  // Message handlers
  private def handleJoin(channel, gameId, playerName): Unit
  private def handleTeamSelect(channel, gameId, team): Unit
  private def handleClick(channel, gameId): Unit  // Optimized for high throughput
  private def handleStart(gameId): Unit
  private def handleNextRound(gameId): Unit
  
  // Broadcasting (throttled position updates for performance)
  private def broadcastGameState(gameId): Unit
  private def broadcastPositionUpdate(gameId, position): Unit
```

**Performance Consideration**: Click messages will be very frequent (spam clicking). Consider:
- Using `AtomicInteger` for position updates (lock-free)
- Throttling position broadcasts (e.g., every 50ms max)
- Batching click counts before broadcasting

### Client (`js/src/main/scala/client/TugOfWarClient.scala`)

```scala
package client

// Session management
private val TugOfWarSessionKey = "tugOfWar"

def initializeTugOfWar(): Unit
def buildGameUI(): Unit
def connectWebSocket(gameId: String): Unit

// Lobby UI
def createLobbySetup(): HTMLElement      // Join/Create tabs (like ColorRush)
def createWaitingArea(): HTMLElement     // Team selection + player lists
def createTeamPicker(): HTMLElement      // Red/Blue team buttons

// Game UI
def createGameArea(): HTMLElement
def createRopeVisualization(): HTMLElement
def createClickButton(): HTMLElement
def createScoreboard(): HTMLElement

// Animations
def updateRopePosition(position: Int): Unit
def animateTeamClick(team: Team): Unit
def showRoundWinner(team: Team): Unit
def showGameWinner(team: Team): Unit
```

### Routes (`jvm/src/main/scala/server/CascadeRoutes.scala`)

Add new routes:

```scala
// TugOfWar endpoints
@cask.get("/tug-of-war")
def tugOfWarPage(): String = staticHtml("tug-of-war")

@cask.websocket("/ws/tug-of-war/:gameId")
def tugOfWarWebSocket(gameId: String): cask.WebsocketResult =
  TugOfWarHandler.handleWebSocket(gameId)
```

## UI Design

### Lobby Screen

```
┌─────────────────────────────────────────────────────────────┐
│  [Navigation Bar]                                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                     🪢 TUG OF WAR                           │
│            Pull the rope to your side to win!               │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐                        │
│  │  Join Game   │  │ Create Game  │  (tabs)                │
│  └──────────────┘  └──────────────┘                        │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  Game Code: [________]                                │ │
│  │  Your Name: [________]                                │ │
│  │                              [Join Game]              │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Waiting Room (Team Selection)

```
┌─────────────────────────────────────────────────────────────┐
│                    Lobby: ABC123                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Choose Your Team:                                         │
│   ┌─────────────────┐    ┌─────────────────┐               │
│   │   🔴 RED TEAM   │    │   🔵 BLUE TEAM  │               │
│   │   (Pull Left)   │    │   (Pull Right)  │               │
│   └─────────────────┘    └─────────────────┘               │
│                                                             │
├──────────────────────┬──────────────────────────────────────┤
│     RED TEAM 🔴      │      BLUE TEAM 🔵                   │
├──────────────────────┼──────────────────────────────────────┤
│  • Alice ⭐          │  • Bob                               │
│  • Charlie           │  • Diana                             │
│                      │                                      │
│  (2 players)         │  (2 players)                         │
├──────────────────────┴──────────────────────────────────────┤
│                                                             │
│  Rounds to Win: [3 ▼]    Time Limit: [None ▼]              │
│                                                             │
│              [Start Game] (host only)                       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Game Screen

```
┌─────────────────────────────────────────────────────────────┐
│  [Return to Lobby]              Round 1 of 3                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│     RED: 2 rounds              BLUE: 1 round                │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  🔴 GOAL                                          GOAL 🔵  │
│  ┌────┐                                            ┌────┐  │
│  │    │                                            │    │  │
│  │    │                                            │    │  │
│  └────┘                                            └────┘  │
│                                                             │
│  ════════════════════════●════════════════════════════════  │
│                          ▲                                  │
│                       marker                                │
│                    (position: +15)                          │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                     │   │
│  │                   🔵 PULL! 🔵                       │   │
│  │                   (Your Team)                       │   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  Your clicks: 47        Team clicks: 156                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Visual Effects

1. **Rope Animation**
   - Rope stretches/compresses based on position
   - Marker slides smoothly with CSS transitions
   - Goal zones glow when marker approaches

2. **Click Feedback**
   - Button pulses/scales on click
   - Small particle effect or ripple
   - Team-colored flash

3. **Round Win**
   - Winning team's goal zone explodes with confetti
   - Overlay announcement with team color
   - Rope snaps animation

4. **Game Win**
   - Full-screen celebration
   - Trophy or crown animation
   - Statistics summary

## CSS Additions (`base.css`)

```css
/* Tug of War specific styles */
.tow-rope-container {
  position: relative;
  height: 60px;
  margin: var(--spacing-xl) 0;
  background: linear-gradient(to bottom, transparent 45%, rgba(139, 69, 19, 0.3) 45%, rgba(139, 69, 19, 0.3) 55%, transparent 55%);
}

.tow-rope {
  position: absolute;
  top: 50%;
  left: 5%;
  right: 5%;
  height: 8px;
  background: linear-gradient(to bottom, #8B4513, #D2691E, #8B4513);
  border-radius: 4px;
  transform: translateY(-50%);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}

.tow-marker {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 20px;
  height: 40px;
  background: var(--accent-yellow);
  border-radius: 4px;
  box-shadow: 0 0 10px rgba(251, 191, 36, 0.5);
  transition: left 0.1s ease-out;
}

.tow-goal {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 30px;
  height: 80px;
  border-radius: 4px;
  opacity: 0.7;
}

.tow-goal-red {
  left: 0;
  background: linear-gradient(to right, #ef4444, transparent);
  border-left: 4px solid #ef4444;
}

.tow-goal-blue {
  right: 0;
  background: linear-gradient(to left, #3b82f6, transparent);
  border-right: 4px solid #3b82f6;
}

.tow-click-button {
  width: 100%;
  max-width: 400px;
  height: 120px;
  font-size: 2rem;
  font-weight: bold;
  border-radius: var(--radius-lg);
  transition: transform 0.05s, box-shadow 0.05s;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.tow-click-button:active {
  transform: scale(0.98);
}

.tow-click-button.team-red {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  box-shadow: 0 6px 20px rgba(239, 68, 68, 0.4);
}

.tow-click-button.team-blue {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  box-shadow: 0 6px 20px rgba(59, 130, 246, 0.4);
}

.tow-team-selector {
  display: flex;
  gap: var(--spacing-lg);
  justify-content: center;
  margin: var(--spacing-xl) 0;
}

.tow-team-btn {
  padding: var(--spacing-lg) var(--spacing-2xl);
  font-size: 1.25rem;
  font-weight: bold;
  border-radius: var(--radius-md);
  border: 3px solid transparent;
  transition: all 0.2s;
}

.tow-team-btn.team-red {
  background: rgba(239, 68, 68, 0.2);
  border-color: #ef4444;
  color: #ef4444;
}

.tow-team-btn.team-red:hover,
.tow-team-btn.team-red.selected {
  background: #ef4444;
  color: white;
}

.tow-team-btn.team-blue {
  background: rgba(59, 130, 246, 0.2);
  border-color: #3b82f6;
  color: #3b82f6;
}

.tow-team-btn.team-blue:hover,
.tow-team-btn.team-blue.selected {
  background: #3b82f6;
  color: white;
}

.tow-scoreboard {
  display: flex;
  justify-content: space-between;
  padding: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-md);
  margin-bottom: var(--spacing-md);
}

.tow-team-score {
  text-align: center;
  padding: var(--spacing-sm) var(--spacing-lg);
}

.tow-team-score.red { color: #ef4444; }
.tow-team-score.blue { color: #3b82f6; }
```

## Implementation Phases

### Phase 1: Core Game Logic
- [ ] Create `shared/TugOfWar/TugOfWar.scala` with game state management
- [ ] Create `shared/TugOfWar/GameMessages.scala` with WebSocket messages
- [ ] Write unit tests for game logic

### Phase 2: Server Implementation
- [ ] Create `TugOfWarHandler.scala` with WebSocket handling
- [ ] Create `TugOfWarStateManager.scala` for game state
- [ ] Add routes to `CascadeRoutes.scala`
- [ ] Test high-frequency click handling performance

### Phase 3: Client Implementation
- [ ] Create `TugOfWarClient.scala` with UI building
- [ ] Implement lobby and team selection UI
- [ ] Implement game visualization
- [ ] Add click button with visual feedback

### Phase 4: Polish
- [ ] Add rope animation and visual effects
- [ ] Add round/game win celebrations
- [ ] Add sound effects (optional)
- [ ] Mobile optimization (touch support)
- [ ] Performance testing under load

## Performance Considerations

1. **High-Frequency Updates**
   - Clicks can happen 10+ times per second per player
   - Use `AtomicInteger` for lock-free position updates
   - Throttle broadcasts to max 20 updates/second
   - Consider using `PositionUpdateMessage` for lightweight updates

2. **WebSocket Efficiency**
   - `ClickMessage` has no payload (team determined by player session)
   - Position updates are just an integer
   - Full game state only broadcast on significant events

3. **Client Rendering**
   - Use CSS transitions for smooth marker movement
   - Debounce position updates if needed
   - Avoid re-rendering entire game state on position change

## Future Enhancements

- **Power-ups**: Special abilities that appear randomly (2x click power, freeze opponents)
- **Spectator mode**: Watch without participating
- **Tournament mode**: Bracket-style competition
- **Custom themes**: Different rope/background visuals
- **Leaderboard**: Track best players/teams over time
- **Sound effects**: Click sounds, crowd cheering

