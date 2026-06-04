package com.Fyren.render.libgdx;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.Fyren.game.FighterPreset;

/**
 * Desktop 启动器 — 解析命令行参数，创建 GameClient 并启动 libGDX。
 *
 * 用法:
 *   java -cp Fyren.jar com.Fyren.render.libgdx.FyrenLauncher --server <ip> --preset kage
 *   java -cp Fyren.jar com.Fyren.render.libgdx.FyrenLauncher --mode demo
 */
public class FyrenLauncher {
    public static void main(String[] args) {
        String mode = "demo";
        String serverIp = "127.0.0.1";
        int serverPort = 9876;
        FighterPreset preset = FighterPreset.TAKESHI;
        FighterPreset preset2 = FighterPreset.GOU;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server": serverIp = args[++i]; mode = "client"; break;
                case "--port": serverPort = Integer.parseInt(args[++i]); break;
                case "--preset": preset = FighterPreset.valueOf(args[++i].toUpperCase()); break;
                case "--preset2": preset2 = FighterPreset.valueOf(args[++i].toUpperCase()); break;
                case "--mode": mode = args[++i]; break;
            }
        }

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Fyren");
        config.setWindowedMode(960, 540);
        config.useVsync(true);
        config.setForegroundFPS(60);

        FyrenGame game = "demo".equals(mode)
            ? FyrenGame.createDemo(preset, preset2)
            : FyrenGame.createNetworkClient(serverIp, serverPort, preset);

        new Lwjgl3Application(game, config);
    }
}
