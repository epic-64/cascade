# Shareable Lobby Links

## Goal

Enable players to share a direct link to their current game/lobby. Clicking the link auto-joins the lobby without requiring manual code entry.

**Example URLs:**
- `https://cascade.app/color-rush/ABC123`
- `https://cascade.app/ai-drawing/XYZ789`
- `https://cascade.app/tug-of-war/DEF456`

---

## Current State

### URL Structure
- Games are accessed via base paths: `/color-rush`, `/ai-drawing`, `/tug-of-war`
- Lobby/game IDs exist only in:
  - WebSocket URLs: `/ws/color-rush/:gameId`
  - Client-side state variables: `currentGameId`, `currentLobbyId`
  - Session storage (for reconnection)

### Lobby Display
- Lobby codes are shown as plain text: `Code: ABC123`
- No link sharing functionality exists
- Players must manually copy the code and communicate it out-of-band

### Routing
- `ClientMain.scala` routes based on `window.location.pathname`
- Routes match exact paths only (e.g., `/color-rush`)
- No support for path parameters

---

## Proposed Solution

### URL Pattern
```
/{game-type}/{lobbyId}
```

Where:
- `game-type`: `color-rush`, `ai-drawing`, `tug-of-war`
- `lobbyId`: 6-character alphanumeric code (e.g., `ABC123`)

### Behavior Matrix

| Scenario | URL | Behavior |
|----------|-----|----------|
| Landing (no lobby) | `/color-rush` | Show create/join UI |
| Direct link | `/color-rush/ABC123` | Auto-populate join form, show join UI |
| Create lobby | `/color-rush` → create | URL updates to `/color-rush/XYZ789` |
| Join lobby | `/color-rush` → join `ABC123` | URL updates to `/color-rush/ABC123` |
| Game ends/leave | `/color-rush/ABC123` | URL reverts to `/color-rush` |

---

## Architecture

### 1. Server-Side: Route Parameters (JVM)

Add parameterized routes that serve the same HTML but allow lobby ID in path:

```scala
// WebServer.scala
@cask.get("/color-rush")
def colorRush(): cask.Response[java.io.InputStream] = serveGamePage("color-rush.html")

@cask.get("/color-rush/:lobbyId")
def colorRushWithLobby(lobbyId: String): cask.Response[java.io.InputStream] = 
  serveGamePage("color-rush.html")

// Same pattern for all games
```

**Note:** The server doesn't validate the lobby ID at this point. The client handles validation via WebSocket.

### 2. Client-Side: URL Parsing (JS)

Update `ClientMain.scala` to extract lobby ID from URL:

```scala
case class GameRoute(gameType: AppRoute, lobbyId: Option[String])

def parseRoute(pathname: String): GameRoute =
  pathname.split("/").filter(_.nonEmpty).toList match
    case "color-rush" :: Nil          => GameRoute(AppRoute.ColorRush, None)
    case "color-rush" :: lobbyId :: _ => GameRoute(AppRoute.ColorRush, Some(lobbyId.toUpperCase))
    case "ai-drawing" :: Nil          => GameRoute(AppRoute.AIDrawing, None)
    case "ai-drawing" :: lobbyId :: _ => GameRoute(AppRoute.AIDrawing, Some(lobbyId.toUpperCase))
    case "tug-of-war" :: Nil          => GameRoute(AppRoute.TugOfWar, None)
    case "tug-of-war" :: lobbyId :: _ => GameRoute(AppRoute.TugOfWar, Some(lobbyId.toUpperCase))
    // ... other routes
```

### 3. Client-Side: URL State Management (JS)

Create a utility module for URL manipulation:

```scala
// js/src/main/scala/client/url/UrlManager.scala
object UrlManager:
  /** Update URL without page reload */
  def setLobbyId(gameType: String, lobbyId: String): Unit =
    val newUrl = s"/$gameType/$lobbyId"
    dom.window.history.pushState(null, "", newUrl)
  
  /** Clear lobby ID from URL */
  def clearLobbyId(gameType: String): Unit =
    val newUrl = s"/$gameType"
    dom.window.history.pushState(null, "", newUrl)
  
  /** Get current lobby ID from URL if present */
  def getLobbyIdFromUrl(): Option[String] =
    dom.window.location.pathname.split("/").filter(_.nonEmpty).toList match
      case _ :: lobbyId :: _ if lobbyId.matches("[A-Z0-9]{6}") => Some(lobbyId)
      case _ => None
```

### 4. Client-Side: Auto-Join Flow

When initializing a game client with a lobby ID in URL:

