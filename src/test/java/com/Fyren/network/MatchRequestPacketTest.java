package com.Fyren.network;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MatchRequestPacket 序列化测试 — 含 JWT token 字段的往返验证。
 */
public class MatchRequestPacketTest {

    @Test
    public void roundtripWithoutToken() {
        MatchRequestPacket pkt = new MatchRequestPacket(1, 42, 1500, 0, "");
        ByteBuffer buf = ByteBuffer.allocate(256);
        pkt.writePayload(buf);
        buf.flip();

        MatchRequestPacket restored = MatchRequestPacket.fromBuffer(buf, 1);
        assertEquals(42, restored.playerId);
        assertEquals(1500, restored.playerRating);
        assertEquals(0, restored.presetOrdinal);
        assertEquals("", restored.jwtToken);
    }

    @Test
    public void roundtripWithToken() {
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0MiJ9.abc123";
        MatchRequestPacket pkt = new MatchRequestPacket(1, 42, 1500, 2, token);
        ByteBuffer buf = ByteBuffer.allocate(512);
        pkt.writePayload(buf);
        buf.flip();

        MatchRequestPacket restored = MatchRequestPacket.fromBuffer(buf, 1);
        assertEquals(42, restored.playerId);
        assertEquals(1500, restored.playerRating);
        assertEquals(2, restored.presetOrdinal);
        assertEquals(token, restored.jwtToken);
    }

    @Test
    public void backwardCompatibleOldFormat() {
        // 旧格式（无 token 字段）：12 字节 = 3 × int
        ByteBuffer buf = ByteBuffer.allocate(12);
        buf.putInt(99);   // playerId
        buf.putInt(1200); // rating
        buf.putInt(1);    // presetOrdinal
        buf.flip();

        MatchRequestPacket restored = MatchRequestPacket.fromBuffer(buf, 5);
        assertEquals(99, restored.playerId);
        assertEquals(1200, restored.playerRating);
        assertEquals(1, restored.presetOrdinal);
        assertEquals("", restored.jwtToken, "旧格式无 token 字段应返回空字符串");
    }

    @Test
    public void payloadSizeMatchesActual() {
        String token = "test.jwt.token";
        MatchRequestPacket pkt = new MatchRequestPacket(1, 1, 1000, 0, token);

        ByteBuffer buf = ByteBuffer.allocate(pkt.getPayloadSize());
        pkt.writePayload(buf);

        // 应该刚好填满，没有 BufferOverflow
        assertEquals(0, buf.remaining());
    }

    @Test
    public void rejectsOverlyLongToken() {
        // token 长度 > 2048 应被拒绝（防止恶意包）
        ByteBuffer buf = ByteBuffer.allocate(16);
        buf.putInt(1);    // playerId
        buf.putInt(1000); // rating
        buf.putInt(0);    // preset
        buf.putInt(9999); // tokenLen > 2048
        buf.flip();

        MatchRequestPacket restored = MatchRequestPacket.fromBuffer(buf, 1);
        assertEquals("", restored.jwtToken, "超长 token 应被拒绝");
    }

    @Test
    public void getPayloadSizeWithEmptyToken() {
        MatchRequestPacket pkt = new MatchRequestPacket(1, 1, 1000);
        // 12 (3 ints) + 4 (tokenLen=0) + 0 (no bytes) = 16
        assertEquals(16, pkt.getPayloadSize());
    }
}
