package shared.DrawingGame

import upickle.default.ReadWriter

// AI Drawing Challenge - Multiplayer drawing game with AI judging

// Proper state machine for the game phases
enum LobbyStatus derives ReadWriter:
  case Waiting           // Waiting for players to join, host can start game
  case Drawing           // Players are drawing, timer is running
  case CollectingDrawings // Brief pause to collect any last-second submissions
  case RevealingDrawings // Showing all drawings (without captions)
  case RevealingCaptions // AI is captioning and revealing captions one by one
  case RevealingAIWinner // AI announces its pick
  case Voting            // Players vote for their favorite, timer is running
  case Results           // Round complete, showing results

case class DrawingLobby(
    lobbyId: String,
    hostId: String,
    players: Map[String, PlayerInfo],
    currentPrompt: Option[String],
    drawings: Map[String, DrawingSubmission],
    votes: Map[String, String], // voterId -> playerIdVotedFor
    status: LobbyStatus,
    currentRound: Int,
    maxRounds: Int,
    timerStartTime: Option[Long]
) derives ReadWriter

case class PlayerInfo(
    playerId: String,
    playerName: String,
    connected: Boolean,
    score: Int
) derives ReadWriter

case class DrawingSubmission(
    playerName: String,
    imageData: String, // base64 PNG
    caption: Option[String]
) derives ReadWriter

case class RoundResult(
    aiWinner: Option[String], // player name
    playerWinner: Option[String], // player name
    votes: Map[String, Int] // playerName -> voteCount
) derives ReadWriter

// Client -> Server messages
enum ClientMessage derives ReadWriter:
  case CreateLobby(playerName: String, apiKey: String)
  case JoinLobby(lobbyId: String, playerName: String)
  case StartGame()
  case SubmitDrawing(imageData: String)
  case SubmitVote(playerNameVotedFor: String)
  case NextRound()

// Server -> Client messages
enum ServerMessage derives ReadWriter:
  case LobbyCreated(lobbyId: String, playerId: String)
  case LobbyUpdate(lobby: DrawingLobby)
  case PromptAnnounced(prompt: String)
  case DrawingTimerUpdate(secondsRemaining: Int)
  case VotingTimerUpdate(secondsRemaining: Int)
  case DrawingSubmitted(playerName: String)
  // Phase 1: Reveal all drawings (without captions)
  case DrawingsRevealed(drawings: Seq[DrawingSubmission], prompt: String)
  // Phase 2: Reveal caption for a specific player
  case CaptionRevealed(playerName: String, caption: String)
  // Phase 3: AI announces its vote
  case AIVoteRevealed(winnerName: String, reasoning: String)
  // Phase 4: Voting phase starts (with timer)
  case VotingStarted(secondsRemaining: Int)
  case VoteUpdate(votes: Map[String, Int]) // playerName -> voteCount
  // Final: Round complete with all results
  case RoundComplete(result: RoundResult)
  case ErrorMessage(message: String)