```scala
def initializeColorRush(lobbyIdFromUrl: Option[String]): Unit =
  lobbyIdFromUrl match
    case Some(lobbyId) =>
      // Pre-populate join form and switch to join tab
      getElementById("joinGameId").foreach(_.asInstanceOf[HTMLInputElement].value = lobbyId)
      switchColorRushTab("join")
      // Optionally: auto-focus the name input
      getElementById("joinPlayerName").foreach(_.asInstanceOf[HTMLElement].focus())
    case None =>
      // Normal flow - check for existing session
      checkForExistingSession()
```

### 5. Shareable Link UI Component

Create a reusable component for displaying and copying the lobby link:

```scala
// js/src/main/scala/client/components/ShareableLink.scala
object ShareableLink:
  def render(gameType: String, lobbyId: String): HTMLElement =
    val fullUrl = s"${dom.window.location.origin}/$gameType/$lobbyId"
    
    div(cls = "shareable-link")(
      span(cls = "lobby-code", content = s"Code: $lobbyId"),
      div(cls = "link-actions")(
        input("text", id = "shareLink", cls = "share-link-input").tap: el =>
          el.value = fullUrl
          el.readOnly = true
        ,
        button(cls = "btn btn-secondary copy-btn", content = "📋 Copy Link").tap: btn =>
          btn.addEventListener("click", (e: Event) => copyToClipboard(fullUrl, btn))
      )
    )
  
  private def copyToClipboard(text: String, button: HTMLElement): Unit =
    dom.window.navigator.clipboard.writeText(text).toFuture.onComplete:
      case Success(_) =>
        button.textContent = "✓ Copied!"
        dom.window.setTimeout(() => button.textContent = "📋 Copy Link", 2000)
      case Failure(_) =>
        button.textContent = "Failed"
```

---

## URL Update Triggers

### When to Update URL to Include Lobby ID
1. **Create lobby** - After receiving `LobbyCreated`/`JoinedMessage` from server
2. **Join lobby** - After receiving successful join confirmation
3. **Rejoin lobby** - After successful reconnection

### When to Clear Lobby ID from URL
1. **Leave lobby** - User clicks "Return to Lobby" or similar
2. **Game ends** - After game completion, return to lobby setup
3. **Lobby not found** - Server responds with error
4. **Session clear** - When clearing reconnection session

---

## Implementation Checklist

### Phase 1: URL Infrastructure

- [ ] **1.1** Create `js/src/main/scala/client/url/UrlManager.scala`
  - [ ] `setLobbyId(gameType, lobbyId)` - Update URL with lobby ID
  - [ ] `clearLobbyId(gameType)` - Remove lobby ID from URL
  - [ ] `getLobbyIdFromUrl()` - Parse lobby ID from current URL

- [ ] **1.2** Update `ClientMain.scala` routing
  - [ ] Create `GameRoute` case class with optional `lobbyId`
  - [ ] Update `parseRoute` to extract lobby ID from path
  - [ ] Pass `lobbyId` to game initialization functions

- [ ] **1.3** Add server routes with path parameters
  - [ ] `/color-rush/:lobbyId` → serves `color-rush.html`
  - [ ] `/ai-drawing/:lobbyId` → serves `ai-drawing.html`
  - [ ] `/tug-of-war/:lobbyId` → serves `tug-of-war.html`

### Phase 2: Shareable Link Component

- [ ] **2.1** Create `js/src/main/scala/client/components/ShareableLink.scala`
  - [ ] Render shareable link with copy button
  - [ ] Implement clipboard copy with feedback

- [ ] **2.2** Add CSS styles for shareable link component
  - [ ] `.shareable-link` container
  - [ ] `.share-link-input` input styling
  - [ ] `.copy-btn` button styling
  - [ ] Copy success animation/feedback

### Phase 3: Color Rush Integration

- [ ] **3.1** Update `initializeColorRush()` signature
  - [ ] Accept `lobbyIdFromUrl: Option[String]` parameter
  - [ ] Auto-populate join form if lobby ID present
  - [ ] Switch to join tab if lobby ID present

- [ ] **3.2** Update URL on lobby events
  - [ ] Call `UrlManager.setLobbyId` after `JoinedMessage` received
  - [ ] Call `UrlManager.clearLobbyId` when returning to lobby setup

- [ ] **3.3** Replace lobby code display with ShareableLink
  - [ ] Update `updateLobbyUI()` to use `ShareableLink.render()`

- [ ] **3.4** Handle URL-based rejoin priority
  - [ ] URL lobby ID takes precedence over session storage
  - [ ] If URL has lobby ID, skip session-based rejoin

### Phase 4: AI Drawing Integration

