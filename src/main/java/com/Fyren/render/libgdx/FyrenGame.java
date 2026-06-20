package com.Fyren.render.libgdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
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
    public enum ScreenState { TITLE, LOGIN, CHAR_SELECT, MATCHING, VS_SPLASH, FIGHT, RESULT }

    private ScreenState state;
    private AbstractScreen currentScreen;

    // ---- Mode ----
    private enum GameMode { DEMO, NETWORK }
    private GameMode mode;
    private FighterPreset demoP1Preset = FighterPreset.TAKESHI;
    private FighterPreset demoP2Preset = FighterPreset.GOU;
    private boolean demoSelectingP1 = true;

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

    // For rendering new screen under fade-in
    private boolean renderingNewScreen = false;

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
        font = createCjkFont();
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
            if (renderingNewScreen && currentScreen != null) {
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                currentScreen.render(delta);
            }
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
        // GameScreen disposes its own injected renderers (spriteRenderer, hudRenderer, etc.)
        if (gameScreen != null) gameScreen.dispose();
        // Only dispose fight renderers if GameScreen never took ownership
        if (gameScreen == null) {
            if (spriteRenderer != null) spriteRenderer.dispose();
            if (hudRenderer != null) hudRenderer.dispose();
            if (particleEffects != null) particleEffects.dispose();
            if (motionTrailEffect != null) motionTrailEffect.dispose();
        }
        // Menu rendering components (not shared with GameScreen)
        if (shapes != null) shapes.dispose();
        if (batch != null) batch.dispose();
        if (font != null) font.dispose();
        if (gameClient != null) gameClient.disconnect();
    }

    // ========== Navigation (called by screens) ==========

    void goToTitle() {
        demoSelectingP1 = true;
        startTransition(ScreenState.TITLE);
    }

    /**
     * TitleScreen "联网对战" 入口。
     * 已登录 → 直接进选人；未登录 → 跳转登录画面。
     */
    void goToNetworkOrLogin() {
        if (mode == GameMode.NETWORK && gameClient != null) {
            // 已登录，直接联网对战
            startTransition(ScreenState.CHAR_SELECT);
        } else {
            startTransition(ScreenState.LOGIN);
        }
    }

    /** LoginScreen 认证成功后调用 — 切换到 NETWORK 模式并进入选人 */
    public void onLoginSuccess(GameClient client) {
        this.mode = GameMode.NETWORK;
        this.gameClient = client;
        startTransition(ScreenState.CHAR_SELECT);
    }

    /** 本地对战入口 */
    void enterLocalMatch() {
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
    boolean isNetworkMode() { return mode == GameMode.NETWORK; }
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
        renderingNewScreen = false;
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
            renderingNewScreen = true;
        }
        if (transitionTimer >= TRANSITION_HALF * 2f) {
            transitioning = false;
            renderingNewScreen = false;
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

    /**
     * 加载支持中文的 BitmapFont。
     * 优先使用系统 CJK 字体（Windows: 微软雅黑），找不到则降级为默认 ASCII 字体。
     */
    private static BitmapFont createCjkFont() {
        // 收集项目中所有中文字符
        String appChinese = "風蓮账号登录用户名密码注册成功失败正在连接返回"
            + "请输入和至少个字符影武刚选择你的角色取消匹配等待对手中"
            + "加载音效已胜败平局再战退出训练模式即将推出联网对战本地";

        String characters = FreeTypeFontGenerator.DEFAULT_CHARS + appChinese;

        // 尝试多个 CJK 字体路径
        String[] fontPaths = {
            "C:/Windows/Fonts/msyh.ttc",       // Windows 微软雅黑
            "C:/Windows/Fonts/simsun.ttc",     // Windows 宋体
            "/System/Library/Fonts/PingFang.ttc", // macOS
            "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf", // Linux
        };

        for (String path : fontPaths) {
            FileHandle fh = Gdx.files.absolute(path);
            if (fh.exists()) {
                try {
                    FreeTypeFontGenerator generator = new FreeTypeFontGenerator(fh);
                    FreeTypeFontParameter param = new FreeTypeFontParameter();
                    param.size = 15;
                    param.characters = characters;
                    BitmapFont font = generator.generateFont(param);
                    generator.dispose();
                    System.out.println("[FyrenGame] CJK 字体已加载: " + path);
                    return font;
                } catch (Exception e) {
                    System.err.println("[FyrenGame] 字体加载失败 " + path + ": " + e.getMessage());
                }
            }
        }

        System.out.println("[FyrenGame] 未找到 CJK 字体，使用默认字体（中文将无法显示）");
        return new BitmapFont();
    }

    private void switchToScreen(ScreenState newState) {
        state = newState;

        switch (newState) {
            case TITLE:
                currentScreen = new TitleScreen(this, shapes, batch, font);
                break;
            case LOGIN:
                currentScreen = new LoginScreen(this, shapes, batch, font);
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