object DrawingGame:
  val drawingTimeSeconds = 60
  val votingTimeSeconds = 10
  val maxPlayersPerLobby = 8
  
  // Static list of prompts for cost control
  private val prompts: Vector[String] = Vector(
    "cat", "house", "tree", "car", "flower",
    "sun", "moon", "star", "cloud", "rainbow",
    "butterfly", "fish", "bird", "dog", "elephant",
    "mountain", "ocean", "apple", "pizza", "cup",
    "chair", "book", "key", "heart", "smile",
    "clock", "guitar", "rocket", "umbrella", "hat",
    "glasses", "shoe", "boat", "castle", "dragon",
    "robot", "snowman", "cupcake", "balloon", "bicycle",
    "camera", "diamond", "fire", "ghost", "ice cream",
    "lightning", "mushroom", "pencil", "scissors", "trophy"
  )

  def createLobby(lobbyId: String, hostId: String, hostName: String, maxRounds: Int = 5): DrawingLobby =
    val host = PlayerInfo(hostId, hostName, connected = true, score = 0)
    DrawingLobby(
      lobbyId = lobbyId,
      hostId = hostId,
      players = Map(hostId -> host),
      currentPrompt = None,
      drawings = Map.empty,
      votes = Map.empty,
      status = LobbyStatus.Waiting,
      currentRound = 0,
      maxRounds = maxRounds,
      timerStartTime = None
    )

  def addPlayer(lobby: DrawingLobby, playerId: String, playerName: String): DrawingLobby =
    if lobby.players.size >= maxPlayersPerLobby then
      lobby
    else
      val player = PlayerInfo(playerId, playerName, connected = true, score = 0)
      lobby.copy(players = lobby.players + (playerId -> player))

  def removePlayer(lobby: DrawingLobby, playerId: String): DrawingLobby =
    lobby.copy(players = lobby.players - playerId)

  def startDrawingPhase(lobby: DrawingLobby): DrawingLobby =
    import scala.util.Random
    val prompt = prompts(Random.nextInt(prompts.length))
    lobby.copy(
      currentPrompt = Some(prompt),
      status = LobbyStatus.Drawing,
      currentRound = lobby.currentRound + 1,
      drawings = Map.empty,
      votes = Map.empty,
      timerStartTime = Some(System.currentTimeMillis())
    )

  def submitDrawing(lobby: DrawingLobby, playerId: String, imageData: String): DrawingLobby =
    lobby.players.get(playerId) match
      case Some(player) =>
        val drawing = DrawingSubmission(player.playerName, imageData, None)
        lobby.copy(drawings = lobby.drawings + (playerId -> drawing))
      case None =>
        lobby

  def allDrawingsSubmitted(lobby: DrawingLobby): Boolean =
    lobby.drawings.size == lobby.players.size

  def addCaption(lobby: DrawingLobby, playerId: String, caption: String): DrawingLobby =
    lobby.drawings.get(playerId) match
      case Some(drawing) =>
        val updatedDrawing = drawing.copy(caption = Some(caption))
        lobby.copy(drawings = lobby.drawings + (playerId -> updatedDrawing))
      case None =>
        lobby

  def allCaptionsReady(lobby: DrawingLobby): Boolean =
    lobby.drawings.values.forall(_.caption.isDefined)

  def startVoting(lobby: DrawingLobby): DrawingLobby =
    lobby.copy(status = LobbyStatus.Voting)

  def submitVote(lobby: DrawingLobby, voterId: String, playerNameVotedFor: String): DrawingLobby =
    // Prevent self-voting
    val voterName = lobby.players.get(voterId).map(_.playerName)
    if voterName.contains(playerNameVotedFor) then
      lobby
    else
      lobby.copy(votes = lobby.votes + (voterId -> playerNameVotedFor))

  def allVotesSubmitted(lobby: DrawingLobby): Boolean =
    lobby.votes.size == lobby.players.size

  def tallyVotes(lobby: DrawingLobby): Map[String, Int] =
    lobby.votes.values.groupBy(identity).view.mapValues(_.size).toMap

  def shouldEndGame(lobby: DrawingLobby): Boolean =
    lobby.currentRound >= lobby.maxRounds

  def updatePlayerScores(lobby: DrawingLobby, aiWinner: Option[String], playerWinner: Option[String]): DrawingLobby =
    var updatedPlayers = lobby.players

    // Award points for AI winner
    aiWinner.foreach: winnerName =>
      updatedPlayers = updatedPlayers.map:
        case (id, player) if player.playerName == winnerName => (id, player.copy(score = player.score + 100))
        case other => other

    // Award points for player-voted winner
    playerWinner.foreach: winnerName =>
      updatedPlayers = updatedPlayers.map:
        case (id, player) if player.playerName == winnerName => (id, player.copy(score = player.score + 50))
        case other => other

    lobby.copy(players = updatedPlayers)

  def resetForNextRound(lobby: DrawingLobby): DrawingLobby =
    lobby.copy(
      currentPrompt = None,
      drawings = Map.empty,
      votes = Map.empty,
      status = LobbyStatus.Waiting,
      timerStartTime = None
    )

