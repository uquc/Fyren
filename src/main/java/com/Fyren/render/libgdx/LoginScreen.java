package com.Fyren.render.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.Fyren.GameClient;

/**
 * 登录画面 — 进入联网对战前的唯一认证入口。
 * 服务器地址完全隐藏；登录一次后 session 保持，不需要重复输入。
 *
 * 4 个可选项:
 *   0 — 用户名
 *   1 — 密码
 *   2 — 登录
 *   3 — 注册
 */
public class LoginScreen extends AbstractScreen {

    /** ECS 服务器，用户不可见 */
    static final String SERVER_HOST = "115.29.230.57";

    private int selectionIndex = 0;
    private String username = "";
    private String password = "";

    private String statusText = "";
    private boolean loading = false;
    private Thread authThread = null;

    private boolean upWasDown, downWasDown, enterWasDown, escWasDown, backWasDown;

    public LoginScreen(FyrenGame game, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        super(game, shapes, batch, font);
    }

    @Override
    public void enter() {
        selectionIndex = 0;
        statusText = "";
        loading = false;
        upWasDown = downWasDown = enterWasDown = escWasDown = backWasDown = false;
    }

    @Override
    public void render(float delta) {
        if (!loading) handleInput();
        if (authThread != null && !authThread.isAlive()) { authThread = null; loading = false; }

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // 标题
        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.9f);
        font.draw(batch, "账号登录", 80, 420);
        font.getData().setScale(1.0f);
        batch.end();

        // 字段
        String[] labels = {"用户名:", "密码:"};
        String[] values = {username, maskPassword()};
        float[] yPositions = {350, 300};

        for (int i = 0; i < 2; i++) {
            boolean sel = (i == selectionIndex);
            batch.begin();
            font.setColor(sel ? 0.898f : 0.47f, sel ? 0.98f : 0.47f, sel ? 0.937f : 0.47f, 1f);
            font.getData().setScale(1.1f);
            font.draw(batch, labels[i], 80, yPositions[i]);
            batch.end();

            float boxX = 200f;
            float boxY = yPositions[i] - 22f;
            float boxW = 360f;
            float boxH = 28f;
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(sel ? 0.15f : 0.25f, sel ? 0.15f : 0.25f, sel ? 0.15f : 0.25f, 1f);
            shapes.rect(boxX, boxY, boxW, boxH);
            shapes.end();
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(sel ? 0.898f : 0.47f, sel ? 0.22f : 0.47f, sel ? 0.275f : 0.47f, 1f);
            shapes.rect(boxX, boxY, boxW, boxH);
            shapes.end();

            batch.begin();
            font.setColor(sel ? 0.945f : 0.7f, sel ? 0.98f : 0.7f, sel ? 0.937f : 0.7f, 1f);
            font.getData().setScale(1.0f);
            String display = values[i] + (sel && (System.currentTimeMillis() % 1000 > 500) ? "_" : "");
            font.draw(batch, display, boxX + 6f, yPositions[i] - 2f);
            batch.end();
        }

        // 按钮
        String[] buttons = {"[ 登录 ]", "[ 注册 ]"};
        for (int i = 0; i < 2; i++) {
            int btnIdx = 2 + i;
            boolean sel = (btnIdx == selectionIndex);
            float btnY = 220 - i * 50;
            batch.begin();
            font.setColor(sel ? 0.898f : 0.47f, sel ? 0.98f : 0.47f, sel ? 0.937f : 0.47f, 1f);
            font.getData().setScale(1.3f);
            String prefix = sel ? "▸ " : "  ";
            font.draw(batch, prefix + buttons[i], 80, btnY);
            font.getData().setScale(1.0f);
            batch.end();
        }

        // 状态
        if (!statusText.isEmpty()) {
            batch.begin();
            boolean isError = statusText.startsWith("✗") || statusText.contains("失败");
            font.setColor(isError ? 0.9f : 0.3f, isError ? 0.3f : 0.9f, isError ? 0.3f : 0.3f, 1f);
            font.getData().setScale(0.9f);
            font.draw(batch, statusText, 80, 130);
            font.getData().setScale(1.0f);
            batch.end();
        }

        if (loading) {
            batch.begin();
            font.setColor(1f, 1f, 0.3f, 1f);
            font.getData().setScale(0.9f);
            font.draw(batch, "正在连接...", 80, 105);
            font.getData().setScale(1.0f);
            batch.end();
        }

