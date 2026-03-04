package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Shop panel - buy items with gold */
object ShopPanel:

  def apply(onBuyItem: (Item, Int) => Unit, onBuyInventorySlots: () => Unit): HtmlElement =
    div(
      cls := "velor-view velor-shop-panel",

      // Header
      div(
        cls := "velor-skill-card",
        h3("🏪 Shop"),
        p(cls := "velor-text-muted", "Buy supplies for your adventures.")
      ),

      // Inventory upgrade section
      inventoryUpgradeCard(onBuyInventorySlots),

      // Shop items
      div(
        cls := "velor-shop-items-section",
        h4("Supplies"),
        VelorIdleLogic.shopItems.map: shopItem =>
          shopItemCard(shopItem, onBuyItem)
      )
    )

  private def inventoryUpgradeCard(onBuyInventorySlots: () => Unit): HtmlElement =
    div(
      cls := "velor-shop-items-section",
      h4("Upgrades"),
      div(
        cls := "velor-shop-item-card",
        child <-- VelorIdleState.inventorySignal.combineWith(VelorIdleState.goldSignal).map:
          case (inventory, gold) =>
            Inventory.nextUpgradeCost(inventory.maxSlots) match
              case None =>
                div(
                  cls := "velor-shop-item-header",
                  span(cls := "velor-shop-item-icon", "📦"),
                  span(cls := "velor-shop-item-name", "Inventory Space"),
                  span(cls := "velor-shop-item-price velor-text-accent", "MAX")
                )
              case Some(cost) =>
                div(
                  div(
                    cls := "velor-shop-item-header",
                    span(cls := "velor-shop-item-icon", "📦"),
                    span(cls := "velor-shop-item-name", s"+4 Inventory Slots"),
                    span(cls := "velor-shop-item-price", s"${formatGold(cost)}g")
                  ),
                  div(
                    cls := "velor-shop-inventory-info",
                    span(s"Current: ${inventory.maxSlots} / ${Inventory.MaxSlots} slots")
                  ),
                  div(
                    cls := "velor-shop-buy-section",
                    span(
                      cls := "velor-shop-total",
                      if gold >= cost then "✓ Can afford" else s"Need ${formatGold(cost - gold)} more"
                    ),
                    button(
                      cls := "btn btn-primary",
                      "Buy",
                      disabled := gold < cost,
                      onClick --> { _ => onBuyInventorySlots() }
                    )
                  )
                )
      )
    )

  private def formatGold(amount: Long): String =
    if amount >= 1_000_000 then f"${amount / 1_000_000.0}%.1fM"
    else if amount >= 1_000 then f"${amount / 1_000.0}%.1fK"
    else amount.toString

  private def shopItemCard(shopItem: VelorIdleLogic.ShopItem, onBuyItem: (Item, Int) => Unit): HtmlElement =
    val item = shopItem.item
    val buyAmountVar = Var(10)

    div(
      cls := "velor-shop-item-card",
      div(
        cls := "velor-shop-item-header",
        span(cls := "velor-shop-item-icon", Item.icon(item)),
        span(cls := "velor-shop-item-name", Item.displayName(item)),
        span(cls := "velor-shop-item-price", s"${shopItem.buyPrice}g each")
      ),
      div(
        cls := "velor-shop-item-controls",
        // Amount selector
        div(
          cls := "velor-shop-amount-selector",
          button(
            cls := "velor-shop-amount-btn",
            "-",
            onClick --> { _ =>
              val current = buyAmountVar.now()
              if current > 1 then buyAmountVar.set(current - 1)
            }
          ),
          span(
            cls := "velor-shop-amount-display",
            child.text <-- buyAmountVar.signal.map(_.toString)
          ),
          button(
            cls := "velor-shop-amount-btn",
            "+",
            onClick --> { _ =>
              val current = buyAmountVar.now()
              if current < 100 then buyAmountVar.set(current + 1)
            }
          )
        ),
        // Quick amount buttons
        div(
          cls := "velor-shop-quick-amounts",
          quickAmountButton(1, buyAmountVar),
          quickAmountButton(10, buyAmountVar),
          quickAmountButton(50, buyAmountVar)
        ),
        // Buy button with total cost
        div(
          cls := "velor-shop-buy-section",
          span(
            cls := "velor-shop-total",
            child.text <-- buyAmountVar.signal.map(amt => s"Total: ${amt * shopItem.buyPrice}g")
          ),
          button(
            cls := "btn btn-primary",
            "Buy",
            disabled <-- VelorIdleState.goldSignal.combineWith(buyAmountVar.signal).map:
              case (gold, amount) => gold < shopItem.buyPrice * amount
            ,
            onClick --> { _ => onBuyItem(item, buyAmountVar.now()) }
          )
        )
      )
    )

  private def quickAmountButton(amount: Int, amountVar: Var[Int]): HtmlElement =
    button(
      cls := "velor-shop-quick-btn",
      s"$amount",
      onClick --> { _ => amountVar.set(amount) }
    )

