package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Centralized reactive state for Velor Idle.
  *
  * This provides a single source of truth that Laminar components can observe.
  * When the game state changes, all subscribing components automatically update.
  * 
  * IMPORTANT: Action progress is stored separately to avoid triggering full
  * reactive updates 10 times per second during skill training.
  */
object VelorIdleState:

  /** The main game state Var - updated only on meaningful changes */
  private val gameVar: Var[VelorIdleGame] = Var(VelorIdleGame.newGame(System.currentTimeMillis()))

  /** Separate progress Var - updated frequently but only progress bar subscribes */
  private val progressVar: Var[Double] = Var(0.0)

  /** Read-only signal for components */
  val gameSignal: Signal[VelorIdleGame] = gameVar.signal

  /** Progress signal - use this for progress bars, NOT gameSignal.map(_.actionProgress) */
  val actionProgressSignal: Signal[Double] = progressVar.signal

  /** Update the game state (does NOT update progress - use updateProgress for that) */
  def update(game: VelorIdleGame): Unit =
    gameVar.set(game)
    // Sync progress when game state changes (e.g., action started/stopped)
    progressVar.set(game.actionProgress)

  /** Update only the progress (called frequently during ticks) */
  def updateProgress(progress: Double): Unit =
    progressVar.set(progress)

  /** Modify the game state with a function */
  def modify(f: VelorIdleGame => VelorIdleGame): Unit =
    val newGame = f(gameVar.now())
    gameVar.set(newGame)
    progressVar.set(newGame.actionProgress)

  /** Get current game state */
  def current: VelorIdleGame = gameVar.now()

  // ============================================================================
  // Derived Signals
  // ============================================================================

  val goldSignal: Signal[Long] = gameSignal.map(_.gold).distinct

  val currentSkillSignal: Signal[Option[Skill]] = gameSignal.map(_.currentSkill).distinct

  val activeActionSignal: Signal[ActiveAction] = gameSignal.map(_.activeAction).distinct


  val inventorySignal: Signal[Inventory] = gameSignal.map(_.inventory).distinct

  val potionSlotsSignal: Signal[PotionSlots] = gameSignal.map(_.potionSlots).distinct

  val tabletSlotsSignal: Signal[TabletSlots] = gameSignal.map(_.tabletSlots).distinct

  val adventureStateSignal: Signal[AdventureState] = gameSignal.map(_.adventureState).distinct

  def skillStateSignal(skill: Skill): Signal[SkillState] =
    gameSignal.map(_.skills.getOrElse(skill, SkillState.initial))

  def actionStateSignal(actionId: String): Signal[ActionState] =
    gameSignal.map(_.actionLevels.getOrElse(actionId, ActionState.initial))

  val currentSkillStateSignal: Signal[Option[SkillState]] =
    gameSignal.map(g => g.currentSkill.map(s => g.skills.getOrElse(s, SkillState.initial)))

  /** Available actions for current skill */
  val availableActionsSignal: Signal[Vector[GatheringAction]] =
    currentSkillSignal.map:
      case Some(skill) if Skill.isGathering(skill) => GatheringActions.forSkill(skill)
      case _ => Vector.empty

  /** Available processing actions for current skill */
  val availableProcessingActionsSignal: Signal[Vector[ProcessingAction]] =
    currentSkillSignal.map:
      case Some(skill) if Skill.isProcessing(skill) => ProcessingActions.forSkill(skill)
      case _ => Vector.empty

  // ============================================================================
  // UI State (not persisted)
  // ============================================================================

  enum ViewMode:
    case SkillSelect    // Grid of all skills to choose from
    case SkillTraining  // Active skill training screen
    case Adventure      // Adventure/combat view
    case Inventory
    case Character      // Character screen with sub-tabs (Potions, Tablets, Equipment)
    case Shop
    case Settings
    case SkillTrees     // Skill tree selection and point allocation
    case SkillBinding   // Bind skills to combat slots

  enum CharacterTab:
    case Potions
    case Tablets
    case Equipment

  val viewModeVar: Var[ViewMode] = Var(ViewMode.SkillSelect)
  val viewModeSignal: Signal[ViewMode] = viewModeVar.signal

  val characterTabVar: Var[CharacterTab] = Var(CharacterTab.Potions)
  val characterTabSignal: Signal[CharacterTab] = characterTabVar.signal

  def setViewMode(mode: ViewMode): Unit = viewModeVar.set(mode)
  def setCharacterTab(tab: CharacterTab): Unit = characterTabVar.set(tab)

  /** Navigate to skill training screen (call after selecting skill via client) */
  def goToSkillTraining(): Unit =
    viewModeVar.set(ViewMode.SkillTraining)

  // ============================================================================
  // Actions (directly modify state - no callbacks needed)
  // ============================================================================

  /** Select a skill and navigate to training view */
  def selectSkill(skill: Skill): Unit =
    modify(game => VelorIdleLogic.selectSkill(game, skill))
    if skill == Skill.Adventure then
      viewModeVar.set(ViewMode.Adventure)
    else
      goToSkillTraining()

