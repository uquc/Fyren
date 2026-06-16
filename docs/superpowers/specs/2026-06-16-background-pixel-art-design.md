# Fyren 背景美术设计 — 像素竹林/山水

**日期:** 2026-06-16
**状态:** 方向已确定，待素材获取+实现
**决策者:** 开发者

## 设计决策

### 风格: 像素风 (Pixel Art)

- 16×16 或 32×32 像素密度
- 2-3 层视差滚动（远景慢 / 近景快）
- 匹配现有火柴人渲染的简约风格

### 主题: 东方竹林/山水

- 远景: 山脉剪影 + 天空渐变
- 中景: 竹林（竹竿分段 + 竹叶）
- 近景: 地面（草地/石头/落叶）
- 色调: 暗系，与现有 `#0a0a0f` 背景色融合

### 美术要求

- **质量门槛:** 能用就行，不追求高品质
- **依赖:** 尽量零外部依赖（如素材不可用则纯代码生成）

## 素材来源

### 首选: Ninja Adventure (CC0)

- **来源:** pixel-boy [Ninja Adventure Asset Pack](https://pixel-boy.itch.io/ninja-adventure-asset-pack)
- **授权:** CC0 (Public Domain)
- **GitHub:** https://github.com/pixel-boy/NinjaAdventure
- **特点:** 16×16 像素，忍者/东方主题，含森林 tileset
- **问题:** itch.io 当前环境被墙，需通过 GitHub 镜像获取

### 备选 A: CraftPix 横向像素背景

- https://craftpix.net/sets/horizontal-pixel-art-backgrounds-collection/
- 横向卷轴背景，含森林/山脉主题
- 部分免费

### 备选 B: 纯代码生成

如果以上素材都不可用:
- 天空渐变 (暗蓝→暗紫)
- 远山三角形 (ShapeRenderer 填充)
- 竹竿 (分段矩形 + 随机偏移)
- 地面 (深色矩形 + 草地线条)

## 实现方案

### 新增文件: `BackgroundRenderer.java`

```java
// 位置: com.Fyren.render.libgdx.BackgroundRenderer
// 职责: 管理背景图层 (Texture/SpriteBatch)，渲染视差背景
// 接口:
//   render(SpriteBatch batch, float cameraX) — 绘制所有图层
//   dispose() — 释放纹理资源
```

### 渲染管线插入点

```
GameScreen.render()
  → BackgroundRenderer.render()   // ★ 新增，位于最底层
  → drawBackground (ground + grid) // 保留或替换
  → SpriteRenderer (角色)
  → HitEffects / ParticleEffects / MotionTrailEffect
  → HudRenderer
```

### 资源目录

- 像素素材: `assets/backgrounds/`
- 如需 tileset 拼合: 运行时或预处理均可

## 已知风险

- 素材获取受网络限制（itch.io 被墙）
- GWT 编译需要处理背景纹理加载路径
- 背景渲染不应影响帧率（保持简单）

## 关联文档

- CLAUDE.md — Current Session (2026-06-16)
- [[dev-current-session]] — 会话进度详情
- `.claude/agents/art-director.md` — 美术方向 agent
- `.claude/agents/technical-artist.md` — 技术美术 agent
