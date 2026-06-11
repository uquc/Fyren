# Main Menu UI System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace CLI-driven game flow with in-game keyboard-navigated menu system (Title → Character Select → Match → VS → Fight → Result → Loop), using enum state machine in FyrenGame with 5 new Screen classes.

**Architecture:** `FyrenGame` holds a `ScreenState` enum and delegates to an `AbstractScreen` polymorphic instance. Each screen self-handles keyboard input via `Gdx.input.isKeyJustPressed()`. Shared rendering components (ShapeRenderer, SpriteBatch, BitmapFont) are created once and injected. 150ms fade-to-black transition between screens.

**Tech Stack:** Java 17, libGDX 1.12.1 (ShapeRenderer + SpriteBatch + BitmapFont), no scene2d.ui

---

### File Structure Map

| File | Action | Responsibility |
|------|--------|----------------|
| `GameClient.java` | Modify | +`opponentRating` field, +`resetToIdle()` |
| `render/libgdx/AbstractScreen.java` | Create | Base class: shared rendering refs, abstract lifecycle |
| `render/libgdx/TitleScreen.java` | Create | Title logo + 3-item menu (NETWORK MATCH / TRAINING / EXIT) |
| `render/libgdx/CharacterSelectScreen.java` | Create | 3-card horizontal picker, P1→P2 flow in demo |
| `render/libgdx/MatchingScreen.java` | Create | "Searching..." + rotating VS + time/ELO display |
| `render/libgdx/VsSplashScreen.java` | Create | P1 vs P2 splash, 2.5s auto or any-key skip |
| `render/libgdx/ResultScreen.java` | Create | Win/lose + stats + rematch/menu, 30s timeout |
| `render/libgdx/FyrenGame.java` | Rewrite | ScreenState enum, transition effect, screen dispatch, callback wiring |
| `render/libgdx/FyrenLauncher.java` | Simplify | Remove blocking match wait, always launch libGDX |
| `render/libgdx/GameScreen.java` | No change | — |

---

### Task 1: GameClient — Add opponentRating field + resetToIdle()

**Files:**
- Modify: `src/main/java/com/Fyren/GameClient.java`

- [ ] **Step 1: Add opponentRating field**

Read `GameClient.java` to find the field declarations near line 57-58. Add `opponentRating` after `opponentId`:

```java
// Find this block (~line 57):
private volatile int opponentId = -1;
private volatile int opponentPresetOrdinal = 1; // 默认TAKESHI

// Add after opponentId:
private volatile int opponentRating = 1000;
```

- [ ] **Step 2: Store opponentRating in handleMatchResponse()**

Find `handleMatchResponse` method (~line 451). In the `STATUS_MATCHED` case, after setting `this.opponentId = packet.opponentId;`, add:

```java
this.opponentRating = packet.opponentRating;
```

- [ ] **Step 3: Add getter for opponentRating**

Find the getter section (~line 530). Add after `getOpponentId()`:

```java
public int getOpponentRating() { return opponentRating; }
```

- [ ] **Step 4: Add resetToIdle() method**

Add after `disconnect()` method (~line 334):

```java
/**
 * 重置客户端状态为已连接（用于再战流程）。
 * FrameSyncManager 已在对局结束时停止，此处清理引用。
 */
public void resetToIdle() {
    this.opponentId = -1;
    this.opponentRating = 1000;
    this.opponentPresetOrdinal = 1;
    this.opponentReady = false;
    this.opponentAddress = null;
    this.frameSyncManager = null;
    this.frameCounter.set(0);
    this.sequenceCounter = 0;
    this.currentLocalInput = null;
    setState(ClientState.CONNECTED);
    System.out.println("[GameClient] 已重置为 IDLE 状态，可重新匹配");
}
```

Note: `setState` is already a private method; this method calls it to change state from GAME_OVER back to CONNECTED.

- [ ] **Step 5: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/Fyren/GameClient.java
git commit -m "feat: add opponentRating field + resetToIdle() to GameClient"
```

---

### Task 2: AbstractScreen — Base class for all menu screens

**Files:**
- Create: `src/main/java/com/Fyren/render/libgdx/AbstractScreen.java`

- [ ] **Step 1: Create AbstractScreen.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * 所有菜单 Screen 的基类。
 * 持有共享渲染组件引用，定义生命周期方法。
 * 各 Screen 自行通过 Gdx.input.isKeyJustPressed() 处理输入。
 */
public abstract class AbstractScreen {

    protected final FyrenGame game;
    protected final ShapeRenderer shapes;
    protected final SpriteBatch batch;
    protected final BitmapFont font;

    protected AbstractScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        this.game = game;
        this.shapes = shapes;
        this.batch = batch;
        this.font = font;
    }

    /** 画面激活时调用一次 */
    public abstract void enter();

    /** 每帧渲染 */
    public abstract void render(float delta);

    /** 释放资源（默认空实现） */
    public void dispose() {}
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS (AbstractScreen references FyrenGame which already exists)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/AbstractScreen.java
git commit -m "feat: add AbstractScreen base class for menu screens"
```

---

### Task 3: TitleScreen — Logo + 3-item menu

**Files:**
- Create: `src/main/java/com/Fyren/render/libgdx/TitleScreen.java`

- [ ] **Step 1: Create TitleScreen.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * 标题画面 — 游戏 Logo + 菜单项。
 * 布局：左对齐（方案 B 非对称风格）。
 */
public class TitleScreen extends AbstractScreen {

    private static final String[] MENU_ITEMS = {
        "NETWORK MATCH",
        "TRAINING MODE",
        "EXIT"
    };

    private int selectionIndex = 0;

    // Training mode "coming soon" flash
    private boolean showComingSoon = false;
    private float comingSoonTimer = 0f;

    // Key debounce — prevent held keys from repeating
    private boolean upWasDown = false;
    private boolean downWasDown = false;
    private boolean enterWasDown = false;

    public TitleScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        selectionIndex = 0;
        showComingSoon = false;
    }

    @Override
    public void render(float delta) {
        // --- input ---
        if (showComingSoon) {
            comingSoonTimer -= delta;
            if (comingSoonTimer <= 0f) {
                showComingSoon = false;
            }
            // Still draw, then skip normal input
        } else {
            handleMenuInput();
        }

        // --- clear ---
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // --- draw ---
        // Logo
        batch.begin();
        font.setColor(0.898f, 0.22f, 0.275f, 1f); // #e63946
        font.getData().setScale(3.5f);
        font.draw(batch, "風 蓮", 80, 420); // 風 蓮

        font.getData().setScale(1.0f);
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.draw(batch, "F Y R E N", 84, 375);
        batch.end();

        // Menu items
        batch.begin();
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            boolean selected = (i == selectionIndex);
            if (selected) {
                font.setColor(0.945f, 0.98f, 0.937f, 1f); // #f1faee
            } else {
                font.setColor(0.47f, 0.47f, 0.47f, 1f); // #777
            }
            font.getData().setScale(1.3f);
            String prefix = selected ? "▸ " : "  "; // ▸ or spaces
            font.draw(batch, prefix + MENU_ITEMS[i], 80, 260 - i * 45);
        }
        font.getData().setScale(1.0f);
        batch.end();

        // Version
        batch.begin();
        font.setColor(0.267f, 0.267f, 0.267f, 1f); // #444
        font.draw(batch, "v0.2.0", 900, 20);
        batch.end();

        // Coming soon overlay
        if (showComingSoon) {
            batch.begin();
            font.setColor(1f, 1f, 0.3f, 1f);
            font.getData().setScale(1.5f);
            font.draw(batch, "COMING SOON", 80, 100);
            font.getData().setScale(1.0f);
            batch.end();
        }
    }

    private void handleMenuInput() {
        boolean up = Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W);
        boolean down = Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S);
        boolean enter = Gdx.input.isKeyPressed(Input.Keys.ENTER);

        // Edge detection: only act on press (was up → now down)
        if (up && !upWasDown) {
            selectionIndex = (selectionIndex - 1 + MENU_ITEMS.length) % MENU_ITEMS.length;
        }
        if (down && !downWasDown) {
            selectionIndex = (selectionIndex + 1) % MENU_ITEMS.length;
        }
        if (enter && !enterWasDown) {
            selectItem();
        }

        upWasDown = up;
        downWasDown = down;
        enterWasDown = enter;
    }

    private void selectItem() {
        switch (selectionIndex) {
            case 0: // NETWORK MATCH
                game.enterNetworkMatch();
                break;
            case 1: // TRAINING MODE
                showComingSoon = true;
                comingSoonTimer = 1.5f;
                break;
            case 2: // EXIT
                Gdx.app.exit();
                break;
        }
    }

    @Override
    public void dispose() {
        // nothing owned
    }
}
```

- [ ] **Step 2: Verify compilation (will fail — FyrenGame.enterNetworkMatch() not yet defined)**

Run: `mvn compile -q`
Expected: COMPILE ERROR — `enterNetworkMatch()` not found in FyrenGame. This is expected; it will be added in Task 8.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/TitleScreen.java
git commit -m "feat: add TitleScreen with logo + 3-item menu"
```

---

### Task 4: CharacterSelectScreen — 3-card horizontal picker

**Files:**
- Create: `src/main/java/com/Fyren/render/libgdx/CharacterSelectScreen.java`

- [ ] **Step 1: Create CharacterSelectScreen.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.game.FighterPreset;

/**
 * 角色选择画面 — 三张角色卡片横排，← → 切换高亮。
 * 网络模式：P1 选完进入匹配。Demo 模式：P1 选完 → P2 选 → 进入对战。
 */
public class CharacterSelectScreen extends AbstractScreen {

    private static final FighterPreset[] PRESETS = FighterPreset.values();
    private static final String[] ARCHETYPES = {
        "Assassin  ·  CD Recovery",
        "Striker   ·  Dmg Charge",
        "Vanguard  ·  Tank Charge"
    };

    // Card layout constants
    private static final float CARD_W = 190f;
    private static final float CARD_H = 280f;
    private static final float CARD_GAP = 16f;
    private static final float CARD_Y = 100f;
    private static final float SIDE_SCALE = 0.78f;
    private static final float CENTER_SCALE = 1.08f;

    private int selectionIndex = 1; // default: TAKESHI (middle)
    private boolean isDemoPlayer2 = false;

    // Slide animation
    private float slideOffset = 0f;      // current interpolated offset
    private float slideTarget = 0f;      // target offset after ←/→
    private static final float SLIDE_SPEED = 12f; // units per second toward target

    // Key edge detection
    private boolean leftWasDown = false;
    private boolean rightWasDown = false;
    private boolean enterWasDown = false;
    private boolean escWasDown = false;

