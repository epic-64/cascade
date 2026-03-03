package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*
import org.scalajs.dom

/** Modal for viewing item details and selling with controls */
object ItemDetailModal:

  case class Actions(
    onSell: (Item, Long) => Unit,
    onSetJunk: (Item, Boolean) => Unit,
    onClose: () => Unit
  )

  def apply(
    itemSignal: Signal[Option[Item]],
    actions: Actions
  ): HtmlElement =
    val sellAmountVar = Var(1L)

    div(
      cls <-- itemSignal.map(item => if item.isDefined then "velor-modal-overlay show" else "velor-modal-overlay"),
      onClick --> { e =>
        // Close when clicking overlay (not modal content)
        if e.target == e.currentTarget then actions.onClose()
      },
      child <-- itemSignal.combineWith(VelorIdleState.gameSignal).map:
        case (None, _) => emptyNode
        case (Some(item), game) =>
          val count = game.inventory.getCount(item)
          val isJunk = game.junkItems.contains(item)
          // Reset sell amount when item changes
          sellAmountVar.set(1L.min(count).max(1L))
          modalContent(item, count, isJunk, sellAmountVar, actions)
    )

  private def modalContent(
    item: Item,
    count: Long,
    isJunk: Boolean,
    sellAmountVar: Var[Long],
    actions: Actions
  ): HtmlElement =
    val isJunkVar = Var(isJunk)
    val sellValue = Item.sellValue(item)

    div(
      cls := "velor-modal velor-item-modal",
      onClick.stopPropagation --> { _ => () }, // Prevent closing when clicking modal

      // Header
      div(
        cls := "velor-modal-header",
        div(
          cls := "velor-item-modal-title",
          span(cls := "velor-item-modal-icon", Item.icon(item)),
          span(Item.displayName(item))
        ),
        button(
          cls := "velor-modal-close",
          "✕",
          onClick --> { _ => actions.onClose() }
        )
      ),

      // Body
      div(
        cls := "velor-modal-body",

        // Item info
        div(
          cls := "velor-item-info-section",
          div(
            cls := "velor-item-info-row",
            span(cls := "velor-item-info-label", "In inventory:"),
            span(cls := "velor-item-info-value", s"$count")
          ),
          div(
            cls := "velor-item-info-row",
            span(cls := "velor-item-info-label", "Sell value:"),
            span(cls := "velor-item-info-value velor-text-xp", s"${sellValue}g each")
          )
        ),

        // Junk checkbox
        div(
          cls := "velor-item-junk-section",
          label(
            cls := "velor-junk-checkbox-label",
            input(
              typ := "checkbox",
              cls := "velor-junk-checkbox",
              checked <-- isJunkVar.signal,
              onChange.mapToChecked --> { checked =>
                isJunkVar.set(checked)
                actions.onSetJunk(item, checked)
              }
            ),
            span("Mark as Junk"),
            span(cls := "velor-junk-hint", "(for quick selling)")
          )
        ),

        // Sell section
        div(
          cls := "velor-item-sell-section",
          div(cls := "velor-item-sell-title", "Sell Items"),

          // Amount slider
          div(
            cls := "velor-sell-slider-container",
            input(
              typ := "range",
              cls := "velor-sell-slider",
              minAttr := "1",
              maxAttr := count.toString,
              value <-- sellAmountVar.signal.map(_.toString),
              onInput.mapToValue --> { v =>
                scala.util.Try(v.toLong).foreach(sellAmountVar.set)
              }
            ),
            div(
              cls := "velor-sell-amount-display",
              child.text <-- sellAmountVar.signal.map(amt => s"$amt / $count")
            )
          ),

          // Quick buttons
          div(
            cls := "velor-sell-quick-buttons",
            button(
              cls := "velor-sell-quick-btn",
              "1",
              onClick --> { _ => sellAmountVar.set(1L) }
            ),
            button(
              cls := "velor-sell-quick-btn",
              "10",
              disabled := count < 10,
              onClick --> { _ => sellAmountVar.set(10L.min(count)) }
            ),
            button(
              cls := "velor-sell-quick-btn",
              "Half",
              onClick --> { _ => sellAmountVar.set((count / 2).max(1L)) }
            ),
            button(
              cls := "velor-sell-quick-btn",
              "Keep 1",
              disabled := count <= 1,
              onClick --> { _ => sellAmountVar.set((count - 1).max(1L)) }
            ),
            button(
              cls := "velor-sell-quick-btn",
              "All",
              onClick --> { _ => sellAmountVar.set(count) }
            )
          ),

          // Sell button with gold preview
          div(
            cls := "velor-sell-action",
            span(
              cls := "velor-sell-gold-preview",
              child.text <-- sellAmountVar.signal.map(amt => s"${amt * sellValue}g")
            ),
            button(
              cls := "btn btn-primary",
              child.text <-- sellAmountVar.signal.map(amt => s"Sell $amt"),
              onClick --> { _ =>
                val amount = sellAmountVar.now()
                actions.onSell(item, amount)
                // Close modal after selling all, or update amount
                val remaining = count - amount
                if remaining <= 0 then
                  actions.onClose()
                else
                  sellAmountVar.set(1L.min(remaining))
              }
            )
          )
        )
      )
    )

