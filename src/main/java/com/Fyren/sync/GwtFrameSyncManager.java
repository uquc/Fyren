package com.Fyren.sync;

import com.Fyren.game.GameWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GWT 兼容的帧同步管理器 — 主线程驱动，无 java.util.concurrent。
 *
 * 算法与 FrameSyncManager 完全相同（锁步 + 回滚），但：
 * - 不由独立线程驱动，改为外部调用 tick()（从 render() 驱动）
 * - 使用 HashMap 替代 ConcurrentHashMap（GWT 单线程）
 * - 无 Thread.sleep / ScheduledExecutor
 * - 无 ReentrantReadWriteLock（单线程不需要锁）
 */
public class GwtFrameSyncManager {

    private static final int TARGET_FPS = 60;
    private static final int FRAME_TIME_MS = 1000 / TARGET_FPS;
    private static final int ROLLBACK_MAX_FRAMES = 10;

    @FunctionalInterface
    public interface LocalInputProvider {
        InputCommand getInput(int frameNumber, int localPlayerId);
    }

    private final GameWorld gameWorld;
    private final InputBuffer localInputBuffer;
    private final Map<Integer, InputBuffer> remoteInputBuffers = new HashMap<>();

    private boolean running = false;
    private int localPlayerId = 1;
    private LocalInputProvider localInputProvider;
    private Runnable onGameOver;

    // 帧时间管理（用 delta 累积替代固定时钟）
    private float accumulatedMs = 0f;
    private int currentFrame = 0;

    // 预测相关
    private final Map<Integer, InputCommand> lastKnownInputs = new HashMap<>();
    private int confirmedFrame = 0;

    public GwtFrameSyncManager(GameWorld gameWorld) {
        this.gameWorld = gameWorld;
        this.localInputBuffer = new InputBuffer(120);
    }

    public void setLocalInputProvider(LocalInputProvider provider) { this.localInputProvider = provider; }
    public void setLocalPlayerId(int id) { this.localPlayerId = id; }
    public void setOnGameOver(Runnable cb) { this.onGameOver = cb; }
    public int getLocalPlayerId() { return localPlayerId; }

    public void start() {
        running = true;
        currentFrame = 0;
        accumulatedMs = 0f;
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() { return running; }

    /**
     * 主驱动方法 — 每帧从 render() 调用。
     * @param deltaMs 自上次渲染以来经过的时间（毫秒）
     */
    public void tick(float deltaMs) {
        if (!running) return;

        accumulatedMs += deltaMs;

        // 以固定时间步长推进（追赶积压帧，最多 5 帧防止螺旋）
        int maxFrames = 5;
        while (accumulatedMs >= FRAME_TIME_MS && maxFrames > 0) {
            accumulatedMs -= FRAME_TIME_MS;
            maxFrames--;
            tickOneFrame();
        }

        // 防止长时间暂停导致的帧雪崩
        if (accumulatedMs > 200f) accumulatedMs = 0f;
    }

    private void tickOneFrame() {
        // 1. 收集本地输入
        InputCommand localCmd = collectLocalInput(currentFrame);
        localInputBuffer.addInput(localCmd);

        // 2. 获取所有输入并预测
        List<InputCommand> allInputs = gatherInputs(currentFrame);
        List<InputCommand> predicted = predictInputs(allInputs, currentFrame);

        // 3. 推进游戏逻辑
        gameWorld.update(predicted, currentFrame);

        // 4. 检测游戏结束
        if (gameWorld.isGameOver()) {
            running = false;
            if (onGameOver != null) onGameOver.run();
            return;
        }

        // 5. 检查回滚
        checkAndRollback(currentFrame);

        currentFrame++;
    }

    private InputCommand collectLocalInput(int frameNumber) {
        if (localInputProvider != null) {
            return localInputProvider.getInput(frameNumber, localPlayerId);
        }
        return new InputCommand(frameNumber, localPlayerId);
    }

    private List<InputCommand> gatherInputs(int frameNumber) {
        List<InputCommand> inputs = new ArrayList<>();
        inputs.add(localInputBuffer.getInput(frameNumber));
        for (InputBuffer remoteBuf : remoteInputBuffers.values()) {
            inputs.add(remoteBuf.getInput(frameNumber));
        }
        return inputs;
    }

    private List<InputCommand> predictInputs(List<InputCommand> inputs, int frameNumber) {
        List<InputCommand> result = new ArrayList<>();
        Set<Integer> seenPlayers = new HashSet<>();

        for (InputCommand cmd : inputs) {
            if (cmd != null) {
                result.add(cmd);
                seenPlayers.add(cmd.playerId);
                if (!cmd.isEmpty()) {
                    lastKnownInputs.put(cmd.playerId, cmd);
                }
            }
        }

        // 预测缺失的远程输入
        for (Map.Entry<Integer, InputBuffer> entry : remoteInputBuffers.entrySet()) {
            int remoteId = entry.getKey();
            if (!seenPlayers.contains(remoteId)) {
                InputCommand predicted = lastKnownInputs.get(remoteId);
                if (predicted != null) {
                    result.add(copyInput(predicted, frameNumber, remoteId));
                }
            }
        }

        // 预测缺失的本地输入（回滚重放时需要）
        if (!seenPlayers.contains(localPlayerId)) {
            InputCommand predicted = lastKnownInputs.get(localPlayerId);
            if (predicted != null) {
                result.add(copyInput(predicted, frameNumber, localPlayerId));
            }
        }

        return result;
    }

    private InputCommand copyInput(InputCommand src, int frame, int playerId) {
        InputCommand copy = new InputCommand(frame, playerId);
        copy.up = src.up;
        copy.down = src.down;
        copy.left = src.left;
        copy.right = src.right;
        copy.punch = src.punch;
        copy.kick = src.kick;
        copy.special = src.special;
        return copy;
    }

    private void checkAndRollback(int currentFrame) {
        for (Map.Entry<Integer, InputBuffer> entry : remoteInputBuffers.entrySet()) {
            int remoteId = entry.getKey();
            InputBuffer remoteBuf = entry.getValue();

            while (confirmedFrame < remoteBuf.getCurrentFrame()) {
                int checkFrame = confirmedFrame + 1;
                InputCommand confirmedInput = remoteBuf.getInput(checkFrame);
                if (confirmedInput == null) break;

                InputCommand predictedInput = lastKnownInputs.get(remoteId);
                if (predictedInput != null && !predictedInput.equals(confirmedInput)) {
                    int rollbackFrames = Math.min(currentFrame - confirmedFrame, ROLLBACK_MAX_FRAMES);
                    rollback(currentFrame - rollbackFrames);
                    return;
                }
                confirmedFrame++;
            }
        }
    }

    private void rollback(int targetFrame) {
        System.out.println("[GwtFSM] 回滚到帧: " + targetFrame);
        int originalFrame = currentFrame;

        // 使用 GameWorld.rollbackTo() 回滚状态，然后从目标帧重放
        gameWorld.rollbackTo(targetFrame);
        for (int frame = targetFrame; frame < originalFrame; frame++) {
            List<InputCommand> inputs = gatherInputs(frame);
            List<InputCommand> predicted = predictInputs(inputs, frame);
            gameWorld.update(predicted, frame);
        }
    }

    /** 接收远程输入（由网络层在收到 INPUT 包时调用） */
    public void receiveRemoteInput(InputCommand remoteCmd) {
        int playerId = remoteCmd.playerId;
        remoteInputBuffers.computeIfAbsent(playerId, k -> new InputBuffer(120))
                .addInput(remoteCmd);
    }
}
