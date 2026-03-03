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

      // Main content area
      div(
        cls := "velor-main",
        child <-- VelorIdleState.viewModeSignal.map:
          case VelorIdleState.ViewMode.SkillSelect => skillSelectView()
          case VelorIdleState.ViewMode.SkillTraining => skillTrainingView()
          case VelorIdleState.ViewMode.Inventory => inventoryView()
          case VelorIdleState.ViewMode.Potions => potionsView()
          case VelorIdleState.ViewMode.Shop => shopView()
          case VelorIdleState.ViewMode.Settings => settingsView()
      ),

      // Bottom navigation
      BottomNav()
    )

    laminarRender(container, app)

  private def skillSelectView(): HtmlElement =
    div(
      h2(styleAttr := "margin-bottom: 1rem; text-align: center;", "Choose a Skill"),
      SkillSelector(VelorIdleState.selectSkill)
    )

  private def skillTrainingView(): HtmlElement =
    SkillTrainingView(handleStartAction, handleStopAction)

  private def inventoryView(): HtmlElement =
    InventoryPanel(handleSellItem)

  private def potionsView(): HtmlElement =
    PotionPanel(handleDrinkPotion, handleRemovePotion)

  private def shopView(): HtmlElement =
    div(
      cls := "velor-skill-card",
      h3("🏪 Shop"),
      p(cls := "velor-text-muted", "Coming soon! Sell items from inventory by tapping them.")
    )

  private def settingsView(): HtmlElement =
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

  // ============================================================================
  // Event Handlers
  // ============================================================================


  private def handleStartAction(actionId: String): Unit =
    VelorIdleLogic.startAction(currentGame, actionId) match
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
    VelorIdleLogic.sellAll(currentGame, item) match
      case Right(newGame) =>
        val sold = currentGame.inventory.getCount(item)
        val gold = Item.sellValue(item) * sold
        currentGame = newGame
        VelorIdleState.update(currentGame)
        isDirty = true
        ToastSystem.show(s"💰 Sold ${sold}x ${Item.displayName(item)} for $gold gold")
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
    val (newGame, events) = VelorIdleLogic.tick(currentGame, currentTime)

    if events.nonEmpty then
      // Meaningful change - update full game state
      currentGame = newGame
      VelorIdleState.update(currentGame)
      isDirty = true
      processEvents(events)
    else if newGame.actionProgress != currentGame.actionProgress then
      // Only progress changed - update just the progress signal
      currentGame = newGame
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
          currentGame = upickle.default.read[VelorIdleGame](json)
          // Update lastTickTime to now to avoid processing all offline time
          currentGame = currentGame.copy(lastTickTime = System.currentTimeMillis())
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

