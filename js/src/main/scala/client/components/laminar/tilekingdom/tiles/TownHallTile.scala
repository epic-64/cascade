package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import org.scalajs.dom.{DragEvent, HTMLElement, DataTransferEffectAllowedKind}
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils}
import client.components.laminar.TileKingdomState

/** Town Hall tile component.
  *
  * Can hold a politician that provides bonuses to nearby tiles.
  * Supports drag-drop for assigning/swapping politicians.
  */
object TownHallTile:

  /** Tile action callbacks */
  case class Actions(
    onAssignPolitician: String => Unit,  // politician ID
    onRemovePolitician: () => Unit,
    onSwapPoliticians: Coord => Unit,    // source coord
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
    politician: Option[Politician],
    actions: Actions
  ): HtmlElement =
    val gameSignal = TileKingdomState.gameSignal
    val isDragOver = Var(false)

    // Compute lifespan with tavern multiplier
    val lifespanMultiplierSignal = gameSignal.map(g => TileKingdomLogic.politicianLifespanMultiplier(g, coord))

    div(
      idAttr := TileUtils.tileId(coord),
      cls := "tile-kingdom-tile unlocked town-hall",
      cls <-- TileGridState.zoomTierClass.combineWith(Val(politician.isDefined)).map:
        case (zoomCls, true) => s"$zoomCls has-politician".trim
        case (zoomCls, false) => zoomCls,
      cls <-- isDragOver.signal.map(over => if over then "drag-over" else ""),
      styleAttr <-- TileGridState.tileStyle(coord),

      // Content
      div(
        cls := "tile-content town-hall-content",
        div(cls := "tile-icon", "🏛️"),

        // Politician slot
        politician match
          case Some(pol) =>
            val effectiveLifespanSignal = lifespanMultiplierSignal.map: mult =>
              (pol.remainingLifespanMs * mult).toLong
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
              div(cls := "politician-effect-small", pol.effectDescription),
              div(
                idAttr := s"politician-lifespan-${coord.row}-${coord.col}",
                cls <-- effectiveLifespanSignal.combineWith(effectiveMaxLifespanSignal).map:
                  case (effective, max) => s"politician-lifespan ${lifespanClass(effective, max)}",
                child.text <-- effectiveLifespanSignal.combineWith(lifespanMultiplierSignal).map:
                  case (effectiveMs, mult) =>
                    val lifespanText = formatLifespan(effectiveMs)
                    val multiplierText = if mult > 1.0 then s" (${mult.toInt}x)" else ""
                    s"⏱️ $lifespanText$multiplierText"
              )
            )

          case None =>
            div(
              cls := "politician-slot empty",
              div(cls := "slot-label", "Drop politician")
            )
      ),

      // Click handler (remove politician if present)
      onClick --> { _ =>
        if !TileGridState.wasDragging && politician.isDefined then
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
          // Swap from another town hall
          val coords = data.stripPrefix("townhall:").split(",")
          if coords.length == 2 then
            val fromCoord = Coord(coords(0).toInt, coords(1).toInt)
            actions.onSwapPoliticians(fromCoord)
        else if data.nonEmpty then
          // Assign from roster
          actions.onAssignPolitician(data)
      },

      // Right-click to destroy
      onContextMenu --> { e =>
        e.preventDefault()
        actions.onDestroy()
      }
    )

