package client

import org.scalajs.dom.*
import scala.util.Try
import scala.util.chaining.*
import shared.Territory.*

def initializeTerritory(): Unit =
  TerritoryClient.init()

object TerritoryClient:

  private val StorageKey = "territory_game_state"
  private var currentGame: TerritoryGame = TerritoryLogic.newGame(System.currentTimeMillis())
  private var gameTickerHandle: Option[Int] = None

  // Track progress (0.0 to 1.0) for each wheat field tile
  private var tileProgress: Map[Coord, Double] = Map.empty
  private val ProductionIntervalMs: Double = TerritoryLogic.ProductionIntervalSeconds * 1000.0
  private val BureauIntervalMs: Double = TerritoryLogic.BureauIntervalSeconds * 1000.0

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
    println("[Territory] Initializing Territory Idle game")
    loadGame()
    buildUI()
    centerOnTerritory()
    renderGame()
    startGameTicker()

  def cleanup(): Unit =
    println("[Territory] Cleaning up Territory Idle game")
    stopGameTicker()

  // ============================================================================
  // UI Building
  // ============================================================================

  private def buildUI(): Unit =
    val container = getElementById("territory-container").getOrElse:
      document.body.appendChild(div(id = "territory-container", cls = "territory-container"))
      document.getElementById("territory-container").asInstanceOf[HTMLElement]

    container.innerHTML = ""

    // Grid viewport (draggable area)
    val viewport = div(id = "territory-grid-viewport", cls = "territory-grid-viewport")
    viewport.appendChild(div(id = "territory-grid", cls = "territory-grid"))
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
    div(cls = "territory-header")(
      h1(content = "🏰 Territory Idle"),
      button(cls = "help-button", content = "?").tap: btn =>
        btn.onclick = (_: MouseEvent) => toggleHelpPopup()
    )

  private def buildResources(): HTMLElement =
    div(cls = "territory-resources")(
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "🌾"),
        span(id = "territory-wheat", cls = "resource-value", content = "0")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "🪵"),
        span(id = "territory-wood", cls = "resource-value", content = "0")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "💰"),
        span(id = "territory-gold", cls = "resource-value", content = "0")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "👑"),
        span(id = "territory-abdications", cls = "resource-value", content = "0")
      )
    )

  private def buildActions(): HTMLElement =
    div(cls = "territory-actions")(
      button(id = "territory-abdicate-btn", cls = "btn-primary disabled", content = "Abdicate").tap: btn =>
        btn.disabled = true
        btn.onclick = (_: MouseEvent) => handleAbdicate()
      ,
      button(id = "territory-center-btn", cls = "btn-secondary", content = "⌖ Center").tap: btn =>
        btn.onclick = (_: MouseEvent) => centerOnTerritory(animated = true)
      ,
      button(id = "territory-reset-btn", cls = "btn-danger", content = "Reset").tap: btn =>
        btn.onclick = (_: MouseEvent) => handleResetGame()
      ,
      button(id = "territory-dev-btn", cls = "btn-dev", content = "🛠️ Dev").tap: btn =>
        btn.onclick = (_: MouseEvent) => toggleDevTools()
    )

  private def buildNotification(): HTMLElement =
    div(id = "territory-notification", cls = "notification")

  private def buildHelpPopup(): HTMLElement =
    div(id = "territory-help-popup", cls = "help-popup")(
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
    div(id = "territory-dev-popup", cls = "help-popup")(
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
              currentGame = TerritoryLogic.unlockManyTiles(currentGame, 100)
              saveGame()
              renderGame()
              showNotification(s"Added 100 tiles")
        )
      )
    )

  private def toggleDevTools(): Unit =
    getElementById("territory-dev-popup").foreach: popup =>
      popup.classList.toggle("show")

  private def toggleHelpPopup(): Unit =
    getElementById("territory-help-popup").foreach: popup =>
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
        getElementById("territory-grid-viewport").foreach(_.asInstanceOf[HTMLElement].style.cursor = "grab")
        snapBackIfNeeded()

    // Touch support
    viewport.addEventListener("touchstart", (e: TouchEvent) =>
      if e.touches.length == 1 then
        val touch = e.touches(0)
        isDragging = true
        dragStartX = touch.clientX
        dragStartY = touch.clientY
        panStartX = panOffsetX
        panStartY = panOffsetY
    )

    viewport.addEventListener("touchmove", (e: TouchEvent) =>
      if isDragging && e.touches.length == 1 then
        e.preventDefault()
        val touch = e.touches(0)
        val dx = touch.clientX - dragStartX
        val dy = touch.clientY - dragStartY
        panOffsetX = panStartX + dx
        panOffsetY = panStartY + dy
        updateGridPosition()
    )

    viewport.addEventListener("touchend", (_: TouchEvent) =>
      if isDragging then
        isDragging = false
        snapBackIfNeeded()
    )

    // Mouse wheel zoom
    viewport.addEventListener("wheel", (e: WheelEvent) =>
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
    getElementById("territory-grid").foreach: grid =>
      grid.asInstanceOf[HTMLElement].style.transform = s"translate(${panOffsetX}px, ${panOffsetY}px)"

  private def centerOnTerritory(animated: Boolean = false): Unit =
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
      showNotification("Snapped back to territory")

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
    val intervalId = window.setInterval(() => gameTick(), TerritoryLogic.TickIntervalSeconds * 1000)
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

    val wheatFields = currentGame.unlockedTiles.filter(_.isWheatField)
    val woodcutters = currentGame.unlockedTiles.filter(_.isWoodcutter)

    // Process wheat fields
    wheatFields.foreach: tile =>
      val currentProgress = tileProgress.getOrElse(tile.coord, 0.0)
      val progressIncrement = elapsedMs / ProductionIntervalMs
      val newProgress = currentProgress + progressIncrement

      if newProgress >= 1.0 then
        val harvests = newProgress.toInt
        val production = TerritoryLogic.productionPerHarvest(currentGame, tile)
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
        val production = TerritoryLogic.woodProductionPerHarvest(currentGame, tile)
        totalWoodHarvested += production * harvests
        tileProgress = tileProgress.updated(tile.coord, newProgress - harvests)
        showFloatingReward(tile.coord, (production * harvests).toInt, "🪵")
      else
        tileProgress = tileProgress.updated(tile.coord, newProgress)

    // Process bureaus
    val bureaus = currentGame.unlockedTiles.filter(_.isBureau)
    var updatedGame = currentGame.copy(
      wheat = currentGame.wheat + totalWheatHarvested,
      wood = currentGame.wood + totalWoodHarvested,
      lastTickTime = currentTime
    )

    // Track upgrades to show floating text after render
    var bureauUpgrades: List[(Coord, Int, Coord, Int)] = List.empty // (upgradedCoord, newLevel, bureauCoord, wheatCost)

    bureaus.foreach: tile =>
      val currentProgress = tileProgress.getOrElse(tile.coord, 0.0)
      val progressIncrement = elapsedMs / BureauIntervalMs
      val newProgress = currentProgress + progressIncrement

      if newProgress >= 1.0 then
        // Bureau ready to attempt an upgrade
        TerritoryLogic.bureauAutoUpgrade(updatedGame, tile.coord, currentTime) match
          case Some((newGame, upgradedCoord)) =>
            updatedGame = newGame
            // Collect upgrade info to show after render
            val upgradedTile = updatedGame.tiles.get(upgradedCoord)
            val upgradeCost = upgradedTile
              .flatMap(t => TerritoryLogic.getUpgradeCost(t.copy(tileType = t.tileType match
                case TileType.WheatField(lvl) => TileType.WheatField(lvl - 1)
                case TileType.Farm(lvl) => TileType.Farm(lvl - 1)
                case TileType.Woodcutter(lvl) => TileType.Woodcutter(lvl - 1)
                case other => other
              )))
              .getOrElse(0)
            val newLevel = upgradedTile.map(_.level).getOrElse(1)
            bureauUpgrades = bureauUpgrades :+ (upgradedCoord, newLevel, tile.coord, upgradeCost)
            tileProgress = tileProgress.updated(tile.coord, 0.0) // Reset progress after successful upgrade
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
    bureauUpgrades.foreach: (upgradedCoord, newLevel, bureauCoord, wheatCost) =>
      showFloatingReward(upgradedCoord, wheatCost, "🌾", isSpend = true)
      showFloatingLevel(upgradedCoord, newLevel)
      showFloatingReward(bureauCoord, TerritoryLogic.BureauWoodCostPerUpgrade, "🪵", isSpend = true)

  // ============================================================================
  // Persistence
  // ============================================================================

  private def saveGame(): Unit =
    Try:
      import upickle.default.*
      val json = write(currentGame)
      window.localStorage.setItem(StorageKey, json)
    .recover:
      case ex => println(s"[Territory] Failed to save game: ${ex.getMessage}")

  private def loadGame(): Unit =
    Try:
      Option(window.localStorage.getItem(StorageKey)) match
        case Some(json) =>
          import upickle.default.*
          val loadedGame = read[TerritoryGame](json)

          // Calculate offline progress
          val currentTime = System.currentTimeMillis()
          currentGame = TerritoryLogic.tick(loadedGame, currentTime)

          val offlineSeconds = (currentTime - loadedGame.lastTickTime) / 1000.0
          if offlineSeconds > 60 then
            val offlineWheat = currentGame.wheat - loadedGame.wheat
            println(s"[Territory] Welcome back! You earned ${offlineWheat.toInt} wheat while away")
            showNotification(s"Welcome back! +${offlineWheat.toInt} wheat")

          println(s"[Territory] Game loaded from localStorage")
        case None =>
          println(s"[Territory] No saved game found, starting new game")
          currentGame = TerritoryLogic.newGame(System.currentTimeMillis())
          saveGame()
    .recover:
      case ex =>
        println(s"[Territory] Failed to load game: ${ex.getMessage}")
        currentGame = TerritoryLogic.newGame(System.currentTimeMillis())

  // ============================================================================
  // UI Rendering
  // ============================================================================

  private def renderGame(): Unit =
    renderResources()
    renderTiles()
    renderAbdicationButton()

  private def renderResources(): Unit =
    setElementText("territory-wheat", f"${currentGame.wheat.toInt}%,d")
    setElementText("territory-wood", f"${currentGame.wood.toInt}%,d")
    setElementText("territory-gold", f"${currentGame.gold}%,d")
    setElementText("territory-abdications", currentGame.totalAbdications.toString)

  private def renderTiles(): Unit =
    getElementById("territory-grid").foreach: gridContainer =>
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
              grid.appendChild(renderInfluenceIndicator(coord, TerritoryLogic.BureauRadius, "bureau-influence"))
            case _ => // No indicator

      // Get all coords we need to render (existing tiles + unlockable if affordable)
      val unlockableCoords = TerritoryLogic.unlockableCoords(currentGame)
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
    val tileDiv = div(id = s"tile-${coord.row}-${coord.col}", cls = "territory-tile")
    val tilePixelSize = (70 * zoomLevel).toInt
    // Scale font size with zoom, but clamp to reasonable range
    val fontScale = math.max(0.6, math.min(1.0, zoomLevel))

    // Position the tile absolutely with zoom-adjusted size
    tileDiv.asInstanceOf[HTMLElement].style.cssText =
      s"position: absolute; left: ${coord.col * TileSize}px; top: ${coord.row * TileSize}px; width: ${tilePixelSize}px; height: ${tilePixelSize}px; font-size: ${fontScale}em;"

    tileDiv.classList.add("unlocked")
    tile.tileType match
      case TileType.Empty =>
        tileDiv.classList.add("empty")
        val wheatCost = TerritoryLogic.wheatFieldBuildCost
        val farmCost = TerritoryLogic.farmBuildCost
        val woodcutterCost = TerritoryLogic.woodcutterBuildCost
        val bureauCost = TerritoryLogic.bureauBuildCost
        val canBuildOthers = currentGame.hasWheatField

        if canBuildOthers then
          tileDiv.appendChild(
            div(cls = "tile-content tile-build-options")(
              div(cls = "build-option").tap: opt =>
                opt.appendChild(div(cls = "build-icon", content = "🌾"))
                opt.appendChild(div(cls = "build-cost", content = s"$wheatCost"))
                opt.onclick = (e: MouseEvent) =>
                  e.stopPropagation()
                  handleBuildWheatField(coord)
              ,
              div(cls = "build-option").tap: opt =>
                opt.appendChild(div(cls = "build-icon", content = "🏠"))
                opt.appendChild(div(cls = "build-cost", content = s"$farmCost"))
                opt.onclick = (e: MouseEvent) =>
                  e.stopPropagation()
                  handleBuildFarm(coord)
              ,
              div(cls = "build-option").tap: opt =>
                opt.appendChild(div(cls = "build-icon", content = "🪓"))
                opt.appendChild(div(cls = "build-cost", content = s"$woodcutterCost"))
                opt.onclick = (e: MouseEvent) =>
                  e.stopPropagation()
                  handleBuildWoodcutter(coord)
              ,
              div(cls = "build-option").tap: opt =>
                opt.appendChild(div(cls = "build-icon", content = "🏛️"))
                opt.appendChild(div(cls = "build-cost wood-cost", content = s"$bureauCost🪵"))
                opt.onclick = (e: MouseEvent) =>
                  e.stopPropagation()
                  handleBuildBureau(coord)
            )
          )
        else
          tileDiv.appendChild(
            div(cls = "tile-content")(
              div(cls = "tile-icon", content = "➕"),
              div(cls = "tile-cost", content = s"$wheatCost 🌾")
            )
          )
          tileDiv.onclick = (_: MouseEvent) => handleBuildWheatField(coord)

      case TileType.WheatField(level) =>
        tileDiv.classList.add("wheat-field")
        tileDiv.setAttribute("data-level", level.toString)
        val harvestAmount = TerritoryLogic.productionPerHarvest(currentGame, tile)
        val bonusMultiplier = TerritoryLogic.farmBonusMultiplier(currentGame, coord)
        val hasBonus = bonusMultiplier > 1.0
        val upgradeCost = TerritoryLogic.wheatFieldLevelUpCost(level)

        val content = div(cls = "tile-content")(
          div(cls = "tile-icon", content = "🌾"),
          div(cls = "tile-label", content = s"Lv$level")
        )

        val prodDiv = div(cls = "tile-production", content = s"+${harvestAmount.toInt}")
        if hasBonus then
          val bonusPercent = ((bonusMultiplier - 1) * 100).toInt
          prodDiv.appendChild(span(cls = "bonus", content = s" +$bonusPercent%"))
        content.appendChild(prodDiv)
        content.appendChild(div(cls = "tile-upgrade", content = s"⬆$upgradeCost"))

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
        val boostPercent = (level * TerritoryLogic.FarmBoostPerLevel * 100).toInt
        val upgradeCost = TerritoryLogic.farmLevelUpCost(level)

        tileDiv.appendChild(
          div(cls = "tile-content")(
            div(cls = "tile-icon", content = "🏠"),
            div(cls = "tile-label", content = s"Lv$level"),
            div(cls = "tile-production", content = s"+$boostPercent%"),
            div(cls = "tile-upgrade", content = s"⬆$upgradeCost")
          )
        )
        tileDiv.onclick = (_: MouseEvent) => handleLevelUpFarm(coord)
        tileDiv.oncontextmenu = (e: MouseEvent) =>
          e.preventDefault()
          handleDestroyBuilding(coord)

      case TileType.Woodcutter(level) =>
        tileDiv.classList.add("woodcutter")
        tileDiv.setAttribute("data-level", level.toString)
        val harvestAmount = TerritoryLogic.woodProductionPerHarvest(currentGame, tile)
        val upgradeCost = TerritoryLogic.woodcutterLevelUpCost(level)

        val content = div(cls = "tile-content")(
          div(cls = "tile-icon", content = "🪓"),
          div(cls = "tile-label", content = s"Lv$level")
        )

        val prodDiv = div(cls = "tile-production", content = s"+${harvestAmount.toInt}🪵")
        val forestBonus = TerritoryLogic.forestGroupBonusMultiplier(currentGame, coord)
        if forestBonus > 1.0 then
          val bonusPercent = ((forestBonus - 1) * 100).toInt
          prodDiv.appendChild(span(cls = "bonus forest-bonus", content = s" +$bonusPercent%"))
        content.appendChild(prodDiv)
        content.appendChild(div(cls = "tile-upgrade", content = s"⬆$upgradeCost"))

        tileDiv.appendChild(content)

        // Add progress bar
        val progress = tileProgress.getOrElse(coord, 0.0)
        val progressContainer = div(cls = "tile-progress-container")
        val progressBar = div(id = s"progress-bar-${coord.row}-${coord.col}", cls = "tile-progress-bar woodcutter-progress")
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

        val content = div(cls = "tile-content")(
          div(cls = "tile-icon", content = "🏛️"),
          div(cls = "tile-label", content = s"Bureau")
        )

        content.appendChild(div(cls = "tile-production", content = s"Auto⬆"))

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

    tileDiv

  private def renderUnlockableTile(coord: Coord): HTMLElement =
    val tileDiv = div(id = s"tile-${coord.row}-${coord.col}", cls = "territory-tile locked unlockable")
    val tilePixelSize = (70 * zoomLevel).toInt
    val fontScale = math.max(0.6, math.min(1.0, zoomLevel))

    // Position the tile absolutely with zoom-adjusted size
    tileDiv.style.cssText =
      s"position: absolute; left: ${coord.col * TileSize}px; top: ${coord.row * TileSize}px; width: ${tilePixelSize}px; height: ${tilePixelSize}px; font-size: ${fontScale}em;"

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
    getElementById("territory-abdicate-btn").foreach: elem =>
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
    val cost = TerritoryLogic.wheatFieldBuildCost
    TerritoryLogic.buildWheatField(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, cost, "🌾", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleBuildFarm(coord: Coord): Unit =
    val cost = TerritoryLogic.farmBuildCost
    TerritoryLogic.buildFarm(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, cost, "🌾", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleBuildWoodcutter(coord: Coord): Unit =
    val cost = TerritoryLogic.woodcutterBuildCost
    TerritoryLogic.buildWoodcutter(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, cost, "🌾", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleBuildBureau(coord: Coord): Unit =
    val cost = TerritoryLogic.bureauBuildCost
    TerritoryLogic.buildBureau(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, cost, "🪵", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleLevelUpWheatField(coord: Coord): Unit =
    currentGame.tiles.get(coord).foreach: tile =>
      val cost = TerritoryLogic.wheatFieldLevelUpCost(tile.level)
      TerritoryLogic.levelUpWheatField(currentGame, coord) match
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
      val cost = TerritoryLogic.farmLevelUpCost(tile.level)
      TerritoryLogic.levelUpFarm(currentGame, coord) match
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
      val cost = TerritoryLogic.woodcutterLevelUpCost(tile.level)
      TerritoryLogic.levelUpWoodcutter(currentGame, coord) match
        case Right(newGame) =>
          currentGame = newGame
          saveGame()
          renderGame()
          showFloatingReward(coord, cost, "🌾", isSpend = true)
          showFloatingLevel(coord, tile.level + 1)
        case Left(error) =>
          showNotification(error)

  private def handleDestroyBuilding(coord: Coord): Unit =
    TerritoryLogic.destroyBuilding(currentGame, coord) match
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
        TerritoryLogic.abdicate(currentGame, System.currentTimeMillis()) match
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
    TerritoryLogic.unlockTile(currentGame, coord) match
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
      currentGame = TerritoryLogic.newGame(System.currentTimeMillis())
      tileProgress = Map.empty
      saveGame()
      centerOnTerritory()
      renderGame()
      showNotification("Game reset!")

  // ============================================================================
  // Utilities
  // ============================================================================

  private def setElementText(id: String, text: String): Unit =
    getElementById(id).foreach(_.textContent = text)

  private def showNotification(message: String): Unit =
    getElementById("territory-notification").foreach: notification =>
      notification.textContent = message
      notification.classList.add("show")
      window.setTimeout(() =>
        notification.classList.remove("show")
      , 3000)

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