        batch.begin();
        font.setColor(0.33f, 0.33f, 0.33f, 1f);
        font.getData().setScale(0.85f);
        font.draw(batch, "ESC - 返回", 80, 30);
        font.getData().setScale(1.0f);
        batch.end();
    }

    private void handleInput() {
        boolean up   = Gdx.input.isKeyPressed(Input.Keys.UP)   || Gdx.input.isKeyPressed(Input.Keys.W);
        boolean down = Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S);
        boolean enter = Gdx.input.isKeyPressed(Input.Keys.ENTER);
        boolean esc  = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
        boolean back = Gdx.input.isKeyPressed(Input.Keys.BACKSPACE);

        if (up && !upWasDown)   selectionIndex = (selectionIndex - 1 + 4) % 4;
        if (down && !downWasDown) selectionIndex = (selectionIndex + 1) % 4;

        if (esc && !escWasDown) { game.goToTitle(); return; }

        if (selectionIndex <= 1) handleTextInput();
        if (back && !backWasDown) deleteChar();

        if (enter && !enterWasDown) {
            if (selectionIndex == 2) doLogin();
            else if (selectionIndex == 3) doRegister();
        }

        upWasDown = up; downWasDown = down; enterWasDown = enter;
        escWasDown = esc; backWasDown = back;
    }

    private void handleTextInput() {
        for (int key = Input.Keys.A; key <= Input.Keys.Z; key++) {
            if (Gdx.input.isKeyJustPressed(key)) {
                char c = (char) ('a' + (key - Input.Keys.A));
                if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) c = Character.toUpperCase(c);
                appendChar(c);
            }
        }
        for (int key = Input.Keys.NUM_0; key <= Input.Keys.NUM_9; key++) {
            if (Gdx.input.isKeyJustPressed(key)) appendChar((char) ('0' + (key - Input.Keys.NUM_0)));
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.PERIOD)) appendChar('.');
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS))
            appendChar(Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT) ? '_' : '-');
    }

    private void appendChar(char c) {
        if (selectionIndex == 0 && username.length() < 20) username += c;
        else if (selectionIndex == 1 && password.length() < 30) password += c;
    }

    private void deleteChar() {
        if (selectionIndex == 0 && !username.isEmpty()) username = username.substring(0, username.length() - 1);
        else if (selectionIndex == 1 && !password.isEmpty()) password = password.substring(0, password.length() - 1);
    }

    private String maskPassword() { return "*".repeat(password.length()); }

    // ========== 认证 ==========

    private void doLogin() {
        if (username.isEmpty() || password.isEmpty()) { statusText = "✗ 请输入用户名和密码"; return; }
        statusText = ""; loading = true;
        final String user = username.trim(), pass = password;
        authThread = new Thread(() -> {
            GameClient.AuthResult r = GameClient.login(SERVER_HOST, 8081, user, pass);
            Gdx.app.postRunnable(() -> onAuthResult(r));
        });
        authThread.setDaemon(true); authThread.start();
    }

    private void doRegister() {
        if (username.isEmpty() || password.isEmpty()) { statusText = "✗ 请输入用户名和密码"; return; }
        if (password.length() < 6) { statusText = "✗ 密码至少需要 6 个字符"; return; }
        statusText = ""; loading = true;
        final String user = username.trim(), pass = password;
        authThread = new Thread(() -> {
            GameClient.AuthResult reg = GameClient.register(SERVER_HOST, 8081, user, pass);
            if (!reg.success) {
                Gdx.app.postRunnable(() -> { statusText = "✗ 注册失败: " + reg.error; loading = false; });
                return;
            }
            GameClient.AuthResult login = GameClient.login(SERVER_HOST, 8081, user, pass);
            Gdx.app.postRunnable(() -> onAuthResult(login));
        });
        authThread.setDaemon(true); authThread.start();
    }

    private void onAuthResult(GameClient.AuthResult result) {
        loading = false;
        if (!result.success) {
            statusText = "✗ " + (result.error != null ? result.error : "认证失败");
            return;
        }
        statusText = "✓ 登录成功";

        GameClient client = new GameClient(SERVER_HOST, 9876, result.userId, result.mmr,
            com.Fyren.game.FighterPreset.TAKESHI);
        client.setTokens(result.accessToken, result.refreshToken);
        game.onLoginSuccess(client);
    }
}
