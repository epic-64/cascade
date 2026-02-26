package client.components.laminar

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** Reusable draggable panel wrapper component. */
object DraggablePanel:

  /** Create a draggable panel with a handle and content.
    * @param panelCls CSS class for the panel
    * @param title Optional title shown in the handle
    * @param content The panel content
    */
  def apply(panelCls: String, title: String = "")(content: Modifier[HtmlElement]*): HtmlElement =
    val isDragging = Var(false)
    val position = Var((0.0, 0.0))
    val dragStart = Var((0.0, 0.0))
    val posStart = Var((0.0, 0.0))

    div(
      cls := s"draggable-panel $panelCls",
      styleAttr <-- position.signal.map { case (x, y) =>
        s"transform: translate(${x}px, ${y}px);"
      },

      // Drag handle
      div(
        cls := "panel-handle",
        if title.nonEmpty then span(cls := "panel-title", title) else emptyNode,
        span(cls := "panel-drag-icon", "⋮⋮"),
        onMouseDown --> { e =>
          e.preventDefault()
          isDragging.set(true)
          dragStart.set((e.clientX, e.clientY))
          posStart.set(position.now())
        }
      ),

      // Content
      div(
        cls := "panel-content",
        content
      ),

      // Global mouse move/up handlers for dragging
      onMountCallback { ctx =>
        val Doc = dom.document
        Doc.addEventListener("mousemove", (e: dom.MouseEvent) => {
          if isDragging.now() then
            val (startX, startY) = dragStart.now()
            val (posX, posY) = posStart.now()
            val dx = e.clientX - startX
            val dy = e.clientY - startY
            position.set((posX + dx, posY + dy))
        })
        Doc.addEventListener("mouseup", (_: dom.MouseEvent) => {
          isDragging.set(false)
        })
      }
    )

