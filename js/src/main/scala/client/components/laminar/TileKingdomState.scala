package client.components.laminar

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*

/** Centralized reactive state for TileKingdom.
  *
  * This provides a single source of truth that Laminar components can observe.
  * When the game state changes, all subscribing components automatically update.
  */
object TileKingdomState:

  /** The main game state signal. Components read from this. */
  private val gameVar: Var[TileKingdomGame] = Var(TileKingdomLogic.newGame(System.currentTimeMillis()))

  /** Read-only signal for components to observe */
  private val gameSignal: Signal[TileKingdomGame] = gameVar.signal

  /** Update the game state - this triggers all reactive updates automatically */
  def update(game: TileKingdomGame): Unit =
    gameVar.set(game)

  /** Modify the game state with a function */
  def modify(f: TileKingdomGame => TileKingdomGame): Unit =
    gameVar.update(f)

  // Derived signals for specific values - these only recompute when their dependencies change

  val wheatSignal: Signal[Double] = gameSignal.map(_.wheat)
  val woodSignal: Signal[Double] = gameSignal.map(_.wood)
  val stoneSignal: Signal[Double] = gameSignal.map(_.stone)
  val faithSignal: Signal[Double] = gameSignal.map(_.faith)
  val goldSignal: Signal[Int] = gameSignal.map(_.gold)

  val totalAbdicationsSignal: Signal[Int] = gameSignal.map(_.totalAbdications)
  val legacyPointsSignal: Signal[Int] = gameSignal.map(_.legacyPoints)
  val skillPointsSignal: Signal[Int] = gameSignal.map(_.skillPoints)

  // Income rate signals
  val wheatIncomeSignal: Signal[Double] = gameSignal.map(g => TileKingdomLogic.totalWheatProductionRate(g))
  val woodIncomeSignal: Signal[Double] = gameSignal.map(g => TileKingdomLogic.totalWoodProductionRate(g))
  val stoneIncomeSignal: Signal[Double] = gameSignal.map(g => TileKingdomLogic.totalStoneProductionRate(g))
  val faithIncomeSignal: Signal[Double] = gameSignal.map(g => TileKingdomLogic.totalFaithProductionRate(g))
  val totalIncomeSignal: Signal[Double] = gameSignal.map(_.totalIncomeRate)

  // Abdication reward (depends on total income)
  val abdicationRewardSignal: Signal[Int] = gameSignal.map(_.abdicationGoldReward)

  // Abdication button state
  val allTilesFilledSignal: Signal[Boolean] = gameSignal.map(_.allTilesFilled)

  // Sail button state
  val canSailSignal: Signal[Boolean] = gameSignal.map(_.canSail)
  val sailLegacyRewardSignal: Signal[Int] = gameSignal.map(_.sailLegacyReward)
  val tileCountSignal: Signal[Int] = gameSignal.map(_.unlockedTiles.size)

  // Skills button state
  val hasSailedSignal: Signal[Boolean] = gameSignal.map(_.hasSailed)

  // Tile unlock costs
  val nextTileUnlockCostsSignal: Signal[Seq[Int]] = gameSignal.map: game =>
    val currentCount = game.unlockedTiles.size
    (0 until 3).map(i => TileKingdomLogic.tileUnlockCost(currentCount + i))

