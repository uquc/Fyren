# Fyren Main Menu UI System — Design Spec

**Date:** 2026-06-11
**Status:** Approved
**Scope:** P1-1 — Main menu / UI system (Title → Character Select → Match → VS → Fight → Result → Loop)

## 1. Overview

Replace the current CLI-driven game flow with an in-game UI system. Players navigate through screens using keyboard input, from title screen through character selection, matchmaking, VS splash, fight, and results — then loop back.

**Key constraints:**
- No external UI framework (no scene2d.ui) — all rendering via libGDX ShapeRenderer + SpriteBatch
- GameScreen (fight screen) must not be modified
- Existing GameClient lifecycle must be preserved
- Both network mode and demo (local 2P) mode must work

## 2. Architecture

### 2.1 Screen State Machine

`FyrenGame` uses a flat enum to dispatch rendering and input. Each screen is a separate class that gets injected with shared rendering components.

```
FyrenGame extends ApplicationAdapter
├── ScreenState: TITLE | CHAR_SELECT | MATCHING | VS_SPLASH | FIGHT | RESULT
├── currentScreen: AbstractScreen (polymorphic dispatch)
├── Shared components: SpriteRenderer, ShapeRenderer, SpriteBatch, BitmapFont, AudioManager
│
├── titleScreen: TitleScreen
├── charSelectScreen: CharacterSelectScreen
├── matchingScreen: MatchingScreen
├── vsSplashScreen: VsSplashScreen
├── gameScreen: GameScreen (EXISTING — not modified)
├── resultScreen: ResultScreen
│
└── render()
    ├── switching screen: dispose old → create new → call enter()
    └── currentScreen.render(shapeRenderer, batch, delta)
```

### 2.2 Screen Class Contract

Each screen is a lightweight class with these methods:

```java
abstract class AbstractScreen {
    void enter();                                          // called once when screen becomes active
    void render(ShapeRenderer sr, SpriteBatch batch);    // every frame
    void handleInput(InputCommand cmd);                   // keyboard input
    void dispose();                                       // cleanup
}
```

No interface — abstract class so shared fields (rendering refs, GameClient ref) can live in the base.

### 2.3 Screen Flow

**Network mode:**
```
TITLE ──[ENTER]──→ CHAR_SELECT ──[ENTER]──→ MATCHING
                                                   │
                                    ┌──[matched]──┘  └──[ESC/cancel]──→ TITLE
                                    ↓
                               VS_SPLASH ──[2.5s auto]──→ FIGHT (GameScreen)
                                                              │
                                                              ↓ [game over]
                                                         RESULT ──[rematch]──→ MATCHING
                                                            │
                                                            └──[menu]──→ TITLE
```

**Demo mode:**
```
TITLE ──[DEMO]──→ CHAR_SELECT(P1) ──[ENTER]──→ CHAR_SELECT(P2) ──[ENTER]──→ FIGHT (GameScreen)
                                                                                    │
                                                                                    ↓ [game over]
                                                                               RESULT ──[rematch]──→ CHAR_SELECT(P1)
                                                                                  │
                                                                                  └──[menu]──→ TITLE
```

## 3. Screen Specifications

### 3.1 TitleScreen

**Layout:** Left-aligned (方案 B — asymmetric). Game logo top-left, menu items below. Right side: character silhouette preview area.

**Menu items:**
1. NETWORK MATCH — connect to server, matchmake, fight online
2. TRAINING MODE — placeholder (future)
3. EXIT — close application

**Input:** ↑↓ to navigate, ENTER to select.

