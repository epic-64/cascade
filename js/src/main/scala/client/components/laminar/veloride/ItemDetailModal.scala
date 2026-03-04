package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

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

    // Derive count signal that only updates when count actually changes
    val countSignal = itemSignal.combineWith(VelorIdleState.inventorySignal).map:
      case (Some(item), inv) => inv.getCount(item)
      case _ => 0L
    .distinct

    // Derive isJunk signal that only updates when junk status changes
    val isJunkSignal = itemSignal.combineWith(VelorIdleState.gameSignal.map(_.junkItems).distinct).map:
      case (Some(item), junkItems) => junkItems.contains(item)
      case _ => false
    .distinct

    div(
      cls <-- itemSignal.map(item => if item.isDefined then "velor-modal-overlay show" else "velor-modal-overlay"),
      onClick --> { e =>
        // Close when clicking overlay (not modal content)
        if e.target == e.currentTarget then actions.onClose()
      },
      // Reset sell amount when item changes
      itemSignal.distinct --> { _ => sellAmountVar.set(1L) },
      // Only recreate modal structure when item changes, not on every gameSignal tick
      child <-- itemSignal.distinct.map:
        case None => emptyNode
        case Some(item) =>
          modalContent(item, countSignal, isJunkSignal, sellAmountVar, actions)
    )

  private def modalContent(
    item: Item,
    countSignal: Signal[Long],
    isJunkSignal: Signal[Boolean],
    sellAmountVar: Var[Long],
    actions: Actions
  ): HtmlElement =
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
            span(cls := "velor-item-info-value", child.text <-- countSignal.map(_.toString))
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
              checked <-- isJunkSignal,
              onChange.mapToChecked --> { checked =>
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
              maxAttr <-- countSignal.map(_.toString),
              value <-- sellAmountVar.signal.map(_.toString),
              onInput.mapToValue --> { v =>
                scala.util.Try(v.toLong).foreach(sellAmountVar.set)
              }
            ),
            div(
              cls := "velor-sell-amount-display",
              child.text <-- sellAmountVar.signal.combineWith(countSignal).map((amt, count) => s"$amt / $count")
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
              "Half",
              onClick.compose(_.sample(countSignal)) --> { count =>
                sellAmountVar.set((count / 2).max(1L))
              }
            ),
            button(
              cls := "velor-sell-quick-btn",
              "Keep 1",
              disabled <-- countSignal.map(_ <= 1),
              onClick.compose(_.sample(countSignal)) --> { count =>
                sellAmountVar.set((count - 1).max(1L))
              }
            ),
            button(
              cls := "velor-sell-quick-btn",
              "All",
              onClick.compose(_.sample(countSignal)) --> { count =>
                sellAmountVar.set(count)
              }
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
              onClick.compose(_.sample(sellAmountVar.signal, countSignal)) --> { case (amount, count) =>
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

