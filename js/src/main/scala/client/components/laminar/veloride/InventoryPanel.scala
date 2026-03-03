package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Inventory panel - displays items in a grid */
object InventoryPanel:

  case class Actions(
    onSellItem: (Item, Long) => Unit,
    onSetJunk: (Item, Boolean) => Unit,
    onSellAllJunk: () => Unit
  )

  def apply(actions: Actions): HtmlElement =
    val inventorySignal = VelorIdleState.inventorySignal
    val selectedItemVar: Var[Option[Item]] = Var(None)
    
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
      
      // Sell all junk button
      div(
        cls := "velor-inventory-actions",
        child <-- VelorIdleState.gameSignal.map: game =>
          val junkCount = game.inventory.slots.flatten.count(s => game.junkItems.contains(s.item))
          if junkCount > 0 then
            button(
              cls := "btn btn-secondary velor-sell-junk-btn",
              s"🗑️ Sell All Junk ($junkCount)",
              onClick --> { _ => actions.onSellAllJunk() }
            )
          else
            emptyNode
      ),
      
      div(
        cls := "velor-inventory-grid",
        children <-- inventorySignal.combineWith(VelorIdleState.gameSignal).map: (inv, game) =>
          inv.slots.zipWithIndex.map: (slot, idx) =>
            itemSlot(slot, idx, game.junkItems, selectedItemVar)
      ),
      
      // Item detail modal
      ItemDetailModal(
        selectedItemVar.signal,
        ItemDetailModal.Actions(
          onSell = actions.onSellItem,
          onSetJunk = actions.onSetJunk,
          onClose = () => selectedItemVar.set(None)
        )
      )
    )

  private def itemSlot(
    slot: Option[ItemStack],
    index: Int,
    junkItems: Set[Item],
    selectedItemVar: Var[Option[Item]]
  ): HtmlElement =
    slot match
      case None =>
        div(
          cls := "velor-item-slot empty"
        )
      case Some(stack) =>
        val isJunk = junkItems.contains(stack.item)
        div(
          cls := s"velor-item-slot${if isJunk then " junk" else ""}",
          title := s"${Item.displayName(stack.item)} (${stack.count})\nClick for options",
          onClick --> { _ => selectedItemVar.set(Some(stack.item)) },
          div(cls := "velor-item-slot-icon", Item.icon(stack.item)),
          div(cls := "velor-item-slot-count", formatCount(stack.count)),
          if isJunk then div(cls := "velor-item-junk-badge", "🗑") else emptyNode
        )

  private def formatCount(count: Long): String = VelorUtils.formatNumber(count)

