# Fyren — 自研帧同步格斗游戏

[![Java 17](https://img.shields.io/badge/Java-17-blue)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-orange)](https://maven.apache.org/)
[![libGDX 1.12](https://img.shields.io/badge/libGDX-1.12.1-red)](https://libgdx.com/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

个人全栈技术验证项目。从帧同步引擎到 P2P 打洞，从打击感到跨平台联机——所有轮子自己造。

**[🎮 网页体验版](https://uquc.github.io/Fyren/)** | **[⬇ 下载 Windows 版](https://github.com/uquc/Fyren/releases)**

## 🎯 特性

- **帧同步网络对战** — 乐观帧锁定 + GGPO 式回滚，最多回滚 10 帧
- **P2P UDP 打洞** — 纯 Java NAT 穿透，对称 NAT 下自动降级服务器中继
- **跨平台联机** — 桌面 (UDP+P2P) 和浏览器 (WebSocket) 共享匹配队列
- **打击感五件套** — 命中停帧、屏幕震动、粒子火花、受击闪烁、运动残影
- **3 角色 × 独特机制** — 影（取消技）、武（伤害充能）、刚（受击充能）
- **4 层视差背景** — 程序化生成：天空星月 → 远山剪影 → 竹林 → 草地
- **训练模式** — 帧数据显示、输入状态、角色即时切换
- **JWT 双 Token 认证** — access (15min) + refresh (7d)，服务端匹配鉴权
- **GWT/WebGL 网页版** — 编译到 JavaScript，点开即玩，支持联网

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+

### 构建

```bash
mvn compile -q        # 编译
mvn test -q           # 测试
mvn package -q        # Fat JAR (含所有依赖)
```

### 启动服务端

```bash
java -cp target/Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain server 9876
```

### 注册 / 登录

```bash
java -cp target/Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain register localhost <用户名> <密码>
java -cp target/Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain login localhost <用户名> <密码> --preset kage
```

### 客户端对战

```bash
# 联网对战
java -cp target/Fyren-1.0-SNAPSHOT.jar com.Fyren.GameMain client <服务器IP> 9876 <玩家ID> --preset kage

# 本地双人 Demo
java -cp target/classes com.Fyren.render.libgdx.FyrenLauncher demo --preset kage --preset2 takeshi
```

### Docker

```bash
cd docker && docker compose up -d
```

## 🏗️ 架构

```
客户端                         服务端                          存储
┌──────────────┐            ┌─────────────────┐          ┌──────────┐
│ libGDX 桌面   │── UDP ──→│  UdpServer :9876 │          │  Redis   │
│ (P2P 直连)    │←── P2P ──│  MatchManager    │←────────│  用户数据  │
└──────────────┘            │  FrameSync(R)    │          └──────────┘
                            │  P2P Relay       │
┌──────────────┐            │  WsGameServer    │          ┌──────────┐
│ WebGL/GWT    │── WS ────→│  :9878           │          │  Auth    │
│ (浏览器)      │←── WS ────│                  │          │  :8081   │
└──────────────┘            └─────────────────┘          └──────────┘
                                     │
                            ┌────────┴────────┐
                            │ Status API :8080 │
                            │ Deploy API :8081 │
                            └─────────────────┘
```

### 核心模块

| 模块 | 职责 |
|------|------|
| `game/` | 确定性格斗核心：状态机、帧数据、碰撞检测、快照 |
| `render/libgdx/` | libGDX 渲染层：SpriteRenderer、视差背景、打击特效、HUD |
| `network/` | 网络通信：UDP 客户端/服务端、WebSocket、P2P 打洞、包协议 |
| `sync/` | 帧同步引擎：锁步 + 预测执行 + 回滚 |
| `match/` | 匹配系统：ELO + 扩散窗口、防重复匹配 |
| `auth/` | JWT 双 Token 认证、HTTP API、bcrypt 密码哈希 |
| `redis/` | Redis 连接池、降级内存模式 |

### 设计决策

- **确定性模拟** — `GameWorld.update()` 按 playerId 排序输入，`strictfp` 保证一致性
- **GGPO 式回滚** — 乐观帧锁，远程输入校验失败时快照恢复 + 重演
- **P2P 降级** — 对称 NAT 下 2 秒超时自动回退服务器中继
- **跨协议匹配** — `MatchResponseSender` 接口解耦 UDP/WebSocket 传输层

## 🎮 操作

| 按键 | P1 | P2 |
|------|----|----|
| 移动 | WASD | 方向键 |
| 拳 | J | 1 |
| 脚 | K | 2 |
| 特殊技 | U | 3 |
| 防御 | L | — |
| 冲刺 | ←← / →→ | ←← / →→ |

### 训练模式

| 按键 | 功能 |
|------|------|
| 1/2/3 | 切换 P1 角色 |
| Shift+1/2/3 | 切换假人角色 |
| ESC | 返回标题 |

## 📦 分发

```bash
# Windows EXE (app-image)
mvn package -q
jpackage --input target/pkg-input --name Fyren \
  --main-jar Fyren-1.0-SNAPSHOT.jar \
  --main-class com.Fyren.render.libgdx.FyrenLauncher \
  --type app-image --java-options "-XstartOnFirstThread" \
  --dest target/pkg-out
# Zip: target/pkg-out/Fyren/ → Fyren-windows-x64.zip
```

## 🌐 部署 (ECS)

```bash
# HTTP 热部署（无需 RDP）
curl -X POST --data-binary @target/Fyren-1.0-SNAPSHOT.jar \
  http://<服务器IP>:8081/admin/deploy
```

## 📄 许可证

MIT License — 个人技术作品，仅供学习交流。

---

**[🌐 项目主页](https://uquc.github.io/Fyren/)** · **[GitHub](https://github.com/uquc/Fyren)** · **[Gitee](https://gitee.com/anchor-feather/fyren)**
