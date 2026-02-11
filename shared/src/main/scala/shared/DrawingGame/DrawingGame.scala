package shared.DrawingGame

import upickle.default.ReadWriter
import shared.session.{PlayerConnection, PlayerConnectionOps}

// AI Drawing Challenge - Multiplayer drawing game with AI judging

// Game mode determines how prompts are generated
enum GameMode derives ReadWriter:
  case SingleWord      // Simple single-word prompts from static list
  case TwoWordScene    // AI generates creative prompts from 2 random words

// Caption style determines how AI describes the drawings
enum CaptionStyle derives ReadWriter:
  case Descriptive     // Default: describe what's drawn and comment on skill
  case Roast           // Roast the art style humorously

// Proper state machine for the game phases
enum LobbyStatus derives ReadWriter:
  case Waiting           // Waiting for players to join, host can start game
  case GeneratingPrompt  // AI is generating prompt (advanced mode only)
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
    timerStartTime: Option[Long],
    gameMode: GameMode = GameMode.SingleWord,
    captionStyle: CaptionStyle = CaptionStyle.Descriptive
) derives ReadWriter

case class PlayerInfo(
    playerId: String,
    playerName: String,
    connected: Boolean,
    score: Int,
    disconnectedAt: Option[Long] = None
) extends PlayerConnection derives ReadWriter

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
  case CreateLobby(playerName: String, apiKey: String, gameMode: GameMode = GameMode.SingleWord, captionStyle: CaptionStyle = CaptionStyle.Descriptive)
  case JoinLobby(lobbyId: String, playerName: String)
  case RejoinLobby(lobbyId: String, playerId: String)
  case StartGame()
  case SubmitDrawing(imageData: String)
  case SubmitVote(playerNameVotedFor: String)
  case NextRound()
  case Ping() // Keepalive ping to prevent idle disconnects

