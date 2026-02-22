package client

import org.scalajs.dom.*
import scala.util.Try
import scala.util.chaining.*
import shared.TileKingdom.*

def initializeTileKingdom(): Unit =
  TileKingdomClient.init()

object TileKingdomClient:

  private val StorageKey = "tile_kingdom_game_state"
  private var currentGame: TileKingdomGame = TileKingdomLogic.newGame(System.currentTimeMillis())
  private var gameTickerHandle: Option[Int] = None

  // Resource emoji mapping
  private def resourceEmoji(resource: Resource): String = resource match
    case Resource.Wheat => "🌾"
    case Resource.Wood  => "🪵"
    case Resource.Faith => "✨"
    case Resource.Gold  => "💰"
    case Resource.Stone => "🪨"

  // Track progress (0.0 to 1.0) for each wheat field tile
  private var tileProgress: Map[Coord, Double] = Map.empty
  private val ProductionIntervalMs: Double = TileKingdomLogic.ProductionIntervalSeconds * 1000.0
  private val BureauIntervalMs: Double = TileKingdomLogic.BureauIntervalSeconds * 1000.0
  private val PoliticianGenerationIntervalMs: Double = TileKingdomLogic.PoliticianGenerationIntervalSeconds * 1000.0

  // Get or create an initial offset for a tile (0.0 to 1.0)
  private def getOrInitProgress(coord: Coord): Double =
    tileProgress.getOrElse(coord, {
      val offset = scala.util.Random.nextDouble()
      tileProgress = tileProgress.updated(coord, offset)
      offset
    })

  // Panning state
  private var panOffsetX: Double = 0.0
  private var panOffsetY: Double = 0.0
  private var isDragging: Boolean = false
  private var wasDragging: Boolean = false // Track if we just finished a drag (to suppress click)
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

  // Track which tile is currently in build-selection mode
  private var selectingTileCoord: Option[Coord] = None

  // Helper to wrap click handlers - only executes if we weren't just dragging
  private def onClick(handler: => Unit): MouseEvent => Unit = (_: MouseEvent) =>
    if !wasDragging then handler

  // Helper that also stops event propagation
  private def onClickStop(handler: => Unit): MouseEvent => Unit = (e: MouseEvent) =>
    e.stopPropagation()
    if !wasDragging then handler

  // Calculate levels needed to reach next multiple of 10
  // E.g. level 16 -> 4 (to reach 20), level 20 -> 10 (to reach 30)
  private def levelsToNextTen(currentLevel: Int): Int =
    val remainder = currentLevel % 10
    if remainder == 0 then 10 else 10 - remainder

  // Format large numbers compactly (e.g. 1.2k, 3.4M, 5.6B)
  private def formatNumber(n: Double): String =
    val absN = math.abs(n)
    val sign = if n < 0 then "-" else ""
    if absN >= 1_000_000_000 then
      val v = absN / 1_000_000_000
      f"$sign$v%.1fB"
    else if absN >= 1_000_000 then
      val v = absN / 1_000_000
      f"$sign$v%.1fM"
    else if absN >= 10_000 then
      val v = absN / 1_000
      f"$sign$v%.1fk"
    else if absN >= 1 then s"$sign${absN.toInt}"
    else if absN > 0 then f"$sign$absN%.1f"
    else "0"

  // Format number for integer values
  private def formatNumber(n: Int): String = formatNumber(n.toDouble)

  // Helper to create a build option with cost checking
  private def buildOption(
    icon: String,
    name: String,
    cost: Int,
    resourceEmoji: String,
    hasEnough: Boolean,
    handler: => Unit
  ): HTMLElement =
    div(cls = "build-option").tap: opt =>
      opt.appendChild(div(cls = "build-icon", content = icon))
      opt.appendChild(div(cls = "build-name", content = name))
      val costCls = if hasEnough then "build-cost" else "build-cost insufficient"
      opt.appendChild(div(cls = costCls, content = s"${formatNumber(cost)}$resourceEmoji"))
      opt.onclick = (e: MouseEvent) =>
        e.stopPropagation()
        handler

  // ============================================================================
  // Initialization
  // ============================================================================

  def init(): Unit =
    println("[TileKingdom] Initializing Tile Kingdom game")
    buildUI()
    loadGame()
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
    container.appendChild(buildLeftSidebar())
    container.appendChild(buildActions())
    container.appendChild(buildNotification())
    container.appendChild(buildWelcomeBackModal())
    container.appendChild(buildHelpPopup())
    container.appendChild(buildDevToolsPopup())
    container.appendChild(buildSkillTreeModal())

    // Setup drag handlers
    setupDragHandlers(viewport)

  private def buildHeader(): HTMLElement =
    div(cls = "tile-kingdom-header")(
      h1(content = "🏰 Tile Kingdom"),
      button(cls = "help-button", content = "?").tap: btn =>
        btn.onclick = (_: MouseEvent) => toggleHelpPopup()
    )

  private def buildLeftSidebar(): HTMLElement =
    div(cls = "tile-kingdom-left-sidebar")(
      buildResources(),
      buildPoliticianRoster()
    )

  private def buildResources(): HTMLElement =
    div(cls = "tile-kingdom-resources")(
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "🌾"),
        span(id = "tile-kingdom-wheat", cls = "resource-value", content = "0"),
        span(id = "tile-kingdom-wheat-income", cls = "resource-income", content = "")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "🪵"),
        span(id = "tile-kingdom-wood", cls = "resource-value", content = "0"),
        span(id = "tile-kingdom-wood-income", cls = "resource-income", content = "")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "🪨"),
        span(id = "tile-kingdom-stone", cls = "resource-value", content = "0"),
        span(id = "tile-kingdom-stone-income", cls = "resource-income", content = "")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "✨"),
        span(id = "tile-kingdom-faith", cls = "resource-value", content = "0"),
        span(id = "tile-kingdom-faith-income", cls = "resource-income", content = "")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "💰"),
        span(id = "tile-kingdom-gold", cls = "resource-value", content = "0")
      ),
      div(cls = "resource-item")(
        span(cls = "resource-label", content = "👑"),
        span(id = "tile-kingdom-abdications", cls = "resource-value", content = "0")
      ),
      div(cls = "resource-item prestige")(
        span(cls = "resource-label", content = "🏅"),
        span(id = "tile-kingdom-legacy-points", cls = "resource-value", content = "0"),
        span(cls = "resource-label-small", content = "/25")
      ),
      div(cls = "resource-item prestige")(
        span(cls = "resource-label", content = "⭐"),
        span(id = "tile-kingdom-skill-points", cls = "resource-value", content = "0")
      ),
      div(cls = "resource-item income")(
        span(cls = "resource-label", content = "📈"),
        span(id = "tile-kingdom-income", cls = "resource-value", content = "0/s")
      ),
      div(cls = "resource-item unlock-costs")(
        span(cls = "resource-label", content = "🔓 Next tiles cost:"),
        span(id = "tile-kingdom-unlock-costs", cls = "unlock-costs-value")
      )
    )

  private def buildPoliticianRoster(): HTMLElement =
    val rosterDiv = div(id = "tile-kingdom-politician-roster", cls = "politician-roster")(
      div(cls = "roster-header")(
        span(cls = "roster-title", content = "🏛️ Politicians"),
        div(cls = "roster-stats")(
          span(id = "politician-timer", cls = "roster-timer", content = ""),
          span(id = "politician-rare-chance", cls = "roster-rare-chance", content = "")
        )
      ),
      div(id = "politician-roster-list", cls = "roster-list"),
      div(id = "politician-trash", cls = "politician-trash")(
        el("i", cls = "fa-solid fa-trash"),
        span(content = "Discard")
      )
    )

    // Setup trash drop zone
    val trashZone = rosterDiv.querySelector("#politician-trash").asInstanceOf[HTMLElement]
    trashZone.ondragover = (e: DragEvent) =>
      e.preventDefault()
      trashZone.classList.add("drag-over")

    trashZone.ondragleave = (_: DragEvent) =>
      trashZone.classList.remove("drag-over")

    trashZone.ondrop = (e: DragEvent) =>
      e.preventDefault()
      trashZone.classList.remove("drag-over")
      val politicianId = e.dataTransfer.getData("text/plain")
      if politicianId.nonEmpty then
        handleDiscardPolitician(politicianId)

    rosterDiv

  private def buildActions(): HTMLElement =
    div(cls = "tile-kingdom-actions")(
      button(id = "tile-kingdom-abdicate-btn", cls = "btn-primary disabled", content = "Abdicate").tap: btn =>
        btn.disabled = true
        btn.onclick = (_: MouseEvent) => handleAbdicate()
      ,
      button(id = "tile-kingdom-sail-btn", cls = "btn-sail disabled", content = "⛵ Sail").tap: btn =>
        btn.disabled = true
        btn.onclick = (_: MouseEvent) => handleSail()
      ,
      button(id = "tile-kingdom-skills-btn", cls = "btn-skills", content = "🌳 Skills").tap: btn =>
        btn.onclick = (_: MouseEvent) => toggleSkillTree()
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

  private def buildWelcomeBackModal(): HTMLElement =
    div(id = "tile-kingdom-welcome-modal", cls = "welcome-modal")(
      div(cls = "welcome-modal-content")(
        div(cls = "welcome-modal-header")(
          h3(content = "👑 Welcome Back!")
        ),
        div(id = "welcome-modal-body", cls = "welcome-modal-body"),
        button(id = "welcome-modal-close", cls = "btn-primary welcome-modal-close", content = "Continue").tap: btn =>
          btn.onclick = (_: MouseEvent) => hideWelcomeBackModal()
      )
    )

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
          p(content = "⛵ At 25 tiles, you can Sail for legacy points"),
          p(content = "🏅 25 legacy points = 1 skill point"),
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
          button(cls = "btn-dev-action", content = "🪨 Stone +1000").tap: btn =>
            btn.onclick = (_: MouseEvent) =>
              currentGame = currentGame.copy(stone = currentGame.stone + 1000)
              saveGame()
              renderGame()
              showNotification(s"Added 1000 stone")
          ,
          button(cls = "btn-dev-action", content = "✨ Faith +1000").tap: btn =>
            btn.onclick = (_: MouseEvent) =>
              currentGame = currentGame.copy(faith = currentGame.faith + 1000)
              saveGame()
              renderGame()
              showNotification(s"Added 1000 faith")
          ,
          button(cls = "btn-dev-action", content = "🌟 +1 Skill Point").tap: btn =>
            btn.onclick = (_: MouseEvent) =>
              currentGame = currentGame.copy(skillPoints = currentGame.skillPoints + 1, hasSailed = true)
              saveGame()
              renderGame()
              showNotification(s"Added 1 skill point (${currentGame.skillPoints} total)")
          ,
          button(cls = "btn-dev-action", content = "🗺️ +100 Tiles").tap: btn =>
            btn.onclick = (_: MouseEvent) =>
              currentGame = TileKingdomLogic.unlockManyTiles(currentGame, 100)
              saveGame()
              renderGame()
              showNotification(s"Added 100 tiles")
          ,
          button(cls = "btn-dev-action", content = "👤 +Politician").tap: btn =>
            btn.onclick = (_: MouseEvent) =>
              val newPolitician = TileKingdomLogic.generatePolitician(System.currentTimeMillis(), 0.0)
              currentGame = currentGame.copy(politicianRoster = currentGame.politicianRoster :+ newPolitician)
              saveGame()
              renderGame()
              showNotification(s"Added ${newPolitician.name}")
          ,
          button(cls = "btn-dev-action", content = "⭐ +Rare Politician").tap: btn =>
            btn.onclick = (_: MouseEvent) =>
              val newPolitician = TileKingdomLogic.generatePolitician(System.currentTimeMillis(), 1.0)
              currentGame = currentGame.copy(politicianRoster = currentGame.politicianRoster :+ newPolitician)
              saveGame()
              renderGame()
              showNotification(s"Added rare: ${newPolitician.name}")
          ,
          button(cls = "btn-dev-action", content = "🌟 +5 Skill Points").tap: btn =>
            btn.onclick = (_: MouseEvent) =>
              currentGame = currentGame.copy(skillPoints = currentGame.skillPoints + 5, hasSailed = true)
              saveGame()
              renderGame()
              showNotification(s"Added 5 skill points (${currentGame.skillPoints} total)")
        )
      )
    )

  private def toggleDevTools(): Unit =
    getElementById("tile-kingdom-dev-popup").foreach: popup =>
      popup.classList.toggle("show")

  private def toggleHelpPopup(): Unit =
    getElementById("tile-kingdom-help-popup").foreach: popup =>
      popup.classList.toggle("show")

  private def toggleSkillTree(): Unit =
    getElementById("tile-kingdom-skill-tree-modal").foreach: modal =>
      if modal.classList.contains("show") then
        modal.classList.remove("show")
      else
        renderSkillTreeContent()
        modal.classList.add("show")

  private def buildSkillTreeModal(): HTMLElement =
    div(id = "tile-kingdom-skill-tree-modal", cls = "skill-tree-modal")(
      div(cls = "skill-tree-modal-content")(
        div(cls = "skill-tree-header")(
          h3(content = "🌳 Skill Tree"),
          div(id = "skill-tree-points", cls = "skill-tree-points", content = ""),
          button(cls = "skill-tree-close-btn", content = "✕").tap: btn =>
            btn.onclick = (_: MouseEvent) => toggleSkillTree()
        ),
        div(id = "skill-tree-body", cls = "skill-tree-body")
      )
    )

  private def renderSkillTreeContent(): Unit =
    getElementById("skill-tree-points").foreach: elem =>
      elem.textContent = s"⭐ ${currentGame.skillPoints} skill points"

    getElementById("skill-tree-body").foreach: body =>
      body.innerHTML = ""

      if !currentGame.hasSailed then
        body.appendChild(div(cls = "skill-tree-locked")(
          div(cls = "locked-icon", content = "🔒"),
          div(cls = "locked-text", content = "Sail at least once to unlock the skill tree"),
          div(cls = "locked-hint", content = "Reach 25 tiles and click ⛵ Sail")
        ))
      else
        // Render each branch
        Skill.allBranches.foreach: branchName =>
          val branchDiv = div(cls = "skill-branch")(
            div(cls = "skill-branch-header")(
              span(cls = "branch-emoji", content = Skill.branchEmoji(branchName)),
              span(cls = "branch-name", content = branchName)
            ),
            div(cls = "skill-branch-nodes")
          )

          val nodesContainer = branchDiv.querySelector(".skill-branch-nodes").asInstanceOf[HTMLElement]
          val skills = Skill.branchSkills(branchName)
          
          // Group skills by cost level, then render with OR between alternatives
          val skillsByCost = skills.groupBy(Skill.cost).toList.sortBy(_._1)
          
          skillsByCost.foreach: (cost, skillsAtLevel) =>
            // Check if this is a dual track level (has mutually exclusive skills)
            val isDualTrack = skillsAtLevel.exists(s => Skill.mutuallyExclusive(s).isDefined)
            
            if isDualTrack then
              // Render dual track with OR separator
              val dualTrackContainer = div(cls = "skill-dual-track")
              skillsAtLevel.zipWithIndex.foreach: (skill, idx) =>
                if idx > 0 then
                  dualTrackContainer.appendChild(div(cls = "skill-or-separator", content = "OR"))
                dualTrackContainer.appendChild(renderSkillNode(skill))
              nodesContainer.appendChild(dualTrackContainer)
            else
              // Render single skill normally
              skillsAtLevel.foreach: skill =>
                nodesContainer.appendChild(renderSkillNode(skill))

          body.appendChild(branchDiv)

  private def renderSkillNode(skill: Skill): HTMLElement =
    val isUnlocked = currentGame.hasSkill(skill)
    val canUnlock = currentGame.canUnlockSkill(skill)
    val isExcluded = Skill.mutuallyExclusive(skill).exists(currentGame.hasSkill)
    val cost = Skill.cost(skill)
    val description = Skill.description(skill)

    val nodeCls = 
      if isUnlocked then "skill-node unlocked"
      else if isExcluded then "skill-node excluded"
      else if canUnlock then "skill-node available"
      else "skill-node locked"

    div(cls = nodeCls)(
      div(cls = "skill-node-cost", content = s"${cost}⭐"),
      div(cls = "skill-node-desc", content = description),
      if isUnlocked then
        div(cls = "skill-node-status", content = "✓ Unlocked")
      else if isExcluded then
        div(cls = "skill-node-status", content = "✗ Excluded")
      else if canUnlock then
        button(cls = "skill-node-btn", content = "Unlock").tap: btn =>
          btn.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            handleUnlockSkill(skill)
      else
        div(cls = "skill-node-status", content = "🔒")
    )

  private def handleUnlockSkill(skill: Skill): Unit =
    TileKingdomLogic.unlockSkill(currentGame, skill) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        renderSkillTreeContent()
        showNotification(s"Unlocked: ${Skill.description(skill)}")
      case Left(error) =>
        showNotification(error)

  // ============================================================================
  // Drag/Pan Handling
  // ============================================================================

  private def setupDragHandlers(viewport: HTMLElement): Unit =
    // Helper to check if element or any ancestor has draggable="true"
    def hasDraggableAncestor(elem: Element): Boolean =
      var current: org.scalajs.dom.Node = elem
      while current != null && current.isInstanceOf[Element] do
        val el = current.asInstanceOf[Element]
        if el.getAttribute("draggable") == "true" then return true
        current = el.parentNode
      false

    viewport.onmousedown = (e: MouseEvent) =>
      if e.button == 0 then // Left mouse button
        // Don't start panning if clicking on a draggable element (like politician slot)
        val target = e.target.asInstanceOf[Element]
        if !hasDraggableAncestor(target) then
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
        // Mark as dragging if moved more than 5 pixels (to distinguish from clicks)
        if math.abs(dx) > 5 || math.abs(dy) > 5 then
          wasDragging = true
        panOffsetX = panStartX + dx
        panOffsetY = panStartY + dy
        updateGridPosition()

    document.onmouseup = (e: MouseEvent) =>
      if isDragging then
        isDragging = false
        getElementById("tile-kingdom-grid-viewport").foreach(_.asInstanceOf[HTMLElement].style.cursor = "grab")
        snapBackIfNeeded()
        // Reset wasDragging after a brief delay to allow click event to check it
        if wasDragging then
          window.setTimeout(() => wasDragging = false, 10)

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
    var totalStoneHarvested = 0.0

    val wheatFields = currentGame.unlockedTiles.filter(_.isWheatField)
    val woodcutters = currentGame.unlockedTiles.filter(_.isWoodcutter)
    val temples = currentGame.unlockedTiles.filter(_.isTemple)
    val quarries = currentGame.unlockedTiles.filter(_.isQuarry)

    // Process wheat fields
    wheatFields.foreach: tile =>
      val currentProgress = getOrInitProgress(tile.coord)
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
      val currentProgress = getOrInitProgress(tile.coord)
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
      val currentProgress = getOrInitProgress(tile.coord)
      val progressIncrement = elapsedMs / ProductionIntervalMs
      val newProgress = currentProgress + progressIncrement

      if newProgress >= 1.0 then
        val harvests = newProgress.toInt
        val production = TileKingdomLogic.faithProductionPerHarvest(currentGame, tile)
        totalFaithHarvested += production * harvests
        tileProgress = tileProgress.updated(tile.coord, newProgress - harvests)
        showFloatingReward(tile.coord, (production * harvests).toInt, "✨")
      else
        tileProgress = tileProgress.updated(tile.coord, newProgress)

    // Process quarries
    quarries.foreach: tile =>
      val currentProgress = getOrInitProgress(tile.coord)
      val progressIncrement = elapsedMs / ProductionIntervalMs
      val newProgress = currentProgress + progressIncrement

      if newProgress >= 1.0 then
        val harvests = newProgress.toInt
        val production = TileKingdomLogic.stoneProductionPerHarvest(currentGame, tile)
        totalStoneHarvested += production * harvests
        tileProgress = tileProgress.updated(tile.coord, newProgress - harvests)
        showFloatingReward(tile.coord, (production * harvests).toInt, "🪨")
      else
        tileProgress = tileProgress.updated(tile.coord, newProgress)

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
    var bureauUpgrades: List[(Coord, Int, Coord, Int, Resource, Boolean)] = List.empty // (upgradedCoord, newLevel, bureauCoord, cost, costResource, wasTurbo)

    bureaus.foreach: tile =>
      val currentProgress = getOrInitProgress(tile.coord)
      val isTurbo = TileKingdomLogic.isBureauTurbo(currentGame, tile.coord)
      // Check if turbo is affordable based on min level nearby tile
      val nearbyCoords = tile.coord.neighborsWithinRadius(TileKingdomLogic.BureauRadius)
      val minLevel = nearbyCoords
        .flatMap(c => currentGame.tiles.get(c))
        .filter(_.isUpgradeable)
        .map(_.level)
        .minOption
        .getOrElse(1)
      val minFaithCost = TileKingdomLogic.bureauTurboFaithCostForLevel(minLevel)
      val canAffordTurbo = currentGame.faith >= minFaithCost
      val effectivelyTurbo = isTurbo && canAffordTurbo
      val speedMultiplier = TileKingdomLogic.bureauSpeedMultiplier(currentGame, tile.coord)
      val progressIncrement = elapsedMs / BureauIntervalMs * speedMultiplier
      val newProgress = currentProgress + progressIncrement

      if newProgress >= 1.0 then
        // Bureau ready to attempt an upgrade
        TileKingdomLogic.bureauAutoUpgrade(updatedGame, tile.coord, currentTime) match
          case Some((newGame, upgradedCoord)) if upgradedCoord != tile.coord =>
            // Actual upgrade happened
            updatedGame = newGame
            // Collect upgrade info to show after render
            val upgradedTile = updatedGame.tiles.get(upgradedCoord)
            val previousTile = upgradedTile.map(t => t.copy(tileType = t.tileType match
              case TileType.WheatField(lvl) => TileType.WheatField(lvl - 1)
              case TileType.Farm(lvl)       => TileType.Farm(lvl - 1)
              case TileType.Woodcutter(lvl) => TileType.Woodcutter(lvl - 1)
              case TileType.Temple(lvl)     => TileType.Temple(lvl - 1)
              case TileType.Quarry(lvl)     => TileType.Quarry(lvl - 1)
              case other                    => other
            ))
            val upgradeCostOpt = previousTile.flatMap(_.upgradeCost)
            val upgradeCost = upgradeCostOpt.map(_.amount).getOrElse(0)
            val costResource = upgradeCostOpt.map(_.resource).getOrElse(Resource.Wheat)
            val newLevel = upgradedTile.map(_.level).getOrElse(1)
            bureauUpgrades = bureauUpgrades :+ (upgradedCoord, newLevel, tile.coord, upgradeCost, costResource, effectivelyTurbo)
            tileProgress = tileProgress.updated(tile.coord, newProgress - 1.0) // Keep excess progress
          case Some((newGame, _)) =>
            // Only turbo mode was disabled, no actual upgrade
            updatedGame = newGame
            updateSingleTile(tile.coord) // Update bureau tile to show slow mode
            tileProgress = tileProgress.updated(tile.coord, 1.0) // Retry next tick
          case None =>
            // No upgrade possible, keep progress at 1.0 to retry next tick
            tileProgress = tileProgress.updated(tile.coord, 1.0)
      else
        tileProgress = tileProgress.updated(tile.coord, newProgress)

    currentGame = updatedGame

    // Tick politician lifespans (only active politicians in Town Halls age)
    val (gameAfterLifespan, destroyedPoliticians) = TileKingdomLogic.tickPoliticianLifespans(currentGame, elapsedMs.toLong)
    currentGame = gameAfterLifespan

    // Generate new politicians if it's time
    val previousRosterSize = currentGame.politicianRoster.size
    currentGame = TileKingdomLogic.generateNewPoliticians(currentGame, currentTime)
    val newPoliticianGenerated = currentGame.politicianRoster.size > previousRosterSize

    saveGame()
    updateProgressBars()
    renderResources()
    updatePoliticianTimer()

    if newPoliticianGenerated then
      renderPoliticianRoster()
      showNotification("A new politician has arrived!")

    // Notify about destroyed politicians
    destroyedPoliticians.foreach: name =>
      showNotification(s"$name has reached the end of their term!")
      renderTiles() // Re-render to show empty town halls

    // Update Town Hall tiles to show lifespan countdown (if no politicians died)
    if destroyedPoliticians.isEmpty then
      val townHalls = currentGame.unlockedTiles.filter(_.isTownHall)
      townHalls.foreach: tile =>
        tile.tileType match
          case TileType.TownHall(Some(_)) => updateTownHallLifespan(tile.coord)
          case _ => ()

    // Update all bureau tiles when faith changes to refresh turbo button states
    if totalFaithHarvested > 0 then
      bureaus.foreach: tile =>
        updateSingleTile(tile.coord)

    // Show projectile and floating text for bureau upgrades
    bureauUpgrades.foreach: (upgradedCoord, newLevel, bureauCoord, cost, costResource, wasTurbo) =>
      // Show cost deduction immediately at bureau
      showFloatingReward(bureauCoord, TileKingdomLogic.BureauWoodCostPerUpgrade, "🪵", isSpend = true, offsetIndex = 0)
      if wasTurbo then
        // Faith cost is based on target tile's level before upgrade (newLevel - 1)
        val previousLevel = newLevel - 1
        val faithCost = TileKingdomLogic.bureauTurboFaithCostForLevel(previousLevel)
        showFloatingReward(bureauCoord, faithCost, "✨", isSpend = true, offsetIndex = 1)

      // Fire projectile, then show upgrade effects when it arrives
      showBureauProjectile(bureauCoord, upgradedCoord, () =>
        updateSingleTile(upgradedCoord)
        updateSingleTile(bureauCoord)
        val costEmoji = resourceEmoji(costResource)
        showFloatingReward(upgradedCoord, cost, costEmoji, isSpend = true)
        showFloatingLevel(upgradedCoord, newLevel)
      )

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
    renderPoliticianRoster()
    renderTiles()
    renderAbdicationButton()
    renderSailButton()

  private def renderResources(): Unit =
    setElementText("tile-kingdom-wheat", formatNumber(currentGame.wheat))
    setElementText("tile-kingdom-wood", formatNumber(currentGame.wood))
    setElementText("tile-kingdom-stone", formatNumber(currentGame.stone))
    setElementText("tile-kingdom-faith", formatNumber(currentGame.faith))
    setElementText("tile-kingdom-gold", formatNumber(currentGame.gold))
    setElementText("tile-kingdom-abdications", currentGame.totalAbdications.toString)
    setElementText("tile-kingdom-legacy-points", currentGame.legacyPoints.toString)
    setElementText("tile-kingdom-skill-points", currentGame.skillPoints.toString)

    // Individual income rates
    val wheatIncome = TileKingdomLogic.totalWheatProductionRate(currentGame)
    val woodIncome = TileKingdomLogic.totalWoodProductionRate(currentGame)
    val stoneIncome = TileKingdomLogic.totalStoneProductionRate(currentGame)
    val faithIncome = TileKingdomLogic.totalFaithProductionRate(currentGame)

    setElementText("tile-kingdom-wheat-income", formatIncome(wheatIncome))
    setElementText("tile-kingdom-wood-income", formatIncome(woodIncome))
    setElementText("tile-kingdom-stone-income", formatIncome(stoneIncome))
    setElementText("tile-kingdom-faith-income", formatIncome(faithIncome))

    // Total income
    val income = currentGame.totalIncomeRate
    setElementText("tile-kingdom-income", s"${formatNumber(income)}/s")

    // Next 3 tile unlock costs
    val currentTileCount = currentGame.unlockedTiles.size
    val nextCosts = (0 until 3).map: i =>
      TileKingdomLogic.tileUnlockCost(currentTileCount + i)
    val costsText = nextCosts.map(formatNumber).mkString(" → ")
    setElementText("tile-kingdom-unlock-costs", costsText)

  private def formatIncome(rate: Double): String =
    if rate <= 0 then ""
    else s"+${formatNumber(rate)}/s"

  private def renderPoliticianRoster(): Unit =
    getElementById("politician-roster-list").foreach: listElem =>
      listElem.innerHTML = ""

      // Check if there's a town hall - politicians only work with town halls
      if !currentGame.hasTownHall then
        listElem.appendChild(div(cls = "roster-empty", content = "🏛️ Build Town Hall"))
      else
        currentGame.politicianRoster.foreach: politician =>
          val cardCls = if politician.isRare then "politician-card rare" else "politician-card"
          val card = div(cls = cardCls)
          card.setAttribute("draggable", "true")
          card.setAttribute("data-politician-id", politician.id)

          // Calculate lifespan display
          val lifespanSeconds = (politician.remainingLifespanMs / 1000).toInt
          val minutes = lifespanSeconds / 60
          val seconds = lifespanSeconds % 60
          val lifespanText = f"$minutes:$seconds%02d"
          val lifespanPercent = (politician.remainingLifespanMs.toDouble / TileKingdomLogic.PoliticianLifespanMs * 100).toInt
          val lifespanClass = if lifespanPercent <= 20 then "lifespan-critical" else if lifespanPercent <= 50 then "lifespan-warning" else "lifespan-normal"

          card.appendChild(div(cls = "politician-emoji", content = politician.emoji))
          card.appendChild(div(cls = "politician-info")(
            div(cls = "politician-name", content = politician.name),
            div(cls = "politician-title", content = politician.title),
            div(cls = "politician-effect", content = politician.effectDescription),
            div(cls = s"politician-roster-lifespan $lifespanClass", content = s"⏱️ $lifespanText remaining")
          ))

          // Setup drag events
          card.ondragstart = (e: DragEvent) =>
            e.dataTransfer.setData("text/plain", politician.id)
            card.classList.add("dragging")

          card.ondragend = (_: DragEvent) =>
            card.classList.remove("dragging")

          listElem.appendChild(card)

        // If roster is empty, show placeholder
        if currentGame.politicianRoster.isEmpty then
          listElem.appendChild(div(cls = "roster-empty", content = "No politicians available"))

    // Update timer for next politician
    updatePoliticianTimer()

  // Update a single tile in place without re-rendering everything
  private def updateSingleTile(coord: Coord): Unit =
    currentGame.tiles.get(coord).foreach: tile =>
      Option(document.getElementById(s"tile-${coord.row}-${coord.col}")).foreach: oldElement =>
        val newElement = renderTile(tile)
        oldElement.parentNode.replaceChild(newElement, oldElement)

  // Update only the TownHall lifespan timer without replacing the entire tile
  // This preserves drag-drop handlers during timer updates
  private def updateTownHallLifespan(coord: Coord): Unit =
    currentGame.tiles.get(coord).flatMap(_.tileType match
      case TileType.TownHall(Some(pol)) => Some(pol)
      case _ => None
    ).foreach: pol =>
      Option(document.getElementById(s"politician-lifespan-${coord.row}-${coord.col}")).foreach: lifespanElem =>
        val elem = lifespanElem.asInstanceOf[HTMLElement]
        val lifespanMultiplier = TileKingdomLogic.politicianLifespanMultiplier(currentGame, coord)
        val effectiveLifespanMs = (pol.remainingLifespanMs * lifespanMultiplier).toLong
        val lifespanSeconds = (effectiveLifespanMs / 1000).toInt
        val minutes = lifespanSeconds / 60
        val seconds = lifespanSeconds % 60
        val lifespanText = f"$minutes:$seconds%02d"
        val effectiveMaxLifespanMs = (TileKingdomLogic.PoliticianLifespanMs * lifespanMultiplier).toLong
        val lifespanPercent = (effectiveLifespanMs.toDouble / effectiveMaxLifespanMs * 100).toInt
        val lifespanClass = if lifespanPercent <= 20 then "lifespan-critical" else if lifespanPercent <= 50 then "lifespan-warning" else "lifespan-normal"
        val multiplierText = if lifespanMultiplier > 1.0 then s" (${lifespanMultiplier.toInt}x)" else ""

        elem.textContent = s"⏱️ $lifespanText$multiplierText"
        elem.className = s"politician-lifespan $lifespanClass"

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
            case TileType.TownHall(Some(_)) =>
              grid.appendChild(renderInfluenceIndicator(coord, TileKingdomLogic.TownHallInfluenceRadius, "town-hall-influence"))
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
    // Scale font size with zoom - allow it to go smaller when zoomed out
    val fontScale = math.max(0.3, math.min(1.0, zoomLevel))

    // Add zoom-minimal class when zoomed out far enough to hide text
    if zoomLevel < 0.7 then tileDiv.classList.add("zoom-minimal")

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
        val townHallCost = TileKingdomLogic.townHallBuildCost(currentGame)
        val quarryCost = TileKingdomLogic.quarryBuildCost
        val academyCost = TileKingdomLogic.academyBuildCost(currentGame)

        // Unlock progression checks
        val canBuildFarm = currentGame.canBuildFarm
        val canBuildWoodcutter = currentGame.canBuildWoodcutter
        val canBuildQuarry = currentGame.canBuildQuarry
        val canBuildBureau = currentGame.canBuildBureau
        val canBuildTemple = currentGame.canBuildTemple
        val canBuildTownHall = currentGame.canBuildTownHall
        val canBuildAcademy = currentGame.canBuildAcademy
        val canBuildTavern = currentGame.canBuildTavern

        // Check if any resource or management buildings are available
        val hasResourceBuildings = canBuildFarm || canBuildWoodcutter || canBuildQuarry
        val hasManagementBuildings = canBuildBureau || canBuildTemple || canBuildTownHall || canBuildAcademy || canBuildTavern

        // Build icon container (shown by default)
        val buildIconContainer = div(cls = "tile-build-icon-container")
        buildIconContainer.appendChild(el("i", cls = "fa-solid fa-hammer"))
        buildIconContainer.appendChild(div(cls = "build-label", content = "Build"))
        buildIconContainer.onclick = onClickStop:
          // Clear any other selecting tiles first
          document.querySelectorAll(".tile-kingdom-tile.selecting").foreach: elem =>
            elem.asInstanceOf[HTMLElement].classList.remove("selecting")
          selectingTileCoord = Some(coord)
          tileDiv.classList.add("selecting")
        tileDiv.appendChild(buildIconContainer)

        // Restore selecting state if this tile was previously selected
        if selectingTileCoord.contains(coord) then
          tileDiv.classList.add("selecting")

        // Build options container (hidden by default, shown when selecting)
        val buildOptions = div(cls = "tile-build-options")

        // Main menu container
        val mainMenu = div(cls = "build-main-menu")

        // Back/cancel button for main menu
        mainMenu.appendChild(div(cls = "build-option build-back").tap: opt =>
          opt.appendChild(el("i", cls = "fa-solid fa-arrow-left build-icon"))
          opt.appendChild(div(cls = "build-name", content = "Back"))
          opt.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            selectingTileCoord = None
            tileDiv.classList.remove("selecting")
        )

        // Resources category button (always show - at minimum wheat field is available)
        mainMenu.appendChild(div(cls = "build-option build-category resources").tap: opt =>
          opt.appendChild(div(cls = "build-icon", content = "🌾"))
          opt.appendChild(div(cls = "build-name", content = "Resources"))
          opt.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            buildOptions.classList.add("submenu-resources")
        )

        // Management category button (only show if any management buildings are unlocked)
        if hasManagementBuildings then
          mainMenu.appendChild(div(cls = "build-option build-category management").tap: opt =>
            opt.appendChild(div(cls = "build-icon", content = "🏛️"))
            opt.appendChild(div(cls = "build-name", content = "Management"))
            opt.onclick = (e: MouseEvent) =>
              e.stopPropagation()
              buildOptions.classList.add("submenu-management")
          )

        buildOptions.appendChild(mainMenu)

        // Resources submenu
        val resourcesSubmenu = div(cls = "build-submenu resources")

        resourcesSubmenu.appendChild(div(cls = "build-option build-back").tap: opt =>
          opt.appendChild(el("i", cls = "fa-solid fa-arrow-left build-icon"))
          opt.appendChild(div(cls = "build-name", content = "Back"))
          opt.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            buildOptions.classList.remove("submenu-resources")
        )

        resourcesSubmenu.appendChild(buildOption("🌾", "Field", wheatCost, "🌾", currentGame.wheat >= wheatCost, handleBuildWheatField(coord)))

        if canBuildFarm then
          resourcesSubmenu.appendChild(buildOption("🏠", "Farm", farmCost, "🌾", currentGame.wheat >= farmCost, handleBuildFarm(coord)))

        if canBuildWoodcutter then
          resourcesSubmenu.appendChild(buildOption("🪓", "Forest", woodcutterCost, "🌾", currentGame.wheat >= woodcutterCost, handleBuildWoodcutter(coord)))

        if canBuildQuarry then
          resourcesSubmenu.appendChild(buildOption("⛏️", "Quarry", quarryCost, "🪵", currentGame.wood >= quarryCost, handleBuildQuarry(coord)))

        buildOptions.appendChild(resourcesSubmenu)

        // Management submenu (only if any management buildings are unlocked)
        if hasManagementBuildings then
          val managementSubmenu = div(cls = "build-submenu management")

          managementSubmenu.appendChild(div(cls = "build-option build-back").tap: opt =>
            opt.appendChild(el("i", cls = "fa-solid fa-arrow-left build-icon"))
            opt.appendChild(div(cls = "build-name", content = "Back"))
            opt.onclick = (e: MouseEvent) =>
              e.stopPropagation()
              buildOptions.classList.remove("submenu-management")
          )

          if canBuildBureau then
            managementSubmenu.appendChild(buildOption("🏛️", "Bureau", bureauCost, "🪵", currentGame.wood >= bureauCost, handleBuildBureau(coord)))

          if canBuildTemple then
            managementSubmenu.appendChild(buildOption("⛪", "Temple", templeCost, "🪵", currentGame.wood >= templeCost, handleBuildTemple(coord)))

          if canBuildTownHall then
            managementSubmenu.appendChild(buildOption("🏛️", "Town Hall", townHallCost, "🪨", currentGame.stone >= townHallCost, handleBuildTownHall(coord)))

          if canBuildAcademy then
            managementSubmenu.appendChild(buildOption("🎓", "Academy", academyCost, "🪨", currentGame.stone >= academyCost, handleBuildAcademy(coord)))

          if canBuildTavern then
            val tavernCost = TileKingdomLogic.TavernBuildCost
            managementSubmenu.appendChild(buildOption("🍺", "Tavern", tavernCost, "🪵", currentGame.wood >= tavernCost, handleBuildTavern(coord)))

          buildOptions.appendChild(managementSubmenu)

        tileDiv.appendChild(buildOptions)

      case TileType.WheatField(level) =>
        tileDiv.classList.add("wheat-field")
        tileDiv.setAttribute("data-level", level.toString)
        val harvestAmount = TileKingdomLogic.productionPerHarvest(currentGame, tile)
        val bonusMultiplier = TileKingdomLogic.farmBonusMultiplier(currentGame, coord)
        val townHallMultiplier = TileKingdomLogic.townHallWheatMultiplier(currentGame, coord)
        val hasBonus = bonusMultiplier > 1.0
        val hasTownHallBonus = townHallMultiplier > 1.0
        val hasSpeedBoost = currentGame.hasSkill(Skill.Agriculture1B)
        val upgradeCost = TileKingdomLogic.wheatFieldLevelUpCost(level)

        val content = div(cls = "tile-content")(
          div(cls = "tile-icon", content = "🌾"),
          div(cls = "tile-label", content = s"Lv$level")
        )

        // Add speed boost indicator
        if hasSpeedBoost then
          content.appendChild(div(cls = "speed-boost-indicator", content = "⚡+25%"))

        val prodDiv = div(cls = "tile-production", content = s"+${formatNumber(harvestAmount)}")
        if hasBonus then
          val bonusPercent = ((bonusMultiplier - 1) * 100).toInt
          prodDiv.appendChild(span(cls = "bonus", content = s" +$bonusPercent%"))
        if hasTownHallBonus then
          val multiplierText = if townHallMultiplier % 1.0 == 0 then s" x${townHallMultiplier.toInt}" else f" x$townHallMultiplier%.1f"
          prodDiv.appendChild(span(cls = "bonus town-hall-bonus", content = multiplierText))
        content.appendChild(prodDiv)

        val upgradeRow = div(cls = "tile-upgrade-row")
        upgradeRow.appendChild(span(cls = "tile-upgrade", content = s"⬆${formatNumber(upgradeCost)}🌾"))
        upgradeRow.appendChild(button(cls = "btn-x10", content = "x10").tap: btn =>
          btn.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            handleBulkLevelUp(coord, levelsToNextTen(level), TileKingdomLogic.levelUpWheatField, TileKingdomLogic.wheatFieldLevelUpCost)
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

        tileDiv.onclick = onClick(handleLevelUpWheatField(coord))
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
        upgradeRow.appendChild(span(cls = "tile-upgrade", content = s"⬆${formatNumber(upgradeCost)}🌾"))
        upgradeRow.appendChild(button(cls = "btn-x10", content = "x10").tap: btn =>
          btn.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            handleBulkLevelUp(coord, levelsToNextTen(level), TileKingdomLogic.levelUpFarm, TileKingdomLogic.farmLevelUpCost)
        )
        content.appendChild(upgradeRow)

        tileDiv.appendChild(content)
        tileDiv.onclick = onClick(handleLevelUpFarm(coord))
        tileDiv.oncontextmenu = (e: MouseEvent) =>
          e.preventDefault()
          handleDestroyBuilding(coord)

      case TileType.Woodcutter(level) =>
        tileDiv.classList.add("woodcutter")
        tileDiv.setAttribute("data-level", level.toString)
        val harvestAmount = TileKingdomLogic.woodProductionPerHarvest(currentGame, tile)
        val upgradeCost = TileKingdomLogic.woodcutterLevelUpCost(level)
        val townHallMultiplier = TileKingdomLogic.townHallWoodMultiplier(currentGame, coord)
        val hasTownHallBonus = townHallMultiplier > 1.0

        val content = div(cls = "tile-content")(
          div(cls = "tile-icon", content = "🪓"),
          div(cls = "tile-label", content = s"Lv$level")
        )

        val prodDiv = div(cls = "tile-production", content = s"+${formatNumber(harvestAmount)}🪵")
        val forestBonus = TileKingdomLogic.forestGroupBonusMultiplier(currentGame, coord)
        if forestBonus > 1.0 then
          val bonusPercent = ((forestBonus - 1) * 100).toInt
          prodDiv.appendChild(span(cls = "bonus forest-bonus", content = s" +$bonusPercent%"))
        if hasTownHallBonus then
          val multiplierText = if townHallMultiplier % 1.0 == 0 then s" x${townHallMultiplier.toInt}" else f" x$townHallMultiplier%.1f"
          prodDiv.appendChild(span(cls = "bonus town-hall-bonus", content = multiplierText))
        content.appendChild(prodDiv)

        val upgradeRow = div(cls = "tile-upgrade-row")
        upgradeRow.appendChild(span(cls = "tile-upgrade", content = s"⬆${formatNumber(upgradeCost)}🌾"))
        upgradeRow.appendChild(button(cls = "btn-x10", content = "x10").tap: btn =>
          btn.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            handleBulkLevelUp(coord, levelsToNextTen(level), TileKingdomLogic.levelUpWoodcutter, TileKingdomLogic.woodcutterLevelUpCost)
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

        tileDiv.onclick = onClick(handleLevelUpWoodcutter(coord))
        tileDiv.oncontextmenu = (e: MouseEvent) =>
          e.preventDefault()
          handleDestroyBuilding(coord)

      case TileType.Bureau(level) =>
        tileDiv.classList.add("bureau")
        tileDiv.setAttribute("data-level", level.toString)
        val isTurbo = TileKingdomLogic.isBureauTurbo(currentGame, coord)
        val speedMultiplier = TileKingdomLogic.bureauSpeedMultiplier(currentGame, coord)

        // Find min level of nearby upgradeable tiles for faith cost display
        val nearbyCoords = coord.neighborsWithinRadius(TileKingdomLogic.BureauRadius)
        val minLevel = nearbyCoords
          .flatMap(c => currentGame.tiles.get(c))
          .filter(_.isUpgradeable)
          .map(_.level)
          .minOption
          .getOrElse(1)
        val minFaithCost = TileKingdomLogic.bureauTurboFaithCostForLevel(minLevel)
        val canAffordTurbo = currentGame.faith >= minFaithCost

        if isTurbo then tileDiv.classList.add("turbo")

        val content = div(cls = "tile-content")(
          div(cls = "tile-icon", content = "🏛️"),
          div(cls = "tile-label", content = if isTurbo then s"⚡x${speedMultiplier.toInt}" else "Bureau")
        )

        content.appendChild(div(cls = "tile-production", content = s"Auto⬆"))
        
        // Show upgrade cost - faith cost is now level × 10
        val costText = if isTurbo then
          s"${TileKingdomLogic.BureauWoodCostPerUpgrade}🪵 Lv×10✨"
        else
          s"${TileKingdomLogic.BureauWoodCostPerUpgrade}🪵"
        content.appendChild(div(cls = "bureau-cost", content = costText))

        // Add mode toggle buttons side by side
        val modeRow = div(cls = "bureau-mode-row")

        // Slow mode button
        modeRow.appendChild(button(cls = s"btn-bureau-mode slow${if !isTurbo then " active" else ""}", content = "🐢").tap: btn =>
          btn.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            if isTurbo then handleToggleBureauTurbo(coord)
        )

        // Turbo mode button (disabled if can't afford)
        val turboBtn = button(cls = s"btn-bureau-mode turbo${if isTurbo then " active" else ""}${if !canAffordTurbo && !isTurbo then " disabled" else ""}", content = "⚡")
        turboBtn.onclick = (e: MouseEvent) =>
          e.stopPropagation()
          if !isTurbo && canAffordTurbo then handleToggleBureauTurbo(coord)
          else if !canAffordTurbo && !isTurbo then showNotification(s"Need ${minFaithCost}✨ for turbo mode (Lv$minLevel × 10)")
        modeRow.appendChild(turboBtn)

        content.appendChild(modeRow)

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
        val faithAmount = TileKingdomLogic.faithProductionPerHarvest(currentGame, tile)
        val upgradeCost = TileKingdomLogic.templeLevelUpCost(level)
        val townHallMultiplier = TileKingdomLogic.townHallFaithMultiplier(currentGame, coord)
        val hasTownHallBonus = townHallMultiplier > 1.0
        val wisdomMultiplier = TileKingdomLogic.templeWisdom2Multiplier(currentGame, coord)
        val hasWisdomBonus = wisdomMultiplier > 1.0

        val content = div(cls = "tile-content")(
          div(cls = "tile-icon", content = "⛪"),
          div(cls = "tile-label", content = s"Lv$level")
        )

        val prodDiv = div(cls = "tile-production temple-production", content = s"+${formatNumber(faithAmount)}✨")
        if hasTownHallBonus then
          val multiplierText = if townHallMultiplier % 1.0 == 0 then s" x${townHallMultiplier.toInt}" else f" x$townHallMultiplier%.1f"
          prodDiv.appendChild(span(cls = "bonus town-hall-bonus", content = multiplierText))
        if hasWisdomBonus then
          val multiplierText = if wisdomMultiplier % 1.0 == 0 then s" x${wisdomMultiplier.toInt}" else f" x$wisdomMultiplier%.1f"
          prodDiv.appendChild(span(cls = "bonus wisdom-bonus", content = s"🌲$multiplierText"))
        content.appendChild(prodDiv)

        val upgradeRow = div(cls = "tile-upgrade-row")
        upgradeRow.appendChild(span(cls = "tile-upgrade", content = s"⬆${formatNumber(upgradeCost)}🪵"))
        upgradeRow.appendChild(button(cls = "btn-x10", content = "x10").tap: btn =>
          btn.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            handleBulkLevelUpTemple(coord, levelsToNextTen(level))
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

        tileDiv.onclick = onClick(handleLevelUpTemple(coord))
        tileDiv.oncontextmenu = (e: MouseEvent) =>
          e.preventDefault()
          handleDestroyBuilding(coord)

      case TileType.Quarry(level) =>
        tileDiv.classList.add("quarry")
        tileDiv.setAttribute("data-level", level.toString)
        val stoneAmount = TileKingdomLogic.stoneProductionPerHarvest(currentGame, tile)
        val upgradeCost = TileKingdomLogic.quarryLevelUpCost(level)
        val townHallMultiplier = TileKingdomLogic.townHallStoneMultiplier(currentGame, coord)
        val hasTownHallBonus = townHallMultiplier > 1.0
        val wisdomMultiplier = TileKingdomLogic.quarryWisdom1Multiplier(currentGame, coord)
        val hasWisdomBonus = wisdomMultiplier > 1.0

        val content = div(cls = "tile-content")(
          div(cls = "tile-icon", content = "⛏️"),
          div(cls = "tile-label", content = s"Lv$level")
        )

        val prodDiv = div(cls = "tile-production quarry-production", content = s"+${formatNumber(stoneAmount)}🪨")
        if hasTownHallBonus then
          val multiplierText = if townHallMultiplier % 1.0 == 0 then s" x${townHallMultiplier.toInt}" else f" x$townHallMultiplier%.1f"
          prodDiv.appendChild(span(cls = "bonus town-hall-bonus", content = multiplierText))
        if hasWisdomBonus then
          val multiplierText = if wisdomMultiplier % 1.0 == 0 then s" x${wisdomMultiplier.toInt}" else f" x$wisdomMultiplier%.1f"
          prodDiv.appendChild(span(cls = "bonus wisdom-bonus", content = s"🌲$multiplierText"))
        content.appendChild(prodDiv)

        val upgradeRow = div(cls = "tile-upgrade-row")
        upgradeRow.appendChild(span(cls = "tile-upgrade", content = s"⬆${formatNumber(upgradeCost)}🪵"))
        upgradeRow.appendChild(button(cls = "btn-x10", content = "x10").tap: btn =>
          btn.onclick = (e: MouseEvent) =>
            e.stopPropagation()
            handleBulkLevelUp(coord, levelsToNextTen(level), TileKingdomLogic.levelUpQuarry, TileKingdomLogic.quarryLevelUpCost, "🪵")
        )
        content.appendChild(upgradeRow)

        tileDiv.appendChild(content)

        // Add progress bar
        val progress = tileProgress.getOrElse(coord, 0.0)
        val progressContainer = div(cls = "tile-progress-container")
        val progressBar = div(id = s"progress-bar-${coord.row}-${coord.col}", cls = "tile-progress-bar quarry-progress")
        progressBar.style.width = s"${(progress * 100).toInt}%"
        progressContainer.appendChild(progressBar)
        tileDiv.appendChild(progressContainer)

        tileDiv.onclick = onClick(handleLevelUpQuarry(coord))
        tileDiv.oncontextmenu = (e: MouseEvent) =>
          e.preventDefault()
          handleDestroyBuilding(coord)

      case TileType.TownHall(politician) =>
        tileDiv.classList.add("town-hall")

        val content = div(cls = "tile-content town-hall-content")(
          div(cls = "tile-icon", content = "🏛️")
        )

        politician match
          case Some(pol) =>
            tileDiv.classList.add("has-politician")
            // Calculate lifespan display with tavern multiplier
            val lifespanMultiplier = TileKingdomLogic.politicianLifespanMultiplier(currentGame, coord)
            val effectiveLifespanMs = (pol.remainingLifespanMs * lifespanMultiplier).toLong
            val lifespanSeconds = (effectiveLifespanMs / 1000).toInt
            val minutes = lifespanSeconds / 60
            val seconds = lifespanSeconds % 60
            val lifespanText = f"$minutes:$seconds%02d"
            val effectiveMaxLifespanMs = (TileKingdomLogic.PoliticianLifespanMs * lifespanMultiplier).toLong
            val lifespanPercent = (effectiveLifespanMs.toDouble / effectiveMaxLifespanMs * 100).toInt
            val lifespanClass = if lifespanPercent <= 20 then "lifespan-critical" else if lifespanPercent <= 50 then "lifespan-warning" else "lifespan-normal"
            val multiplierText = if lifespanMultiplier > 1.0 then s" (${lifespanMultiplier.toInt}x)" else ""

            val slot = div(cls = "politician-slot filled")(
              div(cls = "politician-emoji-small", content = pol.emoji),
              div(cls = "politician-effect-small", content = pol.effectDescription),
              div(id = s"politician-lifespan-${coord.row}-${coord.col}", cls = s"politician-lifespan $lifespanClass", content = s"⏱️ $lifespanText$multiplierText")
            )

            // Make the politician slot draggable for swapping between town halls
            slot.setAttribute("draggable", "true")
            slot.asInstanceOf[HTMLElement].ondragstart = (e: DragEvent) =>
              // Use format "townhall:row,col" to identify source is a town hall
              e.dataTransfer.effectAllowed = "move"
              e.dataTransfer.setData("text/plain", s"townhall:${coord.row},${coord.col}")
              tileDiv.classList.add("dragging")
            slot.asInstanceOf[HTMLElement].ondragend = (_: DragEvent) =>
              tileDiv.classList.remove("dragging")

            content.appendChild(slot)
            // Click to remove politician
            tileDiv.onclick = onClick(handleRemovePolitician(coord))
          case None =>
            content.appendChild(div(cls = "politician-slot empty")(
              div(cls = "slot-label", content = "Drop politician")
            ))

        tileDiv.appendChild(content)

        // Setup drag-drop for receiving politicians
        // Use stopPropagation to prevent event bubbling issues
        tileDiv.ondragover = (e: DragEvent) =>
          e.preventDefault()
          e.stopPropagation()
          tileDiv.classList.add("drag-over")

        tileDiv.ondragenter = (e: DragEvent) =>
          e.preventDefault()
          e.stopPropagation()
          tileDiv.classList.add("drag-over")

        // Only remove drag-over when actually leaving the tile, not when moving to children
        tileDiv.ondragleave = (e: DragEvent) =>
          e.stopPropagation()
          val related = e.relatedTarget
          // Check if we're leaving to an element outside this tile
          val leavingTile = related == null || !tileDiv.contains(related.asInstanceOf[org.scalajs.dom.Node])
          if leavingTile then
            tileDiv.classList.remove("drag-over")

        tileDiv.ondrop = (e: DragEvent) =>
          e.preventDefault()
          e.stopPropagation()
          tileDiv.classList.remove("drag-over")
          val data = e.dataTransfer.getData("text/plain")
          // Check if drag is from another town hall or from roster
          if data.startsWith("townhall:") then
            // Parse source coord from "townhall:row,col"
            val coords = data.stripPrefix("townhall:").split(",")
            if coords.length == 2 then
              val fromCoord = Coord(coords(0).toInt, coords(1).toInt)
              handleSwapPoliticians(fromCoord, coord)
          else
            // Regular roster drag
            handleAssignPolitician(data, coord)

        tileDiv.oncontextmenu = (e: MouseEvent) =>
          e.preventDefault()
          handleDestroyBuilding(coord)

      case TileType.Academy(mode) =>
        tileDiv.classList.add("academy")
        val hasEducation2 = currentGame.hasSkill(Skill.Education2)

        val modeText = if hasEducation2 then
          "⚡ 2x  ⭐ +10%"
        else mode match
          case AcademyMode.FasterPoliticians => "⚡ 2x Speed"
          case AcademyMode.RareChance => "⭐ +10% Rare"

        val content = div(cls = "tile-content academy-content")(
          div(cls = "tile-icon", content = "🎓"),
          div(cls = "tile-label", content = "Academy"),
          div(cls = s"academy-mode${if hasEducation2 then " education2-bonus" else ""}", content = modeText)
        )

        // Only show mode toggle if Education2 is not active
        if !hasEducation2 then
          content.appendChild(button(cls = "btn-toggle-mode", content = "⇄ Mode").tap: btn =>
            btn.onclick = (e: MouseEvent) =>
              e.stopPropagation()
              handleToggleAcademyMode(coord)
          )

        tileDiv.appendChild(content)

        tileDiv.oncontextmenu = (e: MouseEvent) =>
          e.preventDefault()
          handleDestroyBuilding(coord)

      case TileType.Tavern =>
        tileDiv.classList.add("tavern")

        val content = div(cls = "tile-content tavern-content")(
          div(cls = "tile-icon", content = "🍺"),
          div(cls = "tile-label", content = "Tavern"),
          div(cls = "tavern-effect", content = s"${TileKingdomLogic.TavernLifespanMultiplier.toInt}x Lifespan")
        )

        tileDiv.appendChild(content)

        tileDiv.oncontextmenu = (e: MouseEvent) =>
          e.preventDefault()
          handleDestroyBuilding(coord)

    tileDiv

  private def renderUnlockableTile(coord: Coord): HTMLElement =
    val tileDiv = div(id = s"tile-${coord.row}-${coord.col}", cls = "tile-kingdom-tile locked unlockable")
    val tilePixelSize = (70 * zoomLevel).toInt
    val fontScale = math.max(0.3, math.min(1.0, zoomLevel))

    // Add zoom-minimal class when zoomed out far enough to hide text
    if zoomLevel < 0.7 then tileDiv.classList.add("zoom-minimal")

    // Position the tile absolutely with zoom-adjusted size
    tileDiv.style.cssText =
      s"position: absolute; left: ${coord.col * TileSize}px; top: ${coord
          .row * TileSize}px; width: ${tilePixelSize}px; height: ${tilePixelSize}px; font-size: ${fontScale}em;"

    val cost = currentGame.nextTileUnlockCost

    tileDiv.appendChild(
      div(cls = "tile-content")(
        div(cls = "tile-icon", content = "🔓"),
        div(cls = "tile-cost", content = s"${formatNumber(cost)} 💰")
      )
    )

    tileDiv.onclick = onClick(handleUnlockTile(coord))

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

  private def renderSailButton(): Unit =
    getElementById("tile-kingdom-sail-btn").foreach: elem =>
      val btn = elem.asInstanceOf[HTMLButtonElement]
      val tileCount = currentGame.unlockedTiles.size
      val minTiles = TileKingdomLogic.SailMinTiles
      if currentGame.canSail then
        btn.disabled = false
        btn.classList.remove("disabled")
        val legacyReward = currentGame.sailLegacyReward
        btn.textContent = s"⛵ Sail (+$legacyReward 🏅)"
      else
        btn.disabled = true
        btn.classList.add("disabled")
        btn.textContent = s"⛵ Sail ($tileCount/$minTiles tiles)"

  private def renderSkillsButton(): Unit =
    getElementById("tile-kingdom-skills-btn").foreach: elem =>
      val btn = elem.asInstanceOf[HTMLButtonElement]
      // Add glow animation when player has skill points to spend and has sailed
      if currentGame.hasSailed && currentGame.skillPoints > 0 then
        btn.classList.add("has-points")
      else
        btn.classList.remove("has-points")

  // ============================================================================
  // Event Handlers
  // ============================================================================

  private def handleBuildWheatField(coord: Coord): Unit =
    val cost = TileKingdomLogic.wheatFieldBuildCost
    TileKingdomLogic.buildWheatField(currentGame, coord) match
      case Right(newGame) =>
        selectingTileCoord = None
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
        selectingTileCoord = None
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
        selectingTileCoord = None
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
        selectingTileCoord = None
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
        selectingTileCoord = None
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, cost, "🪵", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleBuildTownHall(coord: Coord): Unit =
    val cost = TileKingdomLogic.townHallBuildCost(currentGame)
    TileKingdomLogic.buildTownHall(currentGame, coord) match
      case Right(newGame) =>
        selectingTileCoord = None
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, cost, "🪨", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleBuildQuarry(coord: Coord): Unit =
    val cost = TileKingdomLogic.quarryBuildCost
    TileKingdomLogic.buildQuarry(currentGame, coord) match
      case Right(newGame) =>
        selectingTileCoord = None
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, cost, "🪵", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleBuildAcademy(coord: Coord): Unit =
    val cost = TileKingdomLogic.academyBuildCost(currentGame)
    TileKingdomLogic.buildAcademy(currentGame, coord) match
      case Right(newGame) =>
        selectingTileCoord = None
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, cost, "🪨", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleBuildTavern(coord: Coord): Unit =
    TileKingdomLogic.buildTavern(currentGame, coord) match
      case Right(newGame) =>
        selectingTileCoord = None
        currentGame = newGame
        saveGame()
        renderGame()
        showFloatingReward(coord, TileKingdomLogic.TavernBuildCost, "🪵", isSpend = true)
      case Left(error) =>
        showNotification(error)

  private def handleToggleAcademyMode(coord: Coord): Unit =
    TileKingdomLogic.toggleAcademyMode(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        val modeText = newGame.tiles.get(coord).map(_.tileType) match
          case Some(TileType.Academy(AcademyMode.FasterPoliticians)) => "Faster Politicians (2x speed)"
          case Some(TileType.Academy(AcademyMode.RareChance)) => "Rare Chance (+10%)"
          case _ => "Unknown"
        showNotification(s"Academy mode: $modeText")
      case Left(error) =>
        showNotification(error)

  private def handleAssignPolitician(politicianId: String, townHallCoord: Coord): Unit =
    // Check if there's already a politician (for swap message)
    val hadPolitician = currentGame.tiles.get(townHallCoord).exists: tile =>
      tile.tileType match
        case TileType.TownHall(Some(_)) => true
        case _ => false

    TileKingdomLogic.assignPolitician(currentGame, politicianId, townHallCoord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showNotification(if hadPolitician then "Politician swapped!" else "Politician assigned!")
      case Left(error) =>
        showNotification(error)

  private def handleRemovePolitician(townHallCoord: Coord): Unit =
    TileKingdomLogic.removePolitician(currentGame, townHallCoord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showNotification("Politician returned to roster")
      case Left(error) =>
        showNotification(error)

  private def handleSwapPoliticians(fromCoord: Coord, toCoord: Coord): Unit =
    TileKingdomLogic.swapPoliticians(currentGame, fromCoord, toCoord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        showNotification("Politicians swapped!")
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
                                 costFn: Int => Int,
                                 costEmoji: String = "🌾"
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
        showFloatingReward(coord, totalCost, costEmoji, isSpend = true)
        showFloatingLevel(coord, currentLevel)
      else
        showNotification(s"Not enough resources")

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

  private def handleLevelUpQuarry(coord: Coord): Unit =
    currentGame.tiles.get(coord).foreach: tile =>
      val cost = TileKingdomLogic.quarryLevelUpCost(tile.level)
      TileKingdomLogic.levelUpQuarry(currentGame, coord) match
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

  private def handleToggleBureauTurbo(coord: Coord): Unit =
    TileKingdomLogic.toggleBureauTurbo(currentGame, coord) match
      case Right(newGame) =>
        currentGame = newGame
        saveGame()
        renderGame()
        val isTurbo = TileKingdomLogic.isBureauTurbo(newGame, coord)
        val modeText = if isTurbo then "Turbo mode enabled (10x speed, +100✨/upgrade)" else "Slow mode enabled"
        showNotification(modeText)
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

  private def handleDiscardPolitician(politicianId: String): Unit =
    val politician = currentGame.politicianRoster.find(_.id == politicianId)
    currentGame = TileKingdomLogic.discardPolitician(currentGame, politicianId)
    saveGame()
    renderPoliticianRoster()
    politician.foreach(p => showNotification(s"${p.emoji} ${p.name} dismissed"))

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
            saveGame()
            centerOnKingdom()
            renderGame()
            val notification = if skillPointsEarned > 0 then s"Sailed! +$legacyReward 🏅, +$skillPointsEarned ⭐" else s"Sailed! +$legacyReward 🏅"
            showNotification(notification)
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
    println(s"[TileKingdom] showNotification called with: $message")
    val elem = getElementById("tile-kingdom-notification")
    println(s"[TileKingdom] notification element found: ${elem.isDefined}")
    elem.foreach: notification =>
      notification.textContent = message
      notification.classList.add("show")
      println(s"[TileKingdom] notification class list: ${notification.classList}")
      window.setTimeout(
        () =>
          notification.classList.remove("show"),
        3000
      )

  private def showWelcomeBackModal(wheatGain: Int, woodGain: Int, faithGain: Int, offlineSeconds: Double): Unit =
    getElementById("welcome-modal-body").foreach: body =>
      body.innerHTML = ""
      val timeAway = if offlineSeconds >= 3600 then
        f"${offlineSeconds / 3600}%.1f hours"
      else if offlineSeconds >= 60 then
        f"${offlineSeconds / 60}%.0f minutes"
      else
        f"${offlineSeconds}%.0f seconds"
      
      body.appendChild(p(cls = "welcome-time", content = s"You were away for $timeAway"))
      body.appendChild(div(cls = "welcome-subtitle", content = "Your kingdom produced:"))
      
      val gainsContainer = div(cls = "welcome-gains")
      if wheatGain > 0 then
        gainsContainer.appendChild(div(cls = "welcome-gain-item")(
          span(cls = "welcome-gain-icon", content = "🌾"),
          span(cls = "welcome-gain-value", content = f"+$wheatGain%,d")
        ))
      if woodGain > 0 then
        gainsContainer.appendChild(div(cls = "welcome-gain-item")(
          span(cls = "welcome-gain-icon", content = "🪵"),
          span(cls = "welcome-gain-value", content = f"+$woodGain%,d")
        ))
      if faithGain > 0 then
        gainsContainer.appendChild(div(cls = "welcome-gain-item")(
          span(cls = "welcome-gain-icon", content = "✨"),
          span(cls = "welcome-gain-value", content = f"+$faithGain%,d")
        ))
      body.appendChild(gainsContainer)
    
    getElementById("tile-kingdom-welcome-modal").foreach: modal =>
      modal.classList.add("show")

  private def hideWelcomeBackModal(): Unit =
    getElementById("tile-kingdom-welcome-modal").foreach: modal =>
      modal.classList.remove("show")

  private def updateProgressBars(): Unit =
    currentGame.unlockedTiles.filter(t => t.isWheatField || t.isWoodcutter || t.isBureau).foreach: tile =>
      val coord = tile.coord
      val progress = tileProgress.getOrElse(coord, 0.0)
      getElementById(s"progress-bar-${coord.row}-${coord.col}").foreach: bar =>
        bar.style.width = s"${(progress * 100).toInt}%"

  private def showFloatingReward(coord: Coord, amount: Int, emoji: String = "", isSpend: Boolean = false, offsetIndex: Int = 0): Unit =
    getElementById(s"tile-${coord.row}-${coord.col}").foreach: tileElem =>
      val floater = div()
      floater.className = if isSpend then "floating-reward floating-spend" else "floating-reward"
      val sign = if isSpend then "-" else "+"
      floater.textContent = s"$sign${formatNumber(amount)}$emoji"
      // Apply vertical offset to prevent overlapping (each index shifts down)
      if offsetIndex > 0 then
        floater.style.top = s"calc(50% + ${offsetIndex * 18}px)"
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

  private def showBureauProjectile(fromCoord: Coord, toCoord: Coord, onComplete: () => Unit): Unit =
    getElementById("tile-kingdom-grid").foreach: grid =>
      val projectile = div(cls = "bureau-projectile", content = "📜")

      // Calculate pixel positions (center of tiles)
      val tilePixelSize = 70 * zoomLevel
      val fromX = fromCoord.col * TileSize + tilePixelSize / 2 - 12
      val fromY = fromCoord.row * TileSize + tilePixelSize / 2 - 12
      val toX = toCoord.col * TileSize + tilePixelSize / 2 - 12
      val toY = toCoord.row * TileSize + tilePixelSize / 2 - 12

      // Set initial position
      projectile.style.left = s"${fromX}px"
      projectile.style.top = s"${fromY}px"

      grid.appendChild(projectile)

      // Trigger animation to target after a brief delay (to allow initial render)
      window.setTimeout(() =>
        projectile.style.left = s"${toX}px"
        projectile.style.top = s"${toY}px"
      , 20)

      // When animation completes, trigger effects and remove projectile
      window.setTimeout(() =>
        projectile.classList.add("arrived")
        onComplete()
        window.setTimeout(() => projectile.remove(), 200)
      , 420)

  private def updatePoliticianTimer(): Unit =
    // If no town hall, don't show timer or rare chance
    if !currentGame.hasTownHall then
      setElementText("politician-timer", "")
      setElementText("politician-rare-chance", "")
      return

    // Update rare chance display
    val rareChance = TileKingdomLogic.rarePoliticianChance(currentGame)
    val rareChancePercent = (rareChance * 100).toInt
    val rareText = s"⭐ $rareChancePercent%"
    setElementText("politician-rare-chance", rareText)

    // If roster is full, show "Full"
    val maxRosterSize = TileKingdomLogic.maxPoliticianRosterSize(currentGame)
    if currentGame.politicianRoster.size >= maxRosterSize then
      setElementText("politician-timer", "Full")
      return

    val speedMultiplier = TileKingdomLogic.politicianGenerationSpeedMultiplier(currentGame)
    val baseIntervalSeconds = TileKingdomLogic.PoliticianGenerationIntervalSeconds

    // Calculate remaining time based on progress
    val remainingProgress = 1.0 - currentGame.politicianGenerationProgress
    val remainingSeconds = ((remainingProgress * baseIntervalSeconds) / speedMultiplier).toInt
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timerText = if speedMultiplier > 1.0 then f"Next: $minutes%d:$seconds%02d ⚡" else f"Next: $minutes%d:$seconds%02d"
    setElementText("politician-timer", timerText)

