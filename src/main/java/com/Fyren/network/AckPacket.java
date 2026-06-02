package com.Fyren.network;

import java.nio.ByteBuffer;

/**
 * ACK确认包 - 用于可靠UDP传输的确认机制
 * 接收方收到可靠包后发送ACK，发送方收到ACK后停止重传
 */
public class AckPacket extends Packet {
    public int ackedSequence; // 被确认的包序列号

    public AckPacket(int sequence, int ackedSequence) {
        this.type = Type.ACK;
        this.sequence = sequence;
        this.ackedSequence = ackedSequence;
    }

    /**
     * 从缓冲区反序列化ACK包
     */
    public static AckPacket fromBuffer(ByteBuffer buf, int sequence) {
        int ackedSeq = buf.getInt();
        return new AckPacket(sequence, ackedSeq);
    }

    @Override
    protected void writePayload(ByteBuffer buf) {
        buf.putInt(ackedSequence);
    }

    @Override
    protected int getPayloadSize() {
        return 4; // 4字节(int) — 被确认的序列号
    }
}
