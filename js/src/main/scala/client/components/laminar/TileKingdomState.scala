package client.components.laminar

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*

/** Centralized reactive state for TileKingdom.
  *
  * This provides a single source of truth that Laminar components can observe.
  * When the game state changes, all subscribing components automatically update.
  */
object TileKingdomState:

  /** The main game state Var (private - use update/modify to change) */
  private val gameVar: Var[TileKingdomGame] = Var(TileKingdomLogic.newGame(System.currentTimeMillis()))

  /** Read-only signal for components that need full game state access */
  val gameSignal: Signal[TileKingdomGame] = gameVar.signal

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

  // Politician timer signals
  val politicianTimerSignal: Signal[Option[(Int, Boolean)]] = gameSignal.map: game =>
    if !game.hasTownHall then None
    else
      val maxRosterSize = TileKingdomLogic.maxPoliticianRosterSize(game)
      if game.politicianRoster.size >= maxRosterSize then None // Full
      else
        val speedMultiplier = TileKingdomLogic.politicianGenerationSpeedMultiplier(game)
        val baseIntervalSeconds = TileKingdomLogic.PoliticianGenerationIntervalSeconds
        val remainingProgress = 1.0 - game.politicianGenerationProgress
        val remainingSeconds = ((remainingProgress * baseIntervalSeconds) / speedMultiplier).toInt
        Some((remainingSeconds, speedMultiplier > 1.0))

  val rosterFullSignal: Signal[Boolean] = gameSignal.map: game =>
    game.hasTownHall && game.politicianRoster.size >= TileKingdomLogic.maxPoliticianRosterSize(game)

  val hasTownHallSignal: Signal[Boolean] = gameSignal.map(_.hasTownHall)

  val rareChanceSignal: Signal[Double] = gameSignal.map: game =>
    if game.hasTownHall then TileKingdomLogic.rarePoliticianChance(game) else 0.0

  // Politician roster
  val politicianRosterSignal: Signal[List[Politician]] = gameSignal.map(_.politicianRoster)

  // Tile unlock costs
  val nextTileUnlockCostsSignal: Signal[Seq[Int]] = gameSignal.map: game =>
    val currentCount = game.unlockedTiles.size
    (0 until 3).map(i => TileKingdomLogic.tileUnlockCost(currentCount + i))

  // ============================================================================
  // Tile Grid Signals
  // ============================================================================

  /** Signal for all tiles in the game */
  val tilesSignal: Signal[Map[Coord, Tile]] = gameSignal.map(_.tiles)

  /** Signal for unlockable tile coordinates */
  val unlockableCoordsSignal: Signal[Set[Coord]] = gameSignal.map(TileKingdomLogic.unlockableCoords)

  /** Signal for next tile unlock cost */
  val nextTileUnlockCostSignal: Signal[Int] = gameSignal.map(_.nextTileUnlockCost)

  /** Signal for tile points */
  val tilePointsSignal: Signal[Int] = gameSignal.map(_.tilePoints)

  /** Check if player can afford to unlock a tile (has tile points OR enough gold) */
  val canAffordUnlockSignal: Signal[Boolean] = gameSignal.map: game =>
    game.tilePoints > 0 || game.gold >= game.nextTileUnlockCost

  /** Signal for unlocked tile list */
  val unlockedTilesSignal: Signal[List[Tile]] = gameSignal.map(_.unlockedTiles)

  // Building unlock signals - use distinct to only emit when value changes
  val canBuildFarmSignal: Signal[Boolean] = gameSignal.map(_.canBuildFarm).distinct
  val canBuildWoodcutterSignal: Signal[Boolean] = gameSignal.map(_.canBuildWoodcutter).distinct
  val canBuildQuarrySignal: Signal[Boolean] = gameSignal.map(_.canBuildQuarry).distinct
  val canBuildBureauSignal: Signal[Boolean] = gameSignal.map(_.canBuildBureau).distinct
  val canBuildTempleSignal: Signal[Boolean] = gameSignal.map(_.canBuildTemple).distinct
  val canBuildTownHallSignal: Signal[Boolean] = gameSignal.map(_.canBuildTownHall).distinct
  val canBuildAcademySignal: Signal[Boolean] = gameSignal.map(_.canBuildAcademy).distinct
  val canBuildTavernSignal: Signal[Boolean] = gameSignal.map(_.canBuildTavern).distinct

  /** Get current game state (for imperative code during transition) */
  def currentGame: TileKingdomGame = gameVar.now()

