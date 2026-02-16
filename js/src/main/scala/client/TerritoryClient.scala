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
  private var tileProgress: Map[Int, Double] = Map.empty
  private val ProductionIntervalMs: Double = TerritoryLogic.ProductionIntervalSeconds * 1000.0

  // ============================================================================
  // Initialization
  // ============================================================================

  def init(): Unit =
    println("[Territory] Initializing Territory Idle game")
    loadGame()
    buildUI()
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
    container.appendChild(buildHeader())
    container.appendChild(buildResources())
    container.appendChild(buildGrid())
    container.appendChild(buildActions())
    container.appendChild(buildNotification())
    container.appendChild(buildHelpPopup())

  private def buildHeader(): HTMLElement =
    div(cls = "territory-header")(
      h1(content = "🏰 Territory Idle"),
      button(cls = "help-button", content = "?").tap: btn =>
        btn.onclick = (_: MouseEvent) => toggleHelpPopup()
    )

  private def buildResources(): HTMLElement =
    div(cls = "territory-resources")(
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "🌾 Wheat:"),
        span(id = "territory-wheat", cls = "resource-value", content = "0")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "💰 Gold:"),
        span(id = "territory-gold", cls = "resource-value", content = "0")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "⚡ Income:"),
        span(id = "territory-income-rate", cls = "resource-value", content = "0.0 wheat/s")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "👑 Abdications:"),
        span(id = "territory-abdications", cls = "resource-value", content = "0")
      )
    )

  private def buildGrid(): HTMLElement =
    div(id = "territory-grid", cls = "territory-grid")

  private def buildActions(): HTMLElement =
    div(cls = "territory-actions")(
      button(id = "territory-abdicate-btn", cls = "btn-primary disabled", content = "Abdicate (fill all tiles first)").tap: btn =>
        btn.disabled = true
        btn.onclick = (_: MouseEvent) => handleAbdicate()
      ,
      button(id = "territory-reset-btn", cls = "btn-danger", content = "Reset").tap: btn =>
        btn.onclick = (_: MouseEvent) => handleResetGame()
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
          p(content = "🔓 Click adjacent tiles to expand your territory")
        )
      )
    )

  private def toggleHelpPopup(): Unit =
    getElementById("territory-help-popup").foreach: popup =>
      popup.classList.toggle("show")

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
    
    // Update progress for each wheat field and collect harvests
    var totalHarvested = 0.0
    val wheatFields = currentGame.unlockedTiles.filter(_.isWheatField)
    
    wheatFields.foreach: tile =>
      val currentProgress = tileProgress.getOrElse(tile.id, 0.0)
      val progressIncrement = elapsedMs / ProductionIntervalMs
      val newProgress = currentProgress + progressIncrement
      
      if newProgress >= 1.0 then
        // Harvest!
        val harvests = newProgress.toInt
        val production = TerritoryLogic.productionPerHarvest(currentGame, tile)
        totalHarvested += production * harvests
        tileProgress = tileProgress.updated(tile.id, newProgress - harvests)
        
        // Show floating reward
        showFloatingReward(tile.id, (production * harvests).toInt)
      else
        tileProgress = tileProgress.updated(tile.id, newProgress)
    
    // Add harvested wheat to game state
    currentGame = currentGame.copy(
      wheat = currentGame.wheat + totalHarvested,
      lastTickTime = currentTime
    )
    
    saveGame()
    updateProgressBars()
    renderResources()

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
            println(s"[Territory] Welcome back! You earned ${offlineWheat.toInt} wheat while away (${(offlineSeconds / 60).toInt} minutes)")
            showNotification(s"Welcome back! You earned ${offlineWheat.toInt} wheat while offline.")

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
    setElementText("territory-gold", f"${currentGame.gold}%,d")
    setElementText("territory-income-rate", f"${currentGame.totalIncomeRate}%.1f wheat/s")
    setElementText("territory-abdications", currentGame.totalAbdications.toString)

  private def renderTiles(): Unit =
    getElementById("territory-grid").foreach: gridContainer =>
      gridContainer.innerHTML = ""

      // Render all 64 tiles
      (0 until TerritoryLogic.MaxTiles).foreach: id =>
        currentGame.tiles.get(id).foreach: tile =>
          gridContainer.appendChild(renderTile(tile))

  private def renderTile(tile: Tile): HTMLElement =
    val tileDiv = div(id = s"tile-${tile.id}", cls = "territory-tile")

    if !tile.unlocked then
      tileDiv.classList.add("locked")
      val isUnlockable = TerritoryLogic.unlockableTileIds(currentGame).contains(tile.id)
      val cost = currentGame.nextTileUnlockCost
      val canAfford = currentGame.gold >= cost

      if isUnlockable then
        tileDiv.classList.add("unlockable")
        tileDiv.appendChild(
          div(cls = "tile-content")(
            div(cls = "tile-icon", content = "🔓"),
            div(cls = "tile-cost", content = s"$cost 💰")
          )
        )
        if canAfford then
          tileDiv.classList.add("affordable")
          tileDiv.onclick = (_: MouseEvent) => handleUnlockTile(tile.id)
      else
        tileDiv.appendChild(div(cls = "tile-content", content = "🔒"))
    else
      tileDiv.classList.add("unlocked")
      tile.tileType match
        case TileType.Empty =>
          tileDiv.classList.add("empty")
          val wheatCost = TerritoryLogic.wheatFieldBuildCost
          val farmCost = TerritoryLogic.farmBuildCost
          val canBuildFarm = currentGame.hasWheatField

          if canBuildFarm then
            tileDiv.appendChild(
              div(cls = "tile-content tile-build-options")(
                div(cls = "build-option").tap: opt =>
                  opt.appendChild(div(cls = "build-icon", content = "🌾"))
                  opt.appendChild(div(cls = "build-label", content = "Wheat"))
                  opt.appendChild(div(cls = "build-cost", content = s"$wheatCost"))
                  opt.onclick = (e: MouseEvent) =>
                    e.stopPropagation()
                    handleBuildWheatField(tile.id)
                ,
                div(cls = "build-option").tap: opt =>
                  opt.appendChild(div(cls = "build-icon", content = "🏠"))
                  opt.appendChild(div(cls = "build-label", content = "Farm"))
                  opt.appendChild(div(cls = "build-cost", content = s"$farmCost"))
                  opt.onclick = (e: MouseEvent) =>
                    e.stopPropagation()
                    handleBuildFarm(tile.id)
              )
            )
          else
            tileDiv.appendChild(
              div(cls = "tile-content")(
                div(cls = "tile-icon", content = "➕"),
                div(cls = "tile-cost", content = s"$wheatCost 🌾")
              )
            )
            tileDiv.onclick = (_: MouseEvent) => handleBuildWheatField(tile.id)

        case TileType.WheatField(level) =>
          tileDiv.classList.add("wheat-field")
          tileDiv.setAttribute("data-level", level.toString)
          val baseProduction = TerritoryLogic.baseProductionRate(tile)
          val actualProduction = TerritoryLogic.productionRate(currentGame, tile)
          val harvestAmount = TerritoryLogic.productionPerHarvest(currentGame, tile)
          val hasBonus = actualProduction > TerritoryLogic.productionPerSecond(tile)
          val productionStr = f"$actualProduction%.1f"
          val upgradeCost = TerritoryLogic.wheatFieldLevelUpCost(level)

          val content = div(cls = "tile-content")(
            div(cls = "tile-icon", content = "🌾"),
            div(cls = "tile-label", content = s"Lv$level")
          )

          val prodDiv = div(cls = "tile-production", content = s"+${harvestAmount.toInt}")
          if hasBonus then
            val bonusPercent = ((TerritoryLogic.farmBonusMultiplier(currentGame, tile.id) - 1) * 100).toInt
            prodDiv.appendChild(span(cls = "bonus", content = s" +$bonusPercent%"))
          content.appendChild(prodDiv)
          content.appendChild(div(cls = "tile-upgrade", content = s"⬆$upgradeCost"))

          tileDiv.appendChild(content)
          
          // Add progress bar
          val progress = tileProgress.getOrElse(tile.id, 0.0)
          val progressContainer = div(cls = "tile-progress-container")
          val progressBar = div(id = s"progress-bar-${tile.id}", cls = "tile-progress-bar")
          progressBar.asInstanceOf[HTMLElement].style.width = s"${(progress * 100).toInt}%"
          progressContainer.appendChild(progressBar)
          tileDiv.appendChild(progressContainer)
          
          tileDiv.onclick = (_: MouseEvent) => handleLevelUpWheatField(tile.id)

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
          tileDiv.onclick = (_: MouseEvent) => handleLevelUpFarm(tile.id)

    tileDiv

  private def renderAbdicationButton(): Unit =
    getElementById("territory-abdicate-btn").foreach: elem =>
      val btn = elem.asInstanceOf[HTMLButtonElement]
      if currentGame.allTilesFilled then
        btn.disabled = false
        btn.classList.remove("disabled")
        val reward = currentGame.abdicationGoldReward
        btn.textContent = s"Abdicate (+$reward gold)"
      else
        btn.disabled = true
        btn.classList.add("disabled")
        btn.textContent = "Abdicate (fill all tiles first)"


  // ============================================================================
  // Event Handlers
  // ============================================================================

  private def handleBuildWheatField(tileId: Int): Unit =
    TerritoryLogic.buildWheatField(currentGame, tileId) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
      case Left(error) =>
        showNotification(error)

  private def handleBuildFarm(tileId: Int): Unit =
    TerritoryLogic.buildFarm(currentGame, tileId) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
      case Left(error) =>
        showNotification(error)

  private def handleLevelUpWheatField(tileId: Int): Unit =
    TerritoryLogic.levelUpWheatField(currentGame, tileId) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
      case Left(error) =>
        showNotification(error)

  private def handleLevelUpFarm(tileId: Int): Unit =
    TerritoryLogic.levelUpFarm(currentGame, tileId) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
      case Left(error) =>
        showNotification(error)

  private def handleAbdicate(): Unit =
    if currentGame.allTilesFilled then
      val reward = currentGame.abdicationGoldReward
      if window.confirm(s"Abdicate and earn $reward gold? This will reset all your buildings.") then
        TerritoryLogic.abdicate(currentGame, System.currentTimeMillis()) match
          case Right(newGame) =>
            currentGame = newGame
            saveGame()
            renderGame()
            showNotification(s"Abdicated! Earned $reward gold.")
          case Left(error) =>
            showNotification(error)

  private def handleUnlockTile(tileId: Int): Unit =
    TerritoryLogic.unlockTile(currentGame, tileId) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showNotification("Tile unlocked!")
      case Left(error) =>
        showNotification(error)

  private def handleResetGame(): Unit =
    if window.confirm("Reset game? This will delete all progress!") then
      window.localStorage.removeItem(StorageKey)
      currentGame = TerritoryLogic.newGame(System.currentTimeMillis())
      saveGame()
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
    currentGame.unlockedTiles.filter(_.isWheatField).foreach: tile =>
      val progress = tileProgress.getOrElse(tile.id, 0.0)
      getElementById(s"progress-bar-${tile.id}").foreach: bar =>
        bar.asInstanceOf[HTMLElement].style.width = s"${(progress * 100).toInt}%"

  private def showFloatingReward(tileId: Int, amount: Int): Unit =
    getElementById(s"tile-$tileId").foreach: tileElem =>
      val floater = document.createElement("div").asInstanceOf[HTMLElement]
      floater.className = "floating-reward"
      floater.textContent = s"+$amount"
      tileElem.appendChild(floater)
      
      // Remove after animation completes
      window.setTimeout(() => floater.remove(), 1000)

