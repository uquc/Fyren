package com.Fyren.network;

import java.nio.ByteBuffer;

public class HeartbeatPacket extends Packet {
    public long pingTime;

    public HeartbeatPacket(int sequence) {
        this(sequence, System.currentTimeMillis());
    }

    public HeartbeatPacket(int sequence, long pingTime) {
        this.type = Type.HEARTBEAT;
        this.sequence = sequence;
        this.pingTime = pingTime;
    }

    /**
     * 从缓冲区反序列化心跳包（用于接收端解析ping时间）
     */
    public static HeartbeatPacket fromBuffer(ByteBuffer buf, int sequence) {
        // 心跳包payload是对方发送的时间戳
        long pingTime = buf.getLong();
        return new HeartbeatPacket(sequence, pingTime);
    }

    @Override
    protected void writePayload(ByteBuffer buf) {
        buf.putLong(pingTime);
    }

    @Override
    protected int getPayloadSize() {
        return 8; // 8字节(long)
    }
}