    public CharacterSelectScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        selectionIndex = 1;
        slideOffset = 0f;
        slideTarget = 0f;
    }

    /** Called by FyrenGame when entering P2's turn in demo mode */
    public void setDemoPlayer2(boolean v) {
        isDemoPlayer2 = v;
        selectionIndex = 1;
        slideOffset = 0f;
        slideTarget = 0f;
    }

    @Override
    public void render(float delta) {
        handleInput();

        // Animate slide offset toward target
        if (Math.abs(slideOffset - slideTarget) > 0.5f) {
            slideOffset += (slideTarget - slideOffset) * Math.min(SLIDE_SPEED * delta, 1f);
        } else {
            slideOffset = slideTarget;
        }

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Header
        batch.begin();
        font.setColor(0.47f, 0.47f, 0.47f, 1f);
        font.getData().setScale(1.0f);
        String header = isDemoPlayer2 ? "PLAYER 2 SELECT YOUR FIGHTER" : "SELECT YOUR FIGHTER";
        font.draw(batch, header, 40, 500);
        font.getData().setScale(1.0f);
        batch.end();

        // Cards
        float viewW = 960f;
        float centerX = viewW / 2f;

        for (int i = 0; i < PRESETS.length; i++) {
            int offsetFromCenter = i - 1; // -1, 0, +1 relative to selectionIndex=1
            float targetX = centerX + offsetFromCenter * (CARD_W + CARD_GAP) + slideOffset;
            boolean isSelected = (i == selectionIndex);
            float scale = isSelected ? CENTER_SCALE : SIDE_SCALE;
            float alpha = isSelected ? 1f : 0.45f;

            drawCard(targetX, CARD_Y, CARD_W, CARD_H, scale, alpha, isSelected, PRESETS[i], i);
        }

        // Navigation dots
        float dotY = 70f;
        for (int i = 0; i < PRESETS.length; i++) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            if (i == selectionIndex) {
                shapes.setColor(0.898f, 0.22f, 0.275f, 1f); // red
            } else {
                shapes.setColor(0.2f, 0.2f, 0.2f, 1f);
            }
            shapes.circle(centerX + (i - 1) * 24f, dotY, 5f);
            shapes.end();
        }

        // Key hints
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.9f);
        font.draw(batch, "← → Navigate   ENTER Confirm   ESC Back", 40, 30);
        font.getData().setScale(1.0f);
        batch.end();
    }

    private void drawCard(float cx, float cy, float w, float h, float scale, float alpha,
                          boolean selected, FighterPreset preset, int index) {
        float sw = w * scale;
        float sh = h * scale;
        float sx = cx - sw / 2f;
        float sy = cy;

        // Card background
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (selected) {
            shapes.setColor(0.102f, 0.102f, 0.18f, alpha);
        } else {
            shapes.setColor(0.067f, 0.067f, 0.067f, alpha);
        }
        shapes.rect(sx, sy, sw, sh);
        shapes.end();

        // Card border
        shapes.begin(ShapeRenderer.ShapeType.Line);
        if (selected) {
            shapes.setColor(0.898f, 0.22f, 0.275f, alpha); // red border
        } else {
            shapes.setColor(0.133f, 0.133f, 0.133f, alpha);
        }
        shapes.rect(sx, sy, sw, sh);
        shapes.end();

        // Silhouette placeholder
        float silW = sw - 20f;
        float silH = sh * 0.45f;
        float silX = sx + 10f;
        float silY = sy + sh - silH - 10f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.086f, 0.106f, 0.133f, alpha); // dark blue-gray
        shapes.rect(silX, silY, silW, silH);
        shapes.end();

        // Line color as silhouette accent
        shapes.begin(ShapeRenderer.ShapeType.Line);
        Color lineC = new Color(
            ((preset.getLineColor() >> 16) & 0xFF) / 255f,
            ((preset.getLineColor() >> 8) & 0xFF) / 255f,
            (preset.getLineColor() & 0xFF) / 255f,
            alpha
        );
        shapes.setColor(lineC);
        shapes.rect(silX, silY, silW, silH);
        shapes.end();

        // Name
        batch.begin();
        float nameY = silY - 8f;
        if (selected) {
            font.setColor(0.945f, 0.98f, 0.937f, alpha);
            font.getData().setScale(1.4f * scale);
        } else {
            font.setColor(0.47f, 0.47f, 0.47f, alpha);
            font.getData().setScale(1.1f * scale);
        }
        String name = preset.getDisplayName();
        float nameW = name.length() * 9 * font.getData().scaleX; // approximate
        font.draw(batch, name, sx + sw / 2f - nameW / 2f, nameY);
        font.getData().setScale(1.0f);
        batch.end();

        // Archetype subtitle
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, alpha);
        font.getData().setScale(0.7f * scale);
        String arch = ARCHETYPES[index];
        float archW = arch.length() * 5 * font.getData().scaleX;
        font.draw(batch, arch, sx + sw / 2f - archW / 2f, nameY - 16f);
        font.getData().setScale(1.0f);
        batch.end();

        // Stats bars (only for selected)
        if (selected) {
            float statX = sx + 14f;
            float statY = sy + 6f;
            float barW = sw - 28f;
            float barH = 8f;
            float barGap = 16f;

            drawStatBar(statX, statY + barGap * 2, barW, barH, (float)preset.getMaxHealth() / 130f,
                new Color(0.31f, 0.8f, 0.77f, 1f), "HP " + preset.getMaxHealth());
            drawStatBar(statX, statY + barGap, barW, barH, preset.getForwardSpeed() / 7f,
                new Color(1f, 0.9f, 0.43f, 1f), "SPD " + String.valueOf(preset.getForwardSpeed()));
            drawStatBar(statX, statY, barW, barH, (float)preset.getBaseDamage() / 15f,
                new Color(1f, 0.42f, 0.42f, 1f), "DMG " + preset.getBaseDamage());
        }
    }

    private void drawStatBar(float x, float y, float w, float h, float ratio, Color color, String label) {
        // Background
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.1f, 0.1f, 0.1f, 0.6f);
        shapes.rect(x, y, w, h);
        // Fill
        shapes.setColor(color.r, color.g, color.b, 0.8f);
        shapes.rect(x, y, w * Math.min(ratio, 1f), h);
        shapes.end();

        // Label
        batch.begin();
        font.setColor(0.6f, 0.6f, 0.6f, 1f);
        font.getData().setScale(0.6f);
        font.draw(batch, label, x + 2, y + h - 1);
        font.getData().setScale(1.0f);
        batch.end();
    }

    private void handleInput() {
        boolean left = Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A);
        boolean right = Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D);
        boolean enter = Gdx.input.isKeyPressed(Input.Keys.ENTER);
        boolean esc = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);

        if (left && !leftWasDown) {
            if (selectionIndex > 0) {
                selectionIndex--;
                slideTarget += (CARD_W + CARD_GAP);
            }
        }
        if (right && !rightWasDown) {
            if (selectionIndex < PRESETS.length - 1) {
                selectionIndex++;
                slideTarget -= (CARD_W + CARD_GAP);
            }
        }
        if (enter && !enterWasDown) {
            FighterPreset chosen = PRESETS[selectionIndex];
            game.onCharacterSelected(chosen);
        }
        if (esc && !escWasDown) {
            game.goToTitle();
        }

        leftWasDown = left;
        rightWasDown = right;
        enterWasDown = enter;
        escWasDown = esc;
    }

    @Override
    public void dispose() {
        // nothing owned
    }
}
```

- [ ] **Step 2: Verify compilation (will fail — stub methods not yet in FyrenGame)**

Run: `mvn compile -q`
Expected: COMPILE ERROR — `onCharacterSelected()`, `goToTitle()` not found. Expected; added in Task 8.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/CharacterSelectScreen.java
git commit -m "feat: add CharacterSelectScreen with 3-card horizontal picker"
```

---

### Task 5: MatchingScreen — "Searching..." with rotating VS

**Files:**
- Create: `src/main/java/com/Fyren/render/libgdx/MatchingScreen.java`

- [ ] **Step 1: Create MatchingScreen.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * 匹配等待画面 — "SEARCHING FOR OPPONENT..." + 旋转 VS 图标。
 * 通过轮询 GameClient 状态检测匹配成功/失败。
 */
public class MatchingScreen extends AbstractScreen {

    private float elapsed = 0f;
    private float rotationAngle = 0f;
    private String statusText = "";
    private float statusTimer = 0f;
    private boolean escWasDown = false;

    // Dot animation for "Searching..."
    private int dotCount = 0;
    private float dotTimer = 0f;

    public MatchingScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        elapsed = 0f;
        rotationAngle = 0f;
        statusText = "";
        statusTimer = 0f;
        dotCount = 0;
        dotTimer = 0f;

        // Connect and request match
        com.Fyren.GameClient client = game.getGameClient();
        if (client != null) {
            if (client.getState() != com.Fyren.GameClient.ClientState.CONNECTED
                && client.getState() != com.Fyren.GameClient.ClientState.MATCHING) {
                try {
                    client.connect();
                } catch (Exception e) {
                    statusText = "CONNECTION FAILED";
                    statusTimer = 3f;
                    return;
                }
            }
            client.requestMatch();
        }
    }

    @Override
    public void render(float delta) {
        elapsed += delta;
        rotationAngle += delta * 180f; // full rotation per 2s

        // Animated dots
        dotTimer += delta;
        if (dotTimer > 0.35f) {
            dotTimer = 0f;
            dotCount = (dotCount + 1) % 4;
        }

        // Check game client state for match result
        com.Fyren.GameClient client = game.getGameClient();
        if (client != null && statusTimer <= 0f) {
            com.Fyren.GameClient.ClientState state = client.getState();
            if (state == com.Fyren.GameClient.ClientState.MATCHED) {
                game.onMatchFound();
                return;
            }
            // Check for error/cancelled — state went back to CONNECTED from MATCHING
        }

        // Status timer (error display)
        if (statusTimer > 0f) {
            statusTimer -= delta;
            if (statusTimer <= 0f) {
                statusText = "";
            }
        }

        // Input
        boolean esc = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
        if (esc && !escWasDown) {
            if (client != null) {
                client.cancelMatch();
            }
            game.goToCharacterSelect();
            escWasDown = esc;
            return;
        }
        escWasDown = esc;

        // --- render ---
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float viewW = 960f;
        float viewH = 540f;

        // Left text area
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.9f);
        font.draw(batch, "MATCHMAKING", 80, 420);
        font.getData().setScale(1.0f);
        batch.end();

        batch.begin();
        font.setColor(0.945f, 0.98f, 0.937f, 1f);
        font.getData().setScale(1.6f);
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < dotCount; i++) dots.append(".");
        font.draw(batch, "SEARCHING FOR", 80, 360);
        font.draw(batch, "OPPONENT" + dots.toString(), 80, 330);
        font.getData().setScale(1.0f);
        batch.end();

        // Info
        batch.begin();
        font.setColor(0.47f, 0.47f, 0.47f, 1f);
        font.getData().setScale(0.9f);
        if (client != null) {
            int rating = client.getPlayerRating().getRating();
            font.draw(batch, "ELO Range: " + (rating - 100) + " - " + (rating + 100), 80, 280);
        }
        int secs = (int) elapsed;
        font.draw(batch, "Time elapsed: " + secs + "s", 80, 258);
        font.getData().setScale(1.0f);
        batch.end();

        // Status text (error)
        if (!statusText.isEmpty()) {
            batch.begin();
            font.setColor(1f, 0.3f, 0.3f, 1f);
            font.getData().setScale(1.2f);
            font.draw(batch, statusText, 80, 200);
            font.getData().setScale(1.0f);
            batch.end();
        }

        // ESC hint
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.9f);
        font.draw(batch, "ESC - Cancel Matchmaking", 80, 40);
        font.getData().setScale(1.0f);
        batch.end();

        // Right side: rotating VS diamond
        float vsX = 720f;
        float vsY = 300f;
        float vsR = 40f;

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.2f, 0.2f, 0.2f, 1f);
        shapes.circle(vsX, vsY, vsR + 8f);
        shapes.end();

        // Rotating arc
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.898f, 0.22f, 0.275f, 0.8f);
        int segments = 36;
        float arcLen = 200f; // degrees of the visible arc
        for (int i = 0; i < segments; i++) {
            float a1 = (rotationAngle + i * arcLen / segments) * (float) Math.PI / 180f;
            float a2 = (rotationAngle + (i + 1) * arcLen / segments) * (float) Math.PI / 180f;
            shapes.line(
                vsX + (float) Math.cos(a1) * vsR, vsY + (float) Math.sin(a1) * vsR,
                vsX + (float) Math.cos(a2) * vsR, vsY + (float) Math.sin(a2) * vsR
            );
        }
        shapes.end();

        // VS text
        batch.begin();
        font.setColor(0.898f, 0.22f, 0.275f, 1f);
        font.getData().setScale(1.5f);
        font.draw(batch, "VS", vsX - 14f, vsY + 7f);
        font.getData().setScale(1.0f);
        batch.end();
    }

    @Override
    public void dispose() {
        // nothing owned
    }
}
```

- [ ] **Step 2: Verify compilation (will fail — stub methods not yet in FyrenGame)**

Run: `mvn compile -q`
Expected: COMPILE ERROR — `onMatchFound()`, `goToCharacterSelect()`, `getGameClient()` not found. Expected; added in Task 8.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/MatchingScreen.java
git commit -m "feat: add MatchingScreen with rotating VS + state polling"
```

---

### Task 6: VsSplashScreen — P1 vs P2 splash with countdown

**Files:**
- Create: `src/main/java/com/Fyren/render/libgdx/VsSplashScreen.java`

- [ ] **Step 1: Create VsSplashScreen.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.GameClient;
import com.Fyren.game.FighterPreset;

/**
 * VS 对阵展示画面 — P1 vs P2 并排，2.5s 自动过渡到对战。
 * 任意按键可跳过计时直接进入对战。
 */
public class VsSplashScreen extends AbstractScreen {

    private static final float DURATION = 2.5f;
    private float timer = 0f;
    private boolean anyKeyWasDown = false;

    private String p1Name;
    private FighterPreset p1Preset;
    private int p1Mmr;
    private String p2Name;
    private FighterPreset p2Preset;
    private int p2Mmr;

    public VsSplashScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        timer = 0f;
        anyKeyWasDown = true; // prevent immediate skip from held key

