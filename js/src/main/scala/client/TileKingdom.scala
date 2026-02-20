package client

import org.scalajs.dom.*
import scala.util.Try
import scala.util.chaining.*
import shared.TileKingdom.*

def initializeTileKingdom(): Unit =
  TileKingdom.init()

object TileKingdom:

  private val StorageKey = "tile_kingdom_game_state"
  private var currentGame: TileKingdomGame = TileKingdomLogic.newGame(System.currentTimeMillis())
  private var gameTickerHandle: Option[Int] = None

  // Track progress (0.0 to 1.0) for each wheat field tile
  private var tileProgress: Map[Coord, Double] = Map.empty
  private val ProductionIntervalMs: Double = TileKingdomLogic.ProductionIntervalSeconds * 1000.0
  private val BureauIntervalMs: Double = TileKingdomLogic.BureauIntervalSeconds * 1000.0

  // Panning state
  private var panOffsetX: Double = 0.0
  private var panOffsetY: Double = 0.0
  private var isDragging: Boolean = false
  private var dragStartX: Double = 0.0
  private var dragStartY: Double = 0.0
  private var panStartX: Double = 0.0
  private var panStartY: Double = 0.0

  // Zoom state
  private var zoomLevel: Double = 1.0
  private val MinZoom: Double = 0.3
  private val MaxZoom: Double = 2.0
  private val ZoomStep: Double = 0.1

  // Grid rendering constants
  private val BaseTileSize: Int = 74 // 70px tile + 4px gap
  private def TileSize: Double = BaseTileSize * zoomLevel
  private val VisiblePadding: Int = 2 // Extra tiles to render outside viewport

  // ============================================================================
  // Initialization
  // ============================================================================

  def init(): Unit =
    println("[TileKingdom] Initializing Tile Kingdom game")
    loadGame()
    buildUI()
    centerOnKingdom()
    renderGame()
    startGameTicker()

  def cleanup(): Unit =
    println("[TileKingdom] Cleaning up Tile Kingdom game")
    stopGameTicker()

  // ============================================================================
  // UI Building
  // ============================================================================

  private def buildUI(): Unit =
    val container = getElementById("tile-kingdom-container").getOrElse:
      document.body.appendChild(div(id = "tile-kingdom-container", cls = "tile-kingdom-container"))
      document.getElementById("tile-kingdom-container").asInstanceOf[HTMLElement]

    container.innerHTML = ""

    // Grid viewport (draggable area)
    val viewport = div(id = "tile-kingdom-grid-viewport", cls = "tile-kingdom-grid-viewport")
    viewport.appendChild(div(id = "tile-kingdom-grid", cls = "tile-kingdom-grid"))
    container.appendChild(viewport)

    // Overlay UI elements
    container.appendChild(buildHeader())
    container.appendChild(buildResources())
    container.appendChild(buildActions())
    container.appendChild(buildNotification())
    container.appendChild(buildHelpPopup())
    container.appendChild(buildDevToolsPopup())

    // Setup drag handlers
    setupDragHandlers(viewport)

  private def buildHeader(): HTMLElement =
    div(cls = "tile-kingdom-header")(
      h1(content = "🏰 Tile Kingdom"),
      button(cls = "help-button", content = "?").tap: btn =>
        btn.onclick = (_: MouseEvent) => toggleHelpPopup()
    )

  private def buildResources(): HTMLElement =
    div(cls = "tile-kingdom-resources")(
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "🌾"),
        span(id = "tile-kingdom-wheat", cls = "resource-value", content = "0")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "🪵"),
        span(id = "tile-kingdom-wood", cls = "resource-value", content = "0")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "✨"),
        span(id = "tile-kingdom-faith", cls = "resource-value", content = "0")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "💰"),
        span(id = "tile-kingdom-gold", cls = "resource-value", content = "0")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "👑"),
        span(id = "tile-kingdom-abdications", cls = "resource-value", content = "0")
      )
    )

  private def buildActions(): HTMLElement =
    div(cls = "tile-kingdom-actions")(
      button(id = "tile-kingdom-abdicate-btn", cls = "btn-primary disabled", content = "Abdicate").tap: btn =>
        btn.disabled = true
        btn.onclick = (_: MouseEvent) => handleAbdicate()
      ,
      button(id = "tile-kingdom-center-btn", cls = "btn-secondary", content = "⌖ Center").tap: btn =>
        btn.onclick = (_: MouseEvent) => centerOnKingdom(animated = true),
      button(id = "tile-kingdom-reset-btn", cls = "btn-danger", content = "Reset").tap: btn =>
        btn.onclick = (_: MouseEvent) => handleResetGame(),
      button(id = "tile-kingdom-dev-btn", cls = "btn-dev", content = "🛠️ Dev").tap: btn =>
        btn.onclick = (_: MouseEvent) => toggleDevTools()
    )

  private def buildNotification(): HTMLElement =
    div(id = "tile-kingdom-notification", cls = "notification")

  private def buildHelpPopup(): HTMLElement =
    div(id = "tile-kingdom-help-popup", cls = "help-popup")(
      div(cls = "help-popup-content")(
        div(cls = "help-popup-header")(
          h3(content = "How to Play"),
          button(cls = "help-close-btn", content = "✕").tap: btn =>
            btn.onclick = (_: MouseEvent) => toggleHelpPopup()
        ),
        div(cls = "help-popup-body")(
          p(content = "🌾 Click empty tiles to build wheat fields"),
          p(content = "⬆️ Click buildings to level them up"),
          p(content = "🏠 After your first wheat field, you can build farms"),
          p(content = "📈 Farms boost nearby wheat fields by 25% per level"),
          p(content = "👑 Fill all unlocked tiles to abdicate"),
          p(content = "💰 Abdication earns gold based on income rate"),
          p(content = "🔓 Click adjacent tiles to expand your territory"),
          p(content = "🖱️ Drag to pan, scroll to zoom"),
          p(content = "🗑️ Right-click a building to destroy it")
        )
      )
    )

  private def buildDevToolsPopup(): HTMLElement =
    div(id = "tile-kingdom-dev-popup", cls = "help-popup")(
      div(cls = "help-popup-content dev-tools-content")(
        div(cls = "help-popup-header")(
          h3(content = "🛠️ Dev Tools"),
          button(cls = "help-close-btn", content = "✕").tap: btn =>
            btn.onclick = (_: MouseEvent) => toggleDevTools()
        ),
        div(cls = "help-popup-body")(
          button(cls = "btn-dev-action", content = "💰 Gold x10").tap: btn =>
            btn.onclick = (_: MouseEvent) =>
              currentGame = currentGame.copy(gold = math.max(currentGame.gold * 10, 100))
              saveGame()
              renderGame()
              showNotification(s"Gold is now ${currentGame.gold}")
          ,
          button(cls = "btn-dev-action", content = "🌾 Wheat +1000").tap: btn =>
            btn.onclick = (_: MouseEvent) =>
              currentGame = currentGame.copy(wheat = currentGame.wheat + 1000)
              saveGame()
              renderGame()
              showNotification(s"Added 1000 wheat")
          ,
          button(cls = "btn-dev-action", content = "🪵 Wood +1000").tap: btn =>
            btn.onclick = (_: MouseEvent) =>
              currentGame = currentGame.copy(wood = currentGame.wood + 1000)
              saveGame()
              renderGame()
              showNotification(s"Added 1000 wood")
          ,
          button(cls = "btn-dev-action", content = "🗺️ +100 Tiles").tap: btn =>
            btn.onclick = (_: MouseEvent) =>
              currentGame = TileKingdomLogic.unlockManyTiles(currentGame, 100)
              saveGame()
              renderGame()
              showNotification(s"Added 100 tiles")
        )
      )
    )

  private def toggleDevTools(): Unit =
    getElementById("tile-kingdom-dev-popup").foreach: popup =>
      popup.classList.toggle("show")

  private def toggleHelpPopup(): Unit =
    getElementById("tile-kingdom-help-popup").foreach: popup =>
      popup.classList.toggle("show")

  // ============================================================================
  // Drag/Pan Handling
  // ============================================================================

  private def setupDragHandlers(viewport: HTMLElement): Unit =
    viewport.onmousedown = (e: MouseEvent) =>
      if e.button == 0 then // Left mouse button
        isDragging = true
        dragStartX = e.clientX
        dragStartY = e.clientY
        panStartX = panOffsetX
        panStartY = panOffsetY
        viewport.style.cursor = "grabbing"

    document.onmousemove = (e: MouseEvent) =>
      if isDragging then
        val dx = e.clientX - dragStartX
        val dy = e.clientY - dragStartY
        panOffsetX = panStartX + dx
        panOffsetY = panStartY + dy
        updateGridPosition()

    document.onmouseup = (e: MouseEvent) =>
      if isDragging then
        isDragging = false
        getElementById("tile-kingdom-grid-viewport").foreach(_.asInstanceOf[HTMLElement].style.cursor = "grab")
        snapBackIfNeeded()

    // Touch support
    viewport.addEventListener(
      "touchstart",
      (e: TouchEvent) =>
        if e.touches.length == 1 then
          val touch = e.touches(0)
          isDragging = true
          dragStartX = touch.clientX
          dragStartY = touch.clientY
          panStartX = panOffsetX
          panStartY = panOffsetY
    )

    viewport.addEventListener(
      "touchmove",
      (e: TouchEvent) =>
        if isDragging && e.touches.length == 1 then
          e.preventDefault()
          val touch = e.touches(0)
          val dx = touch.clientX - dragStartX
          val dy = touch.clientY - dragStartY
          panOffsetX = panStartX + dx
          panOffsetY = panStartY + dy
          updateGridPosition()
    )

    viewport.addEventListener(
      "touchend",
      (_: TouchEvent) =>
        if isDragging then
          isDragging = false
          snapBackIfNeeded()
    )

    // Mouse wheel zoom
    viewport.addEventListener(
      "wheel",
      (e: WheelEvent) =>
        e.preventDefault()

        val mouseX = e.clientX
        val mouseY = e.clientY

        // Calculate the world position under the mouse before zoom
        val worldXBefore = (mouseX - panOffsetX) / TileSize
        val worldYBefore = (mouseY - panOffsetY) / TileSize

        // Apply zoom
        val delta = if e.deltaY < 0 then ZoomStep else -ZoomStep
        zoomLevel = math.max(MinZoom, math.min(MaxZoom, zoomLevel + delta))

        // Adjust pan to keep the same world position under the mouse
        panOffsetX = mouseX - worldXBefore * TileSize
        panOffsetY = mouseY - worldYBefore * TileSize

        updateGridPosition()
        renderTiles()
    )

  private def updateGridPosition(): Unit =
    getElementById("tile-kingdom-grid").foreach: grid =>
      grid.asInstanceOf[HTMLElement].style.transform = s"translate(${panOffsetX}px, ${panOffsetY}px)"

  private def centerOnKingdom(animated: Boolean = false): Unit =
    val target = calculateCenterOffset()
    if animated then
      animateTo(target)
    else
      panOffsetX = target._1
      panOffsetY = target._2
      updateGridPosition()
      renderTiles()

  private def snapBackIfNeeded(): Unit =
    val viewportWidth = window.innerWidth
    val viewportHeight = window.innerHeight

    // Check if any unlocked tile is sufficiently visible (at least 50% in viewport)
    val unlockedCoords = currentGame.unlockedTiles.map(_.coord)
    val margin = TileSize * 0.5 // Tile must be at least 50% visible
    val anyVisible = unlockedCoords.exists: coord =>
      val tileScreenX = coord.col * TileSize + panOffsetX
      val tileScreenY = coord.row * TileSize + panOffsetY
      tileScreenX > -TileSize + margin && tileScreenX < viewportWidth - margin &&
      tileScreenY > -TileSize + margin && tileScreenY < viewportHeight - margin

    if !anyVisible then
      // Animate snap back to center
      animateTo(calculateCenterOffset())
      showNotification("Snapped back to kingdom")

  private def calculateCenterOffset(): (Double, Double) =
    val unlockedCoords = currentGame.unlockedTiles.map(_.coord)
    if unlockedCoords.nonEmpty then
      val centerRow = unlockedCoords.map(_.row).sum.toDouble / unlockedCoords.size
      val centerCol = unlockedCoords.map(_.col).sum.toDouble / unlockedCoords.size
      val viewportWidth = window.innerWidth
      val viewportHeight = window.innerHeight
      val targetX = viewportWidth / 2 - (centerCol + 0.5) * TileSize
      val targetY = viewportHeight / 2 - (centerRow + 0.5) * TileSize
      (targetX, targetY)
    else
      (panOffsetX, panOffsetY)

  private def animateTo(target: (Double, Double)): Unit =
    val (targetX, targetY) = target
    val startX = panOffsetX
    val startY = panOffsetY
    val duration = 300.0 // milliseconds
    val startTime = System.currentTimeMillis().toDouble

    def animate(): Unit =
      val elapsed = System.currentTimeMillis().toDouble - startTime
      val progress = math.min(1.0, elapsed / duration)
      // Ease-out cubic for smooth deceleration
      val eased = 1.0 - math.pow(1.0 - progress, 3)

      panOffsetX = startX + (targetX - startX) * eased
      panOffsetY = startY + (targetY - startY) * eased
      updateGridPosition()

      if progress < 1.0 then
        window.requestAnimationFrame((_: Double) => animate())
      else
        renderTiles() // Final render at destination

    animate()

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

    // Update progress for each producing tile and collect harvests
    var totalWheatHarvested = 0.0
    var totalWoodHarvested = 0.0
    var totalFaithHarvested = 0.0

    val wheatFields = currentGame.unlockedTiles.filter(_.isWheatField)
    val woodcutters = currentGame.unlockedTiles.filter(_.isWoodcutter)
    val temples = currentGame.unlockedTiles.filter(_.isTemple)

    // Process wheat fields
    wheatFields.foreach: tile =>
      val currentProgress = tileProgress.getOrElse(tile.coord, 0.0)
      val progressIncrement = elapsedMs / ProductionIntervalMs
      val newProgress = currentProgress + progressIncrement

      if newProgress >= 1.0 then
        val harvests = newProgress.toInt
        val production = TileKingdomLogic.productionPerHarvest(currentGame, tile)
        totalWheatHarvested += production * harvests
        tileProgress = tileProgress.updated(tile.coord, newProgress - harvests)
        showFloatingReward(tile.coord, (production * harvests).toInt, "🌾")
      else
        tileProgress = tileProgress.updated(tile.coord, newProgress)

    // Process woodcutters
    woodcutters.foreach: tile =>
      val currentProgress = tileProgress.getOrElse(tile.coord, 0.0)
      val progressIncrement = elapsedMs / ProductionIntervalMs
      val newProgress = currentProgress + progressIncrement

      if newProgress >= 1.0 then
        val harvests = newProgress.toInt
        val production = TileKingdomLogic.woodProductionPerHarvest(currentGame, tile)
        totalWoodHarvested += production * harvests
        tileProgress = tileProgress.updated(tile.coord, newProgress - harvests)
        showFloatingReward(tile.coord, (production * harvests).toInt, "🪵")
      else
        tileProgress = tileProgress.updated(tile.coord, newProgress)

    // Process temples
    temples.foreach: tile =>
      val currentProgress = tileProgress.getOrElse(tile.coord, 0.0)
      val progressIncrement = elapsedMs / ProductionIntervalMs
      val newProgress = currentProgress + progressIncrement

      if newProgress >= 1.0 then
        val harvests = newProgress.toInt
        val production = TileKingdomLogic.faithProductionPerHarvest(tile)
        totalFaithHarvested += production * harvests
        tileProgress = tileProgress.updated(tile.coord, newProgress - harvests)
        showFloatingReward(tile.coord, (production * harvests).toInt, "✨")
      else
        tileProgress = tileProgress.updated(tile.coord, newProgress)

    // Process bureaus
    val bureaus = currentGame.unlockedTiles.filter(_.isBureau)
    var updatedGame = currentGame.copy(
      wheat = currentGame.wheat + totalWheatHarvested,
      wood = currentGame.wood + totalWoodHarvested,
      faith = currentGame.faith + totalFaithHarvested,
      lastTickTime = currentTime
    )

    // Track upgrades to show floating text after render
    var bureauUpgrades: List[(Coord, Int, Coord, Int, Boolean)] = List.empty // (upgradedCoord, newLevel, bureauCoord, cost, isWoodCost)

    bureaus.foreach: tile =>
      val currentProgress = tileProgress.getOrElse(tile.coord, 0.0)
      val speedMultiplier = TileKingdomLogic.bureauSpeedMultiplier(currentGame, tile.coord)
      val progressIncrement = elapsedMs / BureauIntervalMs * speedMultiplier
      val newProgress = currentProgress + progressIncrement

      if newProgress >= 1.0 then
        // Bureau ready to attempt an upgrade
        TileKingdomLogic.bureauAutoUpgrade(updatedGame, tile.coord, currentTime) match
          case Some((newGame, upgradedCoord)) =>
            updatedGame = newGame
            // Collect upgrade info to show after render
            val upgradedTile = updatedGame.tiles.get(upgradedCoord)
            val previousTile = upgradedTile.map(t => t.copy(tileType = t.tileType match
              case TileType.WheatField(lvl) => TileType.WheatField(lvl - 1)
              case TileType.Farm(lvl)       => TileType.Farm(lvl - 1)
              case TileType.Woodcutter(lvl) => TileType.Woodcutter(lvl - 1)
              case TileType.Temple(lvl)     => TileType.Temple(lvl - 1)
              case other                    => other
            ))
            val upgradeCost = previousTile.flatMap(TileKingdomLogic.getUpgradeCost).getOrElse(0)
            val isWoodCost = previousTile.exists(_.isTemple)
            val newLevel = upgradedTile.map(_.level).getOrElse(1)
            bureauUpgrades = bureauUpgrades :+ (upgradedCoord, newLevel, tile.coord, upgradeCost, isWoodCost)
            tileProgress = tileProgress.updated(tile.coord, newProgress - 1.0) // Keep excess progress
          case None =>
            // No upgrade possible, keep progress at 1.0 to retry next tick
            tileProgress = tileProgress.updated(tile.coord, 1.0)
      else
        tileProgress = tileProgress.updated(tile.coord, newProgress)

    currentGame = updatedGame

    saveGame()
    updateProgressBars()
    renderResources()
    // Re-render tiles if bureau performed upgrades
    if bureaus.nonEmpty then renderTiles()

    // Show floating text after render so elements exist
    bureauUpgrades.foreach: (upgradedCoord, newLevel, bureauCoord, cost, isWoodCost) =>
      val costEmoji = if isWoodCost then "🪵" else "🌾"
      showFloatingReward(upgradedCoord, cost, costEmoji, isSpend = true)
      showFloatingLevel(upgradedCoord, newLevel)
      showFloatingReward(bureauCoord, TileKingdomLogic.BureauWoodCostPerUpgrade, "🪵", isSpend = true)

  // ============================================================================
  // Persistence
  // ============================================================================

  private def saveGame(): Unit =
    Try:
      import upickle.default.*
      val json = write(currentGame)
      window.localStorage.setItem(StorageKey, json)
    .recover:
      case ex => println(s"[TileKingdom] Failed to save game: ${ex.getMessage}")

  private def loadGame(): Unit =
    Try:
      Option(window.localStorage.getItem(StorageKey)) match
        case Some(json) =>
          import upickle.default.*
          val loadedGame = read[TileKingdomGame](json)

          // Calculate offline progress
          val currentTime = System.currentTimeMillis()
          currentGame = TileKingdomLogic.tick(loadedGame, currentTime)

          val offlineSeconds = (currentTime - loadedGame.lastTickTime) / 1000.0
          if offlineSeconds > 60 then
            val offlineWheat = currentGame.wheat - loadedGame.wheat
            println(s"[TileKingdom] Welcome back! You earned ${offlineWheat.toInt} wheat while away")
            showNotification(s"Welcome back! +${offlineWheat.toInt} wheat")

          println(s"[TileKingdom] Game loaded from localStorage")
        case None =>
          println(s"[TileKingdom] No saved game found, starting new game")
          currentGame = TileKingdomLogic.newGame(System.currentTimeMillis())
          saveGame()
    .recover:
      case ex =>
        println(s"[TileKingdom] Failed to load game: ${ex.getMessage}")
        currentGame = TileKingdomLogic.newGame(System.currentTimeMillis())

  // ============================================================================
  // UI Rendering
  // ============================================================================

  private def renderGame(): Unit =
    renderResources()
    renderTiles()
    renderAbdicationButton()

  private def renderResources(): Unit =
    setElementText("tile-kingdom-wheat", f"${currentGame.wheat.toInt}%,d")
    setElementText("tile-kingdom-wood", f"${currentGame.wood.toInt}%,d")
    setElementText("tile-kingdom-faith", f"${currentGame.faith.toInt}%,d")
    setElementText("tile-kingdom-gold", f"${currentGame.gold}%,d")
    setElementText("tile-kingdom-abdications", currentGame.totalAbdications.toString)

  private def renderTiles(): Unit =
    getElementById("tile-kingdom-grid").foreach: gridContainer =>
      gridContainer.innerHTML = ""
      val grid = gridContainer.asInstanceOf[HTMLElement]

      // Calculate visible tile range based on viewport and pan offset
      val viewportWidth = window.innerWidth
      val viewportHeight = window.innerHeight

      val minCol = ((-panOffsetX - TileSize * VisiblePadding) / TileSize).floor.toInt
      val maxCol = ((-panOffsetX + viewportWidth + TileSize * VisiblePadding) / TileSize).ceil.toInt
      val minRow = ((-panOffsetY - TileSize * VisiblePadding) / TileSize).floor.toInt
      val maxRow = ((-panOffsetY + viewportHeight + TileSize * VisiblePadding) / TileSize).ceil.toInt

      // Render influence indicators first (so they appear behind tiles)
      currentGame.unlockedTiles.foreach: tile =>
        val coord = tile.coord
        if coord.row >= minRow - 3 && coord.row <= maxRow + 3 && coord.col >= minCol - 3 && coord.col <= maxCol + 3 then
          tile.tileType match
            case TileType.Farm(_) =>
              grid.appendChild(renderInfluenceIndicator(coord, 1, "farm-influence"))
            case TileType.Bureau(_) =>
              grid.appendChild(renderInfluenceIndicator(coord, TileKingdomLogic.BureauRadius, "bureau-influence"))
            case _ => // No indicator
      // Get all coords we need to render (existing tiles + unlockable if affordable)
      val unlockableCoords = TileKingdomLogic.unlockableCoords(currentGame)
      val canAffordUnlock = currentGame.gold >= currentGame.nextTileUnlockCost
      val coordsToRender = currentGame.tiles.keySet ++ (if canAffordUnlock then unlockableCoords else Set.empty)

      // Render tiles within visible range
      coordsToRender.foreach: coord =>
        if coord.row >= minRow && coord.row <= maxRow && coord.col >= minCol && coord.col <= maxCol then
          currentGame.tiles.get(coord) match
            case Some(tile) =>
              grid.appendChild(renderTile(tile))
            case None if canAffordUnlock && unlockableCoords.contains(coord) =>
              grid.appendChild(renderUnlockableTile(coord))
            case _ => // Don't render
  private def renderInfluenceIndicator(center: Coord, radius: Int, cssClass: String): HTMLElement =
    val indicator = div(cls = s"influence-indicator $cssClass")

    // Calculate the rectangle bounds
    val left = (center.col - radius) * TileSize
    val top = (center.row - radius) * TileSize
    val width = (radius * 2 + 1) * TileSize - 4 // -4 for gap
    val height = (radius * 2 + 1) * TileSize - 4

    indicator.style.cssText =
      s"position: absolute; left: ${left}px; top: ${top}px; width: ${width}px; height: ${height}px;"

    indicator

  private def renderTile(tile: Tile): HTMLElement =
    val coord = tile.coord
    val tileDiv = div(id = s"tile-${coord.row}-${coord.col}", cls = "tile-kingdom-tile")
    val tilePixelSize = (70 * zoomLevel).toInt
    // Scale font size with zoom, but clamp to reasonable range
    val fontScale = math.max(0.6, math.min(1.0, zoomLevel))

    // Position the tile absolutely with zoom-adjusted size
    tileDiv.asInstanceOf[HTMLElement].style.cssText =
      s"position: absolute; left: ${coord.col * TileSize}px; top: ${coord
          .row * TileSize}px; width: ${tilePixelSize}px; height: ${tilePixelSize}px; font-size: ${fontScale}em;"

    tileDiv.classList.add("unlocked")
    tile.tileType match
      case TileType.Empty =>
        tileDiv.classList.add("empty")
        val wheatCost = TileKingdomLogic.wheatFieldBuildCost
        val farmCost = TileKingdomLogic.farmBuildCost
        val woodcutterCost = TileKingdomLogic.woodcutterBuildCost
        val bureauCost = TileKingdomLogic.bureauBuildCost
        val templeCost = TileKingdomLogic.templeBuildCost
        val canBuildOthers = currentGame.hasWheatField

        // Build options container (hidden by default, shown on hover via CSS)
        val buildOptions = div(cls = "tile-build-options")

        buildOptions.appendChild(div(cls = "build-option").tap: opt =>
          opt.appendChild(div(cls = "build-icon", content = "🌾"))
          opt.appendChild(div(cls = "build-name", content = "Field"))
          opt.appendChild(div(cls = "build-cost", content = s"$wheatCost🌾"))
          opt.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            handleBuildWheatField(coord)
        )

        if canBuildOthers then
          buildOptions.appendChild(div(cls = "build-option").tap: opt =>
            opt.appendChild(div(cls = "build-icon", content = "🏠"))
            opt.appendChild(div(cls = "build-name", content = "Farm"))
            opt.appendChild(div(cls = "build-cost", content = s"$farmCost🌾"))
            opt.onclick = (e: MouseEvent) =>
              e.stopPropagation()
              handleBuildFarm(coord)
          )
          buildOptions.appendChild(div(cls = "build-option").tap: opt =>
            opt.appendChild(div(cls = "build-icon", content = "🪓"))
            opt.appendChild(div(cls = "build-name", content = "Wood"))
            opt.appendChild(div(cls = "build-cost", content = s"$woodcutterCost🌾"))
            opt.onclick = (e: MouseEvent) =>
              e.stopPropagation()
              handleBuildWoodcutter(coord)
          )
          buildOptions.appendChild(div(cls = "build-option").tap: opt =>
            opt.appendChild(div(cls = "build-icon", content = "🏛️"))
            opt.appendChild(div(cls = "build-name", content = "Bureau"))
            opt.appendChild(div(cls = "build-cost", content = s"$bureauCost🪵"))
            opt.onclick = (e: MouseEvent) =>
              e.stopPropagation()
              handleBuildBureau(coord)
          )
          buildOptions.appendChild(div(cls = "build-option").tap: opt =>
            opt.appendChild(div(cls = "build-icon", content = "⛪"))
            opt.appendChild(div(cls = "build-name", content = "Temple"))
            opt.appendChild(div(cls = "build-cost", content = s"$templeCost🪵"))
            opt.onclick = (e: MouseEvent) =>
              e.stopPropagation()
              handleBuildTemple(coord)
          )

        tileDiv.appendChild(buildOptions)

      case TileType.WheatField(level) =>
        tileDiv.classList.add("wheat-field")
        tileDiv.setAttribute("data-level", level.toString)
        val harvestAmount = TileKingdomLogic.productionPerHarvest(currentGame, tile)
        val bonusMultiplier = TileKingdomLogic.farmBonusMultiplier(currentGame, coord)
        val hasBonus = bonusMultiplier > 1.0
        val upgradeCost = TileKingdomLogic.wheatFieldLevelUpCost(level)

        val content = div(cls = "tile-content")(
          div(cls = "tile-icon", content = "🌾"),
          div(cls = "tile-label", content = s"Lv$level")
        )

        val prodDiv = div(cls = "tile-production", content = s"+${harvestAmount.toInt}")
        if hasBonus then
          val bonusPercent = ((bonusMultiplier - 1) * 100).toInt
          prodDiv.appendChild(span(cls = "bonus", content = s" +$bonusPercent%"))
        content.appendChild(prodDiv)

        val upgradeRow = div(cls = "tile-upgrade-row")
        upgradeRow.appendChild(span(cls = "tile-upgrade", content = s"⬆$upgradeCost"))
        upgradeRow.appendChild(button(cls = "btn-x10", content = "x10").tap: btn =>
          btn.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            handleBulkLevelUp(coord, 10, TileKingdomLogic.levelUpWheatField, TileKingdomLogic.wheatFieldLevelUpCost)
        )
        content.appendChild(upgradeRow)

        tileDiv.appendChild(content)

        // Add progress bar
        val progress = tileProgress.getOrElse(coord, 0.0)
        val progressContainer = div(cls = "tile-progress-container")
        val progressBar = div(id = s"progress-bar-${coord.row}-${coord.col}", cls = "tile-progress-bar")
        progressBar.asInstanceOf[HTMLElement].style.width = s"${(progress * 100).toInt}%"
        progressContainer.appendChild(progressBar)
        tileDiv.appendChild(progressContainer)

        tileDiv.onclick = (_: MouseEvent) => handleLevelUpWheatField(coord)
        tileDiv.oncontextmenu = (e: MouseEvent) =>
          e.preventDefault()
          handleDestroyBuilding(coord)

      case TileType.Farm(level) =>
        tileDiv.classList.add("farm")
        tileDiv.setAttribute("data-level", level.toString)
        val boostPercent = (level * TileKingdomLogic.FarmBoostPerLevel * 100).toInt
        val upgradeCost = TileKingdomLogic.farmLevelUpCost(level)

        val content = div(cls = "tile-content")(
          div(cls = "tile-icon", content = "🏠"),
          div(cls = "tile-label", content = s"Lv$level"),
          div(cls = "tile-production", content = s"+$boostPercent%")
        )

        val upgradeRow = div(cls = "tile-upgrade-row")
        upgradeRow.appendChild(span(cls = "tile-upgrade", content = s"⬆$upgradeCost"))
        upgradeRow.appendChild(button(cls = "btn-x10", content = "x10").tap: btn =>
          btn.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            handleBulkLevelUp(coord, 10, TileKingdomLogic.levelUpFarm, TileKingdomLogic.farmLevelUpCost)
        )
        content.appendChild(upgradeRow)

        tileDiv.appendChild(content)
        tileDiv.onclick = (_: MouseEvent) => handleLevelUpFarm(coord)
        tileDiv.oncontextmenu = (e: MouseEvent) =>
          e.preventDefault()
          handleDestroyBuilding(coord)

      case TileType.Woodcutter(level) =>
        tileDiv.classList.add("woodcutter")
        tileDiv.setAttribute("data-level", level.toString)
        val harvestAmount = TileKingdomLogic.woodProductionPerHarvest(currentGame, tile)
        val upgradeCost = TileKingdomLogic.woodcutterLevelUpCost(level)

        val content = div(cls = "tile-content")(
          div(cls = "tile-icon", content = "🪓"),
          div(cls = "tile-label", content = s"Lv$level")
        )

        val prodDiv = div(cls = "tile-production", content = s"+${harvestAmount.toInt}🪵")
        val forestBonus = TileKingdomLogic.forestGroupBonusMultiplier(currentGame, coord)
        if forestBonus > 1.0 then
          val bonusPercent = ((forestBonus - 1) * 100).toInt
          prodDiv.appendChild(span(cls = "bonus forest-bonus", content = s" +$bonusPercent%"))
        content.appendChild(prodDiv)

        val upgradeRow = div(cls = "tile-upgrade-row")
        upgradeRow.appendChild(span(cls = "tile-upgrade", content = s"⬆$upgradeCost"))
        upgradeRow.appendChild(button(cls = "btn-x10", content = "x10").tap: btn =>
          btn.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            handleBulkLevelUp(coord, 10, TileKingdomLogic.levelUpWoodcutter, TileKingdomLogic.woodcutterLevelUpCost)
        )
        content.appendChild(upgradeRow)

        tileDiv.appendChild(content)

        // Add progress bar
        val progress = tileProgress.getOrElse(coord, 0.0)
        val progressContainer = div(cls = "tile-progress-container")
        val progressBar =
          div(id = s"progress-bar-${coord.row}-${coord.col}", cls = "tile-progress-bar woodcutter-progress")
        progressBar.style.width = s"${(progress * 100).toInt}%"
        progressContainer.appendChild(progressBar)
        tileDiv.appendChild(progressContainer)

        tileDiv.onclick = (_: MouseEvent) => handleLevelUpWoodcutter(coord)
        tileDiv.oncontextmenu = (e: MouseEvent) =>
          e.preventDefault()
          handleDestroyBuilding(coord)

      case TileType.Bureau(level) =>
        tileDiv.classList.add("bureau")
        tileDiv.setAttribute("data-level", level.toString)
        val boosts = currentGame.bureauBoosts.getOrElse(coord, 0)
        val speedMultiplier = TileKingdomLogic.bureauSpeedMultiplier(currentGame, coord)

        val content = div(cls = "tile-content")(
          div(cls = "tile-icon", content = "🏛️"),
          div(cls = "tile-label", content = if boosts > 0 then s"x${speedMultiplier.toInt}" else "Bureau")
        )

        content.appendChild(div(cls = "tile-production", content = s"Auto⬆"))
        
        // Add boost button if player has enough faith
        val boostRow = div(cls = "tile-upgrade-row")
        boostRow.appendChild(button(cls = "btn-boost", content = s"⚡${TileKingdomLogic.FaithBoostCost}✨").tap: btn =>
          btn.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            handleBoostBureau(coord)
        )
        content.appendChild(boostRow)

        tileDiv.appendChild(content)

        // Add progress bar
        val progress = tileProgress.getOrElse(coord, 0.0)
        val progressContainer = div(cls = "tile-progress-container")
        val progressBar = div(id = s"progress-bar-${coord.row}-${coord.col}", cls = "tile-progress-bar bureau-progress")
        progressBar.style.width = s"${(progress * 100).toInt}%"
        progressContainer.appendChild(progressBar)
        tileDiv.appendChild(progressContainer)

        tileDiv.oncontextmenu = (e: MouseEvent) =>
          e.preventDefault()
          handleDestroyBuilding(coord)

      case TileType.Temple(level) =>
        tileDiv.classList.add("temple")
        tileDiv.setAttribute("data-level", level.toString)
        val faithAmount = TileKingdomLogic.faithProductionPerHarvest(tile)
        val upgradeCost = TileKingdomLogic.templeLevelUpCost(level)

        val content = div(cls = "tile-content")(
          div(cls = "tile-icon", content = "⛪"),
          div(cls = "tile-label", content = s"Lv$level")
        )

        content.appendChild(div(cls = "tile-production temple-production", content = s"+${faithAmount.toInt}✨"))

        val upgradeRow = div(cls = "tile-upgrade-row")
        upgradeRow.appendChild(span(cls = "tile-upgrade", content = s"⬆$upgradeCost🪵"))
        upgradeRow.appendChild(button(cls = "btn-x10", content = "x10").tap: btn =>
          btn.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            handleBulkLevelUpTemple(coord, 10)
        )
        content.appendChild(upgradeRow)

        tileDiv.appendChild(content)

        // Add progress bar
        val progress = tileProgress.getOrElse(coord, 0.0)
        val progressContainer = div(cls = "tile-progress-container")
        val progressBar = div(id = s"progress-bar-${coord.row}-${coord.col}", cls = "tile-progress-bar temple-progress")
        progressBar.style.width = s"${(progress * 100).toInt}%"
        progressContainer.appendChild(progressBar)
        tileDiv.appendChild(progressContainer)

        tileDiv.onclick = (_: MouseEvent) => handleLevelUpTemple(coord)
        tileDiv.oncontextmenu = (e: MouseEvent) =>
          e.preventDefault()
          handleDestroyBuilding(coord)

    tileDiv

  private def renderUnlockableTile(coord: Coord): HTMLElement =
    val tileDiv = div(id = s"tile-${coord.row}-${coord.col}", cls = "tile-kingdom-tile locked unlockable")
    val tilePixelSize = (70 * zoomLevel).toInt
    val fontScale = math.max(0.6, math.min(1.0, zoomLevel))

    // Position the tile absolutely with zoom-adjusted size
    tileDiv.style.cssText =
      s"position: absolute; left: ${coord.col * TileSize}px; top: ${coord
          .row * TileSize}px; width: ${tilePixelSize}px; height: ${tilePixelSize}px; font-size: ${fontScale}em;"

    val cost = currentGame.nextTileUnlockCost

    tileDiv.appendChild(
      div(cls = "tile-content")(
        div(cls = "tile-icon", content = "🔓"),
        div(cls = "tile-cost", content = s"$cost 💰")
      )
    )

    tileDiv.onclick = (_: MouseEvent) => handleUnlockTile(coord)

    tileDiv

  private def renderAbdicationButton(): Unit =
    getElementById("tile-kingdom-abdicate-btn").foreach: elem =>
      val btn = elem.asInstanceOf[HTMLButtonElement]
      if currentGame.allTilesFilled then
        btn.disabled = false
        btn.classList.remove("disabled")
        val reward = currentGame.abdicationGoldReward
        btn.textContent = s"Abdicate (+$reward 💰)"
      else
        btn.disabled = true
        btn.classList.add("disabled")
        btn.textContent = "Abdicate"

  // ============================================================================
  // Event Handlers
  // ============================================================================

  private def handleBuildWheatField(coord: Coord): Unit =
    val cost = TileKingdomLogic.wheatFieldBuildCost
    TileKingdomLogic.buildWheatField(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, cost, "🌾", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleBuildFarm(coord: Coord): Unit =
    val cost = TileKingdomLogic.farmBuildCost
    TileKingdomLogic.buildFarm(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, cost, "🌾", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleBuildWoodcutter(coord: Coord): Unit =
    val cost = TileKingdomLogic.woodcutterBuildCost
    TileKingdomLogic.buildWoodcutter(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, cost, "🌾", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleBuildBureau(coord: Coord): Unit =
    val cost = TileKingdomLogic.bureauBuildCost
    TileKingdomLogic.buildBureau(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, cost, "🪵", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleBuildTemple(coord: Coord): Unit =
    val cost = TileKingdomLogic.templeBuildCost
    TileKingdomLogic.buildTemple(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, cost, "🪵", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleLevelUpWheatField(coord: Coord): Unit =
    currentGame.tiles.get(coord).foreach: tile =>
      val cost = TileKingdomLogic.wheatFieldLevelUpCost(tile.level)
      TileKingdomLogic.levelUpWheatField(currentGame, coord) match
        case Right(newGame) =>
          currentGame = newGame
          saveGame()
          renderGame()
          showFloatingReward(coord, cost, "🌾", isSpend = true)
          showFloatingLevel(coord, tile.level + 1)
        case Left(error) =>
          showNotification(error)

  private def handleLevelUpFarm(coord: Coord): Unit =
    currentGame.tiles.get(coord).foreach: tile =>
      val cost = TileKingdomLogic.farmLevelUpCost(tile.level)
      TileKingdomLogic.levelUpFarm(currentGame, coord) match
        case Right(newGame) =>
          currentGame = newGame
          saveGame()
          renderGame()
          showFloatingReward(coord, cost, "🌾", isSpend = true)
          showFloatingLevel(coord, tile.level + 1)
        case Left(error) =>
          showNotification(error)

  private def handleLevelUpWoodcutter(coord: Coord): Unit =
    currentGame.tiles.get(coord).foreach: tile =>
      val cost = TileKingdomLogic.woodcutterLevelUpCost(tile.level)
      TileKingdomLogic.levelUpWoodcutter(currentGame, coord) match
        case Right(newGame) =>
          currentGame = newGame
          saveGame()
          renderGame()
          showFloatingReward(coord, cost, "🌾", isSpend = true)
          showFloatingLevel(coord, tile.level + 1)
        case Left(error) =>
          showNotification(error)

  private def handleBulkLevelUp(
                                 coord: Coord,
                                 count: Int,
                                 levelUpFn: (TileKingdomGame, Coord) => Either[String, TileKingdomGame],
                                 costFn: Int => Int
  ): Unit =
    currentGame.tiles.get(coord).foreach: tile =>
      var game = currentGame
      var totalCost = 0
      var successCount = 0
      var currentLevel = tile.level

      (1 to count).foreach: _ =>
        levelUpFn(game, coord) match
          case Right(newGame) =>
            totalCost += costFn(currentLevel)
            currentLevel += 1
            successCount += 1
            game = newGame
          case Left(_) => // Stop on first failure

      if successCount > 0 then
        currentGame = game
        saveGame()
        renderGame()
        showFloatingReward(coord, totalCost, "🌾", isSpend = true)
        showFloatingLevel(coord, currentLevel)
      else
        showNotification("Not enough wheat")

  private def handleLevelUpTemple(coord: Coord): Unit =
    currentGame.tiles.get(coord).foreach: tile =>
      val cost = TileKingdomLogic.templeLevelUpCost(tile.level)
      TileKingdomLogic.levelUpTemple(currentGame, coord) match
        case Right(newGame) =>
          currentGame = newGame
          saveGame()
          renderGame()
          showFloatingReward(coord, cost, "🪵", isSpend = true)
          showFloatingLevel(coord, tile.level + 1)
        case Left(error) =>
          showNotification(error)

  private def handleBulkLevelUpTemple(coord: Coord, count: Int): Unit =
    currentGame.tiles.get(coord).foreach: tile =>
      var game = currentGame
      var totalCost = 0
      var successCount = 0
      var currentLevel = tile.level

      (1 to count).foreach: _ =>
        TileKingdomLogic.levelUpTemple(game, coord) match
          case Right(newGame) =>
            totalCost += TileKingdomLogic.templeLevelUpCost(currentLevel)
            currentLevel += 1
            successCount += 1
            game = newGame
          case Left(_) => // Stop on first failure

      if successCount > 0 then
        currentGame = game
        saveGame()
        renderGame()
        showFloatingReward(coord, totalCost, "🪵", isSpend = true)
        showFloatingLevel(coord, currentLevel)
      else
        showNotification("Not enough wood")

  private def handleBoostBureau(coord: Coord): Unit =
    TileKingdomLogic.boostBureau(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, TileKingdomLogic.FaithBoostCost, "✨", isSpend = true)
        showNotification("Bureau speed boosted!")
      case Left(error) =>
        showNotification(error)

  private def handleDestroyBuilding(coord: Coord): Unit =
    TileKingdomLogic.destroyBuilding(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        tileProgress = tileProgress.removed(coord) // Remove progress tracking for this tile
        saveGame()
        renderGame()
        showNotification("Building destroyed")
      case Left(error) =>
        showNotification(error)

  private def handleAbdicate(): Unit =
    if currentGame.allTilesFilled then
      val reward = currentGame.abdicationGoldReward
      if window.confirm(s"Abdicate and earn $reward gold? This will reset all your buildings.") then
        TileKingdomLogic.abdicate(currentGame, System.currentTimeMillis()) match
          case Right(newGame) =>
            currentGame = newGame
            tileProgress = Map.empty // Reset progress tracking
            saveGame()
            renderGame()
            showNotification(s"Abdicated! +$reward gold")
          case Left(error) =>
            showNotification(error)

  private def handleUnlockTile(coord: Coord): Unit =
    val cost = currentGame.nextTileUnlockCost
    TileKingdomLogic.unlockTile(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, cost, "💰", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleResetGame(): Unit =
    if window.confirm("Reset game? This will delete all progress!") then
      window.localStorage.removeItem(StorageKey)
      currentGame = TileKingdomLogic.newGame(System.currentTimeMillis())
      tileProgress = Map.empty
      saveGame()
      centerOnKingdom()
      renderGame()
      showNotification("Game reset!")

  // ============================================================================
  // Utilities
  // ============================================================================

  private def setElementText(id: String, text: String): Unit =
    getElementById(id).foreach(_.textContent = text)

  private def showNotification(message: String): Unit =
    getElementById("tile-kingdom-notification").foreach: notification =>
      notification.textContent = message
      notification.classList.add("show")
      window.setTimeout(
        () =>
          notification.classList.remove("show"),
        3000
      )

  private def updateProgressBars(): Unit =
    currentGame.unlockedTiles.filter(t => t.isWheatField || t.isWoodcutter || t.isBureau).foreach: tile =>
      val coord = tile.coord
      val progress = tileProgress.getOrElse(coord, 0.0)
      getElementById(s"progress-bar-${coord.row}-${coord.col}").foreach: bar =>
        bar.style.width = s"${(progress * 100).toInt}%"

  private def showFloatingReward(coord: Coord, amount: Int, emoji: String = "", isSpend: Boolean = false): Unit =
    getElementById(s"tile-${coord.row}-${coord.col}").foreach: tileElem =>
      val floater = div()
      floater.className = if isSpend then "floating-reward floating-spend" else "floating-reward"
      val sign = if isSpend then "-" else "+"
      floater.textContent = s"$sign$amount$emoji"
      tileElem.appendChild(floater)

      // Remove after animation completes
      window.setTimeout(() => floater.remove(), 1000)

  private def showFloatingLevel(coord: Coord, level: Int): Unit =
    getElementById(s"tile-${coord.row}-${coord.col}").foreach: tileElem =>
      val floater = div()
      floater.className = "floating-reward floating-level"
      floater.textContent = s"Level $level"
      tileElem.appendChild(floater)

      // Remove after animation completes
      window.setTimeout(() => floater.remove(), 1000)
