# 工作岛 · WorkIsland

> **测试状态：Pre-release / Alpha**
> 目前**只在 OnePlus PJA110 · ColorOS 16.0.3 · Android 16（SDK 36）· cccodroid 0.4.2–0.4.6** 上测试成功。
> 其他 ColorOS 版本、其他品牌机型均未验证；接口与文件布局可能继续变动，不建议用于生产或关键任务。
> 详细边界见 [`STATUS.md`](STATUS.md)。

把 **cccodroid 里 Claude 的实时工作状态**，投到 **系统流体云 / 灵动岛**上；
没有这些系统能力的设备，用应用自己画的 **悬浮胶囊** 兜底。

- 干活时：胶囊标题跟着动作走——读文件 / 执行命令 / 思考中 / 等你确认；下面跟着更细的描述
- `TodoWrite` 任务清单会显示「清单 x/y」
- 收工：写「任务完成」+ 用时
- 8 秒后：岛上收起，通知栏只留一条安静的「工作岛 · 在后台等下一轮」
- **无岛也能看**：悬浮胶囊半透明、按内容伸缩、触摸完全穿透，不挡下面任何操作
- **大小可调**：字号、高度、垂直位置、不透明度都能拖；设置页里有实时预览
- **两个开关**：系统岛、悬浮胶囊各自决定是否开启；两个都关就只留普通通知
- **不杂乱**：设置页分成「状态 / 设置」两栏，卡片分组；状态栏、预览、开关、滑块各自归位
- **四态 bot**：Wakes / Works / Asks / Done；14 种 look、7 个头顶标识（`! ? ≡ × ⧗ … z`）
- **21 个模型**：每个 agent 有自己的形状、配色、眼睛，Claude 有手脚、DeepSeek 有肚子、Grok 有环、Cursor 缺一角、OpenClaw 有触须
- **可关**：设置里的「程序化 bot」开关关掉就退回旧的简笔图标
- **岛上不空**：折叠胶囊显示「思考中 1/4」这种状态 + 清单进度；展开后有计时、副标题和分段进度条
- **会动**：思考时头顶省略号逐帧跳、眼睛左右瞟、身体轻浮；每轮开始有 0.4 秒 logo→bot 的唤醒

不是另开一个任务清单，也不是模拟动画——数据直接来自 cccodroid 网关的真实事件流。

| 正在干活 | 任务完成 | 8 秒后收起 |
|---|---|---|
| <img src="screenshots/01-working.png" width="230"> | <img src="screenshots/02-done.png" width="230"> | <img src="screenshots/03-idle.png" width="230"> |

| 四态 bot（应用内预览） | 21 个模型 / 14 个 look | 通知里也带上标识 | 思考时的胶囊 |
|---|---|---|---|
| <img src="screenshots/09-bot-preview.png" width="210"> | <img src="screenshots/10-bot-states.png" width="210"> | <img src="screenshots/11-notification-bot.png" width="210"> | <img src="screenshots/13-island-thinking.png" width="210"> |

| 1.5 新设置页（状态 / 设置两栏） |
|---|
| <img src="screenshots/14-ui-settings.png" width="300"> |

更多截图见 `screenshots/`（含四态 bot、模型对照、岛上 `!` 标识、图标选择器、1.5 新设置页），完整边界见 [`STATUS.md`](STATUS.md)，方案见 [`docs/01-方案与设计.md`](docs/01-方案与设计.md)，UI 规则见 [`docs/08-UI与设计规范.md`](docs/08-UI与设计规范.md)。

