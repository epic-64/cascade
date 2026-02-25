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

  /** Render a single politician card */
  private def renderCard(politician: Politician): HtmlElement =
    val cardCls = if politician.isRare then "politician-card rare" else "politician-card"
    val lifespanCls = lifespanClass(politician.remainingLifespanMs)

    div(
      cls := cardCls,
      draggable := true,
      dataAttr("politician-id") := politician.id,
      // Drag events
      onDragStart --> { e =>
        e.dataTransfer.setData("text/plain", politician.id)
        e.target.asInstanceOf[HTMLElement].classList.add("dragging")
      },
      onDragEnd --> { e =>
        e.target.asInstanceOf[HTMLElement].classList.remove("dragging")
      },
      // Card content
      div(cls := "politician-emoji", politician.emoji),
      div(
        cls := "politician-info",
        div(cls := "politician-name", politician.name),
        div(cls := "politician-title", politician.title),
        div(cls := "politician-effect", politician.effectDescription),
        div(
          cls := s"politician-roster-lifespan $lifespanCls",
          s"⏱️ ${formatLifespan(politician.remainingLifespanMs)} remaining"
        )
      )
    )

  /** The politician list element */
  def apply(): HtmlElement =
    import TileKingdomState.*

    div(
      idAttr := "politician-roster-list",
      cls := "roster-list",
      children <-- hasTownHallSignal.combineWith(politicianRosterSignal).map:
        case (false, _) =>
          List(div(cls := "roster-empty", "🏛️ Build Town Hall"))
        case (true, roster) if roster.isEmpty =>
          List(div(cls := "roster-empty", "No politicians available"))
        case (true, roster) =>
          roster.map(renderCard)
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

