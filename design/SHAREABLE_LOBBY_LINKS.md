# Shareable Lobby Links

## Goal

Enable players to share a direct link to a game lobby. Clicking the link pre-fills the join form with the lobby code.

**Example URLs:**
- `https://cascade.app/color-rush/ABC123`
- `https://cascade.app/ai-drawing/XYZ789`
- `https://cascade.app/tug-of-war/DEF456`

---

## Design Principle: URL as Entry Point Only

The URL serves as an **entry point**, not as live state. Once a player loads the page:
- The lobby ID from the URL pre-fills the join form
- The URL does **not** update during gameplay
- Session reconnection continues to work independently

This keeps the implementation simple with no browser history management.

---

## Current State

- Games accessed via: `/color-rush`, `/ai-drawing`, `/tug-of-war`
- Lobby codes shown as plain text: `Code: ABC123`
- No shareable link functionality
- `ClientMain.scala` routes on exact path matches only

---

## Proposed Solution

### URL Pattern
```
/{game-type}/{lobbyId}
```

### Behavior

| Scenario | URL | Behavior |
|----------|-----|----------|
| Normal access | `/color-rush` | Show create/join UI (default) |
| Shared link | `/color-rush/ABC123` | Pre-fill lobby code, show join tab, focus name input |

That's it. No URL updates after page load.

---

## Architecture

### 1. Server: Parameterized Routes

Add routes that accept an optional lobby ID segment:

```scala
// WebServer.scala
@cask.get("/color-rush/:lobbyId")
def colorRushWithLobby(lobbyId: String): cask.Response[java.io.InputStream] = 
  serveGamePage("color-rush.html")

// Same for ai-drawing, tug-of-war
```

### 2. Client: URL Parsing

Update `ClientMain.scala` to extract lobby ID:

```scala
case class GameRoute(game: AppRoute, lobbyId: Option[String])

def parseRoute(pathname: String): GameRoute =
  pathname.split("/").filter(_.nonEmpty).toList match
    case "color-rush" :: lobbyId :: _ => GameRoute(AppRoute.ColorRush, Some(lobbyId.toUpperCase))
    case "color-rush" :: Nil          => GameRoute(AppRoute.ColorRush, None)
    // ... same pattern for other games
    case _                            => GameRoute(AppRoute.Landing, None)
```

### 3. Client: Pre-fill Join Form

Pass lobby ID to game initializers:

```scala
def initializeColorRush(lobbyIdFromUrl: Option[String]): Unit =
  buildGameUI()
  
  lobbyIdFromUrl match
    case Some(lobbyId) =>
      // Pre-fill and show join UI
      getElementById("joinGameId").foreach(_.asInstanceOf[HTMLInputElement].value = lobbyId)
      switchColorRushTab("join")
      getElementById("joinPlayerName").foreach(_.asInstanceOf[HTMLElement].focus())
    case None =>
      // Normal flow
      checkForExistingSession()
```

### 4. Shareable Link Component

Display copyable link in the lobby waiting area:

```scala
object ShareableLink:
  def render(gameType: String, lobbyId: String): HTMLElement =
    val fullUrl = s"${dom.window.location.origin}/$gameType/$lobbyId"
    
    div(cls = "shareable-link")(
      span(cls = "lobby-code", content = s"Code: $lobbyId"),
      div(cls = "link-actions")(
        input("text", cls = "share-link-input").tap: el =>
          el.asInstanceOf[HTMLInputElement].value = fullUrl
          el.asInstanceOf[HTMLInputElement].readOnly = true
        ,
        button(cls = "btn btn-secondary copy-btn", content = "📋 Copy").tap: btn =>
          btn.addEventListener("click", (e: Event) => copyToClipboard(fullUrl, btn))
      )
    )
```

---

## Implementation Checklist

### Phase 1: Server Routes
- [x] Add `/color-rush/:lobbyId` route to `WebServer.scala`
- [x] Add `/ai-drawing/:lobbyId` route
- [x] Add `/tug-of-war/:lobbyId` route

### Phase 2: Client Routing
- [x] Create `GameRoute` case class in `ClientMain.scala`
- [x] Update `parseRoute` to extract lobby ID from path
- [x] Update game initialization calls to pass `lobbyId`

### Phase 3: ShareableLink Component
- [x] Create `js/src/main/scala/client/components/ShareableLink.scala`
- [x] Implement `render(gameType, lobbyId)` function
- [x] Implement `copyToClipboard` with visual feedback
- [x] Add CSS styles to `base.css`

### Phase 4: Game Client Updates
- [x] **ColorRush**: Update `initializeColorRush(lobbyId: Option[String])`
  - [x] Pre-fill join form if lobby ID present
  - [x] Replace lobby code display with `ShareableLink`
- [x] **AI Drawing**: Update `initializeDrawing(lobbyId: Option[String])`
  - [x] Pre-fill join form if lobby ID present
  - [x] Replace lobby code display with `ShareableLink`
- [x] **Tug of War**: Update `initializeTugOfWar(lobbyId: Option[String])`
  - [x] Pre-fill join form if lobby ID present
  - [x] Replace lobby code display with `ShareableLink`

### Phase 5: Testing
- [ ] Open shared link → join form pre-filled, name input focused
- [ ] Copy link → paste in new tab → works correctly
- [ ] Invalid lobby ID → normal error handling (from WebSocket)
- [ ] Session reconnection still works (no URL)
- [ ] Case insensitivity: `abc123` → `ABC123`

---

## File Changes

### New Files
```
js/src/main/scala/client/components/
  └── ShareableLink.scala
```

### Modified Files
```
jvm/src/main/scala/server/WebServer.scala     # Add parameterized routes
js/src/main/scala/client/ClientMain.scala     # Update routing
js/src/main/scala/client/ColorRushClient.scala
js/src/main/scala/client/DrawingGameClient.scala
js/src/main/scala/client/TugOfWarClient.scala
jvm/src/main/resources/static/base.css        # ShareableLink styles
```

---

## CSS Additions

```css
.shareable-link {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem;
  background: var(--surface-secondary);
  border-radius: 8px;
  margin-bottom: 1rem;
}

.shareable-link .lobby-code {
  font-size: 1.5rem;
  font-weight: bold;
  font-family: monospace;
}

.shareable-link .link-actions {
  display: flex;
  gap: 0.5rem;
}

.shareable-link .share-link-input {
  flex: 1;
  padding: 0.5rem;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  font-size: 0.875rem;
  background: var(--surface-primary);
  color: var(--text-secondary);
}

.shareable-link .copy-btn {
  white-space: nowrap;
}
```

---

## Estimated Effort

| Phase | Effort |
|-------|--------|
| Phase 1: Server Routes | 30 min |
| Phase 2: Client Routing | 30 min |
| Phase 3: ShareableLink | 1 hour |
| Phase 4: Game Updates | 1-2 hours |
| Phase 5: Testing | 30 min |
| **Total** | **3-5 hours** |

