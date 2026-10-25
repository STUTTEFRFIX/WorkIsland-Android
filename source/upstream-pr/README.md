# 上游集成完整源码（PR #21）

这些文件来自 cccodroid 上游 PR #21 的分支 `feat/work-island-signed`：

https://github.com/greyoak111/cccodroid/pull/21

包含：

- `gateway/gw/work-island.mjs` / `work-island-core.mjs`：网关内订阅与纯逻辑核心
- `gateway/gw/bus.mjs` / `settings.mjs`：`onPublish` 钩子、`workIsland` 开关
- `gateway/server.mjs`：启动接入与 SIGTERM 收尾
- `gateway/web/index.html` / `app/12-model-caps.js`：设置页复选框
- `gateway/tests/work-island.test.mjs`：11 个单测
- `android/AndroidManifest.xml`：`<queries>` 可见性
- `docs/work-island.md` / `work-island.zh-CN.md`：上游文档

完整 diff 见 `../upstream-pr-review.patch`。上游项目与这些文件使用 Apache-2.0。