**下载**：[Release 页面](https://github.com/STUTTEFRFIX/WorkIsland-Android/releases) · 1.5 的 APK 与 PDF 也在本仓库 `apk/`、`pdf/` 里。

---

## 交付物

| 路径 | 内容 |
|---|---|
| `apk/WorkIsland.apk` | 可直接安装的 APK（`com.dafeiyu.workisland`，versionName 1.5） |
| `source/app/` | Android 16 应用源码（Java + 资源 + 构建脚本；系统岛 + 悬浮胶囊） |
| `source/app/src/com/dafeiyu/workisland/IslandOverlay.java` | 无岛兜底悬浮窗（半透明、触摸穿透、可调位置） |
| `source/app/src/com/dafeiyu/workisland/CapsuleView.java` | 胶囊绘制（悬浮窗与设置页预览共用） |
| `source/app/src/com/dafeiyu/workisland/CapsuleStyle.java` | 字号 / 高度 / 位置 / 不透明度模型 |
| `source/app/libs/` | Shizuku API 13.1.5（api / provider / shared / aidl，Apache-2.0） |
| `source/driver/driver.mjs` | 独立状态驱动：0.4.2–0.4.6 的部署方式 |
| `source/driver/watchdog.mjs` | 保活看门狗：脱离网关进程树，15 秒检查并重拉 driver |
| `source/gateway/work-island.mjs` | 0.4.2–0.4.6 的网关模块（spawn 独立 driver） |
| `source/gateway/server.mjs` | 0.4.2–0.4.6 已接入的网关入口（原文件见 `server.mjs.original`） |
| `source/upstream-pr/` | 上游 PR #21 合并版 + #22 跟进（网关内订阅 / 清单 / state / 单测 19 条） |
| `source/upstream-pr-review.patch` | 上游 PR #21 的完整 diff |
| `source/upstream-pr-review-22.patch` | 上游 PR #22（OPEN）的完整 diff |
| `screenshots/` | 工作态、完成态、收起、后台存活、应用界面截图 |
| `docs/` | 方案与设计、开发部署、测试报告、提交说明、开发过程与真机测试、状态与保活、厂商适配 |

APK 校验：

```
SHA-256  9e2c983dc6f33fa478ae49e8f7600ce5a3e9a6522b1529a8624c93b57c4c6ff8
大小     379,625 字节
```

---

## 快速开始

1. 安装 `apk/WorkIsland.apk`。
2. 打开「工作岛」，确认状态：
   - 通知：开
   - 实时更新：允许（有流体云 / 灵动岛的设备）
   - 电池：不优化
   - **显示方式**：按设备打开「系统流体云 / 灵动岛」或「悬浮胶囊」；悬浮胶囊需要「显示在其他应用上层」权限
   - **大小与位置**：在设置页的「悬浮胶囊大小与位置」里拖字号、高度、垂直位置、不透明度；上面的预览会实时变化
   - 两个开关都关时只保留普通通知，不会上岛也不会画胶囊
3. 接入网关：
   - **ccc odroid 0.4.7+**：设置 → 工作岛（ColorOS 灵动岛）→ 打开，重启一次网关。
   - **0.4.2–0.4.6**：按 `docs/02-开发与部署.md` 第 4 节接入独立 driver。
4. 在 cccodroid 里让 Claude 干一次活，看岛。

---

## 测试环境

| 项 | 值 |
|---|---|
| 设备 | OnePlus PJA110 |
| 系统 | ColorOS 16.0.3（Android 16 / SDK 36） |
| cccodroid | 0.4.2 |
| 网关 | 127.0.0.1:8791 |
| 应用 | com.dafeiyu.workisland 1.5 |
| 构建 | aapt2 2.20-android-16 / d8 9.2.4 / apksigner 0.9 / javac 21 |
| 编译 SDK | Android 16 platform-36（android.jar） |

---

## 一句话设计

> **网关负责“知道”，模块负责“判断”，工作岛负责“画”。**
> 定时器和事件流不放在会被 ColorOS 冻结的应用里，放在网关进程里。

0.4.7 起上游改为进程内订阅（`onPublish` + `runList`），不再单独起 driver；本仓库的 `source/driver/` 是 0.4.2–0.4.6 的部署方式。两种方式发送给同一个应用。

详细设计见 [`docs/01-方案与设计.md`](docs/01-方案与设计.md)。

---

## 参考与致谢

- [jazzenchen/agent-hud-open](https://github.com/jazzenchen/agent-hud-open)
  （Apache-2.0）：四态 bot、14 look、7 个标识、BotSpec 与渲染几何的移植来源。
- [cccodroid](https://github.com/greyoak111/cccodroid)：网关事件流、终端与宿主能力。
- [CurrentMusicX](https://github.com/bileizhen/CurrentMusicX)（GPL-3.0）：
  悬浮胶囊的窗口分层、可见性策略、「视觉窗口 + 独立触摸层」思路，
  以及字号 / 垂直位置 / 不透明度和实时预览的设置模型参考。
  **没有复制其 Kotlin / Compose 代码**；本项目的 Java + Canvas 实现是独立重写的，
  因此仍按 Apache-2.0 分发。
- [LobeHub Icons](https://github.com/lobehub/lobe-icons)（MIT）：21 个模型图标；
  商标归各自所有者，非官方关联。

---

## 许可与第三方说明

本项目使用 **Apache-2.0**，见 [`LICENSE`](LICENSE)。

第三方素材与许可：

- Agent HUD 图标与 bot 模型：Apache-2.0
- LobeHub 模型图标：MIT（商标归各自所有者）
- Shizuku API：Apache-2.0
- CurrentMusicX：GPL-3.0，仅作悬浮胶囊的**设计参考**，没有复制其代码；
  许可全文见 [`LICENSES/CurrentMusicX-GPL-3.0.txt`](LICENSES/CurrentMusicX-GPL-3.0.txt)

详见 [`THIRD_PARTY.md`](THIRD_PARTY.md) 与 [`LICENSES/`](LICENSES/)。

**项目声明**：工作岛是独立的第三方项目，与 Anthropic、OPPO / 一加、
CurrentMusicX、LobeHub、cccodroid 官方均无隶属、赞助或背书关系；
其他产品名、模型名与图标归各自所有者。

> 工作岛是**第三方应用的可选集成**：cccodroid 只负责把状态送出来，不包含也不负责本应用的功能。
