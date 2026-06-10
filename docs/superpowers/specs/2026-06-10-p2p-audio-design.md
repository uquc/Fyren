# P2P UDP Hole Punch + Audio System Design

**Date:** 2026-06-10  
**Priority:** P0  
**Status:** approved → implementation

---

## Part 1: P2P UDP Hole Punch

### Motivation

Currently all game input flows through server relay: `P1 → Server → P2`. This doubles latency compared to direct P2P. For a fighting game, every frame matters.

### Approach: UDP Hole Punch + Relay Fallback

After matchmaking, both clients punch UDP holes toward each other. If the punch succeeds (~80% of home NATs), input flows directly. If it fails (symmetric NAT, ~20%), fall back to server relay.

### New Type: `P2PHandshake.java`

```
+------------------+
| P2PHandshake     |
+------------------+
| - udpClient      |
| - opponentAddr   |
| - punching: bool |
| - established    |
+------------------+
| + start(addr)    |  ← called from GameClient.onMatchFound
| + isReady():bool |
| + stop()         |
+------------------+
```

**Handshake protocol:**

1. `P2PHandshake.start(opponentAddress)` spawns a thread
2. Thread sends 10 P2P_PING packets (20ms interval) to opponent's public address
3. UdpClient.receiveLoop detects incoming P2P_PING → replies P2P_PONG, marks `punchSuccess = true`
4. If P2P_PONG received within 2s → P2P established → `isReady() = true`
5. If timeout → `isReady() = false` → caller falls back to relay
6. During handshake, game data continues via relay (no interruption)

### Packet Changes

```java
// Packet.java — new types:
P2P_PING,     // NAT hole punch request
P2P_PONG,     // NAT hole punch reply
```

P2P_PING/P2P_PONG are minimal: header only (8 bytes), no payload. The packet itself IS the hole punch — the UDP source address mapping is what matters.

### `UdpClient` Changes

```java
// New fields
private InetSocketAddress p2pAddress;
private volatile boolean p2pActive = false;

// New: send input directly to opponent
public void sendP2P(InputPacket packet) { ... }

// Modified: sendInputToOpponent routes via P2P or relay
public void sendInputToOpponent(InputCommand cmd) {
    InputPacket packet = InputCodec.encode(cmd, nextSequence());
    if (p2pActive && p2pAddress != null) {
        sendP2P(packet);       // direct
    } else {
        sendReliable(packet);  // relay
    }
}

// New: enable P2P after successful handshake  
public void enableP2P(InetSocketAddress addr) {
    this.p2pAddress = addr;
    this.p2pActive = true;
}

// receiveLoop: handle incoming P2P_PING/P2P_PONG
```

### `GameClient` Changes

```java
// onMatchFound: after successful match, start P2P handshake
P2PHandshake handshake = new P2PHandshake(udpClient);
handshake.start(opponentAddress);  // async

// After handshake completes:
// if (handshake.isReady()) udpClient.enableP2P(opponentAddress);
// else: relay continues automatically
```

### Scope

| File | Action | Lines |
|------|--------|-------|
| `network/P2PHandshake.java` | **NEW** — handshake logic | ~80 |
| `network/Packet.java` | Add P2P_PING, P2P_PONG types | ~10 |
| `network/UdpClient.java` | P2P send path, receive PING/PONG | ~35 |
| `GameClient.java` | Trigger handshake on match | ~15 |
| **Total** | | **~140** |

### Error Handling

- Handshake timeout → relay fallback (invisible to player)
- P2P packet loss during game → unreliable delivery anyway (same as relay mode)
- P2P connection drops mid-game → not handled in this pass (player disconnects → game ends)

---

## Part 2: Audio System

### Motivation

AudioManager is a skeleton. No sound effects play. Fighting games rely on audio feedback for hit impact, special moves, and game tension.

### Approach: libGDX Sound API + CC0 WAV Files

Load 6 CC0-licensed WAV sound effects via `Gdx.audio.newSound()`. Play them at trigger points in the game loop. Graceful degradation: if files missing or Gdx.audio unavailable (GWT), log once and run silently.

### Sound Effects

| File | Trigger | Est. Size |
|------|---------|-----------|
| `assets/sounds/hit_light.wav` | damage ≤ 15 | ~50KB |
| `assets/sounds/hit_heavy.wav` | damage > 15 | ~60KB |
| `assets/sounds/special.wav` | special attack (U key) | ~80KB |
| `assets/sounds/dash.wav` | double-tap dash | ~30KB |
| `assets/sounds/block.wav` | blocked attack | ~40KB |
| `assets/sounds/ko.wav` | fighter health ≤ 0 | ~100KB |

Total: ~360KB of audio assets.

### `AudioManager.java` — Fill Implementation

```java
public class AudioManager implements Disposable {
    private Sound hitLight, hitHeavy, special, dash, block, ko;
    private boolean enabled;

    public AudioManager() {
        try { /* load 6 sounds from assets/sounds/ */ }
        catch { enabled = false; log once; }
    }

    public void playHitSound(int damage) {
        if (!enabled) return;
        (damage > 15 ? hitHeavy : hitLight).play(1.0f);
    }
    // ... playSpecialSound(), playDashSound(), playBlockSound(), playKoSound()
}
```

### Trigger Points

```java
// GameScreen.updateDemo / updateNetwork:
if (dmg > 0) {
    boolean blocked = (victim.getLastActionState() == BLOCK);
    audio.playHitSound(victim.getLastRawDamageReceived());
    if (blocked) audio.playBlockSound();
}
// KO checked in game loop:
if (gw.isGameOver() && !koPlayed) { audio.playKoSound(); koPlayed = true; }

// GameWorld update path: Fighter.startDash() triggers audio via 
// a callback registered by GameScreen
```

### Callback for Fighter → Audio Decoupling

```java
// GameWorld — simple callback (not a file change, just a new field):
public interface AudioCallback {
    void onDash(Fighter user);
    void onSpecial(Fighter user);
}
// Set in GameScreen.createDemo / createNetwork
```

### Scope

| File | Action | Lines |
|------|--------|-------|
| `AudioManager.java` | Fill implementation | ~50 |
| `GameScreen.java` | Wire triggers | ~20 |
| `Fighter.java` | Call callback on dash/special | ~10 (2 call sites) |
| `assets/sounds/*.wav` | 6 CC0 sound files | NEW |
| **Total** | | **~80** |

### Error Handling

- Sound files missing → `enabled = false`, no crash, single warning log
- `Gdx.audio == null` (GWT) → `enabled = false`, no crash
- Sound play failure → catch RuntimeException, disable that sound

---

## Implementation Order

1. Audio system (simpler, self-contained) — verify with demo mode
2. P2P handshake — verify with local two-client test
3. Integration test — P2P + audio in network mode

## Verification

- **Audio:** Start demo mode, press J/K/U, hear sounds
- **P2P:** Start server + 2 clients, check logs for "P2P established" vs "P2P failed, relay"
- **Regression:** `mvn test` passes, GWT compiles cleanly
