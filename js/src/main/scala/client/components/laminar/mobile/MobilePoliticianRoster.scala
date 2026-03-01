package client.components.laminar.mobile

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import shared.TileKingdom.*
import client.components.laminar.TileKingdomState

/** Mobile politician roster popup.
  * 
  * Shows available politicians and allows assigning them to town halls.
  * Opens when tapping on a town hall tile in mobile mode.
  */
object MobilePoliticianRoster:

  private val isOpen: Var[Boolean] = Var(false)
  private val targetTownHall: Var[Option[Coord]] = Var(None)
  private val onAssignCallback: Var[Option[(Coord, String) => Unit]] = Var(None)
  private val onDiscardCallback: Var[Option[String => Unit]] = Var(None)

  def open(coord: Coord, onAssign: (Coord, String) => Unit, onDiscard: String => Unit): Unit =
    dom.console.log(s"MobilePoliticianRoster.open called for coord $coord")
    targetTownHall.set(Some(coord))
    onAssignCallback.set(Some(onAssign))
    onDiscardCallback.set(Some(onDiscard))
    isOpen.set(true)
    dom.console.log(s"MobilePoliticianRoster.isOpen is now ${isOpen.now()}")

  def close(): Unit =
    isOpen.set(false)
    targetTownHall.set(None)

  /** Check if the roster popup is currently open */
  def isOpenSignal: Signal[Boolean] = isOpen.signal

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

  def apply(): HtmlElement =
    val rosterSignal = TileKingdomState.politicianRosterSignal
    val hasTownHallSignal = TileKingdomState.hasTownHallSignal.distinct
    val gameSignal = TileKingdomState.gameSignal

    val rosterCountSignal = gameSignal.map: game =>
      val current = game.politicianRoster.size
      val max = TileKingdomLogic.maxPoliticianRosterSize(game)
      s"$current/$max"
    .distinct

    div(
      cls := "mobile-politician-roster-overlay",
      cls <-- isOpen.signal.map(open => if open then "show" else ""),

      // Backdrop
      div(
        cls := "mobile-politician-backdrop",
        onClick --> { _ => close() }
      ),

      // Content
      div(
        cls := "mobile-politician-content",
        
        // Header
        div(
          cls := "mobile-politician-header",
          h3(
            "Politicians ",
            span(cls := "roster-count", child.text <-- rosterCountSignal)
          ),
          button(
            cls := "mobile-politician-close",
            "✕",
            onClick --> { _ => close() }
          )
        ),

        // Target town hall info
        div(
          cls := "mobile-politician-target",
          child.text <-- targetTownHall.signal.map:
            case Some(coord) => s"Assign to Town Hall at (${coord.row}, ${coord.col})"
            case None => "Select a politician"
        ),

        // Politician list
        div(
          cls := "mobile-politician-list",

          // Empty state
          child.maybe <-- hasTownHallSignal.combineWith(rosterSignal.map(_.isEmpty)).map:
            case (false, _) => Some(div(cls := "mobile-politician-empty", "🏛️ Build a Town Hall first"))
            case (true, true) => Some(div(cls := "mobile-politician-empty", "No politicians available"))
            case _ => None,

          // Politician cards
          children <-- rosterSignal.split(_.id) { (id, initial, polSignal) =>
            renderPoliticianCard(id, initial, polSignal)
          }
        )
      )
    )

  private def renderPoliticianCard(
    politicianId: String,
    initial: Politician,
    polSignal: Signal[Politician]
  ): HtmlElement =
    val rareClass = if initial.isRare then " rare" else ""
    
    div(
      cls := s"mobile-politician-card$rareClass",
      
      // Main card content - tap to assign
      div(
        cls := "mobile-politician-card-main",
        onClick --> { _ =>
          targetTownHall.now().foreach: coord =>
            onAssignCallback.now().foreach: callback =>
              callback(coord, politicianId)
              close()
        },
        
        div(cls := "mobile-politician-emoji", initial.emoji),
        div(
          cls := "mobile-politician-info",
          div(cls := "mobile-politician-name", initial.name),
          div(cls := "mobile-politician-title", initial.title),
          div(cls := "mobile-politician-effect", initial.effectDescription),
          div(
            cls <-- polSignal.map(p => s"mobile-politician-lifespan ${lifespanClass(p.remainingLifespanMs)}"),
            child.text <-- polSignal.map(p => s"⏱️ ${formatLifespan(p.remainingLifespanMs)}")
          )
        )
      ),
      
      // Discard button
      button(
        cls := "mobile-politician-discard",
        title := "Discard politician",
        "🗑️",
        onClick.stopPropagation --> { _ =>
          onDiscardCallback.now().foreach: callback =>
            callback(politicianId)
        }
      )
    )

