# AI Drawing Challenge - Design Document

## Overview
A multiplayer drawing game where players draw a prompt, AI captions their drawings, and the best match wins through both AI judgment and player voting.

## Game Flow

### 1. Lobby Creation
- Host creates a lobby and provides their OpenAI API key
- API key stored in server memory (associated with lobby ID)
- Host receives a lobby code to share with other players

### 2. Player Joining
- Players join using the lobby code
- Lobby shows connected players in real-time via WebSocket
- Host can start the game when ready

### 3. Drawing Phase (60 seconds)
- Server broadcasts a word prompt to all players
- Each player draws on an HTML5 canvas
- Timer displayed and synchronized via WebSocket
- On completion, canvas converted to base64 PNG and sent to server

### 4. AI Caption Phase
- Server sends each drawing to OpenAI Vision API
- Prompt: "Describe this drawing in 5 words or less"
- Captions attached to respective drawings
- All captioned drawings broadcast to all players

### 5. AI Judgment
- Server sends original prompt + all captions to OpenAI Chat API
- Prompt: "Which caption best matches '{original_prompt}'? Return only the player name."
- AI winner announced

### 6. Player Voting
- Players vote on most accurate drawing (cannot vote for themselves)
- Votes tallied in real-time
- Player-chosen winner announced

### 7. Results
- Display both AI winner and player-voted winner
- Show all drawings with captions
- Option to play another round

## Technical Architecture

### Backend (Scala/Cask)

**WebSocket Messages:**
```scala
sealed trait GameMessage
case class PlayerJoined(name: String, playerCount: Int) extends GameMessage
case class GameStarted(prompt: String) extends GameMessage
case class TimerUpdate(secondsLeft: Int) extends GameMessage
case class DrawingSubmitted(playerName: String) extends GameMessage
case class AllDrawingsReady(drawings: Seq[Drawing]) extends GameMessage
case class AIWinner(playerName: String, reasoning: String) extends GameMessage
case class VoteUpdate(votes: Map[String, Int]) extends GameMessage
case class PlayerWinner(playerName: String, voteCount: Int) extends GameMessage
```

**Data Models:**
```scala
case class Lobby(
  id: String,
  apiKey: String,
  players: Map[String, Player],
  currentPrompt: Option[String],
  drawings: Map[String, Drawing],
  votes: Map[String, String] // voter -> voted-for
)

case class Player(name: String, connected: Boolean)

case class Drawing(
  playerName: String,
  imageData: String, // base64 PNG
  caption: Option[String]
)
```

**API Endpoints:**
- `POST /api/drawing/lobby/create` - Create lobby with API key
- `POST /api/drawing/lobby/join` - Join lobby with code
- `POST /api/drawing/submit` - Submit drawing
- `POST /api/drawing/vote` - Submit vote
- `WS /ws/drawing/:lobbyId` - Game WebSocket

**OpenAI Integration:**
```scala
object OpenAIClient:
  def captionImage(apiKey: String, imageBase64: String): Future[String]
  def selectWinner(apiKey: String, prompt: String, captions: Map[String, String]): Future[String]
```

### Frontend (ScalaJS)

**Canvas Drawing:**
- HTML5 Canvas with mouse/touch support
- Color picker (black, red, blue, green)
- Brush size selector
- Clear button
- Submit button (enabled after 5 seconds to prevent blank submissions)

**UI Phases:**
- Lobby waiting room
- Drawing canvas with timer
- Gallery view (all drawings with captions)
- Results screen

**Client Structure (similar to CounterClient.scala):**
```scala
def initializeDrawing(): Unit
def buildDrawingUI(): Unit
def connectWebSocket(): Unit
var drawingWebSocket: Option[WebSocket] = None
```

## Security Considerations

### 1. API Key Handling
- Transmitted via HTTPS only in production
- Stored in server memory only (not persisted to disk/database)
- Deleted when lobby closes or times out
- Rate limiting on API calls per lobby
- Never sent to other clients

### 2. Input Validation
- Max drawing size: 512x512 pixels
- Max base64 size: 1MB
- Player name sanitization (alphanumeric + spaces, max 20 chars)
- Lobby code format validation (6-digit alphanumeric)
- Max 8 players per lobby

### 3. Cost Protection
- Max 8 players per lobby
- Max 5 rounds per lobby
- Timeout lobbies after 30 minutes of inactivity
- Request timeout for OpenAI API calls
- Clear warning to host about API costs

### 4. Abuse Prevention
- Rate limit lobby creation (1 per IP per minute)
- Rate limit drawing submissions
- Validate image data is actually PNG format
- Sanitize all text inputs before sending to OpenAI

## Implementation Phases

### Phase 1: Core Infrastructure
- [ ] Lobby management system
- [ ] WebSocket connection with lobby support
- [ ] Basic UI with navigation (following Counter pattern)
- [ ] Player join/leave handling

### Phase 2: Drawing
- [ ] HTML5 Canvas implementation
- [ ] Drawing tools (pen, colors, clear)
- [ ] Canvas to base64 conversion
- [ ] Submit drawing to server

### Phase 3: OpenAI Integration
- [ ] OpenAI client wrapper
- [ ] Image caption endpoint
- [ ] Winner selection logic
- [ ] Error handling for API failures

### Phase 4: Game Flow
- [ ] Timer synchronization
- [ ] Drawing submission phase
- [ ] Caption display phase
- [ ] Voting system
- [ ] Results display

### Phase 5: Polish
- [ ] Improved UI/styling
- [ ] Loading states
- [ ] Error messages
- [ ] Reconnection handling

## Open Questions

1. **Prompt Generation:** Static list vs. OpenAI-generated prompts?
   - **Decision:** Start with static list of 50+ prompts for cost control

2. **Image Quality:** Send full resolution or compress for API costs?
   - **Decision:** 512x512 max, reasonable quality

3. **Tie Handling:** What if multiple players get same votes?
   - **Decision:** Declare multiple winners

4. **Disconnect Recovery:** Should drawings be saved if player disconnects?
   - **Decision:** Phase 1: No recovery. Phase 2: Maybe add

5. **AI Model Selection:** GPT-4 Vision vs GPT-4o vs GPT-4o-mini?
   - **Decision:** Test with GPT-4o for vision, GPT-4o-mini for winner selection

## Future Enhancements

- Gallery of past games (without API keys)
- Difficulty levels (easy/medium/hard prompts)
- Teams mode
- Custom prompt lists
- Drawing replay/time-lapse
- Spectator mode
- Mobile-friendly touch drawing
- Undo/redo for drawings
- More drawing tools (eraser, fill, shapes)

## Estimated Costs

Per round with 4 players using GPT-4o:
- 4 image captions: ~$0.02-0.04 (GPT-4o Vision)
- 1 winner selection: ~$0.001 (GPT-4o-mini text)
- **Total per round: ~$0.021-0.041**

5 rounds with 4 players: ~$0.10-0.20

**Host is responsible for OpenAI costs via their API key.**

## Technology Stack

- **Backend:** Scala 3, Cask framework
- **Frontend:** Scala.js, HTML5 Canvas
- **Communication:** WebSockets
- **AI:** OpenAI API (GPT-4o Vision, GPT-4o-mini)
- **Testing:** ScalaTest with BDD style

## Notes

- Follow existing Counter app pattern for consistency
- Use Scala 3 syntax (braceless, no `_` wildcards)
- WebSocket URL pattern: `ws://host/ws/drawing/:lobbyId`
- API URL pattern: `/api/drawing/*`
- Keep lobby state in memory (no database for MVP)

