package com.dafeiyu.workisland;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int SHIZUKU_REQUEST = 42;

    // MD3 深色 + 陶土主色。禁纯黑/纯白，用暖灰层次区分表面。
    private static final int BG = 0xFF121110;
    private static final int SURFACE = 0xFF1C1A18;
    private static final int CARD = 0xFF26231F;
    private static final int STROKE = 0xFF3A3631;
    private static final int FG = 0xFFF3EFE9;
    private static final int MUTED = 0xFFB6AEA3;
    private static final int FAINT = 0xFF8A837A;
    private static final int ACCENT = 0xFFD97757;
    private static final int ON_ACCENT = 0xFF1A0F0B;
    private static final int ACCENT_SOFT = 0xFF3A2A22;
    private static final int GREEN = 0xFF78C58A;
    private static final int RED = 0xFFE5484D;
    private static final int YELLOW = 0xFFE6A23C;

    private static final int PREVIEW_HERO_DP = 168;
    private static final int PREVIEW_SETTINGS_DP = 148;

    private static final String[] LOOK_NAMES = {
            "待命（醒来）", "干活", "执行命令", "读文件", "写文件", "长命令",
            "思考中", "等你确认", "有问题", "整理清单", "压缩上下文",
            "睡着了", "没有新消息", "完成", "出错（×）"
    };
    private static final BotState.Look[] LOOKS = {
            BotState.Look.IDLE, BotState.Look.PLAIN, BotState.Look.TOOL_COMMAND,
            BotState.Look.TOOL_READ, BotState.Look.TOOL_EDIT, BotState.Look.TOOL_LONG_COMMAND,
            BotState.Look.THINKING, BotState.Look.PERMISSION, BotState.Look.QUESTION,
            BotState.Look.PLAN, BotState.Look.COMPACTING, BotState.Look.ASLEEP,
            BotState.Look.NO_NEWS, BotState.Look.DONE, BotState.Look.PLAIN
    };
    private static final boolean[] LOOK_FAILED = {
            false, false, false, false, false, false, false, false, false,
            false, false, false, false, false, true
    };

    private TextView status;
    private TextView shizukuStatus;
    private TextView capsuleStatus;
    private TextView botLabel;
    private ScrollView statusScroll;
    private ScrollView settingsScroll;
    private TextView tabStatus;
    private TextView tabSettings;
    private TextView[] animButtons;
    private boolean advancedOpen = false;

    private CapsuleStyle editCapsuleStyle;
    private CapsuleView capsulePreview;
    private CapsuleView capsuleHeroPreview;
    private FrameLayout capsulePreviewScreen;
    private FrameLayout capsuleHeroScreen;
    private SeekBar capsuleTextBar;
    private SeekBar capsuleHeightBar;
    private SeekBar capsuleYBar;
    private SeekBar capsuleOpacityBar;

    private boolean askedShizuku = false;
    private final Shizuku.OnRequestPermissionResultListener shizukuListener =
            (requestCode, grantResult) -> {
                if (requestCode == SHIZUKU_REQUEST) refreshShizukuStatus();
            };
    private BotView botView;
    private String botKey = "hud";
    private BotState.Look botLook = BotState.Look.TOOL_COMMAND;
    private boolean botFailed = false;
    private boolean sheetMode = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        // targetSdk 36 强制 edge-to-edge；用 WindowInsets 自己让出状态栏/导航栏。

        if (getIntent() != null && getIntent().getBooleanExtra("sheet", false)) {
            sheetMode = true;
            showSheets();
            return;
        }

        editCapsuleStyle = Prefs.capsuleStyle(this);
        botKey = Prefs.icon(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        buildTopBar(root);
        buildTabs(root);

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        statusScroll = new ScrollView(this);
        statusScroll.setFillViewport(true);
        LinearLayout statusColumn = column();
        statusScroll.addView(statusColumn);
        content.addView(statusScroll, new FrameLayout.LayoutParams(-1, -1));

        settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(true);
        LinearLayout settingsColumn = column();
        settingsScroll.addView(settingsColumn);
        content.addView(settingsScroll, new FrameLayout.LayoutParams(-1, -1));

        buildStatusTab(statusColumn);
        buildSettingsTab(settingsColumn);
        showTab(0);
        applyWindowInsets(root);

        setContentView(root);

        ensureService();
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
        botKey = Prefs.icon(this);
        if (botView != null) botView.setBot(botKey, botLook, botFailed);
        Prefs.writeAnimTier(this);
        Prefs.writeKeepAlive(this);
        refreshLabels();
        refresh();

        try { Shizuku.addRequestPermissionResultListener(shizukuListener); } catch (Throwable ignored) {}
        refreshShizukuStatus();
        if (Prefs.keepAlive(this) && !shizukuGranted() && !askedShizuku) {
            askedShizuku = true;
            if (shizukuStatus != null) shizukuStatus.post(this::requestShizuku);
        }
    }

    // ── 页面骨架 ──────────────────────────────────────────────────────────

    private void buildTopBar(LinearLayout root) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(20), dp(16), dp(20), dp(8));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = medium("工作岛", 26, FG);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView version = text("1.5", 12, MUTED);
        version.setGravity(Gravity.CENTER);
        version.setPadding(dp(10), dp(4), dp(10), dp(4));
        version.setBackground(shape(SURFACE, 12));
        titleRow.addView(version);
        bar.addView(titleRow);

        TextView sub = text("Claude 的工作状态", 14, MUTED);
        sub.setLineSpacing(dp(2), 1.4f);
        bar.addView(sub, margin(0, 2, 0, 0));
        root.addView(bar);
    }

    private void buildTabs(LinearLayout root) {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackground(shape(SURFACE, 24));
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(56));
        p.setMargins(dp(20), dp(4), dp(20), dp(12));
        root.addView(tabs, p);

        tabStatus = tab("状态");
        tabSettings = tab("设置");
        tabStatus.setOnClickListener(v -> showTab(0));
        tabSettings.setOnClickListener(v -> showTab(1));
        tabs.addView(tabStatus, new LinearLayout.LayoutParams(0, -1, 1f));
        tabs.addView(tabSettings, new LinearLayout.LayoutParams(0, -1, 1f));
    }

    private TextView tab(String label) {
        TextView t = medium(label, 14, MUTED);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setFocusable(true);
        t.setContentDescription(label + "页");
        return t;
    }

    private void showTab(int index) {
        if (statusScroll == null || settingsScroll == null) return;
        statusScroll.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        settingsScroll.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        styleTab(tabStatus, index == 0);
        styleTab(tabSettings, index == 1);
        (index == 0 ? statusScroll : settingsScroll).scrollTo(0, 0);
    }

    private void styleTab(TextView tab, boolean selected) {
        if (tab == null) return;
        tab.setTextColor(selected ? FG : MUTED);
        tab.setBackground(selected ? shape(ACCENT_SOFT, 20) : null);
        tab.setSelected(selected);
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(4), dp(20), dp(24));
        return c;
    }

    /** 让出状态栏 / 导航栏 / 挖孔；targetSdk 36 下 setDecorFitsSystemWindows 已不保证生效。 */
    private void applyWindowInsets(View view) {
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
    }

    // ── 状态页 ────────────────────────────────────────────────────────────

    private void buildStatusTab(LinearLayout col) {
        LinearLayout body = card(col, "胶囊预览", "样式预览；实际显示方式在设置里选择");
        capsuleHeroScreen = createCapsulePreview(PREVIEW_HERO_DP, true);
        body.addView(capsuleHeroScreen, new LinearLayout.LayoutParams(-1, dp(PREVIEW_HERO_DP)));

        status = text("", 13, MUTED);
        status.setLineSpacing(dp(3), 1.45f);
        body.addView(status, margin(0, 12, 0, 0));

        Button test = primaryButton("开始一个测试任务", v -> demo(Notifier.ACTION_START));
        col.addView(test, margin(0, 0, 0, 6));

        TextView note = text("开始后回到桌面，按当前显示方式查看。", 12, FAINT);
        note.setLineSpacing(dp(2), 1.4f);
        col.addView(note, margin(0, 0, 0, 20));
    }

    // ── 设置页 ────────────────────────────────────────────────────────────

    private void buildSettingsTab(LinearLayout col) {
        buildDisplayCard(col);
        buildCapsuleStyleCard(col);
        buildAppearanceCard(col);
        buildKeepAliveCard(col);
        buildAdvancedCard(col);

        TextView footer = text("工作岛 1.5 · Apache-2.0 · 模型图标归各自所有者", 11, FAINT);
        footer.setLineSpacing(dp(2), 1.4f);
        col.addView(footer, margin(0, 8, 0, 0));
    }

    private void buildDisplayCard(LinearLayout col) {
        LinearLayout body = card(col, "显示方式", "按设备选择；两个都关只留普通通知");

        switchRow(body, "系统流体云 / 灵动岛",
                "有岛设备使用；关闭后不再请求实时更新",
                Prefs.systemIsland(this), (view, checked) -> {
                    Prefs.setSystemIsland(this, checked);
                    refresh();
                    notifyServiceModeChanged();
                });
        divider(body);

        switchRow(body, "悬浮胶囊",
                "无岛设备使用；半透明、触摸穿透",
                Prefs.capsuleEnabled(this), (view, checked) -> {
                    Prefs.setCapsuleEnabled(this, checked);
                    refresh();
                    notifyServiceModeChanged();
                });
        divider(body);

        capsuleStatus = valueRow(body, "悬浮窗权限",
                "允许胶囊显示在其他应用上层", "", ACCENT,
                view -> requestOverlayPermission());
        refreshCapsuleStatus();

        TextView note = text("没有流体云 / 灵动岛的设备用悬浮胶囊兜底；系统岛正常时建议只开一个。",
                12, FAINT);
        note.setLineSpacing(dp(2), 1.4f);
        body.addView(note, margin(0, 8, 0, 0));
    }

    private void buildCapsuleStyleCard(LinearLayout col) {
        LinearLayout body = card(col, "胶囊样式", "拖动即时预览；松手后保存");

        capsulePreviewScreen = createCapsulePreview(PREVIEW_SETTINGS_DP, false);
        body.addView(capsulePreviewScreen, new LinearLayout.LayoutParams(-1, dp(PREVIEW_SETTINGS_DP)));

        capsuleTextBar = addCapsuleSlider(body, "字号", 9f, 16f, .5f, editCapsuleStyle.textSp,
                value -> editCapsuleStyle = new CapsuleStyle(value, editCapsuleStyle.heightDp,
                        editCapsuleStyle.y, editCapsuleStyle.opacity),
                value -> String.format(Locale.US, "%.1f sp", value));

        capsuleHeightBar = addCapsuleSlider(body, "高度", 32f, 64f, 1f, editCapsuleStyle.heightDp,
                value -> editCapsuleStyle = new CapsuleStyle(editCapsuleStyle.textSp, value,
                        editCapsuleStyle.y, editCapsuleStyle.opacity),
                value -> String.format(Locale.US, "%d dp", Math.round(value)));

        capsuleYBar = addCapsuleSlider(body, "垂直位置", 0f, 80f, 1f, editCapsuleStyle.y * 100f,
                value -> editCapsuleStyle = new CapsuleStyle(editCapsuleStyle.textSp,
                        editCapsuleStyle.heightDp, value / 100f, editCapsuleStyle.opacity),
                value -> String.format(Locale.US, "%d%%", Math.round(value)));

        capsuleOpacityBar = addCapsuleSlider(body, "不透明度", 35f, 80f, 1f,
                editCapsuleStyle.opacity * 100f,
                value -> editCapsuleStyle = new CapsuleStyle(editCapsuleStyle.textSp,
                        editCapsuleStyle.heightDp, editCapsuleStyle.y, value / 100f),
                value -> String.format(Locale.US, "%d%%", Math.round(value)));

        TextView reset = textButton("恢复默认", view -> resetCapsuleStyle());
        body.addView(reset, margin(0, 4, 0, 0));

        updateCapsulePreview();
    }

    private void buildAppearanceCard(LinearLayout col) {
        LinearLayout body = card(col, "外观", "岛上图标、bot 与动画");

        switchRow(body, "程序化 bot",
                "四态 + 标识 + 体征；关闭退回简笔图标",
                Prefs.soul(this), (view, checked) -> {
                    Prefs.setSoul(this, checked);
                    refreshLabels();
                    updateCapsulePreview();
                    try {
                        startForegroundService(new Intent(this, IslandService.class)
                                .setAction(IslandService.ACTION_IDLE));
                    } catch (Throwable ignored) {}
                });
        divider(body);

        body.addView(rowTitle("岛上动画", "应用内预览始终 60fps"));
        body.addView(animSegmented(), margin(0, 0, 0, 8));
        divider(body);

        // bot 预览先建，后面的图标 spinner 回调要用。
        botView = new BotView(this);
        botView.setBot(botKey, botLook, botFailed);
        botLabel = text("", 13, MUTED);
        botLabel.setGravity(Gravity.CENTER);

        body.addView(rowTitle("岛上图标", "按当前模型选择"));
        final String[] iconNames = {
                "Agent HUD", "DeepSeek", "Claude", "Codex", "Grok", "Cursor", "Copilot",
                "Antigravity", "Kimi", "Qwen", "OpenCode", "Pi", "Hermes", "GLM",
                "ZCode", "Qoder", "CodeBuddy", "WorkBuddy", "OpenClaw", "Devin", "小鱼"
        };
        final String[] iconKeys = {
                "hud", "deepseek", "claude", "codex", "grok", "cursor", "copilot",
                "antigravity", "kimi", "qwen", "opencode", "pi", "hermes", "glm",
                "zcode", "qoder", "codebuddy", "workbuddy", "openclaw", "devin", "fish"
        };
        int selected = 0;
        String current = Prefs.icon(this);
        for (int i = 0; i < iconKeys.length; i++) if (iconKeys[i].equals(current)) selected = i;

        ArrayAdapter<String> iconAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, iconNames);
        spinnerRow(body, iconAdapter, selected, new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Prefs.setIcon(MainActivity.this, iconKeys[position]);
                botKey = iconKeys[position];
                if (botView != null) botView.setBot(botKey, botLook, botFailed);
                updateCapsulePreview();
                try {
                    startForegroundService(new Intent(MainActivity.this, IslandService.class)
                            .setAction(IslandService.ACTION_IDLE));
                } catch (Throwable ignored) {}
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        LinearLayout botBox = new LinearLayout(this);
        botBox.setOrientation(LinearLayout.VERTICAL);
        botBox.setGravity(Gravity.CENTER_HORIZONTAL);
        botBox.setPadding(0, dp(8), 0, 0);
        botBox.addView(botView, new LinearLayout.LayoutParams(dp(128), dp(128)));
        botBox.addView(botLabel, margin(0, 2, 0, 0));
        body.addView(botBox);
    }

    private void buildKeepAliveCard(LinearLayout col) {
        LinearLayout body = card(col, "保活", "0.5.14 不需要旧 driver；仅 0.4.x 使用");

        switchRow(body, "保活（看门狗 + Shizuku）",
                "保持 driver / 后台服务",
                Prefs.keepAlive(this), (view, checked) -> Prefs.setKeepAlive(this, checked));
        divider(body);

        shizukuStatus = valueRow(body, "Shizuku",
                "用于 shell 身份操作", "", MUTED, view -> requestShizuku());
        divider(body);

        Button battery = tonalButton("后台保活设置", view -> keepAlive());
        body.addView(battery, margin(0, 8, 0, 0));
    }

    private void buildAdvancedCard(LinearLayout col) {
        LinearLayout body = collapsibleCard(col, "高级与测试", "状态预览、测试按钮与诊断");

        body.addView(rowTitle("状态预览", "切到不同 look 看 bot 的样子"));

        LinearLayout phases = new LinearLayout(this);
        phases.setOrientation(LinearLayout.HORIZONTAL);
        phases.addView(phaseChip("醒来", BotState.Look.IDLE, false));
        phases.addView(phaseChip("干活", BotState.Look.TOOL_COMMAND, false));
        phases.addView(phaseChip("请求", BotState.Look.PERMISSION, false));
        phases.addView(phaseChip("完成", BotState.Look.DONE, false));
        body.addView(phases, margin(0, 4, 0, 4));

        ArrayAdapter<String> lookAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, LOOK_NAMES);
        Spinner lookSpinner = spinnerRow(body, lookAdapter, 2,
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        show(LOOKS[position], LOOK_FAILED[position]);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
        lookSpinner.post(() -> lookSpinner.setSelection(2));

        TextView note = text("测试按钮直接驱动前台服务；开始后回桌面看胶囊。", 12, FAINT);
        note.setLineSpacing(dp(2), 1.4f);
        body.addView(note, margin(0, 10, 0, 4));

        body.addView(tonalButton("更新状态", view -> demo(Notifier.ACTION_UPDATE)),
                margin(0, 4, 0, 4));
        body.addView(tonalButton("完成", view -> demo(Notifier.ACTION_DONE)),
                margin(0, 4, 0, 4));
        body.addView(tonalButton("清除通知", view -> demo(Notifier.ACTION_CANCEL)),
                margin(0, 4, 0, 0));
    }

    // ── 组件 ──────────────────────────────────────────────────────────────

    private LinearLayout card(LinearLayout parent, String title, String summary) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = shape(SURFACE, 12);
        bg.setStroke(dp(1), 0xFF2A2625);
        card.setBackground(bg);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        if (title != null) card.addView(medium(title, 16, FG));
        if (summary != null) {
            TextView s = text(summary, 13, MUTED);
            s.setLineSpacing(dp(2), 1.4f);
            card.addView(s, margin(0, 2, 0, 8));
        }

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        card.addView(body);

        parent.addView(card, margin(0, 0, 0, 16));
        return body;
    }

    private LinearLayout collapsibleCard(LinearLayout parent, String title, String summary) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = shape(SURFACE, 12);
        bg.setStroke(dp(1), 0xFF2A2625);
        card.setBackground(bg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(56));
        header.setPadding(dp(16), dp(16), dp(16), dp(16));
        header.setClickable(true);
        header.setFocusable(true);
        header.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18FFFFFF), null, null));

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(medium(title, 16, FG));
        TextView s = text(summary, 13, MUTED);
        s.setLineSpacing(dp(2), 1.4f);
        block.addView(s, margin(0, 2, 0, 0));
        header.addView(block, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView chevron = text("▸", 16, MUTED);
        chevron.setPadding(dp(8), 0, 0, 0);
        header.addView(chevron);
        card.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), 0, dp(16), dp(16));
        body.setVisibility(advancedOpen ? View.VISIBLE : View.GONE);
        chevron.setText(advancedOpen ? "▾" : "▸");
        card.addView(body);

        header.setOnClickListener(v -> {
            advancedOpen = body.getVisibility() != View.VISIBLE;
            body.setVisibility(advancedOpen ? View.VISIBLE : View.GONE);
            chevron.setText(advancedOpen ? "▾" : "▸");
        });

        parent.addView(card, margin(0, 0, 0, 16));
        return body;
    }

    private LinearLayout rowTitle(String title, String summary) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(8), 0, dp(4));
        block.addView(medium(title, 15, FG));
        if (summary != null) {
            TextView s = text(summary, 13, MUTED);
            s.setLineSpacing(dp(2), 1.4f);
            block.addView(s, margin(0, 2, 0, 0));
        }
        return block;
    }

    private LinearLayout rowBase() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(0, dp(8), 0, dp(8));
        return row;
    }

    private void addTextBlock(LinearLayout row, String title, String summary) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(text(title, 15, FG));
        if (summary != null) {
            TextView s = text(summary, 13, MUTED);
            s.setLineSpacing(dp(2), 1.4f);
            block.addView(s, margin(0, 2, 0, 0));
        }
        row.addView(block, new LinearLayout.LayoutParams(0, -2, 1f));
    }

    private Switch switchRow(LinearLayout body, String title, String summary,
                             boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = rowBase();
        addTextBlock(row, title, summary);
        Switch s = new Switch(this);
        s.setChecked(checked);
        s.setContentDescription(title);
        s.setThumbTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{ACCENT, FAINT}));
        s.setTrackTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{0xFF5A3A2E, 0xFF2A2622}));
        s.setOnCheckedChangeListener(listener);
        row.addView(s, new LinearLayout.LayoutParams(-2, -2));
        body.addView(row);
        return s;
    }

    private TextView valueRow(LinearLayout body, String title, String summary, String value,
                              int color, View.OnClickListener click) {
        LinearLayout row = rowBase();
        addTextBlock(row, title, summary);

        TextView v = text(value, 13, color);
        v.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.addView(v);

        if (click != null) {
            TextView chevron = text("›", 18, FAINT);
            chevron.setPadding(dp(8), 0, 0, 0);
            row.addView(chevron);
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18FFFFFF), null, null));
            row.setOnClickListener(click);
        }
        body.addView(row);
        return v;
    }

    private void divider(LinearLayout body) {
        View line = new View(this);
        line.setBackgroundColor(0xFF2A2625);
        body.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private LinearLayout animSegmented() {
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(shape(SURFACE, 20));
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));

        String[] labels = {"关闭", "标准", "流畅"};
        animButtons = new TextView[3];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView t = text(labels[i], 14, MUTED);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setClickable(true);
            t.setFocusable(true);
            t.setContentDescription(labels[i] + "动画");
            t.setOnClickListener(v -> {
                Prefs.setIslandAnim(this, index);
                selectAnim(index);
            });
            animButtons[i] = t;
            seg.addView(t, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        selectAnim(Prefs.islandAnim(this));
        return seg;
    }

    private void selectAnim(int index) {
        if (animButtons == null) return;
        for (int i = 0; i < animButtons.length; i++) {
            TextView t = animButtons[i];
            boolean on = i == index;
            t.setTextColor(on ? FG : MUTED);
            t.setBackground(on ? shape(ACCENT_SOFT, 18) : null);
            t.setSelected(on);
        }
    }

    private Spinner spinnerRow(LinearLayout body, ArrayAdapter<String> adapter, int selected,
                               AdapterView.OnItemSelectedListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setBackground(shape(CARD, 12));
        row.setPadding(dp(12), 0, dp(4), 0);

        Spinner spinner = new Spinner(this);
        spinner.setAdapter(adapter);
        spinner.setBackground(null);
        spinner.setPadding(0, 0, 0, 0);
        row.addView(spinner, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView chevron = text("▾", 14, MUTED);
        chevron.setGravity(Gravity.CENTER);
        chevron.setContentDescription("展开");
        chevron.setOnClickListener(v -> spinner.performClick());
        row.addView(chevron, new LinearLayout.LayoutParams(dp(32), dp(48)));

        body.addView(row, margin(0, 4, 0, 4));
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(listener);
        return spinner;
    }

    private SeekBar addCapsuleSlider(LinearLayout body, String title, float min, float max,
                                     float step, float initial,
                                     java.util.function.Consumer<Float> apply,
                                     java.util.function.Function<Float, String> format) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(24));

        TextView label = text(title, 14, FG);
        header.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView value = text(format.apply(initial), 13, MUTED);
        header.addView(value);
        body.addView(header, margin(0, 8, 0, 0));

        SeekBar bar = new SeekBar(this);
        bar.setMax(Math.max(1, Math.round((max - min) / step)));
        bar.setProgress(Math.round((initial - min) / step));
        bar.setMinimumHeight(dp(48));
        bar.setPadding(0, 0, 0, 0);
        bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        bar.setThumbTintList(ColorStateList.valueOf(ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF3A3631));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float current = min + progress * step;
                value.setText(format.apply(current));
                apply.accept(current);
                updateCapsulePreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Prefs.setCapsuleStyle(MainActivity.this, editCapsuleStyle);
                notifyServiceModeChanged();
            }
        });
        body.addView(bar, new LinearLayout.LayoutParams(-1, dp(48)));
        return bar;
    }

    private FrameLayout createCapsulePreview(int heightDp, boolean hero) {
        FrameLayout screen = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF2A2622, 0xFF151311});
        bg.setCornerRadius(dp(12));
        screen.setBackground(bg);

        TextView clock = text("22:19", 10, 0x99F3EFE9);
        clock.setPadding(dp(12), dp(8), 0, 0);
        screen.addView(clock, new FrameLayout.LayoutParams(-2, -2));

        CapsuleView view = new CapsuleView(this);
        FrameLayout.LayoutParams p =
                new FrameLayout.LayoutParams(-1, dp(editCapsuleStyle.heightDp));
        p.topMargin = previewTop(editCapsuleStyle, heightDp);
        screen.addView(view, p);

        if (hero) {
            capsuleHeroPreview = view;
            capsuleHeroScreen = screen;
        } else {
            capsulePreview = view;
            capsulePreviewScreen = screen;
        }
        return screen;
    }

    private int previewTop(CapsuleStyle style, int previewHeightDp) {
        float available = Math.max(0f, previewHeightDp - 24f - style.heightDp);
        return dp(24f + available * style.y);
    }

    private void updateCapsulePreview() {
        if (editCapsuleStyle == null) return;
        updatePreview(capsuleHeroPreview, PREVIEW_HERO_DP, editCapsuleStyle);
        updatePreview(capsulePreview, PREVIEW_SETTINGS_DP, editCapsuleStyle);
    }

    private void updatePreview(CapsuleView view, int heightDp, CapsuleStyle style) {
        if (view == null) return;
        view.setStyle(style);
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (p != null) {
            p.height = dp(style.heightDp);
            p.topMargin = previewTop(style, heightDp);
            view.setLayoutParams(p);
        }
        view.setAlpha(style.opacity);
        view.setContent("执行命令", "用 执行命令…",
                System.currentTimeMillis() - 42_000, false, "tool-command", "open", 2, 4, 0);
    }

    private Button primaryButton(String label, View.OnClickListener click) {
        return pillButton(label, ACCENT, ON_ACCENT, 0, click);
    }

    private Button tonalButton(String label, View.OnClickListener click) {
        return pillButton(label, CARD, FG, STROKE, click);
    }

    private Button pillButton(String label, int bg, int fg, int stroke,
                              View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(fg);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable d = shape(bg, 24);
        if (stroke != 0) d.setStroke(dp(1), stroke);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), d, null));
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setMinHeight(dp(48));
        b.setMinimumHeight(dp(48));
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setOnClickListener(click);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(48)));
        return b;
    }

    private TextView textButton(String label, View.OnClickListener click) {
        TextView t = medium(label, 14, ACCENT);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(48));
        t.setMinimumHeight(dp(48));
        t.setClickable(true);
        t.setFocusable(true);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18FFFFFF), null, null));
        t.setOnClickListener(click);
        return t;
    }

    private View phaseChip(String label, BotState.Look look, boolean failed) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(FG);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable d = shape(CARD, 22);
        d.setStroke(dp(1), STROKE);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), d, null));
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setMinHeight(dp(48));
        b.setMinimumHeight(dp(48));
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setOnClickListener(v -> show(look, failed));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
        p.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(p);
        return b;
    }

    private GradientDrawable shape(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private TextView text(String value, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.4f);
        return t;
    }

    private TextView medium(String value, float sp, int color) {
        TextView t = text(value, sp, color);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    // ── 状态与设置刷新 ────────────────────────────────────────────────────

    private void refresh() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean post = nm.areNotificationsEnabled();
        boolean promoted = nm.canPostPromotedNotifications();
        PowerManager pm = getSystemService(PowerManager.class);
        boolean battery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());

        boolean sys = Prefs.systemIsland(this);
        boolean cap = Prefs.capsuleEnabled(this);
        String display = sys && cap ? "系统岛 + 悬浮胶囊"
                : sys ? "仅系统岛"
                : cap ? "仅悬浮胶囊"
                : "仅通知";

        if (status != null) {
            status.setText("通知 " + (post ? "开" : "关")
                    + " · 实时更新 " + (promoted ? "允许" : "未允许")
                    + " · 电池 " + (battery ? "不优化" : "优化中")
                    + "\n显示 " + display);
        }
        refreshCapsuleStatus();
    }

    private void refreshCapsuleStatus() {
        if (capsuleStatus == null) return;
        boolean allowed = Settings.canDrawOverlays(this);
        capsuleStatus.setText(allowed ? "已授权" : "未授权 · 点此开启");
        capsuleStatus.setTextColor(allowed ? GREEN : ACCENT);
    }

    private boolean shizukuGranted() {
        try { return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Throwable t) { return false; }
    }

    private void refreshShizukuStatus() {
        if (shizukuStatus == null) return;
        boolean ok = shizukuGranted();
        shizukuStatus.setText(ok ? "已授权" : "未授权 · 点此申请");
        shizukuStatus.setTextColor(ok ? GREEN : ACCENT);
    }

    private void requestShizuku() {
        try {
            if (Shizuku.isPreV11()) {
                shizukuStatus.setText("版本太旧");
                return;
            }
            if (shizukuGranted()) {
                refreshShizukuStatus();
                return;
            }
            Shizuku.requestPermission(SHIZUKU_REQUEST);
        } catch (Throwable t) {
            Log.w("WorkIsland", "request shizuku failed", t);
            shizukuStatus.setText("申请失败");
        }
    }

    private void show(BotState.Look look, boolean failed) {
        botLook = look;
        botFailed = failed;
        if (botView != null) botView.setBot(botKey, look, failed);
        refreshLabels();
    }

    private void refreshLabels() {
        if (botLabel == null) return;
        BotState.Look look = botLook;
        BotState.Sign sign = BotState.sign(look, botFailed);
        String signName = sign == null ? "无标识"
                : sign == BotState.Sign.EXCLAMATION ? "叹号"
                : sign == BotState.Sign.QUESTION ? "问号"
                : sign == BotState.Sign.LINES ? "清单线"
                : sign == BotState.Sign.CROSS ? "叉"
                : sign == BotState.Sign.HOURGLASS ? "沙漏"
                : sign == BotState.Sign.ELLIPSIS ? "省略号"
                : "z";
        botLabel.setText(BotState.phaseName(BotState.phase(look)) + " · "
                + BotState.lookName(look) + " · " + signName
                + (Prefs.soul(this) ? "" : "（开关已关）"));
    }

    private void resetCapsuleStyle() {
        editCapsuleStyle = CapsuleStyle.defaults();
        if (capsuleTextBar != null) {
            capsuleTextBar.setProgress(Math.round(
                    (CapsuleStyle.DEFAULT_TEXT_SP - 9f) / .5f));
        }
        if (capsuleHeightBar != null) {
            capsuleHeightBar.setProgress(Math.round(CapsuleStyle.DEFAULT_HEIGHT_DP - 32f));
        }
        if (capsuleYBar != null) capsuleYBar.setProgress(0);
        if (capsuleOpacityBar != null) {
            capsuleOpacityBar.setProgress(Math.round(
                    (CapsuleStyle.DEFAULT_OPACITY - .35f) / .01f));
        }
        updateCapsulePreview();
        Prefs.setCapsuleStyle(this, editCapsuleStyle);
        notifyServiceModeChanged();
    }

    private void requestOverlayPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable failure) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Throwable ignored) {
                // 个别 ROM 连这个入口都没有，只能去系统设置里手动找。
            }
        }
    }

    /** 设置页改开关时，让正在跑的服务立即重发通知、重画胶囊；服务没跑就别把它拉起来。 */
    private void notifyServiceModeChanged() {
        if (!serviceRunning()) return;
        try {
            startService(new Intent(this, IslandService.class)
                    .setAction(IslandService.ACTION_REFRESH));
        } catch (Throwable ignored) {
            // 服务刚好被系统收走，下一条状态更新会自然走新模式。
        }
    }

    private boolean serviceRunning() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            for (android.service.notification.StatusBarNotification n : nm.getActiveNotifications()) {
                if (n.getId() == Notifier.ID || n.getId() == 1002) return true;
            }
        } catch (Throwable ignored) {
            // getActiveNotifications 在个别 ROM 上可能受限。
        }
        return false;
    }

    private void ensureService() {
        try {
            startForegroundService(new Intent(this, IslandService.class)
                    .setAction(IslandService.ACTION_IDLE));
        } catch (Throwable ignored) {}
    }

    private void keepAlive() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            try { startActivity(i); return; } catch (Throwable ignored) {}
        }
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    /**
     * 测试按钮直接走服务，而不是旧的广播接收器：这样通知和悬浮胶囊看到的是同一份状态。
     * 应用在前台时胶囊会主动隐藏，按 Home 回桌面就能看到。
     */
    private void demo(String action) {
        Intent i = new Intent(this, IslandService.class).setAction(action);
        boolean done = Notifier.ACTION_DONE.equals(action);
        i.putExtra("title", done ? "任务完成" : "Claude 正在干活");
        i.putExtra("text", done ? "测试完成" : "正在用 Bash…");
        i.putExtra("state", done ? "done" : "running");
        i.putExtra("face", done ? "happy" : "open");
        i.putExtra("since", System.currentTimeMillis());
        try {
            startService(i);
        } catch (Throwable failure) {
            Log.w("WorkIsland", "cannot start demo run", failure);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IslandOverlay.onAppVisibilityChanged(true);
        if (!sheetMode) {
            refresh();
            refreshShizukuStatus();
            updateCapsulePreview();
        }
    }

    @Override
    protected void onPause() {
        IslandOverlay.onAppVisibilityChanged(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    private void showSheets() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF151514);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int width = getResources().getDisplayMetrics().widthPixels;
        for (Bitmap sheet : new Bitmap[]{BotSheet.models(this), BotSheet.looks(this)}) {
            android.widget.ImageView view = new android.widget.ImageView(this);
            view.setImageBitmap(sheet);
            view.setAdjustViewBounds(true);
            view.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            column.addView(view, new LinearLayout.LayoutParams(width, -2));
        }
        scroll.addView(column);
        applyWindowInsets(scroll);
        setContentView(scroll);
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private int dp(float v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