// Server -> Client messages
enum ServerMessage derives ReadWriter:
  case LobbyCreated(lobbyId: String, playerId: String)
  case LobbyUpdate(lobby: DrawingLobby)
  case RejoinFailed(reason: String)
  case GeneratingPrompt() // Show loading state while AI generates prompt
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
    // Animals
    "cat", "dog", "bird", "fish", "elephant", "lion", "tiger", "bear", "monkey", "giraffe",
    "zebra", "penguin", "dolphin", "whale", "shark", "octopus", "crab", "lobster", "snail", "butterfly",
    "bee", "spider", "ant", "ladybug", "frog", "turtle", "snake", "crocodile", "dinosaur", "dragon",
    "unicorn", "horse", "cow", "pig", "sheep", "chicken", "duck", "owl", "eagle", "parrot",
    "flamingo", "peacock", "bat", "rabbit", "squirrel", "deer", "wolf", "fox", "kangaroo", "koala",
    "panda", "gorilla", "hippo", "rhino", "camel", "moose", "beaver", "otter", "seal", "jellyfish",
    
    // Food & Drinks
    "apple", "banana", "orange", "pizza", "burger", "hotdog", "taco", "sushi", "cake", "cupcake",
    "donut", "cookie", "ice cream", "candy", "chocolate", "popcorn", "french fries", "sandwich", "salad", "soup",
    "bread", "cheese", "egg", "bacon", "steak", "chicken leg", "watermelon", "strawberry", "grapes", "pineapple",
    "lemon", "cherry", "avocado", "carrot", "broccoli", "corn", "mushroom", "onion", "tomato", "potato",
    "coffee", "tea", "juice", "milkshake", "wine", "beer", "soda", "water bottle", "coconut", "pretzel",
    
    // Objects & Things
    "house", "car", "bicycle", "motorcycle", "airplane", "helicopter", "rocket", "boat", "ship", "submarine",
    "train", "bus", "truck", "tractor", "ambulance", "fire truck", "police car", "taxi", "skateboard", "scooter",
    "chair", "table", "bed", "couch", "lamp", "television", "computer", "phone", "camera", "clock",
    "book", "pencil", "pen", "scissors", "ruler", "backpack", "briefcase", "wallet", "purse", "key",
    "door", "window", "stairs", "ladder", "bridge", "fence", "mailbox", "trash can", "toilet", "bathtub",
    "umbrella", "hat", "glasses", "sunglasses", "watch", "ring", "necklace", "crown", "helmet", "mask",
    "shoe", "boot", "sock", "glove", "scarf", "tie", "dress", "pants", "shirt", "jacket",
    "guitar", "piano", "drum", "violin", "trumpet", "microphone", "headphones", "speaker", "radio", "record",
    "ball", "balloon", "kite", "yo-yo", "dice", "puzzle", "robot", "teddy bear", "doll", "lego",
    "candle", "lighter", "matches", "flashlight", "battery", "magnet", "compass", "telescope", "microscope", "binoculars",
    
    // Nature & Weather
    "tree", "flower", "grass", "leaf", "bush", "cactus", "palm tree", "pine tree", "bamboo", "vine",
    "sun", "moon", "star", "cloud", "rainbow", "lightning", "tornado", "volcano", "earthquake", "tsunami",
    "mountain", "hill", "valley", "cave", "island", "beach", "desert", "forest", "jungle", "swamp",
    "river", "lake", "ocean", "waterfall", "pond", "wave", "rain", "snow", "ice", "fire",
    "rock", "sand", "mud", "crystal", "gem", "diamond", "gold", "silver", "pearl", "fossil",
    
    // Places & Buildings
    "castle", "palace", "tower", "pyramid", "temple", "church", "mosque", "lighthouse", "windmill", "barn",
    "hospital", "school", "library", "museum", "theater", "stadium", "prison", "factory", "skyscraper", "igloo",
    "tent", "treehouse", "cabin", "cottage", "mansion", "hut", "garage", "shed", "greenhouse", "playground",
    
    // People & Body Parts
    "baby", "ninja", "pirate", "wizard", "witch", "knight", "king", "queen", "princess", "prince",
    "clown", "astronaut", "doctor", "chef", "firefighter", "police officer", "teacher", "farmer", "scientist", "artist",
    "eye", "ear", "nose", "mouth", "tongue", "tooth", "hand", "foot", "heart", "brain",
    "skeleton", "skull", "bone", "muscle", "beard", "mustache", "hair", "smile", "frown", "wink",
    
    // Sports & Activities
    "soccer ball", "basketball", "football", "baseball", "tennis racket", "golf club", "hockey stick", "bowling pin", "dart", "archery",
    "swimming", "surfing", "skiing", "snowboarding", "skateboarding", "fishing", "camping", "hiking", "climbing", "dancing",
    "yoga", "karate", "boxing", "wrestling", "fencing", "horseback riding", "cycling", "running", "jumping", "diving",
    
    // Fantasy & Mythology
    "angel", "devil", "ghost", "zombie", "vampire", "werewolf", "mermaid", "fairy", "elf", "dwarf",
    "giant", "troll", "goblin", "ogre", "phoenix", "griffin", "centaur", "minotaur", "cyclops", "medusa",
    "magic wand", "crystal ball", "treasure chest", "magic carpet", "potion", "spell book", "cauldron", "sword", "shield", "bow and arrow",
    
    // Space & Science
    "planet", "saturn", "mars", "earth", "meteor", "comet", "asteroid", "black hole", "galaxy", "constellation",
    "alien", "ufo", "spaceship", "satellite", "space station", "astronaut", "moon rover", "telescope", "atom", "dna",
    
    // Emotions & Concepts
    "love", "peace", "music", "dream", "idea", "time", "money", "luck", "hope", "fear",
    
    // Misc Fun
    "trophy", "medal", "present", "birthday cake", "christmas tree", "snowman", "jack-o-lantern", "easter egg", "fireworks", "confetti",
    "anchor", "compass", "map", "treasure", "pirate ship", "cannon", "flag", "banner", "sign", "arrow"
  )

  def getRandomPrompt(): String =
    import scala.util.Random
    prompts(Random.nextInt(prompts.length))

  def createLobby(lobbyId: String, hostId: String, hostName: String, maxRounds: Int = 5, gameMode: GameMode = GameMode.SingleWord, captionStyle: CaptionStyle = CaptionStyle.Descriptive): DrawingLobby =
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
      timerStartTime = None,
      gameMode = gameMode,
      captionStyle = captionStyle
    )

  def addPlayer(lobby: DrawingLobby, playerId: String, playerName: String): DrawingLobby =
    if lobby.players.size >= maxPlayersPerLobby then
      lobby
    else
      val player = PlayerInfo(playerId, playerName, connected = true, score = 0, disconnectedAt = None)
      lobby.copy(players = lobby.players + (playerId -> player))

  def removePlayer(lobby: DrawingLobby, playerId: String): DrawingLobby =
    lobby.copy(players = lobby.players - playerId)

  /** Mark a player as disconnected instead of removing them */
  def disconnectPlayer(lobby: DrawingLobby, playerId: String): DrawingLobby =
    lobby.players.get(playerId) match
      case Some(player) =>
        val disconnectedPlayer = player.copy(connected = false, disconnectedAt = Some(System.currentTimeMillis()))
        lobby.copy(players = lobby.players + (playerId -> disconnectedPlayer))
      case None => lobby

  /** Reconnect a previously disconnected player */
  def reconnectPlayer(lobby: DrawingLobby, playerId: String): Option[DrawingLobby] =
    lobby.players.get(playerId).map: player =>
      val reconnectedPlayer = player.copy(connected = true, disconnectedAt = None)
      lobby.copy(players = lobby.players + (playerId -> reconnectedPlayer))

  /** Check if a player can rejoin (exists and within grace period) */
  def canRejoin(lobby: DrawingLobby, playerId: String, gracePeriodMs: Long = PlayerConnectionOps.DefaultGracePeriodMs): Boolean =
    lobby.players.get(playerId).exists(PlayerConnectionOps.canRejoin(_, gracePeriodMs))

  /** Remove players who have been disconnected longer than the grace period */
  def cleanupDisconnectedPlayers(lobby: DrawingLobby, gracePeriodMs: Long = PlayerConnectionOps.DefaultGracePeriodMs): DrawingLobby =
    val activePlayers = lobby.players.filterNot:
      case (_, player) => PlayerConnectionOps.isGracePeriodExpired(player, gracePeriodMs)
    lobby.copy(players = activePlayers)

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