- [ ] **4.1** Update `initializeDrawing()` signature
  - [ ] Accept `lobbyIdFromUrl: Option[String]` parameter
  - [ ] Auto-populate join form if lobby ID present
  - [ ] Switch to join tab if lobby ID present

- [ ] **4.2** Update URL on lobby events
  - [ ] Call `UrlManager.setLobbyId` after `LobbyCreated`/join success
  - [ ] Call `UrlManager.clearLobbyId` when returning to lobby setup

- [ ] **4.3** Replace lobby code display with ShareableLink
  - [ ] Update `updateLobbyUI()` to use `ShareableLink.render()`

- [ ] **4.4** Handle URL-based rejoin priority
  - [ ] URL lobby ID takes precedence over session storage

### Phase 5: Tug of War Integration

- [ ] **5.1** Update `initializeTugOfWar()` signature
  - [ ] Accept `lobbyIdFromUrl: Option[String]` parameter
  - [ ] Auto-populate join form if lobby ID present
  - [ ] Switch to join tab if lobby ID present

- [ ] **5.2** Update URL on lobby events
  - [ ] Call `UrlManager.setLobbyId` after `JoinedMessage` received
  - [ ] Call `UrlManager.clearLobbyId` when returning to lobby setup

- [ ] **5.3** Replace lobby code display with ShareableLink
  - [ ] Update `updateLobbyUI()` to use `ShareableLink.render()`

- [ ] **5.4** Handle URL-based rejoin priority
  - [ ] URL lobby ID takes precedence over session storage

### Phase 6: Testing & Polish

- [ ] **6.1** Manual testing scenarios
  - [ ] Create lobby → URL updates → copy link → open in new tab → auto-join UI shown
  - [ ] Join via link → enter name → join succeeds → URL preserved
  - [ ] Invalid lobby ID → error shown → URL cleared
  - [ ] Session reconnection still works without URL
  - [ ] Browser back/forward navigation behaves sensibly

- [ ] **6.2** Unit tests
  - [ ] `UrlManager` parsing tests
  - [ ] `parseRoute` tests for all patterns

- [ ] **6.3** Edge cases
  - [ ] Malformed lobby IDs (too short, special chars)
  - [ ] Case insensitivity (abc123 → ABC123)
  - [ ] Trailing slashes
  - [ ] Query parameters preserved

---

## File Changes Summary

### New Files
```
js/src/main/scala/client/url/
  └── UrlManager.scala           # URL manipulation utilities

js/src/main/scala/client/components/
  └── ShareableLink.scala        # Reusable link display component
```

### Modified Files
```
js/src/main/scala/client/
  ├── ClientMain.scala           # Updated routing with lobby ID extraction
  ├── ColorRushClient.scala      # URL updates, ShareableLink integration
  ├── DrawingGameClient.scala    # URL updates, ShareableLink integration
  └── TugOfWarClient.scala       # URL updates, ShareableLink integration

jvm/src/main/scala/server/
  └── WebServer.scala            # Add parameterized routes

jvm/src/main/resources/static/
  └── base.css                   # Add shareable-link styles
```

---

## CSS Additions (base.css)

```css
/* Shareable link component */
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

## Considerations

### Browser History
- Using `pushState` means back button returns to previous URL state
- Consider using `replaceState` instead if this behavior is undesired
- Pop-state events should be handled if users navigate with back/forward

### Session Storage Priority
When a player has both:
1. A lobby ID in the URL
2. A saved session with a different lobby ID

**Recommendation:** URL takes priority. Clear old session if URL lobby differs.

### Deep Link Validation
- Server doesn't validate lobby existence on page load
- Client discovers invalid lobbies via WebSocket error
- Show user-friendly error and option to create new lobby

### Analytics/SEO
- Parameterized URLs are crawlable but lobby IDs are ephemeral
- Consider `noindex` meta tag for game pages to avoid search engine issues

---

## Future Enhancements

1. **QR Code Generation** - Display QR code for easy mobile sharing
2. **Social Sharing** - Native share API integration for mobile
3. **Custom Lobby Names** - Allow users to set memorable lobby names
4. **Lobby Preview** - Show player count before joining
5. **Private Lobbies** - Password-protected lobbies with link + password

---

## Estimated Effort

| Phase | Effort |
|-------|--------|
| Phase 1: URL Infrastructure | 2-3 hours |
| Phase 2: ShareableLink Component | 1-2 hours |
| Phase 3: Color Rush | 1-2 hours |
| Phase 4: AI Drawing | 1-2 hours |
| Phase 5: Tug of War | 1-2 hours |
| Phase 6: Testing | 2-3 hours |
| **Total** | **8-14 hours** |

