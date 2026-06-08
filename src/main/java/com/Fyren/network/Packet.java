// Packet.java
package com.Fyren.network;

import java.nio.ByteBuffer;

/**
 * 网络数据包基类 - 使用简单二进制协议
 */
public abstract class Packet {
    public static final int HEADER_SIZE = 8; // 4字节类型 + 4字节序列号

    public enum Type {
        INPUT(1),      // 操作指令
        STATE(2),      // 游戏状态
        HEARTBEAT(3),  // 心跳
        MATCH_REQ(4),  // 匹配请求
        MATCH_RES(5),  // 匹配结果
        ACK(6),        // 确认包
        RESULT(7);     // 比赛结果

        public final int code;
        Type(int code) { this.code = code; }
        public static Type fromCode(int code) {
            for (Type t : values()) if (t.code == code) return t;
            return null;
        }
    }

    public Type type;
    public int sequence; // 序列号，用于处理乱序和丢包

    // 序列化，发数据
    public byte[] serialize() {
        ByteBuffer buf = ByteBuffer.allocate(getPayloadSize() + HEADER_SIZE);
        buf.putInt(type.code);
        buf.putInt(sequence);
        writePayload(buf);
        return buf.array();
    }

    // 反序列化，收数据
    public static Packet deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int typeCode = buf.getInt();
        int seq = buf.getInt();
        Type type = Type.fromCode(typeCode);
        if (type == null) return null;

        switch (type) {
            case INPUT: return InputPacket.fromBuffer(buf, seq);
            case STATE: return StatePacket.fromBuffer(buf, seq);
            case HEARTBEAT: return HeartbeatPacket.fromBuffer(buf, seq);
            case MATCH_REQ: return MatchRequestPacket.fromBuffer(buf, seq);
            case MATCH_RES: return MatchResponsePacket.fromBuffer(buf, seq);
            case ACK: return AckPacket.fromBuffer(buf, seq);
            case RESULT: return ResultPacket.fromBuffer(buf, seq);
            default: return null;
        }
    }

    protected abstract void writePayload(ByteBuffer buf);
    protected abstract int getPayloadSize();
}