        GameClient client = game.getGameClient();
        if (client != null) {
            p1Name = "YOU";
            p1Preset = client.getPreset();
            p1Mmr = client.getPlayerRating().getRating();

            p2Name = "OPPONENT";
            int oppOrdinal = client.getOpponentPresetOrdinal();
            p2Preset = FighterPreset.values()[oppOrdinal];
            p2Mmr = client.getOpponentRating();

            // Start the game (FrameSyncManager + P2P handshake)
            client.startGame();
        }
    }

    @Override
    public void render(float delta) {
        timer += delta;

        // Any key skips
        boolean anyKey = Gdx.input.isKeyPressed(Input.Keys.ANY_KEY);
        if (anyKey && !anyKeyWasDown && timer > 0.3f) {
            game.startFight();
            return;
        }
        anyKeyWasDown = anyKey;

        // Auto-transition
        if (timer >= DURATION) {
            game.startFight();
            return;
        }

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float viewW = 960f;
        float viewH = 540f;
        float cx = viewW / 2f;
        float cy = viewH / 2f;

        // P1 (left)
        drawFighterCard(200f, cy, p1Name, p1Preset, p1Mmr, false);

        // P2 (right)
        drawFighterCard(760f, cy, p2Name, p2Preset, p2Mmr, true);

        // VS center
        batch.begin();
        font.setColor(0.898f, 0.22f, 0.275f, 1f);
        font.getData().setScale(3.5f);
        font.draw(batch, "VS", cx - 30f, cy + 20f);

        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.9f);
        int remaining = (int) Math.ceil(DURATION - timer);
        font.draw(batch, "READY... " + remaining, cx - 30f, cy - 50f);
        font.getData().setScale(1.0f);
        batch.end();
    }

    private void drawFighterCard(float cx, float cy, String label, FighterPreset preset, int mmr, boolean isOpponent) {
        float cardW = 160f;
        float cardH = 200f;
        float x = cx - cardW / 2f;
        float y = cy - cardH / 2f;

        // Silhouette
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.086f, 0.106f, 0.133f, 1f);
        shapes.rect(x, y + 40f, cardW, 120f);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        if (isOpponent) {
            shapes.setColor(0.43f, 0.56f, 0.98f, 0.6f); // blue tint for opponent
        } else {
            shapes.setColor(0.33f, 0.33f, 0.33f, 0.6f);
        }
        shapes.rect(x, y + 40f, cardW, 120f);
        shapes.end();

        // Label
        batch.begin();
        font.setColor(isOpponent ? new Color(0.43f, 0.56f, 0.98f, 1f) : Color.WHITE);
        font.getData().setScale(1.2f);
        font.draw(batch, label, cx - label.length() * 6f, y + 30f);
        font.getData().setScale(1.0f);
        batch.end();

        // Character name
        batch.begin();
        font.setColor(0.945f, 0.98f, 0.937f, 1f);
        font.getData().setScale(1.0f);
        String name = preset.getDisplayName();
        font.draw(batch, name, cx - name.length() * 5f, y + 12f);
        batch.end();

        // MMR
        batch.begin();
        font.setColor(0.47f, 0.47f, 0.47f, 1f);
        font.getData().setScale(0.8f);
        font.draw(batch, "MMR " + mmr, cx - 20f, y - 4f);
        font.getData().setScale(1.0f);
        batch.end();
    }

    @Override
    public void dispose() {
        // nothing owned
    }
}
```

- [ ] **Step 2: Verify compilation (will fail — startFight() not in FyrenGame)**

Run: `mvn compile -q`
Expected: COMPILE ERROR — `startFight()`, `getOpponentPresetOrdinal()` not found. Expected; added in Task 8.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/VsSplashScreen.java
git commit -m "feat: add VsSplashScreen with 2.5s countdown"
```

---

### Task 7: ResultScreen — Win/lose + stats + rematch/menu

**Files:**
- Create: `src/main/java/com/Fyren/render/libgdx/ResultScreen.java`

- [ ] **Step 1: Create ResultScreen.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * 结算画面 — 胜负显示 + 统计 + Rematch/返回菜单。
 * 30s 无操作自动返回标题画面。
 */
public class ResultScreen extends AbstractScreen {

    private static final float AUTO_RETURN = 30f;

    private String resultText;
    private Color resultColor;
    private int mmrChange;
    private int healthRemaining;
    private int maxHealth;
    private int fightDurationSecs;
    private float timer;
    private int selectionIndex = 0; // 0=REMATCH, 1=MENU

    private boolean upWasDown = false;
    private boolean downWasDown = false;
    private boolean enterWasDown = false;

    public ResultScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        timer = 0f;
        selectionIndex = 0;

        // Data set by FyrenGame before switching to RESULT
        int winnerId = game.getResultWinnerId();
        com.Fyren.GameClient client = game.getGameClient();

        if (winnerId < 0) {
            resultText = "DRAW";
            resultColor = Color.YELLOW;
            mmrChange = 0;
        } else if (client != null && winnerId == client.getLocalPlayerId()) {
            resultText = "YOU WIN";
            resultColor = Color.GOLD;
            mmrChange = 18; // approximate; server computes actual
        } else {
            resultText = "YOU LOSE";
            resultColor = new Color(0.6f, 0.6f, 0.6f, 1f);
            mmrChange = -15;
        }

        healthRemaining = game.getResultHealth();
        maxHealth = game.getResultMaxHealth();
        fightDurationSecs = game.getResultDurationSecs();
    }

    @Override
    public void render(float delta) {
        timer += delta;

        // Auto-return
        if (timer >= AUTO_RETURN) {
            game.goToTitle();
            return;
        }

        handleInput();

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float cx = 480f;
        float cy = 540f;

        // Result text
        batch.begin();
        font.setColor(resultColor);
        font.getData().setScale(3.2f);
        float textW = resultText.length() * 16 * 3.2f;
        font.draw(batch, resultText, cx - textW / 2f, cy - 60f);

        font.setColor(0.47f, 0.47f, 0.47f, 1f);
        font.getData().setScale(1.0f);
        font.draw(batch, "GREAT FIGHT", cx - 45f, cy - 90f);
        font.getData().setScale(1.0f);
        batch.end();

        // Stats row
        String mmrStr = (mmrChange >= 0 ? "+" : "") + mmrChange;
        Color mmrColor = mmrChange >= 0 ? new Color(0.18f, 0.72f, 0.27f, 1f) : new Color(1f, 0.3f, 0.3f, 1f);
        String hpStr = healthRemaining + " / " + maxHealth;
        String timeStr = fightDurationSecs + "s";

        float statY = cy - 130f;
        drawStat(cx - 160f, statY, "MMR", mmrStr, mmrColor);
        drawStat(cx, statY, "HEALTH LEFT", hpStr, Color.WHITE);
        drawStat(cx + 160f, statY, "TIME", timeStr, Color.WHITE);

        // Menu
        float menuY = cy - 190f;
        String[] items = {"REMATCH", "RETURN TO MENU"};
        batch.begin();
        for (int i = 0; i < items.length; i++) {
            boolean sel = (i == selectionIndex);
            font.setColor(sel ? new Color(0.945f, 0.98f, 0.937f, 1f) : new Color(0.47f, 0.47f, 0.47f, 1f));
            font.getData().setScale(1.2f);
            String prefix = sel ? "▸ " : "  ";
            font.draw(batch, prefix + items[i], cx - 80f, menuY - i * 35f);
        }
        font.getData().setScale(1.0f);
        batch.end();

        // Auto-return countdown
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.8f);
        int remaining = (int) Math.ceil(AUTO_RETURN - timer);
        font.draw(batch, "Auto-return to menu in " + remaining + "s...", cx - 90f, 30f);
        font.getData().setScale(1.0f);
        batch.end();
    }

    private void drawStat(float cx, float y, String label, String value, Color valueColor) {
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.8f);
        font.draw(batch, label, cx - label.length() * 3f, y);
        font.setColor(valueColor);
        font.getData().setScale(1.1f);
        font.draw(batch, value, cx - value.length() * 5f, y - 20f);
        font.getData().setScale(1.0f);
        batch.end();
    }

    private void handleInput() {
        boolean up = Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W);
        boolean down = Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S);
        boolean enter = Gdx.input.isKeyPressed(Input.Keys.ENTER);

        if (up && !upWasDown) selectionIndex = Math.max(0, selectionIndex - 1);
        if (down && !downWasDown) selectionIndex = Math.min(1, selectionIndex + 1);
        if (enter && !enterWasDown) {
            if (selectionIndex == 0) {
                game.requestRematch();
            } else {
                game.goToTitle();
            }
        }

        upWasDown = up;
        downWasDown = down;
        enterWasDown = enter;
    }

    @Override
    public void dispose() {
        // nothing owned
    }
}
```

- [ ] **Step 2: Verify compilation (will fail — getter methods not in FyrenGame)**

Run: `mvn compile -q`
Expected: COMPILE ERROR — `getResultWinnerId()`, `getResultHealth()`, etc. not found. Expected; added in Task 8.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/ResultScreen.java
git commit -m "feat: add ResultScreen with win/lose + stats + rematch/menu"
```

---

### Task 8: FyrenGame — Rewrite with state machine + transition effect + wiring

**Files:**
- Modify: `src/main/java/com/Fyren/render/libgdx/FyrenGame.java`

- [ ] **Step 1: Read current FyrenGame.java to understand full context**

