package com.Fyren.render.libgdx.gwt;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.Fyren.game.FighterPreset;
import com.Fyren.render.libgdx.FyrenGame;

/**
 * GWT/WebGL 入口 — 启动本地 Demo 双人对战。
 *
 * WebGL 模式下不依赖 network/ 包（GWT 不支持 java.net 原生 Socket），
 * 仅运行本地双人对战 Demo 模式。
 */
public class FyrenGwtLauncher extends GwtApplication {

    @Override
    public GwtApplicationConfiguration getConfig() {
        return new GwtApplicationConfiguration(960, 540);
    }

    @Override
    public ApplicationListener createApplicationListener() {
        // WebGL Demo: KAGE vs GOU
        return FyrenGame.createDemo(FighterPreset.KAGE, FighterPreset.GOU);
    }
}
