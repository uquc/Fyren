package com.Fyren.sync;

import java.util.*;

/**
 * 输入缓冲区 - 存储每个帧的输入指令
 * 支持回滚时重新获取历史输入
 */
public class InputBuffer {
    private final int bufferSize;
    private final Map<Integer, InputCommand> inputs = new HashMap<>();
    private int currentFrame = 0;

    public InputBuffer(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    /**
     * 添加输入指令
     */
    public synchronized void addInput(InputCommand cmd) {
        inputs.put(cmd.frameNumber, cmd);
    }

    /**
     * 获取指定帧的输入（可能为空）
     */
    public synchronized InputCommand getInput(int frameNumber) {
        return inputs.getOrDefault(frameNumber, createEmptyCommand(frameNumber));
    }

    /**
     * 获取所有已知输入（用于同步）
     */
    public synchronized Map<Integer, InputCommand> getAllInputs() {
        return new HashMap<>(inputs);
    }

    /**
     * 回滚到指定帧
     */
    public synchronized void rollbackTo(int frameNumber) {
        // 清除frameNumber之后的所有输入
        inputs.keySet().removeIf(frame -> frame > frameNumber);
        this.currentFrame = frameNumber;
    }

    /**
     * 推进当前帧
     */
    public synchronized void advanceFrame() {
        currentFrame++;
    }

    public synchronized int getCurrentFrame() {
        return currentFrame;
    }

    private InputCommand createEmptyCommand(int frameNumber) {
        InputCommand cmd = new InputCommand(frameNumber, -1);
        return cmd;
    }
}