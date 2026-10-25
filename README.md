# 工作岛 · WorkIsland

> **测试状态：Pre-release / Alpha**
> 目前**只在 OnePlus PJA110 · ColorOS 16.0.3 · Android 16（SDK 36）· cccodroid 0.4.2** 上测试成功。
> 其他 ColorOS 版本、其他品牌机型均未验证；接口与文件布局可能继续变动，不建议用于生产或关键任务。
> 详细边界见 [`STATUS.md`](STATUS.md)。

把 **cccodroid 里 Claude 的实时工作状态**，投到 **ColorOS 16 的流体云 / 灵动岛**上。

- 干活时：岛上写「正在干活」，下面跟着「用 执行命令…」「正在想…」「等你确认…」
- 收工：写「任务完成」+ 用时
- 8 秒后：岛上收起，通知栏只留一条安静的「工作岛 · 在后台等下一轮」

不是另开一个任务清单，也不是模拟动画——数据直接来自 cccodroid 网关的真实事件流。

---

## 交付物

| 路径 | 内容 |
|---|---|
| `apk/WorkIsland.apk` | 可直接安装的 APK（`com.dafeiyu.workisland`，versionName 1.0） |
| `source/app/` | Android 16 Live Update 应用源码（Java + 资源 + 构建脚本） |
| `source/driver/driver.mjs` | 状态驱动：订阅网关事件流，发开始/更新/完成/收起 |
| `source/gateway/work-island.mjs` | 网关启动时拉起 driver 的模块 |
| `source/gateway/server.mjs` | 已接入的网关入口（原文件见 `server.mjs.original`） |
| `screenshots/` | 工作态、完成态、收起、后台存活、应用界面截图 |
| `docs/` | 方案与设计、开发部署、测试报告、提交说明 |

APK 校验：

```
SHA-256  79697f3779e590412dc5eca43b948018f40d6f61f6ee1cddb725e8f85da2140e
大小     41,929 字节
```

---

## 快速开始

1. 安装 `apk/WorkIsland.apk`。
2. 打开「工作岛」，确认三行状态：
   - 通知：开
   - 实时更新：允许
   - 电池：不优化
3. cccodroid 重启后，网关会自动拉起 driver（首次接入见 `docs/02-开发与部署.md`）。
4. 在 cccodroid 里让 Claude 干一次活，看岛。

---

## 测试环境

| 项 | 值 |
|---|---|
| 设备 | OnePlus PJA110 |
| 系统 | ColorOS 16.0.3（Android 16 / SDK 36） |
| cccodroid | 0.4.2 |
| 网关 | 127.0.0.1:8791 |
| 应用 | com.dafeiyu.workisland 1.0 |
| 构建 | aapt2 2.20-android-16 / d8 9.2.4 / apksigner 0.9 / javac 21 |
| 编译 SDK | Android 16 platform-36（android.jar） |

---

## 一句话设计

> **网关负责“知道”，driver 负责“判断”，工作岛负责“画”。**
> 定时器和事件流不放在会被 ColorOS 冻结的应用里，放在活得比每一轮任务更久的网关进程里。

详细设计见 [`docs/01-方案与设计.md`](docs/01-方案与设计.md)。
