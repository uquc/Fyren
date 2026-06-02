package com.Fyren.network;

import java.nio.ByteBuffer;

public class InputPacket extends Packet {
    public int inputFlags; // 输入标志位
    public long timestamp; // 时间戳

    public InputPacket(int sequence, int inputFlags, long timestamp) {
        this.type = Type.INPUT;
        this.sequence = sequence;
        this.inputFlags = inputFlags;
        this.timestamp = timestamp;
    }

    public static InputPacket fromBuffer(ByteBuffer buf, int sequence) {
        int flags = buf.getInt();
        long timestamp = buf.getLong();
        return new InputPacket(sequence, flags, timestamp);
    }

    @Override
    protected void writePayload(ByteBuffer buf) {
        buf.putInt(inputFlags);
        buf.putLong(timestamp);
    }

    @Override
    protected int getPayloadSize() {
        return 12; // 4字节(inputFlags) + 8字节(timestamp)
    }
}