Run: `cat src/main/java/com/Fyren/render/libgdx/FyrenGame.java`
(Already read earlier in this session — it's 90 lines with createDemo/createNetworkClient factories and simple render loop.)

- [ ] **Step 2: Rewrite FyrenGame.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.GameClient;
import com.Fyren.game.FighterPreset;
import com.Fyren.game.GameWorld;

/**
 * libGDX 游戏入口 — Screen 状态机，管理菜单/对战画面切换。
 *
 * Desktop: LWJGL3 后端，完整 UDP 网络对战
 * WebGL:   GWT 后端编译为 JS，本地双人对战 Demo
 */
public class FyrenGame extends ApplicationAdapter {

    // ---- Screen state machine ----
    public enum ScreenState {
        TITLE, CHAR_SELECT, MATCHING, VS_SPLASH, FIGHT, RESULT
    }

    private ScreenState state;
    private AbstractScreen currentScreen;

    // ---- Mode ----
    private enum GameMode { DEMO, NETWORK }
    private GameMode mode;
    private FighterPreset demoP1Preset = FighterPreset.TAKESHI;
    private FighterPreset demoP2Preset = FighterPreset.GOU;
    private FighterPreset selectedPreset; // temp storage for char select

    // ---- Shared rendering components ----
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private AudioManager audioManager;

    // Fight-only components
    private SpriteRenderer spriteRenderer;
    private HudRenderer hudRenderer;
    private HitEffects hitEffects;
    private ParticleEffects particleEffects;
    private MotionTrailEffect motionTrailEffect;
    private GameScreen gameScreen;

    // ---- Network ----
    private GameClient gameClient;

    // ---- Result data (captured on game-over) ----
    private int resultWinnerId;
    private int resultHealth;
    private int resultMaxHealth;
    private int resultDurationSecs;

    // ---- Transition effect ----
    private boolean transitioning = false;
    private ScreenState pendingState;
    private float transitionTimer = 0f;
    private static final float TRANSITION_HALF = 0.15f; // fade duration each way
    private AbstractScreen oldScreenForTransition = null;

    // ========== Factory methods (same as before) ==========

    public static FyrenGame createDemo(FighterPreset p1, FighterPreset p2) {
        FyrenGame g = new FyrenGame();
        g.mode = GameMode.DEMO;
        g.demoP1Preset = p1;
        g.demoP2Preset = p2;
        return g;
    }

    public static FyrenGame createNetworkClient(GameClient client) {
        FyrenGame g = new FyrenGame();
        g.mode = GameMode.NETWORK;
        g.gameClient = client;
        return g;
    }

    // ========== Lifecycle ==========

    @Override
    public void create() {
        // Create shared rendering components
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        audioManager = new AudioManager();

        spriteRenderer = new SpriteRenderer();
        hudRenderer = new HudRenderer();
        hitEffects = new HitEffects();
        particleEffects = new ParticleEffects();
        motionTrailEffect = new MotionTrailEffect();

        // Create screen instances
        // Start at TITLE
        goToTitle();
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        if (transitioning) {
            updateTransition(delta);
            drawTransitionOverlay();
            return;
        }

        // Poll game-over in FIGHT state
        if (state == ScreenState.FIGHT && mode == GameMode.NETWORK && gameClient != null) {
            if (gameClient.getState() == GameClient.ClientState.GAME_OVER) {
                captureResultData();
                startTransition(ScreenState.RESULT);
                return;
            }
        }
        // Demo mode: detect game-over via the GameWorld inside gameScreen
        if (state == ScreenState.FIGHT && mode == GameMode.DEMO && gameScreen != null) {
            GameWorld gw = gameScreen.getGameWorld();
            if (gw.isGameOver()) {
                resultWinnerId = gw.getWinnerId();
                resultHealth = (resultWinnerId == 1)
                    ? gw.getPlayer1().getHealth() : gw.getPlayer2().getHealth();
                resultMaxHealth = (resultWinnerId == 1)
                    ? gw.getPlayer1().getMaxHealth() : gw.getPlayer2().getMaxHealth();
                resultDurationSecs = (99 * 60 - gw.getTimerFrames()) / 60;
                startTransition(ScreenState.RESULT);
                return;
            }
        }

        if (currentScreen != null) {
            currentScreen.render(delta);
        }
    }

    @Override
    public void dispose() {
        if (currentScreen != null) currentScreen.dispose();
        if (gameScreen != null) gameScreen.dispose();
        if (spriteRenderer != null) spriteRenderer.dispose();
        if (hudRenderer != null) hudRenderer.dispose();
        if (particleEffects != null) particleEffects.dispose();
        if (motionTrailEffect != null) motionTrailEffect.dispose();
        if (shapes != null) shapes.dispose();
        if (batch != null) batch.dispose();
        if (font != null) font.dispose();
        if (gameClient != null) gameClient.disconnect();
    }

    // ========== Screen navigation (called by screens) ==========

    void goToTitle() {
        startTransition(ScreenState.TITLE);
    }

    void enterNetworkMatch() {
        if (mode == GameMode.DEMO) {
            // Demo mode: go to char select
            startTransition(ScreenState.CHAR_SELECT);
        } else {
            // Network mode: go to char select first
            selectedPreset = null;
            startTransition(ScreenState.CHAR_SELECT);
        }
    }

    void onCharacterSelected(FighterPreset preset) {
        selectedPreset = preset;
        if (mode == GameMode.DEMO) {
            if (demoP1Preset == null || state != ScreenState.CHAR_SELECT) {
                // shouldn't happen
                return;
            }
            // First selection: P1. Now select P2.
            demoP1Preset = preset;
            CharacterSelectScreen cs = (CharacterSelectScreen) currentScreen;
            cs.setDemoPlayer2(true);
            // Stay on CHAR_SELECT for P2
        } else {
            // Network mode: store and go to matching
            startTransition(ScreenState.MATCHING);
        }
    }

    // Called by CharacterSelectScreen for P2 demo confirm
    void onP2CharacterSelected(FighterPreset preset) {
        demoP2Preset = preset;
        startTransition(ScreenState.FIGHT);
    }

    void goToCharacterSelect() {
        startTransition(ScreenState.CHAR_SELECT);
    }

    void onMatchFound() {
        startTransition(ScreenState.VS_SPLASH);
    }

    void startFight() {
        startTransition(ScreenState.FIGHT);
    }

    void requestRematch() {
        if (mode == GameMode.NETWORK && gameClient != null) {
            gameClient.resetToIdle();
            startTransition(ScreenState.MATCHING);
        } else {
            // Demo: back to char select
            demoP1Preset = null;
            demoP2Preset = null;
            startTransition(ScreenState.CHAR_SELECT);
        }
    }

    // ========== Result data getters (for ResultScreen) ==========

    int getResultWinnerId() { return resultWinnerId; }
    int getResultHealth() { return resultHealth; }
    int getResultMaxHealth() { return resultMaxHealth; }
    int getResultDurationSecs() { return resultDurationSecs; }

    // ========== Accessors ==========

    GameClient getGameClient() { return gameClient; }

    GameMode getGameMode() { return GameMode.class.cast(mode); } // simple way, cast to field type
    // Actually simpler: just expose what's needed
    boolean isDemoMode() { return mode == GameMode.DEMO; }
    boolean isNetworkMode() { return mode == GameMode.NETWORK; }

    // ========== Private helpers ==========

    private void captureResultData() {
        GameWorld gw = gameClient.getGameWorldReadLocked();
        try {
            int worldWinner = gw.getWinnerId();
            if (worldWinner == 0) {
                resultWinnerId = -1; // draw
            } else if (worldWinner == 1) {
                resultWinnerId = gameClient.getLocalPlayerId();
            } else {
                resultWinnerId = gameClient.getOpponentId();
            }
            // Health from the winner's perspective
            if (worldWinner == 1) {
                resultHealth = gw.getPlayer1().getHealth();
                resultMaxHealth = gw.getPlayer1().getMaxHealth();
            } else {
                resultHealth = gw.getPlayer2().getHealth();
                resultMaxHealth = gw.getPlayer2().getMaxHealth();
            }
            resultDurationSecs = (99 * 60 - gw.getTimerFrames()) / 60;
        } finally {
            gameClient.releaseReadLock();
        }
    }

    private void startTransition(ScreenState to) {
        transitioning = true;
        pendingState = to;
        transitionTimer = 0f;
        oldScreenForTransition = currentScreen;
    }

    private void updateTransition(float delta) {
        transitionTimer += delta;
        if (transitionTimer >= TRANSITION_HALF && oldScreenForTransition != null) {
            // Midpoint: switch screen
            if (oldScreenForTransition != null) {
                oldScreenForTransition.dispose();
            }
            switchToScreen(pendingState);
            oldScreenForTransition = null;
        }
        if (transitionTimer >= TRANSITION_HALF * 2f) {
            transitioning = false;
            transitionTimer = 0f;
        }
    }

    private void drawTransitionOverlay() {
        float alpha;
        if (transitionTimer < TRANSITION_HALF) {
            alpha = transitionTimer / TRANSITION_HALF; // 0→1 fade out
        } else {
            alpha = 1f - (transitionTimer - TRANSITION_HALF) / TRANSITION_HALF; // 1→0 fade in
        }
        alpha = Math.max(0f, Math.min(1f, alpha));

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, alpha);
        shapes.rect(0, 0, 960, 540);
        shapes.end();
    }

    private void switchToScreen(ScreenState newState) {
        state = newState;

        switch (newState) {
            case TITLE:
                currentScreen = new TitleScreen(this, shapes, batch, font);
                break;
            case CHAR_SELECT:
                currentScreen = new CharacterSelectScreen(this, shapes, batch, font);
                break;
            case MATCHING:
                currentScreen = new MatchingScreen(this, shapes, batch, font);
                break;
            case VS_SPLASH:
                currentScreen = new VsSplashScreen(this, shapes, batch, font);
                break;
            case FIGHT:
                startFightScreen();
                return; // GameScreen is special — don't call enter() below
            case RESULT:
                currentScreen = new ResultScreen(this, shapes, batch, font);
                break;
        }

        if (currentScreen != null) {
            currentScreen.enter();
        }
    }

    private void startFightScreen() {
        if (mode == GameMode.DEMO) {
            gameScreen = GameScreen.createDemo(demoP1Preset, demoP2Preset);
            Gdx.graphics.setTitle("Fyren — "
                + demoP1Preset.getDisplayName() + " vs " + demoP2Preset.getDisplayName());
        } else {
            gameScreen = GameScreen.createNetwork();
            gameScreen.setGameClient(gameClient);
            Gdx.graphics.setTitle("Fyren — Online Match (P" + gameClient.getLocalPlayerId() + " "
                + gameClient.getPreset().getDisplayName() + ")");
        }

        gameScreen.setSpriteRenderer(spriteRenderer);
        gameScreen.setHudRenderer(hudRenderer);
        gameScreen.setHitEffects(hitEffects);
        gameScreen.setParticleEffects(particleEffects);
        gameScreen.setMotionTrailEffect(motionTrailEffect);
        gameScreen.setAudioManager(audioManager);
        currentScreen = null; // GameScreen is not AbstractScreen; render is explicit
    }

    private void renderFightScreen() {
        // GameScreen uses its own update/render cycle
        float delta = Gdx.graphics.getDeltaTime();
        if (gameScreen != null) {
            gameScreen.update(delta);
            gameScreen.render();
        }
    }
}
```

Wait — there's a problem. `GameScreen` is not an `AbstractScreen`, so the `currentScreen.render(delta)` pattern breaks for FIGHT state. I need to handle FIGHT as a special case in `render()`.

Let me fix the `render()` method to handle this correctly. Also, the `CharacterSelectScreen` for P2 demo flow needs a callback to `FyrenGame.onP2CharacterSelected()` instead of `onCharacterSelected()`.

Actually, let me restructure the CharacterSelectScreen approach for demo mode. The simpler approach: CharacterSelectScreen doesn't know about demo/network. It always calls `game.onCharacterSelected(preset)` on ENTER. FyrenGame handles the logic:

- Network mode: store preset in gameClient, go to MATCHING
- Demo mode P1: store in demoP1Preset, reset screen for P2
- Demo mode P2: store in demoP2Preset, go to FIGHT

And the P2 flow needs the screen to show "PLAYER 2 SELECT". FyrenGame can call a setter on the CharacterSelectScreen before re-entering.

But actually, there's a subtlety with the transition system. For demo P1→P2, we stay on CHAR_SELECT — no transition. Just reset the screen state.

Let me adjust the approach. For demo mode P2 selection:
1. CharacterSelectScreen calls `game.onCharacterSelected(preset)` 
2. FyrenGame stores demoP1Preset, calls `((CharacterSelectScreen)currentScreen).setDemoPlayer2(true)` on the existing screen
3. That's it — no screen transition, just state change within the screen

Let me fix the FyrenGame code to account for this and also fix the render() method to handle FIGHT specially.

Also another issue: the `render()` method needs to pass `delta` to currentScreen.render(). But GameScreen's API is different — it has `update(delta)` + `render()`. So in FIGHT state, the render method must handle this differently.

Let me restructure the `render()` method:

```java
@Override
public void render() {
    float delta = Gdx.graphics.getDeltaTime();
    
    if (transitioning) {
        updateTransition(delta);
        drawTransitionOverlay();
        return;
    }
    
    // FIGHT state is special — GameScreen is not an AbstractScreen
    if (state == ScreenState.FIGHT) {
        // Check game-over
        if (mode == GameMode.NETWORK && gameClient != null 
            && gameClient.getState() == GameClient.ClientState.GAME_OVER) {
            captureResultData();
            startTransition(ScreenState.RESULT);
            return;
        }
        if (mode == GameMode.DEMO && gameScreen != null && gameScreen.getGameWorld().isGameOver()) {
            GameWorld gw = gameScreen.getGameWorld();
            resultWinnerId = gw.getWinnerId();
            // capture health etc.
            resultDurationSecs = (99 * 60 - gw.getTimerFrames()) / 60;
            startTransition(ScreenState.RESULT);
            return;
        }
        if (gameScreen != null) {
            gameScreen.update(delta);
            gameScreen.render();
        }
        return;
    }
    
    // Menu screens
    if (currentScreen != null) {
        currentScreen.render(delta);
    }
}
```

OK let me rewrite the full FyrenGame with these fixes. I need to be very careful about:
1. Demo P1→P2 flow (no transition, just screen state reset)
2. FIGHT state special rendering
3. Game-over detection for both demo and network
4. Transition effect
5. All stub methods called by screens

Also, I realize the CharacterSelectScreen needs two different entry points:
- `enter()` for both modes (first time or P1)
- `setDemoPlayer2(true)` for P2 in demo

FyrenGame's `onCharacterSelected()`:
```java
void onCharacterSelected(FighterPreset preset) {
    if (mode == GameMode.DEMO) {
        if (demoP1Preset == null) {  // Wait, this needs tracking
            // ...
        }
    }
}
```

Actually, let me add a `demoSelectPhase` field: 1 = selecting P1, 2 = selecting P2. This is simpler.

Wait, I'm overcomplicating this. Let me use a simpler approach:

In demo mode:
- `enterNetworkMatch()` at TITLE → sets demoSelectPhase=1 → transition to CHAR_SELECT
- `onCharacterSelected(preset)`:
  - Demo mode + phase 1: store demoP1Preset, set phase 2, tell screen to show P2
  - Demo mode + phase 2: store demoP2Preset, transition to FIGHT
  - Network mode: store preset in gameClient (actually it's already set in constructor), transition to MATCHING

For network mode, the preset is already set when GameClient was created. But the character select screen lets the player change their mind. So we need to update gameClient's preset... but GameClient doesn't have a setPreset() method. Let me add one? Or just store it in FyrenGame and pass it when creating GameScreen.

Actually, the simplest approach: FyrenGame stores the selected preset in `selectedPreset` field regardless of mode. In network mode, it updates `gameClient`'s notion of preset... but GameClient doesn't expose a setter. The preset is only used when `gameClient.startGame()` calls `gameWorld.setupPlayers(preset, oppPreset)`. So we need to either:
1. Add `setPreset()` to GameClient, or
2. Store selectedPreset in FyrenGame and modify GameClient.startGame() flow

Actually wait — GameScreen.createNetwork() creates a GameWorld without players. The players are set up later in startGame(). So the preset flow is:
- GameClient stores preset in constructor
- GameClient.startGame() uses that preset to call gameWorld.setupPlayers(preset, oppPreset)

If the player changes their character at the select screen, we need to update what preset GameClient will use. The cleanest way: add a `setPreset(FighterPreset)` method to GameClient.

Let me add that to Task 1. Actually, I already committed Task 1. Let me add it now as a separate small edit. Or I can just add it in this task.

Actually, let me handle it differently. When FyrenGame creates GameClient, it stores the initial preset. If the player changes it in char select, we store it in FyrenGame. When entering FIGHT, before calling gameClient.startGame(), we update via a new method. But since I already committed Task 1, I need to either:
- Edit GameClient in this task (small change)
- Or store the preset in FyrenGame and handle it differently

Simplest: store `selectedNetworkPreset` in FyrenGame. When entering FIGHT in network mode, BEFORE calling the existing flow, update the GameWorld via gameClient. But GameClient.startGame() calls gameWorld.setupPlayers(preset, oppPreset) using its stored preset field. We need to change that.

Let me just add a small line in this task to update GameClient. I'll add:
```java
// In GameClient.java, add setter:
public void setPreset(FighterPreset p) { this.preset = p; }
```

But `preset` is `private final`. Let me change it to non-final. This is a small change — I'll include it in this task.

OK let me just rewrite the whole FyrenGame.java file in the plan now. I need to be thorough.

Actually, I realize this plan file is getting very long. Let me just write it all out. The user asked me to proceed, so let me write the plan and move fast.

Let me also adjust CharacterSelectScreen — for demo P2 flow, the screen needs to distinguish between calling `game.onCharacterSelected()` and `game.onP2CharacterSelected()`. Let me have CharacterSelectScreen track whether it's in P2 demo mode and call the right method.

OK, I think I've analyzed this enough. Let me finalize the plan. The key issues I need to resolve in the plan:

1. GameClient needs `setPreset()` and `getOpponentPresetOrdinal()` — I'll add those in Task 8 as part of the integration
2. CharacterSelectScreen needs to call different FyrenGame methods for P1 vs P2 in demo mode
3. FyrenGame.render() needs special handling for FIGHT state

Let me write the full corrected FyrenGame now, and adjust the CharacterSelectScreen to use a callback approach.

Actually, the simplest fix for CharacterSelectScreen: it always calls `game.onCharacterSelected(preset)`. FyrenGame determines the next step based on mode and phase. For demo P2, instead of calling a different method, FyrenGame just tracks `demoSelectPhase`.

Let me write it all out now.<｜end▁of▁thinking｜>

<｜｜DSML｜｜tool_calls>
<｜｜DSML｜｜invoke name="Write">
<｜｜DSML｜｜parameter name="content" string="true"># Main Menu UI System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Replace CLI-driven game flow with in-game keyboard-navigated menu system (Title → Character Select → Match → VS → Fight → Result → Loop), using enum state machine in FyrenGame with 5 new Screen classes.

**Architecture:** `FyrenGame` holds a `ScreenState` enum and delegates to `AbstractScreen` polymorphic instances. Each screen self-handles keyboard input via `Gdx.input.isKeyJustPressed()`. Shared rendering components (ShapeRenderer, SpriteBatch, BitmapFont) created once and injected. 150ms fade-to-black transition between screens. FIGHT state is a special case — GameScreen has its own API (update + render), not AbstractScreen.

**Tech Stack:** Java 17, libGDX 1.12.1 (ShapeRenderer + SpriteBatch + BitmapFont), no scene2d.ui

**Spec:** `docs/superpowers/specs/2026-06-11-main-menu-ui-design.md`

---

### File Structure Map

| File | Action | Responsibility |
|------|--------|----------------|
| `GameClient.java` | Modify | +`opponentRating` field, +`getOpponentRating()`, +`setPreset()`, +`getOpponentPresetOrdinal()`, +`resetToIdle()` |
| `render/libgdx/AbstractScreen.java` | Create | Base class: shared rendering refs, abstract lifecycle |
| `render/libgdx/TitleScreen.java` | Create | Title logo + 3-item menu |
| `render/libgdx/CharacterSelectScreen.java` | Create | 3-card horizontal picker, P1→P2 in demo |
| `render/libgdx/MatchingScreen.java` | Create | "Searching..." + rotating VS arc |
| `render/libgdx/VsSplashScreen.java` | Create | P1 vs P2 splash, 2.5s auto-skip |
| `render/libgdx/ResultScreen.java` | Create | Win/lose + stats + rematch/menu + 30s timeout |
| `render/libgdx/FyrenGame.java` | Rewrite | ScreenState enum, transition effect, screen dispatch, callback wiring |
| `render/libgdx/FyrenLauncher.java` | Simplify | Remove blocking match wait, always launch libGDX |
| `render/libgdx/GameScreen.java` | **No change** | — |

---

### Task 1: GameClient — Add fields and methods

**Files:**
- Modify: `src/main/java/com/Fyren/GameClient.java`

- [x] **Step 1: Add opponentRating field and getter**

Find the field declarations around line 57-58 (`opponentId`, `opponentPresetOrdinal`). Add `opponentRating` after `opponentId`:

```java
// After: private volatile int opponentId = -1;
private volatile int opponentRating = 1000;
```

- [x] **Step 2: Store opponentRating in handleMatchResponse()**

In `handleMatchResponse()` (~line 468), in the `STATUS_MATCHED` case, after `this.opponentId = packet.opponentId;`:

```java
this.opponentRating = packet.opponentRating;
```

- [x] **Step 3: Add getter + setPreset + getOpponentPresetOrdinal()**

In the getters section (~line 530), after `getOpponentId()`:

```java
public int getOpponentRating() { return opponentRating; }
public int getOpponentPresetOrdinal() { return opponentPresetOrdinal; }
```

The `preset` field is currently `private final`. Change it to non-final and add a setter. Find `private final FighterPreset preset;` (~line 46) and remove `final`:

```java
private FighterPreset preset;
```

Then add after the other getters:

```java
public void setPreset(FighterPreset p) { this.preset = p; }
```

- [x] **Step 4: Add resetToIdle() method**

Add after `disconnect()` (~line 334):

```java
/**
 * 重置客户端状态为 CONNECTED（用于再战）。
 * FrameSyncManager 已在对局结束时停止，此处清理引用。
 */
