package client

import org.scalajs.dom.*
import scala.util.Try
import shared.Territory.*

def initializeTerritory(): Unit =
  TerritoryClient.init()

object TerritoryClient:

  private val StorageKey = "territory_game_state"
  private var currentGame: TerritoryGame = TerritoryLogic.newGame(System.currentTimeMillis())
  private var gameTickerHandle: Option[Int] = None

  // ============================================================================
  // Initialization
  // ============================================================================

  def init(): Unit =
    println("[Territory] Initializing Territory Idle game")
    loadGame()
    renderGame()
    startGameTicker()
    attachEventListeners()

  def cleanup(): Unit =
    println("[Territory] Cleaning up Territory Idle game")
    stopGameTicker()

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
    currentGame = TerritoryLogic.tick(currentGame, currentTime)
    saveGame()
    renderGame()

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
    renderUnlockButton()

  private def renderResources(): Unit =
    setElementText("territory-wheat", f"${currentGame.wheat.toInt}%,d")
    setElementText("territory-gold", f"${currentGame.gold}%,d")
    setElementText("territory-income-rate", f"${currentGame.totalIncomeRate}%.1f wheat/s")
    setElementText("territory-abdications", currentGame.totalAbdications.toString)

  private def renderTiles(): Unit =
    val gridContainer = document.getElementById("territory-grid")
    if gridContainer != null then
      gridContainer.innerHTML = ""

      // Calculate grid size (2x2, 3x3, 4x4)
      val unlockedCount = currentGame.unlockedTiles.size
      val gridSize = if unlockedCount <= 4 then 2
                     else if unlockedCount <= 9 then 3
                     else 4

      // Update grid layout
      val elem = gridContainer.asInstanceOf[HTMLElement]
      val currentStyle = Option(elem.getAttribute("style")).getOrElse("")
      elem.setAttribute("style", currentStyle + s"; grid-template-columns: repeat($gridSize, 1fr)")

      // Render all tiles (up to max visible)
      val tilesToShow = math.min(gridSize * gridSize, TerritoryLogic.MaxTiles)
      (0 until tilesToShow).foreach: id =>
        currentGame.tiles.get(id) match
          case Some(tile) => renderTile(gridContainer, tile)
          case None => ()

  private def renderTile(container: Element, tile: Tile): Unit =
    val tileDiv = document.createElement("div").asInstanceOf[HTMLElement]
    tileDiv.className = "territory-tile"
    tileDiv.id = s"tile-${tile.id}"

    if !tile.unlocked then
      tileDiv.classList.add("locked")
      tileDiv.innerHTML = """<div class="tile-content">🔒</div>"""
    else
      tileDiv.classList.add("unlocked")
      tile.tileType match
        case TileType.Empty =>
          tileDiv.classList.add("empty")
          val wheatCost = TerritoryLogic.wheatFieldBuildCost
          val farmCost = TerritoryLogic.farmBuildCost
          val canBuildFarm = currentGame.hasWheatField
          
          if canBuildFarm then
            tileDiv.innerHTML = s"""
              <div class="tile-content tile-build-options">
                <div class="build-option" data-build="wheat">
                  <div class="build-icon">🌾</div>
                  <div class="build-label">Wheat</div>
                  <div class="build-cost">$wheatCost 🌾</div>
                </div>
                <div class="build-option" data-build="farm">
                  <div class="build-icon">🏠</div>
                  <div class="build-label">Farm</div>
                  <div class="build-cost">$farmCost 🌾</div>
                </div>
              </div>
            """
            // Attach click handlers to build options
            tileDiv.querySelector(".build-option[data-build='wheat']")
              .asInstanceOf[HTMLElement]
              .onclick = (e: MouseEvent) => 
                e.stopPropagation()
                handleBuildWheatField(tile.id)
            tileDiv.querySelector(".build-option[data-build='farm']")
              .asInstanceOf[HTMLElement]
              .onclick = (e: MouseEvent) => 
                e.stopPropagation()
                handleBuildFarm(tile.id)
          else
            tileDiv.innerHTML = s"""
              <div class="tile-content">
                <div class="tile-icon">➕</div>
                <div class="tile-label">Build</div>
                <div class="tile-cost">$wheatCost 🌾</div>
              </div>
            """
            tileDiv.onclick = (e: MouseEvent) => handleBuildWheatField(tile.id)

        case TileType.WheatField(level) =>
          tileDiv.classList.add("wheat-field")
          tileDiv.setAttribute("data-level", level.toString)
          val baseProduction = TerritoryLogic.baseProductionRate(tile)
          val actualProduction = TerritoryLogic.productionRate(currentGame, tile)
          val hasBonus = actualProduction > baseProduction
          val productionStr = f"$actualProduction%.1f"
          val bonusStr = if hasBonus then s" <span class='bonus'>(+${((actualProduction / baseProduction - 1) * 100).toInt}%)</span>" else ""
          val upgradeCost = TerritoryLogic.wheatFieldLevelUpCost(level)
          tileDiv.innerHTML = s"""
            <div class="tile-content">
              <div class="tile-icon">🌾</div>
              <div class="tile-label">Level $level</div>
              <div class="tile-production">+$productionStr/s$bonusStr</div>
              <div class="tile-upgrade">⬆ $upgradeCost 🌾</div>
            </div>
          """
          tileDiv.onclick = (e: MouseEvent) => handleLevelUpWheatField(tile.id)

        case TileType.Farm(level) =>
          tileDiv.classList.add("farm")
          tileDiv.setAttribute("data-level", level.toString)
          val boostPercent = (level * TerritoryLogic.FarmBoostPerLevel * 100).toInt
          val upgradeCost = TerritoryLogic.farmLevelUpCost(level)
          tileDiv.innerHTML = s"""
            <div class="tile-content">
              <div class="tile-icon">🏠</div>
              <div class="tile-label">Farm Lv$level</div>
              <div class="tile-production">+$boostPercent% nearby</div>
              <div class="tile-upgrade">⬆ $upgradeCost 🌾</div>
            </div>
          """
          tileDiv.onclick = (e: MouseEvent) => handleLevelUpFarm(tile.id)

    container.appendChild(tileDiv)

  private def renderAbdicationButton(): Unit =
    val button = document.getElementById("territory-abdicate-btn").asInstanceOf[HTMLButtonElement]
    if button != null then
      if currentGame.allTilesFilled then
        button.disabled = false
        button.classList.remove("disabled")
        val reward = currentGame.abdicationGoldReward
        button.textContent = s"Abdicate (+$reward gold)"
      else
        button.disabled = true
        button.classList.add("disabled")
        button.textContent = "Abdicate (fill all tiles first)"

  private def renderUnlockButton(): Unit =
    val button = document.getElementById("territory-unlock-btn").asInstanceOf[HTMLButtonElement]
    if button != null then
      if currentGame.lockedTiles.isEmpty then
        button.style.display = "none"
      else
        button.style.display = "block"
        val cost = currentGame.nextTileUnlockCost
        val canAfford = currentGame.gold >= cost
        button.disabled = !canAfford
        if canAfford then
          button.classList.remove("disabled")
        else
          button.classList.add("disabled")
        button.textContent = s"Unlock Tile ($cost gold)"

  // ============================================================================
  // Event Handlers
  // ============================================================================

  private def attachEventListeners(): Unit =
    // Abdicate button
    Option(document.getElementById("territory-abdicate-btn")).foreach: btn =>
      btn.addEventListener("click", (e: Event) => handleAbdicate())

    // Unlock tile button
    Option(document.getElementById("territory-unlock-btn")).foreach: btn =>
      btn.addEventListener("click", (e: Event) => handleUnlockTile())

    // Reset game button (for testing)
    Option(document.getElementById("territory-reset-btn")).foreach: btn =>
      btn.addEventListener("click", (e: Event) => handleResetGame())

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

  private def handleUnlockTile(): Unit =
    TerritoryLogic.unlockTile(currentGame) match
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
    Option(document.getElementById(id)).foreach(_.textContent = text)

  private def showNotification(message: String): Unit =
    val notification = document.getElementById("territory-notification")
    if notification != null then
      notification.textContent = message
      notification.classList.add("show")
      window.setTimeout(() =>
        notification.classList.remove("show")
      , 3000)