**Rendering:**
- Logo: "風 蓮" in large font (color #e63946), "F Y R E N" subtitle below
- Menu items: selected item highlighted with red left-marker (▸) and bright white text; unselected items gray
- Right area: placeholder silhouette box (future: animated character preview)
- Bottom-right: version text

**Enter flow:**
- "NETWORK MATCH" → switch to CHAR_SELECT, mode=network
- "TRAINING MODE" → (not implemented yet, show "Coming Soon" text flash)
- "EXIT" → Gdx.app.exit()

### 3.2 CharacterSelectScreen

**Layout:** Three character cards in horizontal row. Center card is selected (larger, red border, full stats). Side cards dimmed and scaled down.

**Cards (3 characters):**
| Character | Display Name | Archetype | HP | SPD | DMG |
|-----------|-------------|-----------|-----|-----|------|
| KAGE (影) | Assassin · CD Recovery | 700 | 7.0 | 65 |
| TAKESHI (武) | Striker · Damage Charge | 800 | 6.0 | 75 |
| GOU (刚) | Vanguard · Tank Charge | 1000 | 4.5 | 85 |

**Input:**
- ← → : move selection left/right (cards slide 200ms, selected pops up with spring easing)
- ENTER : confirm selection → switch screen
- ESC : return to TITLE

**Demo mode:** After P1 confirms, the screen resets (shows "PLAYER 2 SELECT") and P1's choice is stored. After P2 confirms, switch to FIGHT.

**Network mode:** After P1 confirms, stored as preset, switch to MATCHING.

**Rendering:**
- Header: "SELECT YOUR FIGHTER" (or "PLAYER 2 SELECT" in demo)
- Each card: silhouette preview rectangle, name, archetype subtitle, stat bars (HP/SPD/DMG as colored filled bars)
- Bottom: ← → navigation dots, key hints

### 3.3 MatchingScreen

**Layout:** Left-aligned text, right side animated VS logo.

**Behavior on enter():**
- If not connected: `gameClient.connect()` (async UDP)
- `gameClient.requestMatch()`
- Listen to GameClient.GameEventCallback for state changes

**Rendering:**
- Main text: "SEARCHING FOR OPPONENT..." with animated trailing dots
- Info: "ELO Range: X – Y" (derived from playerRating ± current diffusion window)
- "Time elapsed: Ns" (counting up from enter)
- Right side: rotating "VS" circle (draw rotating arc via ShapeRenderer, or simple spinning diamond)
- Bottom: "ESC — Cancel Matchmaking"
- Animated dots below the VS circle

**Callback handling:**
- `onMatchFound(opponentId, opponentRating)` → store opponent info, switch to VS_SPLASH
- `onError(e)` → show error text for 2s, then return to TITLE
- ESC key → `gameClient.cancelMatch()`, return to CHAR_SELECT

**Edge cases:**
- Match cancelled by server → `STATUS_CANCELLED` → show "Match Cancelled", return to CHAR_SELECT
- Server disconnect → `onError` → show "Connection Lost", return to TITLE

### 3.4 VsSplashScreen

**Layout:** Symmetrical — P1 left, "VS" center, P2 right.

**Rendering:**
- P1 (left): silhouette, "YOU", character name, MMR
- P2 (right): silhouette, "OPPONENT", character name, MMR
- Center: large "VS" text (red, with subtle glow via layered draw)
- Below VS: "READY..." countdown text
- Duration: 2.5 seconds, then auto-switch to FIGHT

**Enter flow:**
- Store opponent preset from MatchResponsePacket.opponentPresetOrdinal
- `gameClient.startGame()` — triggers FrameSyncManager + P2P handshake
- Start 2.5s timer

**Input:** No input handling (auto-transition). Any key press skips the timer and goes to FIGHT immediately.

### 3.5 GameScreen (EXISTING — Unchanged)

The fight screen. Before switching to FIGHT state, FyrenGame must:
1. Set up GameScreen with the appropriate mode (demo or network)
2. Inject rendering components (already done once, reused)
3. Inject GameClient (network mode only)

When fight ends: GameClient.GameEventCallback.onGameOver(winnerId) fires → FyrenGame captures this → switch to RESULT, passing winnerId, final health values, elapsed time.

**Required change:** FyrenGame needs to listen to GameClient's onGameOver callback to know when to transition. Currently GameClient.reportResult() calls onGameOver internally. FyrenGame sets its own callback that wraps the existing one.

### 3.6 ResultScreen

**Layout:** Centered.

**Rendering:**
- "YOU WIN" or "YOU LOSE" or "DRAW" (large centered text)
- Subtitle: "GREAT FIGHT"
- Stats row (horizontal, centered):
  - MMR change (e.g., "+18" green or "-15" red)
  - Health remaining (e.g., "320 / 800")
  - Fight duration (e.g., "43s")
- Menu: "REMATCH" (selected by default) / "RETURN TO MENU"

**Input:**
- ↑↓ : move between REMATCH and RETURN TO MENU
- ENTER : confirm selection
- 30s auto-return to TITLE (timer counts down, shown at bottom)

**Rematch flow (network):**
- `gameClient.setState(CONNECTED)` — reset from GAME_OVER back to CONNECTED. GameClient needs a `resetToIdle()` method that: sets state to CONNECTED, resets opponentId/opponentReady, clears frameSyncManager reference (was stopped on game-over).
- Then `gameClient.requestMatch()` → switch to MATCHING

**Rematch flow (demo):**
- Switch to CHAR_SELECT(P1), both presets reset

## 4. Input Handling

Input is polled from Gdx.input each frame, same as current GdxInputHandler. FyrenGame samples input at the top of render() and passes it to the active screen.

| Screen | ↑↓ | ← → | ENTER | ESC | Any Key |
|--------|-----|------|-------|-----|---------|
| TITLE | Navigate menu | — | Select | — | — |
| CHAR_SELECT | — | Switch card | Confirm | Back to TITLE | — |
| MATCHING | — | — | — | Cancel match | — |
| VS_SPLASH | — | — | — | — | Skip to FIGHT |
| FIGHT | (GameScreen handles) | — | — | — | — |
| RESULT | Navigate menu | — | Select | — | — |

## 5. Rendering Component Lifecycle

All shared rendering objects are created once in FyrenGame.create() and passed to each screen:

```
FyrenGame.create():
  shapeRenderer = new ShapeRenderer()
  spriteBatch = new SpriteBatch()
  bitmapFont = new BitmapFont()
  audioManager = new AudioManager()
  spriteRenderer = new SpriteRenderer()    // for fight only
  hudRenderer = new HudRenderer()          // for fight only
  hitEffects = new HitEffects()            // for fight only
  ...

FyrenGame.dispose():
  shapeRenderer.dispose()
  spriteBatch.dispose()
  bitmapFont.dispose()
  ...
```

Each screen receives what it needs. Only GameScreen uses SpriteRenderer/HudRenderer/HitEffects. Menu screens use ShapeRenderer + BitmapFont.

## 6. Screen Transition Effect

On screen switch:
1. 150ms fade-out: draw a full-screen black overlay with increasing alpha (0 → 1)
2. Dispose old screen, create new screen, call enter()
3. 150ms fade-in: draw overlay with decreasing alpha (1 → 0)

This masks any setup work in enter() and avoids jarring visual jumps.

## 7. Changes to Existing Files

### FyrenGame.java — Rewrite
- Add ScreenState enum
- Add AbstractScreen inner class
- Add screen instances and switching logic
- Add transition effect state management
- Inject rendering components into screens
- Set up GameClient callback for game-over detection

### FyrenLauncher.java — Simplify
- Remove CountDownLatch blocking match wait
- Remove inline GameEventCallback (moved to MatchingScreen)
- For network mode: create GameClient, connect, set mode=network
- For demo mode: set mode=demo, pass presets
- Always launch libGDX window — TitleScreen is the entry point

### GameClient.java — One new method
- Add `resetToIdle()` method:
  - Set state to CONNECTED
  - Reset opponentId to -1, opponentReady to false
  - Clear frameSyncManager reference (was stopped on game-over)
  - Reset frameCounter and sequenceCounter
  - Clear currentLocalInput
- No other changes to existing GameClient code

### GameScreen.java — No changes
### All other existing files — No changes

## 8. New Files

| File | Purpose |
|------|---------|
| `render/libgdx/AbstractScreen.java` | Base class for all screens |
| `render/libgdx/TitleScreen.java` | Title screen with menu |
| `render/libgdx/CharacterSelectScreen.java` | Character card picker |
| `render/libgdx/MatchingScreen.java` | Matchmaking wait screen |
| `render/libgdx/VsSplashScreen.java` | VS splash with countdown |
| `render/libgdx/ResultScreen.java` | Result display with rematch/menu |

## 9. Demo Mode Flow

Demo mode bypasses network entirely — no MatchingScreen, no VsSplashScreen.

```
TITLE → CHAR_SELECT(P1) → CHAR_SELECT(P2) → FIGHT(demo) → RESULT → ...
```

FyrenGame stores `demoP1Preset` and `demoP2Preset` as instance fields. CharacterSelectScreen sets them in order. When both are set, switch to FIGHT.

GameScreen.createDemo(p1Preset, p2Preset) is called — same as current code.

## 10. Training Mode

Placeholder only. TitleScreen shows "TRAINING MODE" as a menu item. On selection, display "Coming Soon" text flash (1.5s), then return to TITLE. Full training mode is P1-3 and out of scope.

## 11. Edge Cases & Error Handling

| Scenario | Handling |
|----------|----------|
| Server unreachable on connect | MatchingScreen shows "Connection Failed — Press ESC to return" |
| Match timeout (60s) | MatchingScreen shows "Match Timeout — Press ESC to retry" |
| Disconnect during fight | GameClient.onError → FyrenGame shows "Connection Lost" overlay → auto-return to TITLE after 3s |
| ESC during matching | Send cancelMatch() packet, return to CHAR_SELECT |
| Result screen idle timeout | 30s countdown, auto-return to TITLE |
| Demo: only 1 player confirms then ESC | Return to TITLE, discard partial selection |

## 12. What's NOT in Scope

- scene2d.ui integration (we don't use it)
- Menu animations beyond fade transitions and card sliding
- Training mode implementation
- Settings/options menu
- Background art (separate P1-2 task)
- Mouse input (keyboard-only for consistency with fight controls)