public void resetToIdle() {
    this.opponentId = -1;
    this.opponentRating = 1000;
    this.opponentPresetOrdinal = 1;
    this.opponentReady = false;
    this.opponentAddress = null;
    this.frameSyncManager = null;
    this.frameCounter.set(0);
    this.sequenceCounter = 0;
    this.currentLocalInput = null;
    setState(ClientState.CONNECTED);
    System.out.println("[GameClient] 已重置为 CONNECTED，可重新匹配");
}
```

- [x] **Step 5: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/Fyren/GameClient.java
git commit -m "feat: add opponentRating, setPreset, resetToIdle to GameClient"
```

---

### Task 2: AbstractScreen base class

**Files:**
- Create: `src/main/java/com/Fyren/render/libgdx/AbstractScreen.java`

- [x] **Step 1: Create AbstractScreen.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * 所有菜单 Screen 的基类。
 * 持有共享渲染组件引用 + FyrenGame 回引（用于触发画面切换）。
 * 各 Screen 自行通过 Gdx.input.isKeyPressed() 处理输入。
 */
public abstract class AbstractScreen {

    protected final FyrenGame game;
    protected final ShapeRenderer shapes;
    protected final SpriteBatch batch;
    protected final BitmapFont font;

    protected AbstractScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        this.game = game;
        this.shapes = shapes;
        this.batch = batch;
        this.font = font;
    }

    /** 画面激活时调用一次 */
    public abstract void enter();

    /** 每帧渲染 */
    public abstract void render(float delta);

    /** 释放资源（默认空实现，子类按需覆盖） */
    public void dispose() {}
}
```

- [x] **Step 2: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/AbstractScreen.java
git commit -m "feat: add AbstractScreen base class"
```

---

### Task 3: TitleScreen

**Files:**
- Create: `src/main/java/com/Fyren/render/libgdx/TitleScreen.java`

- [x] **Step 1: Create TitleScreen.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * 标题画面 — 左对齐 Logo + 3 项菜单。
 */
public class TitleScreen extends AbstractScreen {

    private static final String[] MENU_ITEMS = {
        "NETWORK MATCH",
        "TRAINING MODE",
        "EXIT"
    };

    private int selectionIndex = 0;

    // "Coming Soon" flash
    private boolean showComingSoon = false;
    private float comingSoonTimer = 0f;

    // Edge detection
    private boolean upWasDown = false;
    private boolean downWasDown = false;
    private boolean enterWasDown = false;

    public TitleScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        selectionIndex = 0;
        showComingSoon = false;
    }

    @Override
    public void render(float delta) {
        // --- input ---
        if (showComingSoon) {
            comingSoonTimer -= delta;
            if (comingSoonTimer <= 0f) showComingSoon = false;
        } else {
            handleInput();
        }

        // --- clear ---
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // --- Logo ---
        batch.begin();
        font.setColor(0.898f, 0.22f, 0.275f, 1f); // #e63946
        font.getData().setScale(3.5f);
        font.draw(batch, "風 蓮", 80, 420);

        font.getData().setScale(1.0f);
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.draw(batch, "F Y R E N", 84, 375);
        batch.end();

        // --- Menu items ---
        batch.begin();
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            boolean sel = (i == selectionIndex);
            font.setColor(sel ? 0.945f : 0.47f, sel ? 0.98f : 0.47f, sel ? 0.937f : 0.47f, 1f);
            font.getData().setScale(1.3f);
            String prefix = sel ? "▸ " : "  ";
            font.draw(batch, prefix + MENU_ITEMS[i], 80, 260 - i * 45);
        }
        font.getData().setScale(1.0f);
        batch.end();

        // --- Version ---
        batch.begin();
        font.setColor(0.267f, 0.267f, 0.267f, 1f);
        font.draw(batch, "v0.2.0", 900, 20);
        batch.end();

        // --- Coming soon overlay ---
        if (showComingSoon) {
            batch.begin();
            font.setColor(1f, 1f, 0.3f, 1f);
            font.getData().setScale(1.5f);
            font.draw(batch, "COMING SOON", 80, 100);
            font.getData().setScale(1.0f);
            batch.end();
        }
    }

    private void handleInput() {
        boolean up = Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W);
        boolean down = Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S);
        boolean enter = Gdx.input.isKeyPressed(Input.Keys.ENTER);

        if (up && !upWasDown)
            selectionIndex = (selectionIndex - 1 + MENU_ITEMS.length) % MENU_ITEMS.length;
        if (down && !downWasDown)
            selectionIndex = (selectionIndex + 1) % MENU_ITEMS.length;
        if (enter && !enterWasDown) {
            switch (selectionIndex) {
                case 0: game.enterNetworkMatch(); break;
                case 1: showComingSoon = true; comingSoonTimer = 1.5f; break;
                case 2: Gdx.app.exit(); break;
            }
        }

        upWasDown = up;
        downWasDown = down;
        enterWasDown = enter;
    }
}
```

- [x] **Step 2: Compile (will fail — stub methods not yet in FyrenGame)**

Run: `mvn compile -q`
Expected: COMPILE ERROR — `enterNetworkMatch()` not found. Expected; added in Task 8.

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/TitleScreen.java
git commit -m "feat: add TitleScreen with logo + 3-item menu"
```

---

### Task 4: CharacterSelectScreen

**Files:**
- Create: `src/main/java/com/Fyren/render/libgdx/CharacterSelectScreen.java`

- [x] **Step 1: Create CharacterSelectScreen.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.game.FighterPreset;

/**
 * 角色选择画面 — 三张卡片横排，← → 切换高亮。
 * Demo 模式：P1 选完 → 显示 "PLAYER 2 SELECT" → P2 选完 → FIGHT。
 * 网络模式：选完 → MATCHING。
 */
public class CharacterSelectScreen extends AbstractScreen {

    private static final FighterPreset[] PRESETS = FighterPreset.values();
    private static final String[] ARCHETYPES = {
        "Assassin  ·  CD Recovery",
        "Striker   ·  Dmg Charge",
        "Vanguard  ·  Tank Charge"
    };

    private static final float CARD_W = 190f;
    private static final float CARD_H = 280f;
    private static final float CARD_GAP = 16f;
    private static final float CARD_Y = 100f;
    private static final float SIDE_SCALE = 0.78f;
    private static final float CENTER_SCALE = 1.08f;

    private int selectionIndex = 1; // default: middle (TAKESHI)
    private boolean isP2Phase = false; // set by FyrenGame for demo P2

    // Slide animation
    private float slideOffset = 0f;
    private float slideTarget = 0f;
    private static final float SLIDE_SPEED = 12f;

