package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Shop panel - buy items with gold */
object ShopPanel:

  def apply(onBuyItem: (Item, Int) => Unit): HtmlElement =
    div(
      cls := "velor-shop-panel",

      // Header
      div(
        cls := "velor-skill-card",
        h3("🏪 Shop"),
        p(cls := "velor-text-muted", "Buy supplies for your adventures.")
      ),

      // Shop items
      div(
        cls := "velor-shop-items-section",
        h4("Available Items"),
        VelorIdleLogic.shopItems.map: shopItem =>
          shopItemCard(shopItem, onBuyItem)
      )
    )

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

