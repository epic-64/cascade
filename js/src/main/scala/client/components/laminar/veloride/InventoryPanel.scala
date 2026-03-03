package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Inventory panel - displays items in a grid */
object InventoryPanel:

  def apply(onSellItem: (Item, Long) => Unit): HtmlElement =
    val inventorySignal = VelorIdleState.inventorySignal
    
    div(
      cls := "velor-inventory",
      div(
        cls := "velor-inventory-header",
        div(cls := "velor-inventory-title", "📦 Inventory"),
        div(
          cls := "velor-inventory-slots",
          child.text <-- inventorySignal.map(inv => s"${inv.usedSlots}/${inv.maxSlots} slots")
        )
      ),
      div(
        cls := "velor-inventory-grid",
        children <-- inventorySignal.map { inv =>
          inv.slots.zipWithIndex.map { case (slot, idx) =>
            itemSlot(slot, idx, onSellItem)
          }
        }
      )
    )

  private def itemSlot(slot: Option[ItemStack], index: Int, onSell: (Item, Long) => Unit): HtmlElement =
    slot match
      case None =>
        div(
          cls := "velor-item-slot empty"
        )
      case Some(stack) =>
        div(
          cls := "velor-item-slot",
          title := s"${Item.displayName(stack.item)} (${stack.count})\nClick to sell",
          onClick --> { _ => onSell(stack.item, 1) },
          div(cls := "velor-item-slot-icon", Item.icon(stack.item)),
          div(cls := "velor-item-slot-count", formatCount(stack.count))
        )

  private def formatCount(count: Long): String = VelorUtils.formatNumber(count)