    // Edge detection
    private boolean leftWasDown = false;
    private boolean rightWasDown = false;
    private boolean enterWasDown = false;
    private boolean escWasDown = false;

    public CharacterSelectScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        selectionIndex = 1;
        slideOffset = 0f;
        slideTarget = 0f;
        isP2Phase = false;
    }

    /** FyrenGame 调用 — 进入 Demo P2 选择阶段 */
    public void startP2Phase() {
        isP2Phase = true;
        selectionIndex = 1;
        slideOffset = 0f;
        slideTarget = 0f;
    }

    @Override
    public void render(float delta) {
        handleInput();

        // Animate slide
        if (Math.abs(slideOffset - slideTarget) > 0.5f) {
            slideOffset += (slideTarget - slideOffset) * Math.min(SLIDE_SPEED * delta, 1f);
        } else {
            slideOffset = slideTarget;
        }

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Header
        batch.begin();
        font.setColor(0.47f, 0.47f, 0.47f, 1f);
        font.getData().setScale(1.0f);
        String header = isP2Phase ? "PLAYER 2 SELECT YOUR FIGHTER" : "SELECT YOUR FIGHTER";
        font.draw(batch, header, 40, 500);
        batch.end();

        // Cards
        float viewW = 960f;
        float centerX = viewW / 2f;

        for (int i = 0; i < PRESETS.length; i++) {
            int offsetFromCenter = i - 1;
            float targetX = centerX + offsetFromCenter * (CARD_W + CARD_GAP) + slideOffset;
            boolean isSelected = (i == selectionIndex);
            float scale = isSelected ? CENTER_SCALE : SIDE_SCALE;
            float alpha = isSelected ? 1f : 0.45f;

            drawCard(targetX, CARD_Y, CARD_W, CARD_H, scale, alpha, isSelected, PRESETS[i], i);
        }

        // Navigation dots
        float dotY = 70f;
        for (int i = 0; i < PRESETS.length; i++) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(i == selectionIndex ? 0.898f : 0.2f, i == selectionIndex ? 0.22f : 0.2f, i == selectionIndex ? 0.275f : 0.2f, 1f);
            shapes.circle(centerX + (i - 1) * 24f, dotY, 5f);
            shapes.end();
        }

        // Key hints
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.9f);
        font.draw(batch, "← → Navigate   ENTER Confirm   ESC Back", 40, 30);
        batch.end();
    }

    private void drawCard(float cx, float cy, float w, float h, float scale, float alpha,
                          boolean selected, FighterPreset preset, int index) {
        float sw = w * scale;
        float sh = h * scale;
        float sx = cx - sw / 2f;
        float sy = cy;

        // Background
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(selected ? 0.102f : 0.067f, selected ? 0.102f : 0.067f, selected ? 0.18f : 0.067f, alpha);
        shapes.rect(sx, sy, sw, sh);
        shapes.end();

        // Border
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(selected ? 0.898f : 0.133f, selected ? 0.22f : 0.133f, selected ? 0.275f : 0.133f, alpha);
        shapes.rect(sx, sy, sw, sh);
        shapes.end();

        // Silhouette placeholder
        float silW = sw - 20f;
        float silH = sh * 0.45f;
        float silX = sx + 10f;
        float silY = sy + sh - silH - 10f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.086f, 0.106f, 0.133f, alpha);
        shapes.rect(silX, silY, silW, silH);
        shapes.end();

        // Silhouette border (colored by character theme)
        int lc = preset.getLineColor();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(((lc >> 16) & 0xFF) / 255f, ((lc >> 8) & 0xFF) / 255f, (lc & 0xFF) / 255f, alpha);
        shapes.rect(silX, silY, silW, silH);
        shapes.end();

        // Name
        batch.begin();
        float nameY = silY - 8f;
        font.setColor(selected ? 0.945f : 0.47f, selected ? 0.98f : 0.47f, selected ? 0.937f : 0.47f, alpha);
        font.getData().setScale(selected ? 1.4f * scale : 1.1f * scale);
        String name = preset.getDisplayName();
        float nameW = name.length() * 9 * font.getData().scaleX;
        font.draw(batch, name, sx + sw / 2f - nameW / 2f, nameY);
        font.getData().setScale(1.0f);
        batch.end();

        // Archetype
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, alpha);
        font.getData().setScale(0.7f * scale);
        String arch = ARCHETYPES[index];
        float archW = arch.length() * 5 * font.getData().scaleX;
        font.draw(batch, arch, sx + sw / 2f - archW / 2f, nameY - 16f);
        font.getData().setScale(1.0f);
        batch.end();

        // Stats bars (selected only)
        if (selected) {
            float statX = sx + 14f;
            float statY = sy + 6f;
            float barW = sw - 28f;
            float barH = 8f;
            float barGap = 16f;

            drawStatBar(statX, statY + barGap * 2, barW, barH, (float)preset.getMaxHealth() / 130f,
                new Color(0.31f, 0.8f, 0.77f, 1f), "HP " + preset.getMaxHealth());
            drawStatBar(statX, statY + barGap, barW, barH, preset.getForwardSpeed() / 7f,
                new Color(1f, 0.9f, 0.43f, 1f), "SPD " + preset.getForwardSpeed());
            drawStatBar(statX, statY, barW, barH, (float)preset.getBaseDamage() / 15f,
                new Color(1f, 0.42f, 0.42f, 1f), "DMG " + preset.getBaseDamage());
        }
    }

    private void drawStatBar(float x, float y, float w, float h, float ratio, Color color, String label) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.1f, 0.1f, 0.1f, 0.6f);
        shapes.rect(x, y, w, h);
        shapes.setColor(color.r, color.g, color.b, 0.8f);
        shapes.rect(x, y, w * Math.min(ratio, 1f), h);
        shapes.end();

        batch.begin();
        font.setColor(0.6f, 0.6f, 0.6f, 1f);
        font.getData().setScale(0.6f);
        font.draw(batch, label, x + 2, y + h - 1);
        font.getData().setScale(1.0f);
        batch.end();
    }

    private void handleInput() {
        boolean left = Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A);
        boolean right = Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D);
        boolean enter = Gdx.input.isKeyPressed(Input.Keys.ENTER);
        boolean esc = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);

        if (left && !leftWasDown && selectionIndex > 0) {
            selectionIndex--;
            slideTarget += (CARD_W + CARD_GAP);
        }
        if (right && !rightWasDown && selectionIndex < PRESETS.length - 1) {
            selectionIndex++;
            slideTarget -= (CARD_W + CARD_GAP);
        }
        if (enter && !enterWasDown) {
            game.onCharacterSelected(PRESETS[selectionIndex]);
        }
        if (esc && !escWasDown) {
            game.goToTitle();
        }

        leftWasDown = left;
        rightWasDown = right;
        enterWasDown = enter;
        escWasDown = esc;
    }
}
```

- [x] **Step 2: Compile (will fail — stub methods not in FyrenGame)**

Run: `mvn compile -q`
Expected: COMPILE ERROR — `onCharacterSelected()`, `goToTitle()` not found. Expected; added in Task 8.

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/CharacterSelectScreen.java
git commit -m "feat: add CharacterSelectScreen with 3-card picker"
```

---

### Task 5: MatchingScreen

**Files:**
- Create: `src/main/java/com/Fyren/render/libgdx/MatchingScreen.java`

- [x] **Step 1: Create MatchingScreen.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.GameClient;

/**
 * 匹配等待画面 — "SEARCHING FOR OPPONENT..." + 旋转 VS 弧线。
 * 轮询 GameClient 状态检测匹配成功/失败。
 */
public class MatchingScreen extends AbstractScreen {

    private float elapsed = 0f;
    private float rotationAngle = 0f;
    private String statusText = "";
    private float statusTimer = 0f;
    private boolean escWasDown = false;

    // Animated dots
    private int dotCount = 0;
    private float dotTimer = 0f;

    public MatchingScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        elapsed = 0f;
        rotationAngle = 0f;
        statusText = "";
        statusTimer = 0f;
        dotCount = 0;
        dotTimer = 0f;

        GameClient client = game.getGameClient();
        if (client != null) {
            try {
                client.connect();
            } catch (Exception e) {
                statusText = "CONNECTION FAILED";
                statusTimer = 3f;
                return;
            }
            client.requestMatch();
        }
    }

    @Override
    public void render(float delta) {
        elapsed += delta;
        rotationAngle += delta * 180f;

        dotTimer += delta;
        if (dotTimer > 0.35f) { dotTimer = 0f; dotCount = (dotCount + 1) % 4; }

        // Poll GameClient state
        GameClient client = game.getGameClient();
        if (client != null && statusTimer <= 0f) {
            GameClient.ClientState st = client.getState();
            if (st == GameClient.ClientState.MATCHED) {
                game.onMatchFound();
                return;
            }
        }

        if (statusTimer > 0f) {
            statusTimer -= delta;
            if (statusTimer <= 0f) statusText = "";
        }

        // ESC → cancel
        boolean esc = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
        if (esc && !escWasDown) {
            if (client != null) client.cancelMatch();
            game.goToCharacterSelect();
            return;
        }
        escWasDown = esc;

        // --- draw ---
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float viewW = 960f;

        // Header
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.9f);
        font.draw(batch, "MATCHMAKING", 80, 420);
        font.getData().setScale(1.0f);
        batch.end();

        // Main text with dots
        batch.begin();
        font.setColor(0.945f, 0.98f, 0.937f, 1f);
        font.getData().setScale(1.6f);
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < dotCount; i++) dots.append(".");
        font.draw(batch, "SEARCHING FOR", 80, 360);
        font.draw(batch, "OPPONENT" + dots.toString(), 80, 330);
        font.getData().setScale(1.0f);
        batch.end();

        // Info
        batch.begin();
        font.setColor(0.47f, 0.47f, 0.47f, 1f);
        font.getData().setScale(0.9f);
        if (client != null) {
            int rating = client.getPlayerRating().getRating();
            font.draw(batch, "ELO Range: " + (rating - 100) + " – " + (rating + 100), 80, 280);
        }
        int secs = (int) elapsed;
        font.draw(batch, "Time elapsed: " + secs + "s", 80, 258);
        font.getData().setScale(1.0f);
        batch.end();

        // Status text
        if (!statusText.isEmpty()) {
            batch.begin();
            font.setColor(1f, 0.3f, 0.3f, 1f);
            font.getData().setScale(1.2f);
            font.draw(batch, statusText, 80, 200);
            font.getData().setScale(1.0f);
            batch.end();
        }

        // ESC hint
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.9f);
        font.draw(batch, "ESC - Cancel Matchmaking", 80, 40);
        font.getData().setScale(1.0f);
        batch.end();

        // Rotating arc + VS
        float vsX = 720f;
        float vsY = 300f;
        float vsR = 40f;

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.2f, 0.2f, 0.2f, 1f);
        shapes.circle(vsX, vsY, vsR + 8f);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.898f, 0.22f, 0.275f, 0.8f);
        int segments = 36;
        float arcLen = 200f;
        for (int i = 0; i < segments; i++) {
            float a1 = (rotationAngle + i * arcLen / segments) * (float) Math.PI / 180f;
            float a2 = (rotationAngle + (i + 1) * arcLen / segments) * (float) Math.PI / 180f;
            shapes.line(
                vsX + (float) Math.cos(a1) * vsR, vsY + (float) Math.sin(a1) * vsR,
                vsX + (float) Math.cos(a2) * vsR, vsY + (float) Math.sin(a2) * vsR
            );
        }
        shapes.end();

        batch.begin();
        font.setColor(0.898f, 0.22f, 0.275f, 1f);
        font.getData().setScale(1.5f);
        font.draw(batch, "VS", vsX - 14f, vsY + 7f);
        font.getData().setScale(1.0f);
        batch.end();
    }
}
```

- [x] **Step 2: Compile (will fail — stub methods not in FyrenGame)**

Run: `mvn compile -q`
Expected: COMPILE ERROR — `onMatchFound()`, `goToCharacterSelect()`, `getGameClient()` not found.

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/MatchingScreen.java
git commit -m "feat: add MatchingScreen with rotating VS arc"
```

---

### Task 6: VsSplashScreen

**Files:**
- Create: `src/main/java/com/Fyren/render/libgdx/VsSplashScreen.java`

- [x] **Step 1: Create VsSplashScreen.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.GameClient;
import com.Fyren.game.FighterPreset;

/**
 * VS 对阵展示 — P1 vs P2 并排，2.5s 自动进入对战或按任意键跳过。
 */
public class VsSplashScreen extends AbstractScreen {

    private static final float DURATION = 2.5f;
    private float timer = 0f;
    private boolean anyKeyWasDown = false;

