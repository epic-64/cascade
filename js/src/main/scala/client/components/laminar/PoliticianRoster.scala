package client.components.laminar

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.{DragEvent, HTMLElement}
import shared.TileKingdom.*

/** Laminar-based politician roster for TileKingdom.
  *
  * Displays the list of available politicians with drag-drop support.
  */
object PoliticianRoster:

  /** Callback for discarding a politician */
  type OnDiscard = String => Unit

  /** Format lifespan for display */
  private def formatLifespan(ms: Long): String =
    val totalSeconds = (ms / 1000).toInt
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    f"$minutes:$seconds%02d"

  /** Calculate lifespan CSS class based on remaining percentage */
  private def lifespanClass(ms: Long): String =
    val percent = (ms.toDouble / TileKingdomLogic.PoliticianLifespanMs * 100).toInt
    if percent <= 20 then "lifespan-critical"
    else if percent <= 50 then "lifespan-warning"
    else "lifespan-normal"

  /** Render a single politician card - reads lifespan from signal for live updates */
  private def renderCard(politicianId: String, initialPolitician: Politician, politicianSignal: Signal[Politician]): HtmlElement =
    val cardCls = if initialPolitician.isRare then "politician-card rare" else "politician-card"

    div(
      cls := cardCls,
      draggable := true,
      dataAttr("politician-id") := politicianId,
      // Drag events
      onDragStart --> { e =>
        e.dataTransfer.setData("text/plain", politicianId)
        e.target.asInstanceOf[HTMLElement].classList.add("dragging")
      },
      onDragEnd --> { e =>
        e.target.asInstanceOf[HTMLElement].classList.remove("dragging")
      },
      // Card content - static parts use initial values, dynamic parts use signals
      div(cls := "politician-emoji", initialPolitician.emoji),
      div(
        cls := "politician-info",
        div(cls := "politician-name", initialPolitician.name),
        div(cls := "politician-title", initialPolitician.title),
        div(cls := "politician-effect", initialPolitician.effectDescription),
        div(
          cls <-- politicianSignal.map(p => s"politician-roster-lifespan ${lifespanClass(p.remainingLifespanMs)}"),
          child.text <-- politicianSignal.map(p => s"⏱️ ${formatLifespan(p.remainingLifespanMs)} remaining")
        )
      )
    )

  /** The politician list element */
  def apply(): HtmlElement =
    import TileKingdomState.*

    // Signal for just the politician IDs (for structure changes)
    val rosterIdsSignal = politicianRosterSignal.map(_.map(_.id)).distinct

    // Whether we have a town hall - use distinct to prevent flicker
    val hasTownHallDistinct = hasTownHallSignal.distinct

    // Whether roster is empty - use distinct
    val isEmptySignal = politicianRosterSignal.map(_.isEmpty).distinct

    div(
      idAttr := "politician-roster-list",
      cls := "roster-list",

      // Empty state messages
      child.maybe <-- hasTownHallDistinct.combineWith(isEmptySignal).map:
        case (false, _) => Some(div(cls := "roster-empty", "🏛️ Build Town Hall"))
        case (true, true) => Some(div(cls := "roster-empty", "No politicians available"))
        case _ => None,

      // Politician cards - use split to keep cards stable
      children <-- politicianRosterSignal.split(_.id) { (id, initial, polSignal) =>
        renderCard(id, initial, polSignal)
      }
    )

  /** The trash zone element for discarding politicians */
  def trashZone(onDiscard: OnDiscard): HtmlElement =
    val isDragOver = Var(false)

    div(
      idAttr := "politician-trash",
      cls := "politician-trash",
      cls <-- isDragOver.signal.map(over => if over then "drag-over" else ""),
      onDragOver --> { e =>
        e.preventDefault()
        isDragOver.set(true)
      },
      onDragLeave --> { _ =>
        isDragOver.set(false)
      },
      onDrop --> { e =>
        e.preventDefault()
        isDragOver.set(false)
        val politicianId = e.dataTransfer.getData("text/plain")
        if politicianId.nonEmpty && !politicianId.startsWith("townhall:") then
          onDiscard(politicianId)
      },
      i(cls := "fa-solid fa-trash"),
      span("Discard")
    )

