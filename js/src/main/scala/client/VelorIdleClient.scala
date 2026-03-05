package client

import org.scalajs.dom
import org.scalajs.dom.*
import scala.scalajs.js
import scala.util.Try
import scala.util.chaining.*
import shared.VelorIdle.*
import client.components.laminar.veloride.*
import com.raquo.laminar.api.L.{render as laminarRender, *}

def initializeVelorIdle(): Unit =
  VelorIdleClient.init()

object VelorIdleClient:

  private val StorageKey = "velor_idle_game_state"
  private val SaveIntervalMs: Int = 30_000
  private val TickIntervalMs: Int = 100

  private var currentGame: VelorIdleGame = VelorIdleGame.newGame(System.currentTimeMillis())
  private var tickIntervalHandle: Option[Int] = None
  private var saveIntervalHandle: Option[Int] = None
  private var isDirty: Boolean = false

  // ============================================================================
  // Initialization
  // ============================================================================

  def init(): Unit =
    println("[VelorIdle] Initializing Velor Idle game")
    loadGame()
    buildUI()
    VelorIdleState.update(currentGame)
    startGameTicker()
    startSaveTimer()
    registerLifecycleHooks()

  def cleanup(): Unit =
    println("[VelorIdle] Cleaning up Velor Idle game")
    stopGameTicker()
    stopSaveTimer()
    saveIfDirty()

  // ============================================================================
  // UI Building
  // ============================================================================

  private def buildUI(): Unit =
    val container = document.getElementById("velor-idle-container")
    if container == null then
      println("[VelorIdle] ERROR: Container not found!")
      return

    container.innerHTML = ""

    val app = div(
      cls := "velor-container",

      // Header
      Header(),

      // Toast notifications
      ToastSystem.container(),

      // Offline Progress Modal
      OfflineProgressModal(),

      // Main content area
      div(
        cls := "velor-main",
        child <-- VelorIdleState.viewModeSignal.map:
          case VelorIdleState.ViewMode.SkillSelect => skillSelectView()
          case VelorIdleState.ViewMode.SkillTraining => skillTrainingView()
          case VelorIdleState.ViewMode.Adventure => adventureView()
          case VelorIdleState.ViewMode.Inventory => inventoryView()
          case VelorIdleState.ViewMode.Character => characterView()
          case VelorIdleState.ViewMode.Shop => shopView()
          case VelorIdleState.ViewMode.Settings => settingsView()
          case VelorIdleState.ViewMode.SkillTrees => skillTreesView()
          case VelorIdleState.ViewMode.SkillBinding => skillBindingView()
      ),

      // Bottom navigation
      BottomNav()
    )

    laminarRender(container, app)

  private def skillSelectView(): HtmlElement =
    div(
      cls := "velor-view",
      h2(styleAttr := "margin-bottom: 1rem; text-align: center;", "Choose a Skill"),
      SkillSelector(VelorIdleState.selectSkill)
    )

  private def skillTrainingView(): HtmlElement =
    SkillTrainingView(handleStartAction, handleStopAction)

  private def adventureView(): HtmlElement =
    AdventureView(handleStartCombat, handleUseSkill, handleStopCombat, handleRestartCombat, handleRest)

  private def inventoryView(): HtmlElement =
    div(
      cls := "velor-view",
      InventoryPanel(InventoryPanel.Actions(
        onSellItem = handleSellItem,
        onSetJunk = handleSetJunk,
        onSellAllJunk = handleSellAllJunk
      ))
    )

  private def characterView(): HtmlElement =
    CharacterPanel(
      onDrinkPotion = handleDrinkPotion,
      onRemovePotion = handleRemovePotion,
      onEquipTablet = handleEquipTablet,
      onUnequipTablet = handleUnequipTablet,
      onEquipWeapon = handleEquipWeapon,
      onUnequipWeapon = handleUnequipWeapon,
      onEquipArmor = handleEquipArmor,
      onUnequipArmor = handleUnequipArmor,
      onSellEquipment = handleSellEquipment,
      onSellEquipmentBulk = handleSellEquipmentBulk,
      onSellEquipmentBulkMagical = handleSellEquipmentBulkMagical
    )

  private def shopView(): HtmlElement =
    ShopPanel(handleBuyItem, handleBuyInventorySlots)

  private def settingsView(): HtmlElement =
    div(
      cls := "velor-view",
      div(
        cls := "velor-skill-card",
        h3("⚙️ Settings"),
        div(
          styleAttr := "display: flex; flex-direction: column; gap: 1rem; margin-top: 1rem;",
          button(
            cls := "btn btn-secondary",
            "💾 Save Game",
            onClick --> { _ =>
              saveGame()
              ToastSystem.show("Game saved!")
            }
          ),
          button(
            cls := "btn btn-secondary",
            "🗑️ Reset Game",
            onClick --> { _ => handleReset() }
          )
        ),
        DevPanel()
      )
    )

  private def skillTreesView(): HtmlElement =
    SkillTreeView(
      onAllocatePoint = handleAllocateSkillPoint,
      onDeallocatePoint = handleDeallocateSkillPoint,
      onBindSkill = handleBindSkill,
      onBack = () => VelorIdleState.setViewMode(VelorIdleState.ViewMode.Adventure)
    )

  private def skillBindingView(): HtmlElement =
    // Redirect to skill trees - binding is now integrated there
    skillTreesView()

  // ============================================================================
  // Event Handlers
  // ============================================================================


  private def handleStartAction(actionId: String): Unit =
    VelorIdleLogic.startAction(currentGame, actionId, System.currentTimeMillis()) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleStopAction(): Unit =
    currentGame = VelorIdleLogic.stopAction(currentGame)
    VelorIdleState.update(currentGame)
    isDirty = true

  private def handleSellItem(item: Item, count: Long): Unit =
    VelorIdleLogic.sellItem(currentGame, item, count) match
      case Right(newGame) =>
        val gold = Item.sellValue(item) * count
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"💰 Sold ${count}x ${Item.displayName(item)} for $gold gold")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleSetJunk(item: Item, isJunk: Boolean): Unit =
    currentGame = VelorIdleLogic.setJunk(currentGame, item, isJunk)
    VelorIdleState.update(currentGame)
    isDirty = true

  private def handleSellAllJunk(): Unit =
    VelorIdleLogic.sellAllJunk(currentGame) match
      case Right((newGame, totalGold, itemCount)) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"🗑️ Sold $itemCount junk items for $totalGold gold!")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleDrinkPotion(potion: Item): Unit =
    VelorIdleLogic.drinkPotion(currentGame, potion) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"🧪 Drank ${Item.displayName(potion)}!")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleRemovePotion(): Unit =
    currentGame = VelorIdleLogic.removeActivePotion(currentGame)
    VelorIdleState.update(currentGame)
    isDirty = true
    ToastSystem.show("Potion effect removed")

  private def handleEquipTablet(tablet: Item, slot: Int): Unit =
    VelorIdleLogic.equipTablet(currentGame, tablet, slot) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"📜 Equipped ${Item.displayName(tablet)} in slot $slot")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleUnequipTablet(slot: Int): Unit =
    VelorIdleLogic.unequipTablet(currentGame, slot) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"📜 Tablet unequipped from slot $slot")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleEquipWeapon(instanceId: Long): Unit =
    VelorIdleLogic.equipWeapon(currentGame, instanceId) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"⚔️ Weapon equipped!")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleUnequipWeapon(): Unit =
    VelorIdleLogic.unequipWeapon(currentGame) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"⚔️ Weapon unequipped")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleEquipArmor(instanceId: Long): Unit =
    VelorIdleLogic.equipArmor(currentGame, instanceId) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"🛡️ Armor equipped!")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleUnequipArmor(): Unit =
    VelorIdleLogic.unequipArmor(currentGame) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"🛡️ Armor unequipped")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleSellEquipment(instanceId: Long): Unit =
    VelorIdleLogic.sellEquipment(currentGame, instanceId) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"💰 Equipment sold!")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleSellEquipmentBulk(defId: String, quality: EquipmentQuality): Unit =
    VelorIdleLogic.sellEquipmentBulk(currentGame, defId, quality) match
      case Right((newGame, count, totalGold)) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"💰 Sold $count items for $totalGold gold!")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleSellEquipmentBulkMagical(defId: String): Unit =
    VelorIdleLogic.sellEquipmentBulkMagical(currentGame, defId) match
      case Right((newGame, count, totalGold)) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"💰 Sold $count magical items for $totalGold gold!")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleBuyItem(item: Item, count: Int): Unit =
    VelorIdleLogic.buyItem(currentGame, item, count) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"📦 Bought ${count}x ${Item.displayName(item)}")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleBuyInventorySlots(): Unit =
    VelorIdleLogic.buyInventorySlots(currentGame) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"📦 Inventory expanded to ${newGame.inventory.maxSlots} slots!")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  // ============================================================================
  // Combat Event Handlers
  // ============================================================================

  private def handleStartCombat(enemyId: String): Unit =
    VelorIdleLogic.startAction(currentGame, enemyId, System.currentTimeMillis()) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleUseSkill(slotIndex: Int): Unit =
    VelorIdleLogic.useAdventureSkill(currentGame, slotIndex, System.currentTimeMillis()) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleStopCombat(): Unit =
    currentGame = VelorIdleLogic.stopAction(currentGame)
    VelorIdleState.update(currentGame)
    isDirty = true

  private def handleRestartCombat(): Unit =
    VelorIdleLogic.restartAdventure(currentGame, System.currentTimeMillis()) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleRest(): Unit =
    currentGame = VelorIdleLogic.startRest(currentGame)
    VelorIdleState.update(currentGame)
    isDirty = true

  // ============================================================================
  // Skill Tree Event Handlers
  // ============================================================================

  private def handleAllocateSkillPoint(skillId: String): Unit =
    VelorIdleLogic.allocateCombatSkillPoint(currentGame, skillId) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        shared.VelorIdle.SkillTrees.getSkillById(skillId).foreach { skill =>
          val level = newGame.adventureState.combatSkillState.getSkillLevel(skillId)
          ToastSystem.show(s"✨ ${skill.name} → Level $level")
        }
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleDeallocateSkillPoint(skillId: String): Unit =
    VelorIdleLogic.deallocateCombatSkillPoint(currentGame, skillId) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        shared.VelorIdle.SkillTrees.getSkillById(skillId).foreach { skill =>
          val level = newGame.adventureState.combatSkillState.getSkillLevel(skillId)
          val cost = shared.VelorIdle.SkillTreeLogic.RefundCostGold
          if level > 0 then
            ToastSystem.show(s"💰 ${skill.name} → Level $level (-$cost gold)")
          else
            ToastSystem.show(s"💰 ${skill.name} refunded (-$cost gold)")
        }
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleBindSkill(skillId: String, slot: Int): Unit =
    VelorIdleLogic.bindCombatSkill(currentGame, skillId, slot) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        shared.VelorIdle.SkillTrees.getSkillById(skillId).foreach { skill =>
          ToastSystem.show(s"⚔️ ${skill.name} bound to Slot ${slot + 1}")
        }
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleUnbindSkill(slot: Int): Unit =
    VelorIdleLogic.unbindCombatSkill(currentGame, slot) match
      case Right(newGame) =>
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"✖ Slot ${slot + 1} cleared")
      case Left(error) =>
        ToastSystem.show(s"❌ $error")

  private def handleReset(): Unit =
    if dom.window.confirm("Are you sure you want to reset? All progress will be lost!") then
      currentGame = VelorIdleGame.newGame(System.currentTimeMillis())
      VelorIdleState.update(currentGame)
      saveGame()
      ToastSystem.show("Game reset!")

  // ============================================================================
  // Game Loop
  // ============================================================================

  private def startGameTicker(): Unit =
    tickIntervalHandle = Some(
      dom.window.setInterval(
        () => gameTick(),
        TickIntervalMs
      )
    )

  private def stopGameTicker(): Unit =
    tickIntervalHandle.foreach(dom.window.clearInterval)
    tickIntervalHandle = None

  private def gameTick(): Unit =
    // Sync from state in case UI modified it directly (e.g., skill selection from Header)
    currentGame = VelorIdleState.current
    
    val currentTime = System.currentTimeMillis()
    
    // Process game tick (handles all activities including Adventure)
    val (newGame, events) = VelorIdleLogic.tick(currentGame, currentTime)

    if events.nonEmpty then
      // Meaningful change - update full game state
      currentGame = newGame
      VelorIdleState.update(currentGame)
      isDirty = true
      processEvents(events)
    else if newGame.actionProgress != currentGame.actionProgress || newGame != currentGame then
      // Progress or state changed
      currentGame = newGame
      VelorIdleState.update(currentGame)
      VelorIdleState.updateProgress(newGame.actionProgress)

  private def processEvents(events: Vector[GameEvent]): Unit =
    events.foreach:
      case GameEvent.XpGained(skill, amount) =>
        FloatingRewards.showXp(amount)
      case GameEvent.ItemGained(item, count) =>
        FloatingRewards.showItem(item, count)
      case GameEvent.LevelUp(skill, level) =>
        ToastSystem.showLevelUp(skill, level)
      case GameEvent.RareDrop(item) =>
        ToastSystem.showRareDrop(item)
      case GameEvent.InventoryFull =>
        ToastSystem.show("⚠️ Inventory full!")
      case GameEvent.OutOfMaterials =>
        ToastSystem.show("⚠️ Out of materials!")
      case GameEvent.ActionFailed(reason) =>
        ToastSystem.show(s"🔥 $reason")
      case GameEvent.PotionExpired(potion) =>
        ToastSystem.show(s"🧪 ${Item.displayName(potion)} wore off")
      case GameEvent.TabletConsumed(tablet, slot) =>
        ToastSystem.show(s"📜 ${Item.displayName(tablet)} depleted from slot $slot")
      case GameEvent.GoldGained(amount) =>
        FloatingRewards.showGold(amount)
      case GameEvent.ThievingFailed(reason) =>
        ToastSystem.show(s"🚨 $reason")
      case GameEvent.AdventureEnemyDefeated(enemyId) =>
        ToastSystem.show(s"⚔️ Enemy defeated!")
      case GameEvent.AdventurePlayerDied =>
        ToastSystem.show(s"💀 You were defeated!")
      case _ => ()

  // ============================================================================
  // Save/Load
  // ============================================================================

  private def startSaveTimer(): Unit =
    saveIntervalHandle = Some(
      dom.window.setInterval(
        () => saveIfDirty(),
        SaveIntervalMs
      )
    )

  private def stopSaveTimer(): Unit =
    saveIntervalHandle.foreach(dom.window.clearInterval)
    saveIntervalHandle = None

  private def saveIfDirty(): Unit =
    if isDirty then
      saveGame()
      isDirty = false

  private def saveGame(): Unit =
    Try:
      val json = upickle.default.write(currentGame)
      dom.window.localStorage.setItem(StorageKey, json)
      println("[VelorIdle] Game saved")
    .failed.foreach(e => println(s"[VelorIdle] Save failed: ${e.getMessage}"))

  private def loadGame(): Unit =
    Try:
      Option(dom.window.localStorage.getItem(StorageKey))
        .filter(_.nonEmpty)
        .foreach { json =>
          val loadedGame = upickle.default.read[VelorIdleGame](json)
          val currentTime = System.currentTimeMillis()
          
          // Calculate offline progress
          val offlineResult = OfflineProgress.calculateOfflineProgress(
            loadedGame, 
            loadedGame.lastTickTime, 
            currentTime
          )
          
          // Apply the result and update lastTickTime
          currentGame = offlineResult.game.copy(lastTickTime = currentTime)
          
          // Show offline progress modal if any significant progress was made
          if offlineResult.secondsProcessed >= OfflineProgress.ChunkDurationSeconds then
            OfflineProgressModal.show(offlineResult)
          
          println("[VelorIdle] Game loaded")
        }
    .failed.foreach { e =>
      println(s"[VelorIdle] Load failed: ${e.getMessage}, starting fresh")
      currentGame = VelorIdleGame.newGame(System.currentTimeMillis())
    }


  private def registerLifecycleHooks(): Unit =
    dom.window.addEventListener("beforeunload", (_: Event) => saveIfDirty())
    dom.window.addEventListener("visibilitychange", (_: Event) =>
      if dom.document.visibilityState == "hidden" then saveIfDirty()
    )

