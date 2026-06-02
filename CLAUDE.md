# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build (JDK 17+, Maven 3.8+)
mvn package -q

# Start server
java -cp target/classes com.Fyren.GameMain server 9876

# Start client (optional preset: kage|takeshi|gou)
java -cp target/classes com.Fyren.GameMain client <serverIp> [port] [playerId] --preset kage

# Local demo (no network, console I/O)
java -cp target/classes com.Fyren.GameMain demo
```

Only dependency is Lombok (provided scope). No test framework configured yet.

## Architecture

**Fyren** is a 2D fighting game with UDP networking, hidden-MMR matchmaking, and lockstep + rollback frame sync. Java 17+, Maven, single jar for both client and server.

```
com.Fyren
├── game/          Fighter, GameWorld, CollisionSystem, GameStateSnapshot, FighterPreset
├── network/       UdpClient, UdpServer, Packet subtypes (INPUT/STATE/HEARTBEAT/MATCH_REQ/MATCH_RES/ACK)
├── sync/          FrameSyncManager (lockstep + speculative execution + rollback),
│                  InputBuffer, InputCommand, InputCodec
├── match/         Matchmaker (ELO + diffusion window), MatchManager, PlayerRating
└── util/          InputCodec (bit-flag encoding of 7 inputs into int)

# Entry points
GameMain.java     — CLI router: server/client/demo modes
GameServer.java   — Wires UdpServer + MatchManager
GameClient.java   — Wires UdpClient + FrameSyncManager + GameWorld
```

**Key design decisions:**
- **No P2P yet** — all game input goes through server relay. MatchResponsePacket carries opponent address but client doesn't establish direct connection.
- **Deterministic simulation** — GameWorld.update() sorts inputs by playerId before processing. No floating-point RNG. Java `strictfp` keeps single-platform determinism.
- **Rollback netcode** — saves GameStateSnapshot every 10 frames, rolls back when confirmed input differs from prediction (max 10 frames).
- **Reliable vs unreliable channel** — ACK+retransmit for matchmaking/heartbeat, fire-and-forget for input packets (one dropped frame corrected by rollback).
- **Hidden MMR** — ELO with dynamic K-factor (64→32→16 by games played), diffusion window expands over wait time (50+5/sec, cap 400).
- **Binary protocol** — Packet serialization via `ByteBuffer`, 8-byte header (type + sequence), subclasses encode/decode their own payloads.

## Design Spec

Current design document: `docs/superpowers/specs/2026-06-02-fyren-render-and-fixes-design.md`
Covers: Java2D stick-figure rendering, 3-character preset system (影/武/刚), full combat system (frame data, hitbox/attack-box, hitstun, throw-breaks-guard, clash, dash with 3 charges), bug fixes for FrameSyncManager double-creation and input collection.

## File Change Rules

- PACKAGES TOUCH: `com.Fyren.game`, `com.Fyren.sync`, `com.Fyren.render` (new), `GameClient`, `GameMain`
- PACKAGES FREEZE: `com.Fyren.network.*`, `com.Fyren.match.*`, `InputBuffer`, `InputCodec`, `InputCommand`
- `GameStateSnapshot` may need new fields for timer/special-resource state; don't restructure it.

---

Behavioral guidelines to reduce common LLM coding mistakes:

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```
