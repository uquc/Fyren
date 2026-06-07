// FrameSyncManager.java
package com.Fyren.sync;

import com.Fyren.game.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 帧同步管理器 - 格斗游戏的核心
 * 采用乐观帧锁定 + 回滚机制
 */
public class FrameSyncManager {
    private static final int TARGET_FPS = 60;
    private static final int FRAME_TIME_MS = 1000 / TARGET_FPS;
    private static final int ROLLBACK_MAX_FRAMES = 10; // 最大回滚帧数

    /**
     * 本地输入提供者 — 由GameClient注入，解耦输入源
     */
    @FunctionalInterface
    public interface LocalInputProvider {
        InputCommand getInput(int frameNumber, int localPlayerId);
    }

    private final GameWorld gameWorld;
    private final InputBuffer localInputBuffer;
    private final Map<Integer, InputBuffer> remoteInputBuffers = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private Thread gameLoopThread;

    private LocalInputProvider localInputProvider;
    private int localPlayerId = 1;

    // 预测相关
    private final Map<Integer, InputCommand> lastKnownInputs = new ConcurrentHashMap<>();
    private int confirmedFrame = 0; // 已确认的最高帧

    public FrameSyncManager(GameWorld gameWorld) {
        this.gameWorld = gameWorld;
        this.localInputBuffer = new InputBuffer(120); // 2秒缓冲
    }

    public void setLocalInputProvider(LocalInputProvider provider) {
        this.localInputProvider = provider;
    }

    public void setLocalPlayerId(int localPlayerId) {
        this.localPlayerId = localPlayerId;
    }

    /**
     * 启动游戏循环
     */
    public void start() {
        running = true;
        gameLoopThread = new Thread(this::gameLoop);
        gameLoopThread.start();
    }

    public void stop() {
        running = false;
    }

    /**
     * 游戏主循环 - 固定时间步长
     */
    private void gameLoop() {
        long lastUpdateTime = System.currentTimeMillis();
        int currentFrame = 0;

        while (running) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastUpdateTime;

            if (elapsed >= FRAME_TIME_MS) {
                // 1. 收集本地输入
                InputCommand localCmd = collectLocalInput(currentFrame);
                localInputBuffer.addInput(localCmd);

                // 2. 获取所有玩家的输入（本地 + 远程）
                List<InputCommand> allInputs = gatherInputs(currentFrame);

                // 3. 预测：如果缺少远程输入，使用上一次的输入预测
                List<InputCommand> inputsForSimulation = predictInputs(allInputs, currentFrame);

                // 4. 推进游戏逻辑
                gameWorld.update(inputsForSimulation, currentFrame);

                // 5. 检查是否需要回滚
                checkAndRollback(currentFrame);

                currentFrame++;
                lastUpdateTime = now;

                // 控制帧率
                long sleepTime = FRAME_TIME_MS - (System.currentTimeMillis() - now);
                if (sleepTime > 0) {
                    try { Thread.sleep(sleepTime); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    /**
     * 收集本地玩家输入
     */
    private InputCommand collectLocalInput(int frameNumber) {
        if (localInputProvider != null) {
            return localInputProvider.getInput(frameNumber, localPlayerId);
        }
        return new InputCommand(frameNumber, localPlayerId);
    }

    /**
     * 收集所有玩家的输入
     */
    private List<InputCommand> gatherInputs(int frameNumber) {
        List<InputCommand> inputs = new ArrayList<>();

        // 本地输入
        inputs.add(localInputBuffer.getInput(frameNumber));

        // 远程输入
        for (InputBuffer remoteBuffer : remoteInputBuffers.values()) {
            inputs.add(remoteBuffer.getInput(frameNumber));
        }

        return inputs;
    }

    /**
     * 预测缺失的输入
     */
    private List<InputCommand> predictInputs(List<InputCommand> inputs, int frameNumber) {
        List<InputCommand> result = new ArrayList<>();
        java.util.Set<Integer> seenPlayers = new java.util.HashSet<>();

        for (InputCommand cmd : inputs) {
            if (cmd != null) {
                result.add(cmd);
                seenPlayers.add(cmd.playerId);
                if (!cmd.isEmpty()) {
                    lastKnownInputs.put(cmd.playerId, cmd);
                }
                // 空指令不更新 lastKnownInputs，保留上一帧的有效输入用于后续预测
            }
        }

        // 预测缺失的远程输入（getInput 返回 null 的玩家）
        for (Map.Entry<Integer, InputBuffer> entry : remoteInputBuffers.entrySet()) {
            int remotePlayerId = entry.getKey();
            if (!seenPlayers.contains(remotePlayerId)) {
                InputCommand predicted = lastKnownInputs.get(remotePlayerId);
                if (predicted != null) {
                    InputCommand copy = new InputCommand(frameNumber, remotePlayerId);
                    copy.up = predicted.up; copy.down = predicted.down;
                    copy.left = predicted.left; copy.right = predicted.right;
                    copy.punch = predicted.punch; copy.kick = predicted.kick;
                    copy.special = predicted.special;
                    result.add(copy);
                }
            }
        }

        return result;
    }

    /**
     * 检查并执行回滚
     */
    private void checkAndRollback(int currentFrame) {
        // 逐帧校验远程确认输入，避免 UDP 乱序导致的跳帧遗漏
        for (Map.Entry<Integer, InputBuffer> entry : remoteInputBuffers.entrySet()) {
            int remotePlayerId = entry.getKey();
            InputBuffer remoteBuf = entry.getValue();

            // 处理该远程玩家所有已到达但尚未校验的帧
            while (confirmedFrame < remoteBuf.getCurrentFrame()) {
                int checkFrame = confirmedFrame + 1;
                InputCommand confirmedInput = remoteBuf.getInput(checkFrame);
                if (confirmedInput == null) break; // 该帧尚未到达，等待

                InputCommand predictedInput = lastKnownInputs.get(remotePlayerId);
                if (predictedInput != null && !predictedInput.equals(confirmedInput)) {
                    // 预测错误，需要回滚
                    int rollbackFrames = Math.min(currentFrame - confirmedFrame, ROLLBACK_MAX_FRAMES);
                    rollback(currentFrame - rollbackFrames);
                    return; // 回滚后当前检测上下文失效，直接返回
                }

                confirmedFrame++;
            }
        }
    }

    /**
     * 执行回滚
     */
    private void rollback(int targetFrame) {
        System.out.println("回滚到帧: " + targetFrame);

        // 0. 保存当前帧号（rollbackTo 会将其重置为 targetFrame）
        int originalFrame = gameWorld.getCurrentFrame();

        // 1. 只回滚游戏世界状态——输入缓冲区不删，输入是既成事实
        gameWorld.rollbackTo(targetFrame);

        // 2. 从目标帧重新模拟（用已记录的输入重跑，缺失的远程输入由 predictInputs 预测）
        for (int frame = targetFrame; frame < originalFrame; frame++) {
            List<InputCommand> inputs = gatherInputs(frame);
            List<InputCommand> predicted = predictInputs(inputs, frame);
            gameWorld.update(predicted, frame);
        }
    }

    /**
     * 接收远程输入（由网络层调用）
     */
    public void receiveRemoteInput(InputCommand remoteCmd) {
        int playerId = remoteCmd.playerId;
        remoteInputBuffers.computeIfAbsent(playerId, k -> new InputBuffer(120))
                .addInput(remoteCmd);
    }
}