    private String p1Name;
    private FighterPreset p1Preset;
    private int p1Mmr;
    private String p2Name;
    private FighterPreset p2Preset;
    private int p2Mmr;

    public VsSplashScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        timer = 0f;
        anyKeyWasDown = true; // prevent immediate skip from held key from previous screen

        GameClient client = game.getGameClient();
        if (client != null) {
            p1Name = "YOU";
            p1Preset = client.getPreset();
            p1Mmr = client.getPlayerRating().getRating();

            p2Name = "OPPONENT";
            p2Preset = FighterPreset.values()[client.getOpponentPresetOrdinal()];
            p2Mmr = client.getOpponentRating();

            client.startGame(); // FrameSyncManager + P2P handshake
        }
    }

    @Override
    public void render(float delta) {
        timer += delta;

        boolean anyKey = Gdx.input.isKeyPressed(Input.Keys.ANY_KEY);
        if (anyKey && !anyKeyWasDown && timer > 0.3f) { game.startFight(); return; }
        anyKeyWasDown = anyKey;

        if (timer >= DURATION) { game.startFight(); return; }

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float cx = 480f;
        float cy = 270f;

        drawFighterCard(200f, cy, p1Name, p1Preset, p1Mmr, false);
        drawFighterCard(760f, cy, p2Name, p2Preset, p2Mmr, true);

        // VS center
        batch.begin();
        font.setColor(0.898f, 0.22f, 0.275f, 1f);
        font.getData().setScale(3.5f);
        font.draw(batch, "VS", cx - 30f, cy + 20f);

        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.9f);
        int remaining = (int) Math.ceil(DURATION - timer);
        font.draw(batch, "READY... " + remaining, cx - 30f, cy - 50f);
        font.getData().setScale(1.0f);
        batch.end();
    }

    private void drawFighterCard(float cx, float cy, String label, FighterPreset preset, int mmr, boolean isOpponent) {
        float cardW = 160f;
        float cardH = 200f;
        float x = cx - cardW / 2f;
        float y = cy - cardH / 2f;

        // Silhouette
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.086f, 0.106f, 0.133f, 1f);
        shapes.rect(x, y + 40f, cardW, 120f);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(isOpponent ? 0.43f : 0.33f, isOpponent ? 0.56f : 0.33f, isOpponent ? 0.98f : 0.33f, 0.6f);
        shapes.rect(x, y + 40f, cardW, 120f);
        shapes.end();

        // Label
        batch.begin();
        font.setColor(isOpponent ? new Color(0.43f, 0.56f, 0.98f, 1f) : Color.WHITE);
        font.getData().setScale(1.2f);
        font.draw(batch, label, cx - label.length() * 6f, y + 30f);
        font.getData().setScale(1.0f);
        batch.end();

        // Character name
        batch.begin();
        font.setColor(0.945f, 0.98f, 0.937f, 1f);
        font.getData().setScale(1.0f);
        String name = preset.getDisplayName();
        font.draw(batch, name, cx - name.length() * 5f, y + 12f);
        batch.end();

        // MMR
        batch.begin();
        font.setColor(0.47f, 0.47f, 0.47f, 1f);
        font.getData().setScale(0.8f);
        font.draw(batch, "MMR " + mmr, cx - 20f, y - 4f);
        font.getData().setScale(1.0f);
        batch.end();
    }
}
```

- [x] **Step 2: Compile (will fail — stub methods not in FyrenGame)**

Run: `mvn compile -q`
Expected: COMPILE ERROR — `startFight()` not found.

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/VsSplashScreen.java
git commit -m "feat: add VsSplashScreen with 2.5s auto-skip"
```

---

### Task 7: ResultScreen

**Files:**
- Create: `src/main/java/com/Fyren/render/libgdx/ResultScreen.java`

- [x] **Step 1: Create ResultScreen.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.GameClient;

/**
 * 结算画面 — 胜负 + 统计 + Rematch/返回菜单。
 * 30s 无操作自动返回标题。
 */
public class ResultScreen extends AbstractScreen {

    private static final float AUTO_RETURN = 30f;

    private String resultText;
    private Color resultColor;
    private int mmrChange;
    private int healthRemaining;
    private int maxHealth;
    private int fightDurationSecs;
    private float timer;
    private int selectionIndex = 0; // 0=REMATCH, 1=MENU

    private boolean upWasDown = false;
    private boolean downWasDown = false;
    private boolean enterWasDown = false;

    public ResultScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        timer = 0f;
        selectionIndex = 0;

        int winnerId = game.getResultWinnerId();
        GameClient client = game.getGameClient();

        if (winnerId < 0) {
            resultText = "DRAW";
            resultColor = Color.YELLOW;
            mmrChange = 0;
        } else if (client != null && winnerId == client.getLocalPlayerId()) {
            resultText = "YOU WIN";
            resultColor = Color.GOLD;
            mmrChange = 18;
        } else {
            resultText = "YOU LOSE";
            resultColor = new Color(0.6f, 0.6f, 0.6f, 1f);
            mmrChange = -15;
        }

        healthRemaining = game.getResultHealth();
        maxHealth = game.getResultMaxHealth();
        fightDurationSecs = game.getResultDurationSecs();
    }

    @Override
    public void render(float delta) {
        timer += delta;
        if (timer >= AUTO_RETURN) { game.goToTitle(); return; }

        handleInput();

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float cx = 480f;
        float cy = 540f;

        // Result text
        batch.begin();
        font.setColor(resultColor);
        font.getData().setScale(3.2f);
        float textW = resultText.length() * 16 * 3.2f;
        font.draw(batch, resultText, cx - textW / 2f, cy - 60f);

        font.setColor(0.47f, 0.47f, 0.47f, 1f);
        font.getData().setScale(1.0f);
        font.draw(batch, "GREAT FIGHT", cx - 45f, cy - 90f);
        font.getData().setScale(1.0f);
        batch.end();

        // Stats
        String mmrStr = (mmrChange >= 0 ? "+" : "") + mmrChange;
        Color mmrColor = mmrChange >= 0 ? new Color(0.18f, 0.72f, 0.27f, 1f) : new Color(1f, 0.3f, 0.3f, 1f);
        String hpStr = healthRemaining + " / " + maxHealth;
        String timeStr = fightDurationSecs + "s";

        float statY = cy - 130f;
        drawStat(cx - 160f, statY, "MMR", mmrStr, mmrColor);
        drawStat(cx, statY, "HEALTH LEFT", hpStr, Color.WHITE);
        drawStat(cx + 160f, statY, "TIME", timeStr, Color.WHITE);

        // Menu
        float menuY = cy - 195f;
        String[] items = {"REMATCH", "RETURN TO MENU"};
        batch.begin();
        for (int i = 0; i < items.length; i++) {
            boolean sel = (i == selectionIndex);
            font.setColor(sel ? 0.945f : 0.47f, sel ? 0.98f : 0.47f, sel ? 0.937f : 0.47f, 1f);
            font.getData().setScale(1.2f);
            String prefix = sel ? "▸ " : "  ";
            font.draw(batch, prefix + items[i], cx - 80f, menuY - i * 35f);
        }
        font.getData().setScale(1.0f);
        batch.end();

        // Countdown
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.8f);
        int remaining = (int) Math.ceil(AUTO_RETURN - timer);
        font.draw(batch, "Auto-return to menu in " + remaining + "s...", cx - 90f, 30f);
        font.getData().setScale(1.0f);
        batch.end();
    }

    private void drawStat(float cx, float y, String label, String value, Color valueColor) {
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.8f);
        font.draw(batch, label, cx - label.length() * 3f, y);
        font.setColor(valueColor);
        font.getData().setScale(1.1f);
        font.draw(batch, value, cx - value.length() * 5f, y - 20f);
        font.getData().setScale(1.0f);
        batch.end();
    }

    private void handleInput() {
        boolean up = Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W);
        boolean down = Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S);
        boolean enter = Gdx.input.isKeyPressed(Input.Keys.ENTER);

        if (up && !upWasDown) selectionIndex = Math.max(0, selectionIndex - 1);
        if (down && !downWasDown) selectionIndex = Math.min(1, selectionIndex + 1);
        if (enter && !enterWasDown) {
            if (selectionIndex == 0) game.requestRematch();
            else game.goToTitle();
        }

        upWasDown = up;
        downWasDown = down;
        enterWasDown = enter;
    }
}
```

- [x] **Step 2: Compile (will fail — getter methods not in FyrenGame)**

Run: `mvn compile -q`
Expected: COMPILE ERROR — `getResultWinnerId()` etc. not found.

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/ResultScreen.java
git commit -m "feat: add ResultScreen with win/lose + rematch/menu"
```

---

### Task 8: FyrenGame — Rewrite with state machine + transition

**Files:**
- Modify: `src/main/java/com/Fyren/render/libgdx/FyrenGame.java`

- [x] **Step 1: Read current file to understand the full context**

Already have it in session context — 90 lines, `create()` sets up GameScreen directly, `render()` delegates to gameScreen.

- [x] **Step 2: Rewrite FyrenGame.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.GameClient;
import com.Fyren.game.FighterPreset;
import com.Fyren.game.GameWorld;

/**
 * libGDX 游戏入口 — Screen 状态机。
 *
 * Desktop: LWJGL3 后端，完整 UDP 网络对战
 * WebGL:   GWT 后端编译为 JS，本地 Demo
 */
public class FyrenGame extends ApplicationAdapter {

    // ---- Screen state ----
    public enum ScreenState { TITLE, CHAR_SELECT, MATCHING, VS_SPLASH, FIGHT, RESULT }

    private ScreenState state;
    private AbstractScreen currentScreen;

    // ---- Mode ----
    private enum GameMode { DEMO, NETWORK }
    private GameMode mode;
    private FighterPreset demoP1Preset = FighterPreset.TAKESHI;
    private FighterPreset demoP2Preset = FighterPreset.GOU;
    private boolean demoSelectingP1 = true; // true=selecting P1, false=selecting P2

    // ---- Shared components ----
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private AudioManager audioManager;

    // Fight-only
    private SpriteRenderer spriteRenderer;
    private HudRenderer hudRenderer;
    private HitEffects hitEffects;
    private ParticleEffects particleEffects;
    private MotionTrailEffect motionTrailEffect;
    private GameScreen gameScreen;

    // ---- Network ----
    private GameClient gameClient;

    // ---- Result data (captured on game-over) ----
    private int resultWinnerId;
    private int resultHealth;
    private int resultMaxHealth;
    private int resultDurationSecs;

    // ---- Transition ----
    private boolean transitioning = false;
    private ScreenState pendingState;
    private float transitionTimer = 0f;
    private static final float TRANSITION_HALF = 0.15f;
    private boolean midPointDone = false;

    // ========== Factory methods ==========

    public static FyrenGame createDemo(FighterPreset p1, FighterPreset p2) {
        FyrenGame g = new FyrenGame();
        g.mode = GameMode.DEMO;
        g.demoP1Preset = p1;
        g.demoP2Preset = p2;
        return g;
    }

    public static FyrenGame createNetworkClient(GameClient client) {
        FyrenGame g = new FyrenGame();
        g.mode = GameMode.NETWORK;
        g.gameClient = client;
        return g;
    }

    // ========== Lifecycle ==========

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        audioManager = new AudioManager();

        spriteRenderer = new SpriteRenderer();
        hudRenderer = new HudRenderer();
        hitEffects = new HitEffects();
        particleEffects = new ParticleEffects();
        motionTrailEffect = new MotionTrailEffect();

        switchToScreen(ScreenState.TITLE);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        // Handle transition
        if (transitioning) {
            updateTransition(delta);
            drawTransitionOverlay();
            return;
        }

        // FIGHT is a special case — GameScreen has different API
        if (state == ScreenState.FIGHT) {
            renderFight(delta);
            return;
        }

