package client.components.laminar.tilekingdom

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.TileKingdomState

/** Build menu component for empty tiles.
  *
  * Provides a two-level menu: main categories (Resources/Management)
  * and submenus with specific building options.
  */
object BuildMenu:

  /** Build action callbacks */
  case class Actions(
    onBuildWheatField: () => Unit,
    onBuildFarm: () => Unit,
    onBuildWoodcutter: () => Unit,
    onBuildQuarry: () => Unit,
    onBuildBureau: () => Unit,
    onBuildTemple: () => Unit,
    onBuildTownHall: () => Unit,
    onBuildAcademy: () => Unit,
    onBuildTavern: () => Unit,
    onCancel: () => Unit
  )

  /** A single build option with cost checking */
  private def buildOption(
    icon: String,
    name: String,
    cost: Int,
    costEmoji: String,
    hasEnoughSignal: Signal[Boolean],
    onClickHandler: () => Unit
  ): HtmlElement =
    div(
      cls := "build-option",
      div(cls := "build-icon", icon),
      div(cls := "build-name", name),
      div(
        cls <-- hasEnoughSignal.map(has => if has then "build-cost" else "build-cost insufficient"),
        s"${TileUtils.formatNumber(cost)}$costEmoji"
      ),
      onClick --> { e =>
        e.stopPropagation()
        if !TileGridState.wasDragging then onClickHandler()
      }
    )

  /** Back button for navigation */
  private def backButton(onClickHandler: () => Unit): HtmlElement =
    div(
      cls := "build-option build-back",
      i(cls := "fa-solid fa-arrow-left build-icon"),
      div(cls := "build-name", "Back"),
      onClick --> { e =>
        e.stopPropagation()
        if !TileGridState.wasDragging then onClickHandler()
      }
    )

  /** Category button for main menu */
  private def categoryButton(icon: String, name: String, extraCls: String, onClickHandler: () => Unit): HtmlElement =
    div(
      cls := s"build-option build-category $extraCls",
      div(cls := "build-icon", icon),
      div(cls := "build-name", name),
      onClick --> { e =>
        e.stopPropagation()
        if !TileGridState.wasDragging then onClickHandler()
      }
    )

  def apply(actions: Actions): HtmlElement =
    val gameSignal = TileKingdomState.gameSignal
    val activeSubmenuSignal = TileGridState.activeSubmenu.signal

    // Resource checks
    val wheatCost = TileKingdomLogic.wheatFieldBuildCost
    val farmCost = TileKingdomLogic.farmBuildCost
    val woodcutterCost = TileKingdomLogic.woodcutterBuildCost
    val bureauCost = TileKingdomLogic.bureauBuildCost
    val templeCost = TileKingdomLogic.templeBuildCost
    val quarryCost = TileKingdomLogic.quarryBuildCost

    // Dynamic costs - use distinct to prevent re-renders
    val townHallCostSignal = gameSignal.map(TileKingdomLogic.townHallBuildCost).distinct
    val academyCostSignal = gameSignal.map(TileKingdomLogic.academyBuildCost).distinct
    val tavernCost = TileKingdomLogic.TavernBuildCost

    // Can afford checks - use distinct to only emit when affordability changes
    val canAffordWheatSignal = gameSignal.map(_.wheat >= wheatCost).distinct
    val canAffordFarmSignal = gameSignal.map(_.wheat >= farmCost).distinct
    val canAffordWoodcutterSignal = gameSignal.map(_.wheat >= woodcutterCost).distinct
    val canAffordBureauSignal = gameSignal.map(_.wood >= bureauCost).distinct
    val canAffordTempleSignal = gameSignal.map(_.wood >= templeCost).distinct
    val canAffordQuarrySignal = gameSignal.map(_.wood >= quarryCost).distinct
    val canAffordTownHallSignal = gameSignal.combineWith(townHallCostSignal).map { case (g, c) => g.stone >= c }.distinct
    val canAffordAcademySignal = gameSignal.combineWith(academyCostSignal).map { case (g, c) => g.stone >= c }.distinct
    val canAffordTavernSignal = gameSignal.map(_.wood >= tavernCost).distinct

    // Building unlock checks
    val canBuildFarmSignal = TileKingdomState.canBuildFarmSignal
    val canBuildWoodcutterSignal = TileKingdomState.canBuildWoodcutterSignal
    val canBuildQuarrySignal = TileKingdomState.canBuildQuarrySignal
    val canBuildBureauSignal = TileKingdomState.canBuildBureauSignal
    val canBuildTempleSignal = TileKingdomState.canBuildTempleSignal
    val canBuildTownHallSignal = TileKingdomState.canBuildTownHallSignal
    val canBuildAcademySignal = TileKingdomState.canBuildAcademySignal
    val canBuildTavernSignal = TileKingdomState.canBuildTavernSignal

    // Check if any management buildings available
    val hasManagementBuildingsSignal: Signal[Boolean] = 
      canBuildBureauSignal
        .combineWith(canBuildTempleSignal)
        .combineWith(canBuildTownHallSignal)
        .combineWith(canBuildAcademySignal)
        .combineWith(canBuildTavernSignal)
        .map { (bureau: Boolean, temple: Boolean, townHall: Boolean, academy: Boolean, tavern: Boolean) =>
          bureau || temple || townHall || academy || tavern
        }

    div(
      cls := "tile-build-options",
      cls <-- activeSubmenuSignal.map:
        case Some("resources") => "submenu-resources"
        case Some("management") => "submenu-management"
        case _ => "",

      // Main menu
      div(
        cls := "build-main-menu",
        backButton(actions.onCancel),
        categoryButton("🌾", "Resources", "resources", () => TileGridState.openSubmenu("resources")),
        child.maybe <-- hasManagementBuildingsSignal.map: has =>
          Option.when(has):
            categoryButton("🏛️", "Management", "management", () => TileGridState.openSubmenu("management"))
      ),

      // Resources submenu
      div(
        cls := "build-submenu resources",
        backButton(() => TileGridState.closeSubmenu()),
        buildOption("🌾", "Field", wheatCost, "🌾", canAffordWheatSignal, actions.onBuildWheatField),

        child.maybe <-- canBuildFarmSignal.map: can =>
          Option.when(can):
            buildOption("🏠", "Farm", farmCost, "🌾", canAffordFarmSignal, actions.onBuildFarm),

        child.maybe <-- canBuildWoodcutterSignal.map: can =>
          Option.when(can):
            buildOption("🪓", "Forest", woodcutterCost, "🌾", canAffordWoodcutterSignal, actions.onBuildWoodcutter),

        child.maybe <-- canBuildQuarrySignal.map: can =>
          Option.when(can):
            buildOption("⛏️", "Quarry", quarryCost, "🪵", canAffordQuarrySignal, actions.onBuildQuarry)
      ),

      // Management submenu
      div(
        cls := "build-submenu management",
        backButton(() => TileGridState.closeSubmenu()),

        child.maybe <-- canBuildBureauSignal.map: can =>
          Option.when(can):
            buildOption("🏛️", "Bureau", bureauCost, "🪵", canAffordBureauSignal, actions.onBuildBureau),

        child.maybe <-- canBuildTempleSignal.map: can =>
          Option.when(can):
            buildOption("⛪", "Temple", templeCost, "🪵", canAffordTempleSignal, actions.onBuildTemple),

        child.maybe <-- canBuildTownHallSignal.combineWith(townHallCostSignal).map: (can, cost) =>
          Option.when(can):
            buildOption("🏛️", "Town Hall", cost, "🪨", canAffordTownHallSignal, actions.onBuildTownHall),

        child.maybe <-- canBuildAcademySignal.combineWith(academyCostSignal).map: (can, cost) =>
          Option.when(can):
            buildOption("🎓", "Academy", cost, "🪨", canAffordAcademySignal, actions.onBuildAcademy),

        child.maybe <-- canBuildTavernSignal.map: can =>
          Option.when(can):
            buildOption("🍺", "Tavern", tavernCost, "🪵", canAffordTavernSignal, actions.onBuildTavern)
      )
    )

