# 测试状态

> **当前状态：测试阶段（Pre-release / Alpha）**
>
> **目前只在一种设备组合上测试成功：**
> **OnePlus PJA110 · ColorOS 16.0.3 · Android 16（SDK 36）· cccodroid 0.4.2。**

项目仍在测试阶段：接口、通知文案、网关接入方式和文件布局都可能继续变动，**不建议用于生产或关键任务**。

---

## 已验证

- ColorOS 16 流体云能接住 Android 16 Live Update（`PROMOTED_ONGOING` 实测出现）
- 工作态「正在干活」、完成态「任务完成」、8 秒后收起
- 并行轮次结束不会误报完成
- driver 被杀后由守护循环/网关看门狗拉起
- cccodroid 与工作岛都退到后台 8 秒，driver、网关、前台服务仍存活
- 构建、签名、安装、通知授权在设备上通过

## 未验证

- 其他 ColorOS 版本（如 15、17）与其他机型
- 其他厂商的“灵动岛 / 实时活动”实现（HyperOS、OriginOS、One UI、MagicOS 等）
- 开发端网关（8790）与 cccodroid（8791）的差异场景
- 重启开机自启（`BOOT_COMPLETED` 已声明，未做整机重启验证）
- 长时间 Doze（30 分钟以上）
- force-stop 后的自动恢复
- 多轮并行时的文案归属（收工判定已按 `runs` 处理，文案可能跟随最后事件）

## 已知限制

- cccodroid 被强行停止后，网关与 driver 都会消失，岛上会停在最后状态；重开 cccodroid 恢复。
- ColorOS 会在后台冻结应用（`do_freezer_trap`，前台服务也冻），所以定时器放在网关 driver，而不是应用里。
- cccodroid 升级可能覆盖 `files/app` 下的网关文件，需要重新接入。
- 工作岛的服务是 exported 的，同机应用可以伪造显示状态（仅影响显示）；生产化需加签名级权限。
- 胶囊外观由系统绘制，未使用 OPPO UPK 自定义卡片。

## 反馈

如果你在别的 ColorOS 版本或机型上试了，欢迎带上：

- 机型、ColorOS 版本、Android 版本
- `dumpsys notification --noredact | grep -A1 "pkg=com.dafeiyu.workisland"` 的 flags 行
- 现象截图

这些信息足够判断是“没推广”“被冻结”还是“收工判定”的问题。
