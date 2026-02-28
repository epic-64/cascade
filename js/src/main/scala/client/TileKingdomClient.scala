package client

import org.scalajs.dom.*

import scala.util.Try
import scala.util.chaining.*
import shared.TileKingdom.*
import shared.TileKingdom.AcademyMode.FasterPoliticians
import client.components.laminar.{ActionBar, DevToolsPopup, HelpPopup, NotificationSystem, PoliticianRosterPanel, ResourcePanel, SaveRecoveryModal, SkillTree, TileKingdomState, WelcomeBackModal}
import client.components.laminar.tilekingdom.{FloatingEffects, IslandNavigator, TileGrid, TileGridState, TileRenderer, TileUtils}
import client.components.laminar.mobile
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
  // Key is (islandIndex, coord) to distinguish tiles on different islands
  private var tileProgress: Map[(Int, Coord), Double] = Map.empty

  private val ProductionIntervalMs: Double = TileKingdomLogic.ProductionIntervalSeconds * 1000.0
  private val BureauIntervalMs: Double = TileKingdomLogic.BureauIntervalSeconds * 1000.0

  // Get or create an initial offset for a tile (0.0 to 1.0)
  private def getOrInitProgress(islandIndex: Int, coord: Coord): Double =
    val key = (islandIndex, coord)
    tileProgress.getOrElse(key, {
      val offset = scala.util.Random.nextDouble()
      tileProgress = tileProgress.updated(key, offset)
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
    setupKeyboardNavigation()

  def cleanup(): Unit =
    println("[TileKingdom] Cleaning up Tile Kingdom game")
    stopGameTicker()
    stopSaveTimer()
    saveIfDirty()

  private def setupKeyboardNavigation(): Unit =
    document.addEventListener("keydown", (event: KeyboardEvent) =>
      event.key match
        case "ArrowLeft" =>
          event.preventDefault()
          handlePreviousIsland()
        case "ArrowRight" =>
          event.preventDefault()
          handleNextIsland()
        case _ => ()
    )

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
    container.appendChild(div.idx("laminar-island-navigator"))
    container.appendChild(buildActions())
    container.appendChild(buildNotification())
    container.appendChild(buildWelcomeBackModal())
    container.appendChild(buildHelpPopup())
    container.appendChild(buildDevToolsPopup())
    container.appendChild(buildSkillTreeModal())
    container.appendChild(buildSaveRecoveryModal())
    
    // Mobile UI containers
    container.appendChild(div.idx("laminar-mobile-top-bar"))
    container.appendChild(div.idx("laminar-mobile-action-bar"))
    container.appendChild(div.idx("laminar-mobile-menu"))

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
    getElementById("laminar-island-navigator").foreach: container =>
      val actions = IslandNavigator.Actions(
        onPreviousIsland = () => handlePreviousIsland(),
        onNextIsland = () => handleNextIsland(),
        onUnlockNewIsland = () => handleUnlockNewIsland()
      )
      laminarRender(container, IslandNavigator(actions))
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
    
    // Mobile UI components
    getElementById("laminar-mobile-top-bar").foreach: container =>
      laminarRender(container, mobile.MobileTopBar(() => mobile.MobileMenu.open()))
    getElementById("laminar-mobile-action-bar").foreach: container =>
      laminarRender(container, mobile.MobileActionBar(
        onAbdicate = () => handleAbdicate(),
        onSail = () => handleSail(),
        onToggleSkillTree = () => toggleSkillTree()
      ))
    getElementById("laminar-mobile-menu").foreach: container =>
      laminarRender(container, mobile.MobileMenu(
        onHelp = () => toggleHelpPopup(),
        onReset = () => handleResetGame(),
        onDevTools = () => toggleDevTools()
      ))

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
    DevToolsPopup.DevAction("⏰ Skip 1h", () => simulateTimeSkip(1)),
    DevToolsPopup.DevAction("⏰ Skip 2h", () => simulateTimeSkip(2)),
    DevToolsPopup.DevAction("⏰ Skip 6h", () => simulateTimeSkip(6)),
    DevToolsPopup.DevAction("⏰ Skip 12h", () => simulateTimeSkip(12)),
    DevToolsPopup.DevAction("⏰ Skip 24h", () => simulateTimeSkip(24)),
    DevToolsPopup.DevAction("💥 Corrupt Save", () => {
      stopGameTicker()
      stopSaveTimer()
      isDirty = false
      window.localStorage.setItem(StorageKey, "{corrupted}")
      window.location.reload()
    })
  )

  /** Simulate being away for a number of hours to test offline progression */
  private def simulateTimeSkip(hours: Int): Unit =
    val previousGame = currentGame
    val skipMs = hours * 60L * 60L * 1000L
    
    // Set lastTickTime and lastPoliticianGeneration to the past so tick() thinks time has passed
    val gameInPast = currentGame.copy(
      lastTickTime = currentGame.lastTickTime - skipMs,
      lastPoliticianGeneration = currentGame.lastPoliticianGeneration - skipMs
    )
    currentGame = TileKingdomLogic.tick(gameInPast, System.currentTimeMillis())
    
    // Reset tile progress
    tileProgress = Map.empty
    TileGridState.tileProgress.set(Map.empty)
    
    TileKingdomState.update(currentGame)
    saveGame()
    
    // Show what happened
    val offlineWheat = (currentGame.wheat - previousGame.wheat).toInt
    val offlineWood = (currentGame.wood - previousGame.wood).toInt
    val offlineFaith = (currentGame.faith - previousGame.faith).toInt
    val offlineStone = (currentGame.stone - previousGame.stone).toInt
    showWelcomeBackModal(offlineWheat, offlineWood, offlineFaith, offlineStone, hours * 3600.0)

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


  private def gameTick(): Unit =
    val currentTime = System.currentTimeMillis()
    val elapsedMs = (currentTime - currentGame.lastTickTime).toDouble
    if elapsedMs <= 0 then return

    // Track state before tick for visual effects
    val previousGame = currentGame
    val previousRosterSize = previousGame.politicianRoster.size
    
    // Track resources harvested and bureau upgrades for visual effects
    var totalWheatHarvested = 0.0
    var totalWoodHarvested = 0.0
    var totalFaithHarvested = 0.0
    var totalStoneHarvested = 0.0
    var bureauUpgrades: List[(Coord, Int, Coord, Int, Resource, Boolean, Int)] = List.empty
    
    // Update tile progress bars and collect harvests
    val currentIslandIndex = currentGame.currentIslandIndex
    currentGame.islands.zipWithIndex.foreach: (island, islandIndex) =>
      // Update producing tile progress bars
      island.unlockedTiles.foreach: tile =>
        val key = (islandIndex, tile.coord)
        if tile.isWheatField || tile.isWoodcutter || tile.isTemple || tile.isQuarry then
          val intervalMultiplier = if tile.isWheatField then TileKingdomLogic.agriculture2AIntervalMultiplier(currentGame) else 1.0
          val currentProgress = getOrInitProgress(islandIndex, tile.coord)
          val newProgress = currentProgress + elapsedMs / (ProductionIntervalMs * intervalMultiplier)
          if newProgress >= 1.0 then
            val harvests = newProgress.toInt
            tileProgress = tileProgress.updated(key, newProgress - harvests)
            
            // Calculate and accumulate production
            val production = tile.tileType match
              case TileType.WheatField(_) => TileKingdomLogic.productionPerHarvest(currentGame, tile) * harvests
              case TileType.Woodcutter(_) => TileKingdomLogic.woodProductionPerHarvest(currentGame, tile) * harvests
              case TileType.Temple(_) => TileKingdomLogic.faithProductionPerHarvest(currentGame, tile) * harvests
              case TileType.Quarry(_) => TileKingdomLogic.stoneProductionPerHarvest(currentGame, tile) * harvests
              case _ => 0.0
            
            tile.tileType match
              case TileType.WheatField(_) => totalWheatHarvested += production
              case TileType.Woodcutter(_) => totalWoodHarvested += production
              case TileType.Temple(_) => totalFaithHarvested += production
              case TileType.Quarry(_) => totalStoneHarvested += production
              case _ => ()
            
            // Show floating reward for current island only
            if islandIndex == currentIslandIndex && production > 0 then
              val emoji = tile.tileType match
                case TileType.WheatField(_) => "🌾"
                case TileType.Woodcutter(_) => "🪵"
                case TileType.Temple(_) => "✨"
                case TileType.Quarry(_) => "🪨"
                case _ => ""
              showFloatingReward(tile.coord, production.toInt, emoji, isSpend = false, offsetIndex = 0)
          else
            tileProgress = tileProgress.updated(key, newProgress)
      
      // Update bureau progress and track upgrades for visual effects
      island.unlockedTiles.filter(_.isBureau).foreach: tile =>
        val key = (islandIndex, tile.coord)
        val currentProgress = getOrInitProgress(islandIndex, tile.coord)
        val speedMultiplier = TileKingdomLogic.bureauSpeedMultiplier(currentGame, tile.coord)
        val progressIncrement = elapsedMs / BureauIntervalMs * speedMultiplier
        val newProgress = currentProgress + progressIncrement
        
        if newProgress >= 1.0 then
          // Check if bureau will upgrade something (for visual effects)
          val isTurbo = TileKingdomLogic.isBureauTurbo(currentGame, tile.coord)
          val nearbyCoords = TileKingdomLogic.bureauAffectedCoords(currentGame, tile.coord)
          val minLevel = nearbyCoords
            .flatMap(c => currentGame.tiles.get(c))
            .filter(_.isUpgradeable)
            .map(_.level)
            .minOption
            .getOrElse(1)
          val minFaithCost = TileKingdomLogic.effectiveBureauFaithCostForLevel(currentGame, minLevel)
          val effectivelyTurbo = isTurbo && currentGame.faith >= minFaithCost
          
          // Peek at what would be upgraded (before the actual tick)
          TileKingdomLogic.bureauAutoUpgrade(currentGame, tile.coord, currentTime) match
            case Some((_, upgradedCoord)) if upgradedCoord != tile.coord =>
              val upgradedTile = currentGame.tiles.get(upgradedCoord)
              val upgradeCostOpt = upgradedTile.flatMap(t => TileKingdomLogic.effectiveUpgradeCost(currentGame, t))
              val upgradeCost = upgradeCostOpt.map(_.amount).getOrElse(0)
              val costResource = upgradeCostOpt.map(_.resource).getOrElse(Resource.Wheat)
              val newLevel = upgradedTile.map(_.level + 1).getOrElse(1)
              bureauUpgrades = bureauUpgrades :+ (upgradedCoord, newLevel, tile.coord, upgradeCost, costResource, effectivelyTurbo, islandIndex)
              tileProgress = tileProgress.updated(key, newProgress - 1.0)
            case _ =>
              tileProgress = tileProgress.updated(key, math.min(newProgress, 1.0))
        else
          tileProgress = tileProgress.updated(key, newProgress)

    // Update game state with harvested resources
    currentGame = currentGame.copy(
      wheat = currentGame.wheat + totalWheatHarvested,
      wood = currentGame.wood + totalWoodHarvested,
      faith = currentGame.faith + totalFaithHarvested,
      stone = currentGame.stone + totalStoneHarvested,
      lastTickTime = currentTime
    )
    
    // Process bureau auto-upgrades
    currentGame.islands.foreach: island =>
      island.unlockedTiles.filter(_.isBureau).foreach: tile =>
        TileKingdomLogic.bureauAutoUpgrade(currentGame, tile.coord, currentTime) match
          case Some((newGame, _)) => currentGame = newGame
          case None => ()
    
    // Tick politician lifespans
    val (gameAfterLifespan, destroyedPoliticianNames) = TileKingdomLogic.tickPoliticianLifespans(currentGame, elapsedMs.toLong)
    currentGame = gameAfterLifespan
    
    // Generate new politicians
    currentGame = TileKingdomLogic.generateNewPoliticians(currentGame, currentTime)
    
    // Check for new politicians
    val newPoliticianGenerated = currentGame.politicianRoster.size > previousRosterSize

    // Sync with Laminar reactive state
    TileKingdomState.update(currentGame)
    // Only sync progress for current island tiles to the UI
    val currentIslandProgress = tileProgress
      .filter(_._1._1 == currentGame.currentIslandIndex)
      .map { case ((_, coord), progress) => coord -> progress }
    TileGridState.tileProgress.set(currentIslandProgress)

    markDirty()

    if newPoliticianGenerated then
      showNotification("A new politician has arrived!")

    destroyedPoliticianNames.foreach: name =>
      showNotification(s"$name has reached the end of their term!")

    // Show projectile and floating text for bureau upgrades (only on current island)
    bureauUpgrades.foreach: (upgradedCoord, newLevel, bureauCoord, cost, costResource, wasTurbo, islandIndex) =>
      // Only show floating effects if the bureau is on the current island
      if islandIndex == currentIslandIndex then
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
      if document.visibilityState == "hidden" then
        // Tab is being hidden - save and stop the ticker to prevent event accumulation
        saveIfDirty()
        stopGameTicker()
      else
        // Tab is becoming visible again - reload game state with offline progression
        reloadGameState()
        startGameTicker()
    )

  /** Reload game state from memory, applying offline progression */
  private def reloadGameState(): Unit =
    val currentTime = System.currentTimeMillis()
    val previousGame = currentGame
    val offlineMs = currentTime - previousGame.lastTickTime
    
    // Only apply offline progression if significant time has passed (> 1 second)
    if offlineMs > 1000 then
      currentGame = TileKingdomLogic.tick(previousGame, currentTime)
      TileKingdomState.update(currentGame)
      
      // Reset tile progress to avoid visual glitches from stale progress
      tileProgress = Map.empty
      TileGridState.tileProgress.set(Map.empty)
      
      val offlineSeconds = offlineMs / 1000.0
      if offlineSeconds > 5 then
        val offlineWheat = (currentGame.wheat - previousGame.wheat).toInt
        val offlineWood = (currentGame.wood - previousGame.wood).toInt
        val offlineFaith = (currentGame.faith - previousGame.faith).toInt
        val offlineStone = (currentGame.stone - previousGame.stone).toInt
        if offlineWheat > 0 || offlineWood > 0 || offlineFaith > 0 || offlineStone > 0 then
          val parts = List(
            Option.when(offlineWheat > 0)(s"+$offlineWheat🌾"),
            Option.when(offlineWood > 0)(s"+$offlineWood🪵"),
            Option.when(offlineFaith > 0)(s"+$offlineFaith✨"),
            Option.when(offlineStone > 0)(s"+$offlineStone🪨")
          ).flatten.mkString(" ")
          showNotification(s"Welcome back! $parts")

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
            val offlineStone = (currentGame.stone - loadedGame.stone).toInt
            if offlineWheat > 0 || offlineWood > 0 || offlineFaith > 0 || offlineStone > 0 then
              showWelcomeBackModal(offlineWheat, offlineWood, offlineFaith, offlineStone, offlineSeconds)

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
        val islandIndex = currentGame.currentIslandIndex
        currentGame = newGame
        tileProgress = tileProgress.removed((islandIndex, coord))
        TileKingdomState.update(currentGame)
        saveGame()
        showNotification("Building destroyed")
      case Left(error) =>
        showNotification(error)

  private def handleDestroyTile(coord: Coord): Unit =
    TileKingdomLogic.destroyTile(currentGame, coord) match
      case Right(newGame) =>
        val islandIndex = currentGame.currentIslandIndex
        currentGame = newGame
        tileProgress = tileProgress.removed((islandIndex, coord))
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

  // ============================================================================
  // Island Navigation
  // ============================================================================

  private def handlePreviousIsland(): Unit =
    if currentGame.canGoPreviousIsland then
      currentGame = TileKingdomLogic.previousIsland(currentGame)
      TileKingdomState.update(currentGame)
      TileGridState.centerOnKingdom(currentGame, animated = true)

  private def handleNextIsland(): Unit =
    if currentGame.canGoNextIsland then
      currentGame = TileKingdomLogic.nextIsland(currentGame)
      TileKingdomState.update(currentGame)
      TileGridState.centerOnKingdom(currentGame, animated = true)

  private def handleUnlockNewIsland(): Unit =
    TileKingdomLogic.unlockNewIsland(currentGame) match
      case Right(newGame) =>
        currentGame = newGame
        TileKingdomState.update(currentGame)
        saveGame()
        TileGridState.centerOnKingdom(currentGame, animated = true)
        showNotification(s"🏝️ New island unlocked! Welcome to Island ${currentGame.currentIslandIndex + 1}")
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

  private def showWelcomeBackModal(wheatGain: Int, woodGain: Int, faithGain: Int, stoneGain: Int, offlineSeconds: Double): Unit =
    WelcomeBackModal.show(wheatGain, woodGain, faithGain, stoneGain, offlineSeconds)

  private def showFloatingReward(coord: Coord, amount: Int, emoji: String, isSpend: Boolean, offsetIndex: Int): Unit =
    getElementById("tile-kingdom-grid").foreach: grid =>
      FloatingEffects.showFloatingReward(grid, coord, amount, emoji, isSpend, offsetIndex)

  private def showFloatingLevel(coord: Coord, level: Int): Unit =
    getElementById("tile-kingdom-grid").foreach: grid =>
      FloatingEffects.showFloatingLevel(grid, coord, level)

  private def showBureauProjectile(fromCoord: Coord, toCoord: Coord, onComplete: () => Unit): Unit =
    getElementById("tile-kingdom-grid").foreach: grid =>
      FloatingEffects.showBureauProjectile(grid, fromCoord, toCoord, onComplete)