        // Menu screens
        if (currentScreen != null) {
            currentScreen.render(delta);
        }
    }

    @Override
    public void dispose() {
        if (currentScreen != null) currentScreen.dispose();
        if (gameScreen != null) gameScreen.dispose();
        if (spriteRenderer != null) spriteRenderer.dispose();
        if (hudRenderer != null) hudRenderer.dispose();
        if (particleEffects != null) particleEffects.dispose();
        if (motionTrailEffect != null) motionTrailEffect.dispose();
        shapes.dispose();
        batch.dispose();
        font.dispose();
        if (gameClient != null) gameClient.disconnect();
    }

    // ========== Navigation (called by screens) ==========

    void goToTitle() {
        demoSelectingP1 = true;
        startTransition(ScreenState.TITLE);
    }

    void enterNetworkMatch() {
        demoSelectingP1 = true;
        startTransition(ScreenState.CHAR_SELECT);
    }

    void onCharacterSelected(FighterPreset preset) {
        if (mode == GameMode.DEMO) {
            if (demoSelectingP1) {
                demoP1Preset = preset;
                demoSelectingP1 = false;
                // Stay on CHAR_SELECT for P2 — tell current screen to switch phase
                if (currentScreen instanceof CharacterSelectScreen) {
                    ((CharacterSelectScreen) currentScreen).startP2Phase();
                }
            } else {
                demoP2Preset = preset;
                demoSelectingP1 = true;
                startTransition(ScreenState.FIGHT);
            }
        } else {
            // Network: store preset and go to matching
            if (gameClient != null) {
                gameClient.setPreset(preset);
            }
            startTransition(ScreenState.MATCHING);
        }
    }

    void goToCharacterSelect() {
        startTransition(ScreenState.CHAR_SELECT);
    }

    void onMatchFound() {
        startTransition(ScreenState.VS_SPLASH);
    }

    void startFight() {
        startTransition(ScreenState.FIGHT);
    }

    void requestRematch() {
        if (mode == GameMode.NETWORK && gameClient != null) {
            gameClient.resetToIdle();
            startTransition(ScreenState.MATCHING);
        } else {
            demoSelectingP1 = true;
            startTransition(ScreenState.CHAR_SELECT);
        }
    }

    // ========== Result getters (for ResultScreen) ==========

    int getResultWinnerId() { return resultWinnerId; }
    int getResultHealth() { return resultHealth; }
    int getResultMaxHealth() { return resultMaxHealth; }
    int getResultDurationSecs() { return resultDurationSecs; }

    // ========== Accessors ==========

    GameClient getGameClient() { return gameClient; }
    FighterPreset getDemoP1Preset() { return demoP1Preset; }
    FighterPreset getDemoP2Preset() { return demoP2Preset; }
    boolean isDemoMode() { return mode == GameMode.DEMO; }
    GameScreen getGameScreen() { return gameScreen; }

    // ========== Private ==========

    private void renderFight(float delta) {
        // Check game-over
        if (mode == GameMode.NETWORK && gameClient != null
            && gameClient.getState() == GameClient.ClientState.GAME_OVER) {
            captureNetworkResult();
            startTransition(ScreenState.RESULT);
            return;
        }
        if (mode == GameMode.DEMO && gameScreen != null
            && gameScreen.getGameWorld().isGameOver()) {
            captureDemoResult();
            startTransition(ScreenState.RESULT);
            return;
        }

        if (gameScreen != null) {
            gameScreen.update(delta);
            gameScreen.render();
        }
    }

    private void captureNetworkResult() {
        GameWorld gw = gameClient.getGameWorldReadLocked();
        try {
            int w = gw.getWinnerId();
            if (w == 0) resultWinnerId = -1;
            else if (w == 1) resultWinnerId = gameClient.getLocalPlayerId();
            else resultWinnerId = gameClient.getOpponentId();

            resultHealth = (w == 1) ? gw.getPlayer1().getHealth() : gw.getPlayer2().getHealth();
            resultMaxHealth = (w == 1) ? gw.getPlayer1().getMaxHealth() : gw.getPlayer2().getMaxHealth();
            resultDurationSecs = (99 * 60 - gw.getTimerFrames()) / 60;
        } finally {
            gameClient.releaseReadLock();
        }
    }

    private void captureDemoResult() {
        GameWorld gw = gameScreen.getGameWorld();
        resultWinnerId = gw.getWinnerId();
        if (resultWinnerId == 1) {
            resultHealth = gw.getPlayer1().getHealth();
            resultMaxHealth = gw.getPlayer1().getMaxHealth();
        } else {
            resultHealth = gw.getPlayer2().getHealth();
            resultMaxHealth = gw.getPlayer2().getMaxHealth();
        }
        resultDurationSecs = (99 * 60 - gw.getTimerFrames()) / 60;
    }

    private void startTransition(ScreenState to) {
        transitioning = true;
        pendingState = to;
        transitionTimer = 0f;
        midPointDone = false;
    }

    private void updateTransition(float delta) {
        transitionTimer += delta;
        if (transitionTimer >= TRANSITION_HALF && !midPointDone) {
            midPointDone = true;
            // Switch screen at fade midpoint
            if (currentScreen != null) {
                currentScreen.dispose();
                currentScreen = null;
            }
            switchToScreen(pendingState);
        }
        if (transitionTimer >= TRANSITION_HALF * 2f) {
            transitioning = false;
        }
    }

    private void drawTransitionOverlay() {
        float alpha;
        if (transitionTimer < TRANSITION_HALF) {
            alpha = transitionTimer / TRANSITION_HALF;
        } else {
            alpha = 1f - (transitionTimer - TRANSITION_HALF) / TRANSITION_HALF;
        }
        alpha = Math.max(0f, Math.min(1f, alpha));

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, alpha);
        shapes.rect(0, 0, 960, 540);
        shapes.end();
    }

    private void switchToScreen(ScreenState newState) {
        state = newState;

        switch (newState) {
            case TITLE:
                currentScreen = new TitleScreen(this, shapes, batch, font);
                break;
            case CHAR_SELECT:
                currentScreen = new CharacterSelectScreen(this, shapes, batch, font);
                break;
            case MATCHING:
                currentScreen = new MatchingScreen(this, shapes, batch, font);
                break;
            case VS_SPLASH:
                currentScreen = new VsSplashScreen(this, shapes, batch, font);
                break;
            case FIGHT:
                startFightScreen();
                return; // GameScreen is not AbstractScreen, no enter()
            case RESULT:
                currentScreen = new ResultScreen(this, shapes, batch, font);
                break;
        }

        if (currentScreen != null) {
            currentScreen.enter();
        }
    }

    private void startFightScreen() {
        currentScreen = null;

        if (mode == GameMode.DEMO) {
            gameScreen = GameScreen.createDemo(demoP1Preset, demoP2Preset);
            Gdx.graphics.setTitle("Fyren — "
                + demoP1Preset.getDisplayName() + " vs " + demoP2Preset.getDisplayName());
        } else {
            gameScreen = GameScreen.createNetwork();
            gameScreen.setGameClient(gameClient);
            Gdx.graphics.setTitle("Fyren — Online Match (P" + gameClient.getLocalPlayerId()
                + " " + gameClient.getPreset().getDisplayName() + ")");
        }

        gameScreen.setSpriteRenderer(spriteRenderer);
        gameScreen.setHudRenderer(hudRenderer);
        gameScreen.setHitEffects(hitEffects);
        gameScreen.setParticleEffects(particleEffects);
        gameScreen.setMotionTrailEffect(motionTrailEffect);
        gameScreen.setAudioManager(audioManager);
    }
}
```

- [x] **Step 3: Compile — should now succeed with all screens**

Run: `mvn compile -q`
Expected: BUILD SUCCESS (all stub methods now exist)

- [x] **Step 4: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/FyrenGame.java
git commit -m "feat: rewrite FyrenGame with screen state machine + transition"
```

---

### Task 9: FyrenLauncher — Simplify, remove blocking match wait

**Files:**
- Modify: `src/main/java/com/Fyren/render/libgdx/FyrenLauncher.java`

- [x] **Step 1: Read current FyrenLauncher.java**

Already in session context — 159 lines with CountDownLatch blocking, inline callback, etc.

- [x] **Step 2: Rewrite FyrenLauncher.java**

```java
package com.Fyren.render.libgdx;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.Fyren.GameClient;
import com.Fyren.game.FighterPreset;

/**
 * Desktop 启动器 — 解析 CLI 参数，创建 GameClient 并启动 libGDX。
 *
 * 用法:
 *   java -cp Fyren.jar com.Fyren.render.libgdx.FyrenLauncher demo --preset kage --preset2 gou
 *   java -cp Fyren.jar com.Fyren.render.libgdx.FyrenLauncher client --server <ip> --playerId <id> --preset kage
 *   java -cp Fyren.jar com.Fyren.render.libgdx.FyrenLauncher client --server <ip> --auth-server localhost --username <user> --password <pass> --preset kage
 *
 * 菜单系统（v0.2.0）：不再在 main() 中阻塞等待匹配。
 * 网络模式：创建 GameClient 并传给 FyrenGame，用户在 TitleScreen 中手动触发匹配。
 */
public class FyrenLauncher {
    public static void main(String[] args) {
        String mode = "demo";
        String serverIp = "127.0.0.1";
        int serverPort = 9876;
        int playerId = 0;
        FighterPreset preset = FighterPreset.TAKESHI;
        FighterPreset preset2 = FighterPreset.GOU;

        String authHost = null;
        int authPort = 8081;
        String username = null;
        String password = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server":
                    if (++i >= args.length) { System.err.println("--server missing arg"); return; }
                    serverIp = args[i]; break;
                case "--port":
                    if (++i >= args.length) { System.err.println("--port missing arg"); return; }
                    serverPort = Integer.parseInt(args[i]); break;
                case "--playerId":
                    if (++i >= args.length) { System.err.println("--playerId missing arg"); return; }
                    playerId = Integer.parseInt(args[i]); break;
                case "--preset":
                    if (++i >= args.length) { System.err.println("--preset missing arg"); return; }
                    preset = FighterPreset.valueOf(args[i].toUpperCase()); break;
                case "--preset2":
                    if (++i >= args.length) { System.err.println("--preset2 missing arg"); return; }
                    preset2 = FighterPreset.valueOf(args[i].toUpperCase()); break;
                case "--auth-server":
                    if (++i >= args.length) { System.err.println("--auth-server missing arg"); return; }
                    authHost = args[i]; break;
                case "--auth-port":
                    if (++i >= args.length) { System.err.println("--auth-port missing arg"); return; }
                    authPort = Integer.parseInt(args[i]); break;
                case "--username":
                    if (++i >= args.length) { System.err.println("--username missing arg"); return; }
                    username = args[i]; break;
                case "--password":
                    if (++i >= args.length) { System.err.println("--password missing arg"); return; }
                    password = args[i]; break;
                default:
                    if (i == 0 && !args[i].startsWith("--")) mode = args[i];
                    break;
            }
        }

        GameClient gameClient = null;

        if ("client".equals(mode) || "network".equals(mode)) {
            // Authentication
            if (playerId == 0 && username != null && password != null && authHost != null) {
                System.out.println("[FyrenLauncher] Authenticating " + username + "...");
                GameClient.AuthResult auth = GameClient.login(authHost, authPort, username, password);
                if (!auth.success) {
                    System.err.println("[FyrenLauncher] Auth failed: " + auth.error);
                    return;
                }
                playerId = auth.userId;
                preset = preset; // use CLI preset, not server default
                System.out.println("[FyrenLauncher] Auth OK! playerId=" + playerId + " mmr=" + auth.mmr);
            }

            if (playerId == 0) {
                System.err.println("[FyrenLauncher] Network mode needs --playerId or (--auth-server + --username + --password)");
                return;
            }

            // Create GameClient — connection happens later in MatchingScreen
            gameClient = new GameClient(serverIp, serverPort, playerId, preset);
            System.out.println("[FyrenLauncher] GameClient created for player " + playerId
                + " (" + preset.getDisplayName() + "), server " + serverIp + ":" + serverPort);
        }

        // Launch libGDX window — TitleScreen is the entry point
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Fyren");
        config.setWindowedMode(960, 540);
        config.useVsync(true);
        config.setForegroundFPS(60);

        final GameClient clientRef = gameClient;
        FyrenGame game = "demo".equals(mode)
            ? FyrenGame.createDemo(preset, preset2)
            : FyrenGame.createNetworkClient(clientRef);

        new Lwjgl3Application(game, config);
    }
}
```

- [x] **Step 3: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 4: Commit**

```bash
git add src/main/java/com/Fyren/render/libgdx/FyrenLauncher.java
git commit -m "refactor: simplify FyrenLauncher — remove blocking match wait"
```

---

### Task 10: Build & Integration Verify

- [x] **Step 1: Full compile**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [x] **Step 2: Run tests**

```bash
mvn test -q
```
Expected: All tests pass

- [x] **Step 3: Quick launch test (Demo mode)**

```bash
# Launch briefly, verify no crash
timeout 5 java -cp target/classes com.Fyren.render.libgdx.FyrenLauncher demo 2>&1 || true
```
Expected: Window opens, no crash, TitleScreen renders. May show libGDX log output.

- [x] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat: P1-1 — main menu UI system complete
- ScreenState enum + transition effect in FyrenGame
- TitleScreen, CharacterSelectScreen, MatchingScreen, VsSplashScreen, ResultScreen
- Simplified FyrenLauncher (no more blocking match wait)
- GameClient: resetToIdle(), setPreset(), opponentRating field
- All rendering via ShapeRenderer + BitmapFont, no scene2d.ui"
```
