package com.Fyren.network;

import java.nio.ByteBuffer;

/**
 * P2P 打洞包 — 仅含包头(8字节)，无 payload。
 * 包本身的到达即完成了 NAT 映射建立。
 * 区分 P2P_PING（打洞请求）和 P2P_PONG（打洞应答）。
 */
public class P2pPacket extends Packet {

    public P2pPacket(int sequence, Type type) {
        this.type = type;
        this.sequence = sequence;
    }

    public static P2pPacket fromBuffer(ByteBuffer buf, int sequence, Type type) {
        return new P2pPacket(sequence, type);
    }

    @Override
    protected void writePayload(ByteBuffer buf) {
        // 无 payload
    }

    @Override
    protected int getPayloadSize() {
        return 0;
    }

    @Override
    public String toString() {
        return "P2P-" + (type == Type.P2P_PING ? "PING" : "PONG") + "(seq=" + sequence + ")";
    }
}
