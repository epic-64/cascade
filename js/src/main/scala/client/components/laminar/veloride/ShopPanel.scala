package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Shop panel - buy items with gold */
object ShopPanel:

  def apply(onBuyItem: (Item, Int) => Unit, onBuyInventorySlots: () => Unit): HtmlElement =
    div(
      cls := "velor-view velor-view-fill velor-shop-panel",

      // Fixed header
      div(
        cls := "velor-shop-header",
        h3("🏪 Shop"),
        p(cls := "velor-text-muted", "Buy supplies for your adventures.")
      ),

      // Scrollable content
      div(
        cls := "velor-shop-content",

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
    val buyAmountVar = Var(1)
    val maxBuyAmount = 100

    // Calculate max affordable based on current gold
    val maxAffordableSignal = VelorIdleState.goldSignal.map(gold =>
      (gold / shopItem.buyPrice).toInt.min(maxBuyAmount).max(1)
    ).distinct

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
        // Amount slider
        div(
          cls := "velor-sell-slider-container",
          input(
            typ := "range",
            cls := "velor-sell-slider",
            minAttr := "1",
            maxAttr := maxBuyAmount.toString,
            value <-- buyAmountVar.signal.map(_.toString),
            onInput.mapToValue --> { v =>
              scala.util.Try(v.toInt).foreach(buyAmountVar.set)
            }
          ),
          div(
            cls := "velor-sell-amount-display",
            child.text <-- buyAmountVar.signal.map(amt => s"$amt / $maxBuyAmount")
          )
        ),
        // Quick amount buttons
        div(
          cls := "velor-sell-quick-buttons",
          button(
            cls := "velor-sell-quick-btn",
            "1",
            onClick --> { _ => buyAmountVar.set(1) }
          ),
          button(
            cls := "velor-sell-quick-btn",
            "10",
            onClick --> { _ => buyAmountVar.set(10) }
          ),
          button(
            cls := "velor-sell-quick-btn",
            "Max",
            onClick.compose(_.sample(maxAffordableSignal)) --> { maxAffordable =>
              buyAmountVar.set(maxAffordable)
            }
          )
        ),
        // Buy button with total cost
        div(
          cls := "velor-sell-action",
          span(
            cls := "velor-sell-gold-preview",
            child.text <-- buyAmountVar.signal.map(amt => s"${amt * shopItem.buyPrice}g")
          ),
          button(
            cls := "btn btn-primary",
            child.text <-- buyAmountVar.signal.map(amt => s"Buy $amt"),
            disabled <-- VelorIdleState.goldSignal.combineWith(buyAmountVar.signal).map:
              case (gold, amount) => gold < shopItem.buyPrice * amount
            ,
            onClick --> { _ => onBuyItem(item, buyAmountVar.now()) }
          )
        )
      )
    )

