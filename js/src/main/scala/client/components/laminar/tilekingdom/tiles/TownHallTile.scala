package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import org.scalajs.dom.{DragEvent, HTMLElement, DataTransferEffectAllowedKind}
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils}
import client.components.laminar.TileKingdomState
import TileComponents.*

/** Town Hall tile component.
  *
  * Can hold one or two politicians (Management4 skill enables the second slot).
  * Supports drag-drop for assigning/swapping politicians.
  */
object TownHallTile:

  /** Town Hall-specific actions (politician management) */
  case class Actions(
    onAssignPolitician: String => Unit,
    onRemovePolitician: () => Unit,
    onSwapPoliticians: Coord => Unit,
    onDestroy: () => Unit
  )

  /** Format lifespan for display */
  private def formatLifespan(ms: Long): String =
    val totalSeconds = (ms / 1000).toInt
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    f"$minutes:$seconds%02d"

  /** Calculate lifespan CSS class based on remaining percentage */
  private def lifespanClass(ms: Long, maxMs: Long): String =
    val percent = (ms.toDouble / maxMs * 100).toInt
    if percent <= 20 then "lifespan-critical"
    else if percent <= 50 then "lifespan-warning"
    else "lifespan-normal"

  def apply(
    coord: Coord,
    politicians: List[Politician],
    actions: Actions
  ): HtmlElement =
    val gameSignal = TileKingdomState.gameSignal
    val isDragOver = Var(false)

    // Read politician list from signal for live lifespan updates
    val politiciansSignal: Signal[List[Politician]] = gameSignal
      .map(_.tiles.get(coord).map(_.tileType match
        case TileType.TownHall(pols) => pols
        case _ => List.empty
      ).getOrElse(List.empty))

    // Compute lifespan with tavern multiplier - use distinct to prevent flickering
    val lifespanMultiplierSignal = gameSignal.map(g => TileKingdomLogic.politicianLifespanMultiplier(g, coord)).distinct

    // Whether we have any politician (for structure, doesn't change as often as lifespan)
    val hasPoliticianSignal = politiciansSignal.map(_.nonEmpty).distinct

    // Whether Management4 is unlocked (dual politician slots)
    val capacitySignal = gameSignal.map(g => TileKingdomLogic.townHallCapacity(g)).distinct

    // Combined extra classes for politician presence and drag-over state
    val extraClsSignal = hasPoliticianSignal.combineWith(isDragOver.signal).map:
      case (hasPol, dragOver) =>
        val polCls = if hasPol then "has-politician" else ""
        val dragCls = if dragOver then "drag-over" else ""
        s"$polCls $dragCls".trim

    tileWrapper(coord, "town-hall", extraCls = extraClsSignal)(
      div(
        cls := "tile-content town-hall-content",
        div(cls := "tile-icon", "🏛️"),

        // Politician slots — render based on initial snapshot, lifespan updates via signals
        renderPoliticianSlots(coord, politicians, politiciansSignal, lifespanMultiplierSignal, capacitySignal, actions)
      ),

      // Click handler (remove first politician if present)
      onClick --> { _ =>
        if politicians.nonEmpty then
          actions.onRemovePolitician()
      },

      // Drag-drop handlers for receiving politicians
      onDragOver --> { e =>
        e.preventDefault()
        e.stopPropagation()
        isDragOver.set(true)
      },

      onDragEnter --> { e =>
        e.preventDefault()
        e.stopPropagation()
        isDragOver.set(true)
      },

      onDragLeave --> { e =>
        e.stopPropagation()
        val related = e.relatedTarget
        val tile = e.currentTarget.asInstanceOf[HTMLElement]
        val leavingTile = related == null || !tile.contains(related.asInstanceOf[org.scalajs.dom.Node])
        if leavingTile then
          isDragOver.set(false)
      },

      onDrop --> { e =>
        e.preventDefault()
        e.stopPropagation()
        isDragOver.set(false)
        val data = e.dataTransfer.getData("text/plain")
        if data.startsWith("townhall:") then
          val coords = data.stripPrefix("townhall:").split(",")
          if coords.length == 2 then
            val fromCoord = Coord(coords(0).toInt, coords(1).toInt)
            actions.onSwapPoliticians(fromCoord)
        else if data.nonEmpty then
          actions.onAssignPolitician(data)
      },

      destroyTileHandler(actions.onDestroy)
    )

  /** Render politician slots — one filled slot per politician, plus empty slots up to capacity */
  private def renderPoliticianSlots(
    coord: Coord,
    politicians: List[Politician],
    politiciansSignal: Signal[List[Politician]],
    lifespanMultiplierSignal: Signal[Double],
    capacitySignal: Signal[Int],
    actions: Actions
  ): Modifier[HtmlElement] =
    val slots = politicians.zipWithIndex.map: (pol, idx) =>
      renderFilledSlot(coord, pol, idx, politiciansSignal, lifespanMultiplierSignal, actions)

    val emptySlotCount = math.max(0, (if politicians.isEmpty then 1 else 0))
    val emptySlots = (0 until emptySlotCount).map: _ =>
      div(
        cls := "politician-slot empty",
        div(cls := "slot-label", "Drop politician")
      )

    // Show second empty slot when Management4 is unlocked and only 0 or 1 politicians
    val secondSlot = capacitySignal.map: cap =>
      if cap >= 2 && politicians.size < 2 && politicians.nonEmpty then
        Some(div(
          cls := "politician-slot empty dual-slot",
          div(cls := "slot-label", "Slot 2")
        ))
      else None

    Seq(
      slots,
      emptySlots,
      Seq(child.maybe <-- secondSlot)
    ).flatten

  /** Render a single filled politician slot */
  private def renderFilledSlot(
    coord: Coord,
    pol: Politician,
    slotIndex: Int,
    politiciansSignal: Signal[List[Politician]],
    lifespanMultiplierSignal: Signal[Double],
    actions: Actions
  ): HtmlElement =
    val polSignal = politiciansSignal.map(_.lift(slotIndex))

    val effectiveLifespanSignal = polSignal.combineWith(lifespanMultiplierSignal).map:
      case (Some(p), mult) => (p.remainingLifespanMs * mult).toLong
      case (None, _) => 0L
    val effectiveMaxLifespanSignal = lifespanMultiplierSignal.map: mult =>
      (TileKingdomLogic.PoliticianLifespanMs * mult).toLong

    div(
      cls := "politician-slot filled",
      draggable := true,
      onDragStart --> { e =>
        e.dataTransfer.effectAllowed = DataTransferEffectAllowedKind.move
        e.dataTransfer.setData("text/plain", s"townhall:${coord.row},${coord.col}")
        e.target.asInstanceOf[HTMLElement].parentElement.parentElement.classList.add("dragging")
      },
      onDragEnd --> { e =>
        e.target.asInstanceOf[HTMLElement].parentElement.parentElement.classList.remove("dragging")
      },

      div(cls := "politician-emoji-small", pol.emoji),
      div(
        cls := "politician-effect-small",
        title := pol.effectDescription,
        pol.effectDescription
      ),
      div(
        idAttr := s"politician-lifespan-${coord.row}-${coord.col}-$slotIndex",
        cls <-- effectiveLifespanSignal.combineWith(effectiveMaxLifespanSignal).map:
          case (effective, max) => s"politician-lifespan ${lifespanClass(effective, max)}",
        title := "Time remaining before politician retires",
        child.text <-- effectiveLifespanSignal.combineWith(lifespanMultiplierSignal).map:
          case (effectiveMs, mult) =>
            val lifespanText = formatLifespan(effectiveMs)
            val multiplierText = if mult > 1.0 then s" (${mult.toInt}x)" else ""
            s"⏱️ $lifespanText$multiplierText"
      )
    )

