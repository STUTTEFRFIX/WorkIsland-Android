# 第三方素材与许可

## agent-hud-open（图标 + bot 模型）

- 来源：[jazzenchen/agent-hud-open](https://github.com/jazzenchen/agent-hud-open)
- 许可：Apache-2.0，全文见
  [`LICENSES/agent-hud-open-LICENSE.txt`](LICENSES/agent-hud-open-LICENSE.txt)。
- 用途：
  - 工作岛的启动图标，以及岛上「Agent HUD」选项的默认图标；
  - 1.4 起，四态 bot 的模型也移植自该项目：`BotState`（14 look / 四态）、
    `BotSigns`（5×7 点阵标识）、`BotSpec`（每个 agent 的形状/配色/眼睛/鳍）、
    `BotForm`（360 点轮廓 / 60×60 配色场）、`BotRenderer`（身体、装饰、表情）。
  - 移植的是行为与几何常量，语言从 Swift/TypeScript 改为 Java，运行在 Android Canvas 上。

## Shizuku API（保活授权）

- 来源：[RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) 的 API 13.1.5
  （`api` / `provider` / `shared` / `aidl` 四个 jar，放在 `source/app/libs/`）。
- 许可：Apache-2.0，全文见
  [`LICENSES/Shizuku-API-LICENSE.txt`](LICENSES/Shizuku-API-LICENSE.txt)。
- 用途：应用内申请 Shizuku 授权、设置页显示授权状态；保活链用它执行 shell 身份的操作
  （appops、拉起服务、force-stop 后重拉）。

## CurrentMusicX（悬浮胶囊设计参考，未复制代码）

- 来源：[bileizhen/CurrentMusicX](https://github.com/bileizhen/CurrentMusicX)
- 许可：GPL-3.0，全文见
  [`LICENSES/CurrentMusicX-GPL-3.0.txt`](LICENSES/CurrentMusicX-GPL-3.0.txt)。
- 用途（1.5 悬浮胶囊）：
  - 窗口分层与 `TYPE_APPLICATION_OVERLAY` 的用法；
  - 「屏幕亮 + 未锁屏 + 应用在后台」的可见性策略；
  - 「视觉窗口 + 独立触摸层」的思路（本项目改为完全触摸穿透）；
  - 字号 / 垂直位置 / 不透明度设置模型与实时预览的交互参考。
- **声明**：本仓库**没有复制** CurrentMusicX 的 Kotlin / Compose 代码。
  `IslandOverlay` / `CapsuleView` / `CapsuleStyle` 是用 Java + Canvas 独立重写的；
  工作岛整体仍按 Apache-2.0 分发。GPL-3.0 全文随附，仅用于标明参考项目的许可。
- 感谢 CurrentMusicX 的作者与贡献者。

## cccodroid（宿主，未捆绑）

- 来源：[greyoak111/cccodroid](https://github.com/greyoak111/cccodroid)
- 用途：工作岛作为 cccodroid 的可选集成，订阅其网关事件流并把 Claude 的工作状态画到岛上；
  本仓库不包含 cccodroid 的源码，只提供网关接入补丁与 driver。
- 感谢 cccodroid 的作者与贡献者。

## 模型图标（21 个）

- 来源：`agent-hud-open` 的 `src/packages/hud/src/assets/*.png`，原始图标来自
  [LobeHub](https://github.com/lobehub/lobe-icons)。
- 许可：MIT，全文见 [`LICENSES/LobeIcons-LICENSE.txt`](LICENSES/LobeIcons-LICENSE.txt)。
- DeepSeek、Claude、OpenAI、Grok、Cursor、GitHub Copilot、Antigravity、Kimi、Qwen、
  OpenCode、Pi、Hermes、GLM、ZCode、Qoder、CodeBuddy、WorkBuddy、OpenClaw、Devin
  等标识归各自所有者；这里只用于**标识当前模型**，不代表任何官方关联或背书。
- Claude 图标是可选项，默认图标是 Agent HUD；上游 cccodroid 的集成不包含这些图标。

## 未使用

- `clawd-sprites.json` 是 Anthropic PBC 的美术素材（all rights reserved），
  本仓库**没有**包含或使用它。

## 项目声明

- 工作岛是独立的第三方项目，与 Anthropic、OPPO / 一加、CurrentMusicX、LobeHub、
  cccodroid 官方均无隶属、赞助或背书关系。
- 「工作岛」「WorkIsland」是本项目的名称；文中出现的其他产品名、模型名与图标
  归各自所有者，仅用于说明兼容性或标识当前模型。
- 系统流体云 / 灵动岛由厂商系统绘制；悬浮胶囊由本应用自己绘制，二者都不代表厂商官方功能。
- 本项目按 Apache-2.0 分发；第三方素材与参考项目按各自的许可使用，详见上文与
  [`LICENSES/`](LICENSES/)。

## 说明

模型图标的商标权与图标文件的 MIT 许可是两回事：MIT 允许复制图标文件，但不授予商标使用权。
如果你要公开发布带这些图标的版本，请自行确认商标使用是否合适，或改用自定义图标。
