package com.Fyren.network;

import java.nio.ByteBuffer;

public class StatePacket extends Packet {
    public float playerX;
    public float playerY;
    public float health;
    public long stateTimestamp;

    public StatePacket(int sequence, float playerX, float playerY, float health, long stateTimestamp) {
        this.type = Type.STATE;
        this.sequence = sequence;
        this.playerX = playerX;
        this.playerY = playerY;
        this.health = health;
        this.stateTimestamp = stateTimestamp;
    }

    public static StatePacket fromBuffer(ByteBuffer buf, int sequence) {
        float x = buf.getFloat();
        float y = buf.getFloat();
        float health = buf.getFloat();
        long timestamp = buf.getLong();
        return new StatePacket(sequence, x, y, health, timestamp);
    }

    @Override
    protected void writePayload(ByteBuffer buf) {
        buf.putFloat(playerX);
        buf.putFloat(playerY);
        buf.putFloat(health);
        buf.putLong(stateTimestamp);
    }

    @Override
    protected int getPayloadSize() {
        return 20; // 4*3字节(float) + 8字节(long)
    }
}
