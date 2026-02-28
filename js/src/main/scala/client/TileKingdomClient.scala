package client

import org.scalajs.dom.*

import scala.util.Try
import scala.util.chaining.*
import shared.TileKingdom.*
import shared.TileKingdom.AcademyMode.FasterPoliticians
import client.components.laminar.{ActionBar, DevToolsPopup, HelpPopup, NotificationSystem, PoliticianRosterPanel, ResourcePanel, SaveRecoveryModal, SkillTree, TileKingdomState, WelcomeBackModal}
import client.components.laminar.tilekingdom.{FloatingEffects, TileGrid, TileGridState, TileRenderer, TileUtils}
import com.raquo.laminar.api.L.render as laminarRender

def initializeTileKingdom(): Unit =
  TileKingdomClient.init()

object TileKingdomClient:

  private val StorageKey = "tile_kingdom_game_state"
  private val MetadataKey = "tile_kingdom_metadata"
  private val SaveIntervalMs: Int = 30_000 // Save to localStorage at most every 30 seconds
  private var currentGame: TileKingdomGame = TileKingdomLogic.newGame(System.currentTimeMillis())
  private var gameTickerHandle: Option[Int] = None
  private var saveTimerHandle: Option[Int] = None
  private var isDirty: Boolean = false

  // Resource emoji mapping
  private def resourceEmoji(resource: Resource): String = resource match
    case Resource.Wheat => "🌾"
    case Resource.Wood  => "🪵"
    case Resource.Faith => "✨"
    case Resource.Gold  => "💰"
    case Resource.Stone => "🪨"

  // Track progress (0.0 to 1.0) for each producing tile
  private var tileProgress: Map[Coord, Double] = Map.empty

  private val ProductionIntervalMs: Double = TileKingdomLogic.ProductionIntervalSeconds * 1000.0
  private val BureauIntervalMs: Double = TileKingdomLogic.BureauIntervalSeconds * 1000.0

  // Get or create an initial offset for a tile (0.0 to 1.0)
  private def getOrInitProgress(coord: Coord): Double =
    tileProgress.getOrElse(coord, {
      val offset = scala.util.Random.nextDouble()
      tileProgress = tileProgress.updated(coord, offset)
      offset
    })

  // ============================================================================
  // Initialization
  // ============================================================================

  def init(): Unit =
    println("[TileKingdom] Initializing Tile Kingdom game")
    buildUI()
    loadGame()
    TileGridState.centerOnKingdom(currentGame, animated = false)
    TileKingdomState.update(currentGame)
    startGameTicker()
    startSaveTimer()
    registerLifecycleHooks()

  def cleanup(): Unit =
    println("[TileKingdom] Cleaning up Tile Kingdom game")
    stopGameTicker()
    stopSaveTimer()
    saveIfDirty()

  // ============================================================================
  // UI Building
  // ============================================================================

  private def buildUI(): Unit =
    val container = getElementById("tile-kingdom-container").getOrElse:
      document.body.appendChild(div.idx("tile-kingdom-container").cls("tile-kingdom-container"))
      document.getElementById("tile-kingdom-container").asInstanceOf[HTMLElement]

    container.innerHTML = ""

    // Laminar-based grid rendering
    val viewportContainer = div.idx("laminar-tile-grid")
    container.appendChild(viewportContainer)

    // Overlay UI elements
    container.appendChild(buildHeader())
    container.appendChild(div.idx("laminar-resource-panel"))
    container.appendChild(div.idx("laminar-politician-roster-panel"))
    container.appendChild(div.idx("laminar-meta-panel"))
    container.appendChild(buildActions())
    container.appendChild(buildNotification())
    container.appendChild(buildWelcomeBackModal())
    container.appendChild(buildHelpPopup())
    container.appendChild(buildDevToolsPopup())
    container.appendChild(buildSkillTreeModal())
    container.appendChild(buildSaveRecoveryModal())

    // Mount Laminar components AFTER elements are in the DOM
    mountLaminarComponents()

  /** Mount Laminar components into their containers (must be called after DOM is ready) */
  private def mountLaminarComponents(): Unit =
    // Mount Laminar TileGrid
    getElementById("laminar-tile-grid").foreach: container =>
      laminarRender(container, TileGrid(tileRendererActions, showNotification))

    getElementById("laminar-resource-panel").foreach: container =>
      laminarRender(container, ResourcePanel())
    getElementById("laminar-meta-panel").foreach: container =>
      laminarRender(container, ResourcePanel.metaPanel())
    getElementById("laminar-action-bar").foreach: container =>
      laminarRender(container, ActionBar(
        currentGame = () => currentGame,
        onAbdicate = () => handleAbdicate(),
        onSail = () => handleSail(),
        onToggleSkillTree = () => toggleSkillTree(),
        onReset = () => handleResetGame(),
        onToggleDevTools = () => toggleDevTools()
      ))
    getElementById("laminar-politician-roster-panel").foreach: container =>
      laminarRender(container, PoliticianRosterPanel(handleDiscardPolitician))
    getElementById("laminar-notification").foreach: container =>
      laminarRender(container, NotificationSystem())
    getElementById("laminar-skill-tree-modal").foreach: container =>
      val actions = SkillTree.Actions(
        onUnlock = handleUnlockSkill,
        onSwitch = handleSwitchSkill,
        onRefund = handleRefundSkill,
        onClose = () => toggleSkillTree()
      )
      laminarRender(container, SkillTree(actions))
    getElementById("laminar-help-popup").foreach: container =>
      laminarRender(container, HelpPopup(() => toggleHelpPopup()))
    getElementById("laminar-dev-tools-popup").foreach: container =>
      laminarRender(container, DevToolsPopup(() => toggleDevTools(), devToolsActions))
    getElementById("laminar-welcome-modal").foreach: container =>
      laminarRender(container, WelcomeBackModal())
    getElementById("laminar-save-recovery-modal").foreach: container =>
      laminarRender(container, SaveRecoveryModal())

  /** Create TileRenderer.Actions from the existing handlers */
  private def tileRendererActions: TileRenderer.Actions = TileRenderer.Actions(
    onBuildWheatField = handleBuildWheatField,
    onBuildFarm = handleBuildFarm,
    onBuildWoodcutter = handleBuildWoodcutter,
    onBuildQuarry = handleBuildQuarry,
    onBuildBureau = handleBuildBureau,
    onBuildTemple = handleBuildTemple,
    onBuildTownHall = handleBuildTownHall,
    onBuildAcademy = handleBuildAcademy,
    onBuildTavern = handleBuildTavern,
    onLevelUp = handleLevelUp,
    onBulkLevelUp = handleBulkLevelUp,
    onDestroy = handleDestroyBuilding,
    onDestroyTile = handleDestroyTile,
    onSetBureauMode = handleSetBureauMode,
    onSetBureauDirection = handleSetBureauDirection,
    onToggleAcademyMode = handleToggleAcademyMode,
    onAssignPolitician = (coord, id) => handleAssignPolitician(id, coord),
    onRemovePolitician = handleRemovePolitician,
    onSwapPoliticians = handleSwapPoliticians,
    onSetTownHallDirection = handleSetTownHallDirection,
    onUnlockTile = handleUnlockTile
  )

  /** Dev tools actions for the dev popup */
  private def devToolsActions: Seq[DevToolsPopup.DevAction] = Seq(
    DevToolsPopup.DevAction("💰 Gold x10", () => devAction { currentGame = currentGame.copy(gold = math.max(currentGame.gold * 10, 100)) }),
    DevToolsPopup.DevAction("🌾 Wheat +1000", () => devAction { currentGame = currentGame.copy(wheat = currentGame.wheat + 1000) }),
    DevToolsPopup.DevAction("🪵 Wood +1000", () => devAction { currentGame = currentGame.copy(wood = currentGame.wood + 1000) }),
    DevToolsPopup.DevAction("🪨 Stone +1000", () => devAction { currentGame = currentGame.copy(stone = currentGame.stone + 1000) }),
    DevToolsPopup.DevAction("✨ Faith +1000", () => devAction { currentGame = currentGame.copy(faith = currentGame.faith + 1000) }),
    DevToolsPopup.DevAction("🌟 +1 Skill Point", () => devAction { currentGame = currentGame.copy(skillPoints = currentGame.skillPoints + 1, totalSkillPointsEarned = currentGame.totalSkillPointsEarned + 1, hasSailed = true) }),
    DevToolsPopup.DevAction("👤 +Politician", () => devAction {
      val p = TileKingdomLogic.generatePolitician(currentGame, System.currentTimeMillis())
      currentGame = currentGame.copy(politicianRoster = currentGame.politicianRoster :+ p)
    }),
    DevToolsPopup.DevAction("⭐ +Rare Politician", () => devAction {
      val p = TileKingdomLogic.generatePolitician(currentGame, System.currentTimeMillis(), forceRare = true)
      currentGame = currentGame.copy(politicianRoster = currentGame.politicianRoster :+ p)
    }),
    DevToolsPopup.DevAction("🌟 +5 Skill Points", () => devAction { currentGame = currentGame.copy(skillPoints = currentGame.skillPoints + 5, totalSkillPointsEarned = currentGame.totalSkillPointsEarned + 5, hasSailed = true) }),
    DevToolsPopup.DevAction("🪓 Fill Forests", () => devAction {
      val filled = currentGame.currentIsland.unlockedTiles.filter(_.isEmpty).map(_.coord)
      filled.foreach { coord =>
        currentGame = currentGame.updateTileOnCurrentIsland(coord, currentGame.tiles(coord).copy(tileType = TileType.Woodcutter(1)))
      }
    }),
    DevToolsPopup.DevAction("💥 Corrupt Save", () => {
      stopGameTicker()
      stopSaveTimer()
      isDirty = false
      window.localStorage.setItem(StorageKey, "{corrupted}")
      window.location.reload()
    })
  )

  /** Helper for dev actions - updates state and saves */
  private def devAction(transform: => Unit): Unit =
    transform
    TileKingdomState.update(currentGame)
    saveGame()

  private def buildHeader(): HTMLElement =
    div.cls("tile-kingdom-header")(
      h1.content("🏰 Tile Kingdom"),
      button.cls("help-button").content("?").tap: btn =>
        btn.onclick = (_: MouseEvent) => toggleHelpPopup()
    )


  private def buildActions(): HTMLElement =
    div.idx("laminar-action-bar")

  private def buildNotification(): HTMLElement =
    div.idx("laminar-notification")

  private def buildWelcomeBackModal(): HTMLElement =
    div.idx("laminar-welcome-modal")

  private def buildHelpPopup(): HTMLElement =
    div.idx("laminar-help-popup")

  private def buildDevToolsPopup(): HTMLElement =
    div.idx("laminar-dev-tools-popup")

  private def toggleDevTools(): Unit =
    getElementById("tile-kingdom-dev-popup").foreach: popup =>
      popup.classList.toggle("show")

  private def toggleHelpPopup(): Unit =
    getElementById("tile-kingdom-help-popup").foreach: popup =>
      popup.classList.toggle("show")

  private def toggleSkillTree(): Unit =
    getElementById("tile-kingdom-skill-tree-modal").foreach: modal =>
      modal.classList.toggle("show")

  private def buildSkillTreeModal(): HTMLElement =
    div.idx("laminar-skill-tree-modal")

  private def buildSaveRecoveryModal(): HTMLElement =
    div.idx("laminar-save-recovery-modal")

  private def handleUnlockSkill(skill: Skill): Unit =
    TileKingdomLogic.unlockSkill(currentGame, skill) match
      case Right(newGame) =>
        currentGame = newGame
        TileKingdomState.update(currentGame)
        saveGame()
        showNotification(s"Unlocked: ${Skill.description(skill)}")
      case Left(error) =>
        showNotification(error)

  private def handleSwitchSkill(skill: Skill): Unit =
    TileKingdomLogic.switchSkill(currentGame, skill) match
      case Right(newGame) =>
        currentGame = newGame
        TileKingdomState.update(currentGame)
        saveGame()
        showNotification(s"Switched to: ${Skill.description(skill)}")
      case Left(error) =>
        showNotification(error)

  private def handleRefundSkill(skill: Skill): Unit =
    TileKingdomLogic.refundSkill(currentGame, skill) match
      case Right(newGame) =>
        currentGame = newGame
        TileKingdomState.update(currentGame)
        saveGame()
        val cost = Skill.cost(skill)
        val goldCost = cost * TileKingdomLogic.SkillRefundGoldCost
        showNotification(s"Refunded ${Skill.description(skill)} (+${cost}⭐, -${TileUtils.formatNumber(goldCost)} 💰)")
      case Left(error) =>
        showNotification(error)

  // ============================================================================
  // Game Loop
  // ============================================================================

  private def startGameTicker(): Unit =
    stopGameTicker()
    val intervalId = window.setInterval(() => gameTick(), TileKingdomLogic.TickIntervalSeconds * 1000)
    gameTickerHandle = Some(intervalId)

  private def stopGameTicker(): Unit =
    gameTickerHandle.foreach(window.clearInterval)
    gameTickerHandle = None

  /** Process a group of producing tiles, advancing progress and collecting harvests. */
  private def harvestProducingTiles(
      tiles: List[Tile],
      elapsedMs: Double,
      productionFn: Tile => Double,
      emoji: String,
      intervalMultiplier: Double = 1.0
  ): Double =
    var totalHarvested = 0.0
    tiles.foreach: tile =>
      val currentProgress = getOrInitProgress(tile.coord)
      val newProgress = currentProgress + elapsedMs / (ProductionIntervalMs * intervalMultiplier)
      if newProgress >= 1.0 then
        val harvests = newProgress.toInt
        val production = productionFn(tile)
        totalHarvested += production * harvests
        tileProgress = tileProgress.updated(tile.coord, newProgress - harvests)
        showFloatingReward(tile.coord, (production * harvests).toInt, emoji, isSpend = false, offsetIndex = 0)
      else
        tileProgress = tileProgress.updated(tile.coord, newProgress)
    totalHarvested

  private def gameTick(): Unit =
    val currentTime = System.currentTimeMillis()
    val elapsedMs = (currentTime - currentGame.lastTickTime).toDouble

    // Update progress for each producing tile and collect harvests
    val totalWheatHarvested = harvestProducingTiles(
      currentGame.unlockedTiles.filter(_.isWheatField), elapsedMs,
      TileKingdomLogic.productionPerHarvest(currentGame, _), "🌾",
      TileKingdomLogic.agriculture2AIntervalMultiplier(currentGame))
    val totalWoodHarvested = harvestProducingTiles(
      currentGame.unlockedTiles.filter(_.isWoodcutter), elapsedMs,
      TileKingdomLogic.woodProductionPerHarvest(currentGame, _), "🪵")
    val totalFaithHarvested = harvestProducingTiles(
      currentGame.unlockedTiles.filter(_.isTemple), elapsedMs,
      TileKingdomLogic.faithProductionPerHarvest(currentGame, _), "✨")
    val totalStoneHarvested = harvestProducingTiles(
      currentGame.unlockedTiles.filter(_.isQuarry), elapsedMs,
      TileKingdomLogic.stoneProductionPerHarvest(currentGame, _), "🪨")

    // Process bureaus
    val bureaus = currentGame.unlockedTiles.filter(_.isBureau)
    var updatedGame = currentGame.copy(
      wheat = currentGame.wheat + totalWheatHarvested,
      wood = currentGame.wood + totalWoodHarvested,
      faith = currentGame.faith + totalFaithHarvested,
      stone = currentGame.stone + totalStoneHarvested,
      lastTickTime = currentTime
    )

    // Track upgrades to show floating text after render
    var bureauUpgrades: List[(Coord, Int, Coord, Int, Resource, Boolean)] = List.empty

    bureaus.foreach: tile =>
      val currentProgress = getOrInitProgress(tile.coord)
      val isTurbo = TileKingdomLogic.isBureauTurbo(currentGame, tile.coord)
      val nearbyCoords = TileKingdomLogic.bureauAffectedCoords(currentGame, tile.coord)
      val minLevel = nearbyCoords
        .flatMap(c => currentGame.tiles.get(c))
        .filter(_.isUpgradeable)
        .map(_.level)
        .minOption
        .getOrElse(1)
      val minFaithCost = TileKingdomLogic.effectiveBureauFaithCostForLevel(currentGame, minLevel)
      val canAffordTurbo = currentGame.faith >= minFaithCost
      val effectivelyTurbo = isTurbo && canAffordTurbo
      val speedMultiplier = TileKingdomLogic.bureauSpeedMultiplier(currentGame, tile.coord)
      val progressIncrement = elapsedMs / BureauIntervalMs * speedMultiplier
      val newProgress = currentProgress + progressIncrement

      if newProgress >= 1.0 then
        TileKingdomLogic.bureauAutoUpgrade(updatedGame, tile.coord, currentTime) match
          case Some((newGame, upgradedCoord)) if upgradedCoord != tile.coord =>
            updatedGame = newGame
            val upgradedTile = updatedGame.tiles.get(upgradedCoord)
            val previousTile = upgradedTile.map(t => t.copy(tileType = t.tileType match
              case TileType.WheatField(lvl) => TileType.WheatField(lvl - 1)
              case TileType.Farm(lvl)       => TileType.Farm(lvl - 1)
              case TileType.Woodcutter(lvl) => TileType.Woodcutter(lvl - 1)
              case TileType.Temple(lvl)     => TileType.Temple(lvl - 1)
              case TileType.Quarry(lvl)     => TileType.Quarry(lvl - 1)
              case other                    => other
            ))
            val upgradeCostOpt = previousTile.flatMap(t => TileKingdomLogic.effectiveUpgradeCost(currentGame, t))
            val upgradeCost = upgradeCostOpt.map(_.amount).getOrElse(0)
            val costResource = upgradeCostOpt.map(_.resource).getOrElse(Resource.Wheat)
            val newLevel = upgradedTile.map(_.level).getOrElse(1)
            bureauUpgrades = bureauUpgrades :+ (upgradedCoord, newLevel, tile.coord, upgradeCost, costResource, effectivelyTurbo)
            tileProgress = tileProgress.updated(tile.coord, newProgress - 1.0)
          case Some((newGame, _)) =>
            updatedGame = newGame
            tileProgress = tileProgress.updated(tile.coord, 1.0)
          case None =>
            tileProgress = tileProgress.updated(tile.coord, 1.0)
      else
        tileProgress = tileProgress.updated(tile.coord, newProgress)

    currentGame = updatedGame

    // Tick politician lifespans
    val (gameAfterLifespan, destroyedPoliticians) = TileKingdomLogic.tickPoliticianLifespans(currentGame, elapsedMs.toLong)
    currentGame = gameAfterLifespan

    // Generate new politicians if it's time
    val previousRosterSize = currentGame.politicianRoster.size
    currentGame = TileKingdomLogic.generateNewPoliticians(currentGame, currentTime)
    val newPoliticianGenerated = currentGame.politicianRoster.size > previousRosterSize

    // Sync with Laminar reactive state
    TileKingdomState.update(currentGame)
    TileGridState.tileProgress.set(tileProgress)

    markDirty()

    if newPoliticianGenerated then
      showNotification("A new politician has arrived!")

    destroyedPoliticians.foreach: name =>
      showNotification(s"$name has reached the end of their term!")

    // Show projectile and floating text for bureau upgrades
    bureauUpgrades.foreach: (upgradedCoord, newLevel, bureauCoord, cost, costResource, wasTurbo) =>
      val woodCost = TileKingdomLogic.effectiveBureauWoodCost(currentGame)
      if woodCost > 0 then
        showFloatingReward(bureauCoord, woodCost, "🪵", isSpend = true, offsetIndex = 0)
      if wasTurbo then
        val previousLevel = newLevel - 1
        val faithCost = TileKingdomLogic.effectiveBureauFaithCostForLevel(currentGame, previousLevel)
        showFloatingReward(bureauCoord, faithCost, "✨", isSpend = true, offsetIndex = 1)

      showBureauProjectile(bureauCoord, upgradedCoord, () =>
        val costEmoji = resourceEmoji(costResource)
        showFloatingReward(upgradedCoord, cost, costEmoji, isSpend = true, offsetIndex = 0)
        showFloatingLevel(upgradedCoord, newLevel)
      )

  // ============================================================================
  // Persistence
  // ============================================================================

  private def markDirty(): Unit =
    isDirty = true

  private def saveIfDirty(): Unit =
    if isDirty then saveGame()

  private def saveGame(): Unit =
    Try:
      import upickle.default.*
      val json = write(currentGame)
      window.localStorage.setItem(StorageKey, json)
      // Save metadata separately for recovery on failed deserialization
      val metadata = ujson.Obj("totalSkillPointsEarned" -> currentGame.totalSkillPointsEarned)
      window.localStorage.setItem(MetadataKey, ujson.write(metadata))
      isDirty = false
    .recover:
      case ex => println(s"[TileKingdom] Failed to save game: ${ex.getMessage}")

  private def startSaveTimer(): Unit =
    stopSaveTimer()
    val id = window.setInterval(() => saveIfDirty(), SaveIntervalMs)
    saveTimerHandle = Some(id)

  private def stopSaveTimer(): Unit =
    saveTimerHandle.foreach(window.clearInterval)
    saveTimerHandle = None

  private def registerLifecycleHooks(): Unit =
    window.addEventListener("beforeunload", (_: Event) => saveIfDirty())
    document.addEventListener("visibilitychange", (_: Event) =>
      if document.visibilityState == "hidden" then saveIfDirty()
    )

  private def loadGame(): Unit =
    Try:
      Option(window.localStorage.getItem(StorageKey)) match
        case Some(json) =>
          import upickle.default.*
          val loadedGame = read[TileKingdomGame](json)
          val currentTime = System.currentTimeMillis()
          currentGame = TileKingdomLogic.tick(loadedGame, currentTime)
          TileKingdomState.update(currentGame)

          val offlineSeconds = (currentTime - loadedGame.lastTickTime) / 1000.0
          if offlineSeconds > 5 then
            val offlineWheat = (currentGame.wheat - loadedGame.wheat).toInt
            val offlineWood = (currentGame.wood - loadedGame.wood).toInt
            val offlineFaith = (currentGame.faith - loadedGame.faith).toInt
            if offlineWheat > 0 || offlineWood > 0 || offlineFaith > 0 then
              showWelcomeBackModal(offlineWheat, offlineWood, offlineFaith, offlineSeconds)

          println(s"[TileKingdom] Game loaded from localStorage")
        case None =>
          println(s"[TileKingdom] No saved game found, starting new game")
          currentGame = TileKingdomLogic.newGame(System.currentTimeMillis())
          TileKingdomState.update(currentGame)
          saveGame()
    .recover:
      case ex =>
        println(s"[TileKingdom] Failed to load game: ${ex.getMessage}")
        val recoveredSkillPoints = loadMetadataSkillPoints()
        currentGame = TileKingdomLogic.newGame(System.currentTimeMillis()).copy(
          skillPoints = recoveredSkillPoints,
          totalSkillPointsEarned = recoveredSkillPoints,
          hasSailed = recoveredSkillPoints > 0,
          gold = 5000
        )
        TileKingdomState.update(currentGame)
        saveGame()
        SaveRecoveryModal.show(recoveredSkillPoints)

  /** Load totalSkillPointsEarned from the separate metadata key, returning 0 if not found */
  private def loadMetadataSkillPoints(): Int =
    Try:
      Option(window.localStorage.getItem(MetadataKey))
        .map(ujson.read(_))
        .flatMap(json => Try(json("totalSkillPointsEarned").num.toInt).toOption)
        .getOrElse(0)
    .getOrElse(0)

  // ============================================================================
  // Event Handlers
  // ============================================================================

  private def handleBuild(
      coord: Coord,
      buildFn: (TileKingdomGame, Coord) => Either[String, TileKingdomGame],
      cost: Int,
      costEmoji: String
  ): Unit =
    buildFn(currentGame, coord) match
      case Right(newGame) =>
        TileGridState.clearSelection()
        currentGame = newGame
        TileKingdomState.update(currentGame)
        saveGame()
        showFloatingReward(coord, cost, costEmoji, isSpend = true, offsetIndex = 0)
      case Left(error) =>
        showNotification(error)

  private def handleBuildWheatField(coord: Coord): Unit =
    handleBuild(coord, TileKingdomLogic.buildWheatField, TileKingdomLogic.wheatFieldBuildCost, "🌾")

  private def handleBuildFarm(coord: Coord): Unit =
    handleBuild(coord, TileKingdomLogic.buildFarm, TileKingdomLogic.farmBuildCost, "🌾")

  private def handleBuildWoodcutter(coord: Coord): Unit =
    handleBuild(coord, TileKingdomLogic.buildWoodcutter, TileKingdomLogic.woodcutterBuildCost, "🌾")

  private def handleBuildBureau(coord: Coord): Unit =
    handleBuild(coord, TileKingdomLogic.buildBureau, TileKingdomLogic.bureauBuildCost(currentGame), "🪵")

  private def handleBuildTemple(coord: Coord): Unit =
    handleBuild(coord, TileKingdomLogic.buildTemple, TileKingdomLogic.templeBuildCost, "🪵")

  private def handleBuildTownHall(coord: Coord): Unit =
    handleBuild(coord, TileKingdomLogic.buildTownHall(_, _), TileKingdomLogic.townHallBuildCost(currentGame), "🪨")

  private def handleBuildQuarry(coord: Coord): Unit =
    handleBuild(coord, TileKingdomLogic.buildQuarry, TileKingdomLogic.quarryBuildCost, "🪵")

  private def handleBuildAcademy(coord: Coord): Unit =
    handleBuild(coord, TileKingdomLogic.buildAcademy, TileKingdomLogic.academyBuildCost(currentGame), "🪨")

  private def handleBuildTavern(coord: Coord): Unit =
    handleBuild(coord, TileKingdomLogic.buildTavern, TileKingdomLogic.TavernBuildCost, "🪵")

  private def handleToggleAcademyMode(coord: Coord): Unit =
    TileKingdomLogic.toggleAcademyMode(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        TileKingdomState.update(currentGame)
        saveGame()
        val modeText = newGame.tiles.get(coord).map(_.tileType) match
          case Some(TileType.Academy(AcademyMode.FasterPoliticians)) => "Faster Politicians (2x speed)"
          case Some(TileType.Academy(AcademyMode.RareChance)) => "Rare Chance (+10%)"
          case _ => "Unknown"
        showNotification(s"Academy mode: $modeText")
      case Left(error) =>
        showNotification(error)

  private def handleAssignPolitician(politicianId: String, townHallCoord: Coord): Unit =
    val hadPolitician = currentGame.tiles.get(townHallCoord).exists: tile =>
      tile.tileType match
        case TileType.TownHall(pols) if pols.nonEmpty => true
        case _ => false

    val wasFull = currentGame.tiles.get(townHallCoord).exists: tile =>
      tile.tileType match
        case TileType.TownHall(pols) => pols.size >= TileKingdomLogic.townHallCapacity(currentGame)
        case _ => false

    TileKingdomLogic.assignPolitician(currentGame, politicianId, townHallCoord) match
      case Right(newGame) =>
        currentGame = newGame
        TileKingdomState.update(currentGame)
        saveGame()
        showNotification(if wasFull then "Politician swapped!" else "Politician assigned!")
      case Left(error) =>
        showNotification(error)

  private def handleRemovePolitician(townHallCoord: Coord): Unit =
    TileKingdomLogic.removePolitician(currentGame, townHallCoord) match
      case Right(newGame) =>
        currentGame = newGame
        TileKingdomState.update(currentGame)
        saveGame()
        showNotification("Politician returned to roster")
      case Left(error) =>
        showNotification(error)

  private def handleSwapPoliticians(fromCoord: Coord, toCoord: Coord): Unit =
    TileKingdomLogic.swapPoliticians(currentGame, fromCoord, toCoord) match
      case Right(newGame) =>
        currentGame = newGame
        TileKingdomState.update(currentGame)
        saveGame()
        showNotification("Politicians swapped!")
      case Left(error) =>
        showNotification(error)

  private def handleLevelUp(coord: Coord): Unit =
    currentGame.tiles.get(coord).foreach: tile =>
      TileKingdomLogic.effectiveUpgradeCost(currentGame, tile).foreach: cost =>
        TileKingdomLogic.levelUp(currentGame, coord) match
          case Right(newGame) =>
            currentGame = newGame
            TileKingdomState.update(currentGame)
            saveGame()
            showFloatingReward(coord, cost.amount, resourceEmoji(cost.resource), isSpend = true, offsetIndex = 0)
            showFloatingLevel(coord, tile.level + 1)
          case Left(error) =>
            showNotification(error)

  private def handleBulkLevelUp(coord: Coord, count: Int): Unit =
    currentGame.tiles.get(coord).foreach: tile =>
      var game = currentGame
      var totalCost = 0
      var successCount = 0
      var currentLevel = tile.level
      var costResource = TileKingdomLogic.effectiveUpgradeCost(currentGame, tile).map(_.resource).getOrElse(Resource.Wheat)

      (1 to count).foreach: _ =>
        TileKingdomLogic.levelUp(game, coord) match
          case Right(newGame) =>
            game.tiles.get(coord).foreach: t =>
              TileKingdomLogic.effectiveUpgradeCost(game, t).foreach: c =>
                totalCost += c.amount
                costResource = c.resource
            currentLevel += 1
            successCount += 1
            game = newGame
          case Left(_) => ()

      if successCount > 0 then
        currentGame = game
        TileKingdomState.update(currentGame)
        saveGame()
        showFloatingReward(coord, totalCost, resourceEmoji(costResource), isSpend = true, offsetIndex = 0)
        showFloatingLevel(coord, currentLevel)
      else
        showNotification(s"Not enough resources")

  private def handleSetBureauMode(coord: Coord, targetMode: BureauMode): Unit =
    var game = currentGame
    val currentMode = TileKingdomLogic.getBureauMode(game, coord)
    if currentMode != targetMode then
      var attempts = 0
      while TileKingdomLogic.getBureauMode(game, coord) != targetMode && attempts < 3 do
        TileKingdomLogic.cycleBureauMode(game, coord) match
          case Right(g) => game = g
          case Left(_) => ()
        attempts += 1

      currentGame = game
      TileKingdomState.update(currentGame)
      saveGame()
      val modeText = targetMode match
        case BureauMode.Slow => "Slow mode (1x speed)"
        case BureauMode.Turbo => "Turbo mode (10x speed, +✨/upgrade)"
        case BureauMode.Disabled => "Bureau paused"
      showNotification(modeText)

  private def handleSetBureauDirection(coord: Coord, direction: BureauDirection): Unit =
    TileKingdomLogic.setBureauDirection(currentGame, coord, direction) match
      case Right(newGame) =>
        currentGame = newGame
        TileKingdomState.update(currentGame)
        saveGame()
        val dirText = direction match
          case BureauDirection.Center => "Centered (2-tile radius)"
          case BureauDirection.Up => "Directed upward (5×3)"
          case BureauDirection.Down => "Directed downward (5×3)"
          case BureauDirection.Left => "Directed left (3×5)"
          case BureauDirection.Right => "Directed right (3×5)"
        showNotification(dirText)
      case Left(error) =>
        showNotification(error)

  private def handleSetTownHallDirection(coord: Coord, direction: BureauDirection): Unit =
    TileKingdomLogic.setTownHallDirection(currentGame, coord, direction) match
      case Right(newGame) =>
        currentGame = newGame
        TileKingdomState.update(currentGame)
        saveGame()
        val dirText = direction match
          case BureauDirection.Center => "Centered (2-tile radius)"
          case BureauDirection.Up => "Directed upward (5×3)"
          case BureauDirection.Down => "Directed downward (5×3)"
          case BureauDirection.Left => "Directed left (3×5)"
          case BureauDirection.Right => "Directed right (3×5)"
        showNotification(dirText)
      case Left(error) =>
        showNotification(error)

  private def handleDestroyBuilding(coord: Coord): Unit =
    TileKingdomLogic.destroyBuilding(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        tileProgress = tileProgress.removed(coord)
        TileKingdomState.update(currentGame)
        saveGame()
        showNotification("Building destroyed")
      case Left(error) =>
        showNotification(error)

  private def handleDestroyTile(coord: Coord): Unit =
    TileKingdomLogic.destroyTile(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        tileProgress = tileProgress.removed(coord)
        TileKingdomState.update(currentGame)
        saveGame()
        showNotification(s"Tile destroyed! +1 🎫")
      case Left(error) =>
        showNotification(error)

  private def handleDiscardPolitician(politicianId: String): Unit =
    val politician = currentGame.politicianRoster.find(_.id == politicianId)
    currentGame = TileKingdomLogic.discardPolitician(currentGame, politicianId)
    TileKingdomState.update(currentGame)
    saveGame()
    politician.foreach(p => showNotification(s"${p.emoji} ${p.name} dismissed"))

  private def handleAbdicate(): Unit =
    if currentGame.allTilesFilled then
      val reward = currentGame.abdicationGoldReward
      if window.confirm(s"Abdicate and earn $reward gold? This will reset all your buildings.") then
        TileKingdomLogic.abdicate(currentGame, System.currentTimeMillis()) match
          case Right(newGame) =>
            currentGame = newGame
            tileProgress = Map.empty
            TileKingdomState.update(currentGame)
            saveGame()
            showNotification(s"Abdicated! +$reward gold")
          case Left(error) =>
            showNotification(error)

  private def handleSail(): Unit =
    if currentGame.canSail then
      val legacyReward = currentGame.sailLegacyReward
      val totalLegacy = currentGame.legacyPoints + legacyReward
      val skillPointsEarned = totalLegacy / TileKingdomLogic.LegacyPointsPerSkillPoint
      val skillMsg = if skillPointsEarned > 0 then s" (+$skillPointsEarned ⭐)" else ""
      if window.confirm(s"Sail away and earn $legacyReward legacy points?$skillMsg\n\nThis will reset ALL progress including gold and tiles!") then
        TileKingdomLogic.sail(currentGame, System.currentTimeMillis()) match
          case Right(newGame) =>
            currentGame = newGame
            tileProgress = Map.empty
            TileKingdomState.update(currentGame)
            saveGame()
            TileGridState.centerOnKingdom(currentGame, animated = false)
            val notification = if skillPointsEarned > 0 then s"Sailed! +$legacyReward 🏅, +$skillPointsEarned ⭐" else s"Sailed! +$legacyReward 🏅"
            showNotification(notification)
          case Left(error) =>
            showNotification(error)

  private def handleUnlockTile(coord: Coord): Unit =
    val useTilePoint = currentGame.tilePoints > 0
    val goldCost = currentGame.nextTileUnlockCost
    TileKingdomLogic.unlockTile(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        TileKingdomState.update(currentGame)
        saveGame()
        if useTilePoint then
          showFloatingReward(coord, 1, "🎫", isSpend = true, offsetIndex = 0)
        else
          showFloatingReward(coord, goldCost, "💰", isSpend = true, offsetIndex = 0)
      case Left(error) =>
        showNotification(error)

  private def handleResetGame(): Unit =
    if window.confirm("Reset game? This will delete all progress!") then
      window.localStorage.removeItem(StorageKey)
      window.localStorage.removeItem(MetadataKey)
      isDirty = false
      currentGame = TileKingdomLogic.newGame(System.currentTimeMillis())
      tileProgress = Map.empty
      TileKingdomState.update(currentGame)
      saveGame()
      TileGridState.centerOnKingdom(currentGame, animated = false)
      showNotification("Game reset!")

  // ============================================================================
  // Utilities
  // ============================================================================

  private def showNotification(message: String): Unit =
    NotificationSystem.show(message, 2000)

  private def showWelcomeBackModal(wheatGain: Int, woodGain: Int, faithGain: Int, offlineSeconds: Double): Unit =
    WelcomeBackModal.show(wheatGain, woodGain, faithGain, offlineSeconds)

  private def showFloatingReward(coord: Coord, amount: Int, emoji: String, isSpend: Boolean, offsetIndex: Int): Unit =
    getElementById("tile-kingdom-grid").foreach: grid =>
      FloatingEffects.showFloatingReward(grid, coord, amount, emoji, isSpend, offsetIndex)

  private def showFloatingLevel(coord: Coord, level: Int): Unit =
    getElementById("tile-kingdom-grid").foreach: grid =>
      FloatingEffects.showFloatingLevel(grid, coord, level)

  private def showBureauProjectile(fromCoord: Coord, toCoord: Coord, onComplete: () => Unit): Unit =
    getElementById("tile-kingdom-grid").foreach: grid =>
      FloatingEffects.showBureauProjectile(grid, fromCoord, toCoord, onComplete)
