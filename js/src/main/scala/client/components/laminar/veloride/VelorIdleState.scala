package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Centralized reactive state for Velor Idle.
  *
  * This provides a single source of truth that Laminar components can observe.
  * When the game state changes, all subscribing components automatically update.
  */
object VelorIdleState:

  /** The main game state Var */
  private val gameVar: Var[VelorIdleGame] = Var(VelorIdleGame.newGame(System.currentTimeMillis()))

  /** Read-only signal for components */
  val gameSignal: Signal[VelorIdleGame] = gameVar.signal

  /** Update the game state */
  def update(game: VelorIdleGame): Unit =
    gameVar.set(game)

  /** Modify the game state with a function */
  def modify(f: VelorIdleGame => VelorIdleGame): Unit =
    gameVar.update(f)

  /** Get current game state */
  def current: VelorIdleGame = gameVar.now()

  // ============================================================================
  // Derived Signals
  // ============================================================================

  val goldSignal: Signal[Long] = gameSignal.map(_.gold)

  val currentSkillSignal: Signal[Option[Skill]] = gameSignal.map(_.currentSkill)

  val activeActionSignal: Signal[ActiveAction] = gameSignal.map(_.activeAction)

  val actionProgressSignal: Signal[Double] = gameSignal.map(_.actionProgress)

  val inventorySignal: Signal[Inventory] = gameSignal.map(_.inventory)

  def skillStateSignal(skill: Skill): Signal[SkillState] =
    gameSignal.map(_.skills.getOrElse(skill, SkillState.initial))

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
    case Inventory
    case Shop
    case Settings
  
  val viewModeVar: Var[ViewMode] = Var(ViewMode.SkillSelect)
  val viewModeSignal: Signal[ViewMode] = viewModeVar.signal
  
  def setViewMode(mode: ViewMode): Unit = viewModeVar.set(mode)
  
  /** Navigate to skill training screen (call after selecting skill via client) */
  def goToSkillTraining(): Unit =
    viewModeVar.set(ViewMode.SkillTraining)

