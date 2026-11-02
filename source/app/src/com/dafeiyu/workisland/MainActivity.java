package com.dafeiyu.workisland;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int SHIZUKU_REQUEST = 42;
    private static final int BG = 0xFF1F1E1D;
    private static final int CARD = 0xFF282725;
    private static final int STROKE = 0xFF3B3936;
    private static final int FG = 0xFFFAF9F5;
    private static final int MUTED = 0xFFA9A49B;
    private static final int ACCENT = 0xFFD97757;

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

    private static final int PREVIEW_HEIGHT_DP = 180;

    private TextView status;
    private TextView shizukuStatus;
    private TextView capsuleStatus;
    private TextView botLabel;

    private CapsuleStyle editCapsuleStyle;
    private CapsuleView capsulePreview;
    private FrameLayout capsulePreviewScreen;
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
        if (getIntent() != null && getIntent().getBooleanExtra("sheet", false)) {
            sheetMode = true;
            showSheets();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("工作岛");
        title.setTextColor(FG);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(0, 6, 0, 0));

        TextView sub = new TextView(this);
        sub.setText("Claude 的工作状态 · 显示在灵动岛");
        sub.setTextColor(MUTED);
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, lp(0, 0, 0, 18));

        status = new TextView(this);
        status.setTextColor(ACCENT);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(dp(3), 1f);
        root.addView(status, lp(0, 0, 0, 16));

        // ---- bot 舞台 ----
        // 不装进卡片：让 bot 直接站在这页纸上，像一枚自己躺着的感叹号。
        LinearLayout stage = new LinearLayout(this);
        stage.setOrientation(LinearLayout.VERTICAL);
        stage.setGravity(Gravity.CENTER_HORIZONTAL);

        botView = new BotView(this);
        botView.setBot(botKey, botLook, botFailed);
        stage.addView(botView, new LinearLayout.LayoutParams(dp(340), dp(340)));

        botLabel = new TextView(this);
        botLabel.setTextColor(MUTED);
        botLabel.setTextSize(13);
        botLabel.setGravity(Gravity.CENTER);
        stage.addView(botLabel, lp(0, 2, 0, 4));
        root.addView(stage, lp(0, 0, 0, 12));

        LinearLayout phases = new LinearLayout(this);
        phases.setOrientation(LinearLayout.HORIZONTAL);
        phases.setGravity(Gravity.CENTER);
        phases.addView(phaseChip("醒来", BotState.Look.IDLE, false));
        phases.addView(phaseChip("干活", BotState.Look.TOOL_COMMAND, false));
        phases.addView(phaseChip("请求", BotState.Look.PERMISSION, false));
        phases.addView(phaseChip("完成", BotState.Look.DONE, false));
        root.addView(phases, lp(0, 0, 0, 10));

        Spinner lookSpinner = new Spinner(this);
        ArrayAdapter<String> lookAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, LOOK_NAMES);
        lookSpinner.setAdapter(lookAdapter);
        lookSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                show(LOOKS[position], LOOK_FAILED[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        // 等控件挂上去再选中「执行命令」，否则首帧会被默认的「待命」覆盖。
        lookSpinner.post(() -> lookSpinner.setSelection(2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(dp(12), 0, dp(12), dp(8));
        root.addView(lookSpinner, slp);

        // ---- 开关：程序化 bot ----
        Switch soul = new Switch(this);
        soul.setText("程序化 bot（四态 + 标识 + 体征）");
        soul.setTextColor(FG);
        soul.setTextSize(14);
        soul.setChecked(Prefs.soul(this));
        soul.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean checked) {
                Prefs.setSoul(MainActivity.this, checked);
                refreshLabels();
                updateCapsulePreview();
                try {
                    startForegroundService(new Intent(MainActivity.this, IslandService.class)
                            .setAction(IslandService.ACTION_IDLE));
                } catch (Throwable ignored) {}
            }
        });
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(-1, -2);
        klp.setMargins(dp(8), 0, dp(8), dp(4));
        root.addView(soul, klp);

        TextView animLabel = new TextView(this);
        animLabel.setText("岛上动画（应用内预览始终 60fps）");
        animLabel.setTextColor(FG);
        animLabel.setTextSize(14);
        LinearLayout.LayoutParams allp = new LinearLayout.LayoutParams(-1, -2);
        allp.setMargins(dp(8), dp(6), dp(8), 0);
        root.addView(animLabel, allp);

        Spinner animSpinner = new Spinner(this);
        ArrayAdapter<String> animAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"关闭", "标准（4 帧）", "流畅（8 帧）"});
        animSpinner.setAdapter(animAdapter);
        animSpinner.setSelection(Prefs.islandAnim(this));
        animSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Prefs.setIslandAnim(MainActivity.this, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        LinearLayout.LayoutParams anlp = new LinearLayout.LayoutParams(-1, -2);
        anlp.setMargins(dp(12), 0, dp(12), dp(6));
        root.addView(animSpinner, anlp);

        // ---- 两个独立开关：系统岛 / 应用自己画的悬浮胶囊 ----
        TextView modeLabel = new TextView(this);
        modeLabel.setText("显示方式（两个都关就只留普通通知）");
        modeLabel.setTextColor(FG);
        modeLabel.setTextSize(14);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.setMargins(dp(8), dp(6), dp(8), 0);
        root.addView(modeLabel, mlp);

        Switch systemIsland = new Switch(this);
        systemIsland.setText("系统流体云 / 灵动岛（实时更新）");
        systemIsland.setTextColor(FG);
        systemIsland.setTextSize(14);
        systemIsland.setChecked(Prefs.systemIsland(this));
        systemIsland.setOnCheckedChangeListener((buttonView, checked) -> {
            Prefs.setSystemIsland(MainActivity.this, checked);
            notifyServiceModeChanged();
        });
        LinearLayout.LayoutParams silp = new LinearLayout.LayoutParams(-1, -2);
        silp.setMargins(dp(8), dp(4), dp(8), dp(2));
        root.addView(systemIsland, silp);

        Switch capsule = new Switch(this);
        capsule.setText("悬浮胶囊（无岛兜底，触摸穿透）");
        capsule.setTextColor(FG);
        capsule.setTextSize(14);
        capsule.setChecked(Prefs.capsuleEnabled(this));
        capsule.setOnCheckedChangeListener((buttonView, checked) -> {
            Prefs.setCapsuleEnabled(MainActivity.this, checked);
            refreshCapsuleStatus();
            notifyServiceModeChanged();
        });
        LinearLayout.LayoutParams cilp = new LinearLayout.LayoutParams(-1, -2);
        cilp.setMargins(dp(8), 0, dp(8), dp(2));
        root.addView(capsule, cilp);

        capsuleStatus = new TextView(this);
        capsuleStatus.setTextSize(12);
        capsuleStatus.setGravity(Gravity.CENTER);
        capsuleStatus.setPadding(0, dp(4), 0, dp(6));
        capsuleStatus.setOnClickListener(v -> requestOverlayPermission());
        root.addView(capsuleStatus, new LinearLayout.LayoutParams(-1, -2));

        TextView capsuleNote = new TextView(this);
        capsuleNote.setText("悬浮胶囊由应用自己绘制：半透明、触摸穿透，不挡操作；"
                + "需要「显示在其他应用上层」权限。系统岛正常时建议关掉它，避免两条都显示。");
        capsuleNote.setTextColor(0xFF6F6B64);
        capsuleNote.setTextSize(11);
        capsuleNote.setGravity(Gravity.CENTER);
        root.addView(capsuleNote, lp(0, 0, 0, 8));

        addCapsuleStyleSection(root);

        Switch keepAlive = new Switch(this);
        keepAlive.setText("保活（看门狗 + Shizuku 兜底）");
        keepAlive.setTextColor(FG);
        keepAlive.setTextSize(14);
        keepAlive.setChecked(Prefs.keepAlive(this));
        keepAlive.setOnCheckedChangeListener((buttonView, checked) -> Prefs.setKeepAlive(MainActivity.this, checked));
        LinearLayout.LayoutParams kalp = new LinearLayout.LayoutParams(-1, -2);
        kalp.setMargins(dp(8), 0, dp(8), dp(4));
        root.addView(keepAlive, kalp);

        shizukuStatus = new TextView(this);
        shizukuStatus.setTextSize(12);
        shizukuStatus.setGravity(Gravity.CENTER);
        shizukuStatus.setPadding(0, dp(4), 0, dp(6));
        shizukuStatus.setOnClickListener(v -> requestShizuku());
        root.addView(shizukuStatus, new LinearLayout.LayoutParams(-1, -2));

        TextView soulNote = new TextView(this);
        soulNote.setText("关掉就退回旧的简笔图标。标识：! 请求　? 提问　≡ 清单　× 出错　⧗ 限额　… 思考　z 睡着");
        soulNote.setTextColor(0xFF6F6B64);
        soulNote.setTextSize(11);
        soulNote.setGravity(Gravity.CENTER);
        root.addView(soulNote, lp(0, 0, 0, 14));

        root.addView(button("开始一个测试任务", ACCENT, BG, v -> demo(Notifier.ACTION_START)));
        root.addView(button("更新状态", CARD, FG, v -> demo(Notifier.ACTION_UPDATE)));
        root.addView(button("完成", CARD, FG, v -> demo(Notifier.ACTION_DONE)));
        root.addView(button("清除通知", CARD, MUTED, v -> demo(Notifier.ACTION_CANCEL)));
        root.addView(button("后台保活设置", CARD, FG, v -> keepAlive()));

        TextView iconLabel = new TextView(this);
        iconLabel.setText("岛上图标（按模型自选）");
        iconLabel.setTextColor(MUTED);
        iconLabel.setTextSize(13);
        iconLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-2, -2);
        ilp.setMargins(0, dp(12), 0, dp(6));
        root.addView(iconLabel, ilp);

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
        Spinner iconSpinner = new Spinner(this);
        ArrayAdapter<String> iconAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, iconNames);
        iconSpinner.setAdapter(iconAdapter);
        int selected = 0;
        String current = Prefs.icon(this);
        for (int i = 0; i < iconKeys.length; i++) if (iconKeys[i].equals(current)) selected = i;
        iconSpinner.setSelection(selected);
        iconSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Prefs.setIcon(MainActivity.this, iconKeys[position]);
                botKey = iconKeys[position];
                botView.setBot(botKey, botLook, botFailed);
                updateCapsulePreview();
                try {
                    startForegroundService(new Intent(MainActivity.this, IslandService.class)
                            .setAction(IslandService.ACTION_IDLE));
                } catch (Throwable ignored) {}
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        LinearLayout.LayoutParams islp = new LinearLayout.LayoutParams(-1, -2);
        islp.setMargins(dp(12), 0, dp(12), dp(10));
        root.addView(iconSpinner, islp);

        TextView iconNote = new TextView(this);
        iconNote.setText("模型图标来自 LobeHub（MIT）；商标归各自所有者，非官方关联。");
        iconNote.setTextColor(0xFF6F6B64);
        iconNote.setTextSize(11);
        iconNote.setGravity(Gravity.CENTER);
        root.addView(iconNote, lp(0, 0, 0, 8));

        TextView hint = new TextView(this);
        hint.setText("如果岛上没出现：系统设置 → 通知 → 工作岛 → 允许实时更新；"
                + "没有该开关的设备请选「总是悬浮胶囊」并授权悬浮窗。");
        hint.setTextColor(0xFF6F6B64);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, lp(0, 22, 0, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.addView(root);
        setContentView(scroll);
        // 预览放大后内容会超出一屏，别让 ScrollView 自己滚到底把标题顶进状态栏。
        scroll.post(() -> scroll.scrollTo(0, 0));
        ensureService();

        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
        botKey = Prefs.icon(this);
        botView.setBot(botKey, botLook, botFailed);
        Prefs.writeAnimTier(this);   // 让外面读的 driver 一开始就有档位文件
        Prefs.writeKeepAlive(this);  // 看门狗读这个决定要不要保持 driver
        refreshLabels();
        refresh();

        try { Shizuku.addRequestPermissionResultListener(shizukuListener); } catch (Throwable ignored) {}
        refreshShizukuStatus();
        if (Prefs.keepAlive(this) && !shizukuGranted() && !askedShizuku) {
            askedShizuku = true;
            shizukuStatus.post(this::requestShizuku);
        }

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
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IslandOverlay.onAppVisibilityChanged(true);
        if (!sheetMode) { refresh(); refreshShizukuStatus(); updateCapsulePreview(); }
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

    private boolean shizukuGranted() {
        try { return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Throwable t) { return false; }
    }

    private void refreshShizukuStatus() {
        if (shizukuStatus == null) return;
        boolean ok = shizukuGranted();
        shizukuStatus.setText(ok ? "Shizuku：已授权" : "Shizuku：未授权 · 点这里申请");
        shizukuStatus.setTextColor(ok ? 0xFF4CAF50 : ACCENT);
    }

    private void requestShizuku() {
        try {
            if (Shizuku.isPreV11()) {
                shizukuStatus.setText("Shizuku：版本太旧，请升级");
                return;
            }
            if (shizukuGranted()) {
                refreshShizukuStatus();
                return;
            }
            Shizuku.requestPermission(SHIZUKU_REQUEST);
        } catch (Throwable t) {
            Log.w("WorkIsland", "request shizuku failed", t);
            shizukuStatus.setText("Shizuku：申请失败（" + t.getClass().getSimpleName() + "）");
        }
    }

    private View phaseChip(String text, BotState.Look look, boolean failed) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(FG);
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD);
        d.setCornerRadius(dp(14));
        d.setStroke(dp(1), STROKE);
        b.setBackground(d);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setMinHeight(dp(40));
        b.setMinimumHeight(dp(40));
        b.setOnClickListener(v -> show(look, failed));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(p);
        return b;
    }

    private void show(BotState.Look look, boolean failed) {
        botLook = look;
        botFailed = failed;
        botView.setBot(botKey, look, failed);
        refreshLabels();
    }

    private void refreshLabels() {
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

    private void refresh() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean post = nm.areNotificationsEnabled();
        boolean promoted = nm.canPostPromotedNotifications();
        PowerManager pm = getSystemService(PowerManager.class);
        boolean battery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        status.setText("通知：" + (post ? "开" : "关")
                + "　实时更新：" + (promoted ? "允许" : "未允许")
                + "\n电池：" + (battery ? "不优化" : "还在优化"));
        refreshCapsuleStatus();
    }

    /** 预览 + 四个滑块：字号 / 高度 / 垂直位置 / 不透明度。 */
    private void addCapsuleStyleSection(LinearLayout root) {
        editCapsuleStyle = Prefs.capsuleStyle(this);

        TextView styleLabel = new TextView(this);
        styleLabel.setText("悬浮胶囊大小与位置");
        styleLabel.setTextColor(FG);
        styleLabel.setTextSize(14);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(dp(8), dp(10), dp(8), 0);
        root.addView(styleLabel, slp);

        TextView previewLabel = new TextView(this);
        previewLabel.setText("实时预览（设置页内，不需要悬浮窗权限）");
        previewLabel.setTextColor(MUTED);
        previewLabel.setTextSize(12);
        previewLabel.setGravity(Gravity.CENTER);
        root.addView(previewLabel, lp(0, 2, 0, 6));

        capsulePreviewScreen = new FrameLayout(this);
        GradientDrawable previewBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{0xFF2B3550, 0xFF10131C});
        previewBg.setCornerRadius(dp(16));
        previewBg.setStroke(dp(1), 0x33FFFFFF);
        capsulePreviewScreen.setBackground(previewBg);
        root.addView(capsulePreviewScreen,
                new LinearLayout.LayoutParams(-1, dp(PREVIEW_HEIGHT_DP)));

        TextView clock = new TextView(this);
        clock.setText("22:19");
        clock.setTextColor(0x99FAF9F5);
        clock.setTextSize(10);
        clock.setPadding(dp(12), dp(6), 0, 0);
        capsulePreviewScreen.addView(clock, new FrameLayout.LayoutParams(-2, -2));

        capsulePreview = new CapsuleView(this);
        FrameLayout.LayoutParams previewParams =
                new FrameLayout.LayoutParams(-1, dp(editCapsuleStyle.heightDp));
        previewParams.topMargin = previewTop(editCapsuleStyle);
        capsulePreviewScreen.addView(capsulePreview, previewParams);

        capsuleTextBar = addCapsuleSlider(root, 9f, 16f, .5f, editCapsuleStyle.textSp,
                value -> editCapsuleStyle = new CapsuleStyle(value, editCapsuleStyle.heightDp,
                        editCapsuleStyle.y, editCapsuleStyle.opacity),
                value -> String.format(Locale.US, "大小（字号）：%.1f sp", value));

        capsuleHeightBar = addCapsuleSlider(root, 32f, 64f, 1f, editCapsuleStyle.heightDp,
                value -> editCapsuleStyle = new CapsuleStyle(editCapsuleStyle.textSp, value,
                        editCapsuleStyle.y, editCapsuleStyle.opacity),
                value -> String.format(Locale.US, "高度：%d dp", Math.round(value)));

        capsuleYBar = addCapsuleSlider(root, 0f, 80f, 1f, editCapsuleStyle.y * 100f,
                value -> editCapsuleStyle = new CapsuleStyle(editCapsuleStyle.textSp,
                        editCapsuleStyle.heightDp, value / 100f, editCapsuleStyle.opacity),
                value -> String.format(Locale.US, "垂直位置：%d%%（状态栏下方）", Math.round(value)));

        capsuleOpacityBar = addCapsuleSlider(root, 35f, 80f, 1f, editCapsuleStyle.opacity * 100f,
                value -> editCapsuleStyle = new CapsuleStyle(editCapsuleStyle.textSp,
                        editCapsuleStyle.heightDp, editCapsuleStyle.y, value / 100f),
                value -> String.format(Locale.US, "不透明度：%d%%", Math.round(value)));

        TextView hint = new TextView(this);
        hint.setText("拖动即时预览；松手后保存并应用到悬浮窗。触摸穿透要求不透明度不超过 80%。");
        hint.setTextColor(0xFF6F6B64);
        hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, lp(0, 2, 0, 4));

        Button reset = button("恢复默认大小与位置", CARD, FG, v -> resetCapsuleStyle());
        root.addView(reset);

        updateCapsulePreview();
    }

    private SeekBar addCapsuleSlider(LinearLayout root, float min, float max, float step,
                                     float initial, java.util.function.Consumer<Float> apply,
                                     java.util.function.Function<Float, String> label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(2), dp(8), 0);

        TextView valueLabel = new TextView(this);
        valueLabel.setTextColor(FG);
        valueLabel.setTextSize(13);
        valueLabel.setText(label.apply(initial));
        box.addView(valueLabel, new LinearLayout.LayoutParams(-1, -2));

        SeekBar bar = new SeekBar(this);
        bar.setMax(Math.max(1, Math.round((max - min) / step)));
        bar.setProgress(Math.round((initial - min) / step));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = min + progress * step;
                valueLabel.setText(label.apply(value));
                apply.accept(value);
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
        box.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        root.addView(box, new LinearLayout.LayoutParams(-1, -2));
        return bar;
    }

    private int previewTop(CapsuleStyle style) {
        float available = Math.max(0f, PREVIEW_HEIGHT_DP - 22f - style.heightDp);
        return dp(22f + available * style.y);
    }

    private void updateCapsulePreview() {
        if (capsulePreview == null || editCapsuleStyle == null) return;
        CapsuleStyle style = editCapsuleStyle;
        capsulePreview.setStyle(style);
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) capsulePreview.getLayoutParams();
        if (params != null) {
            params.height = dp(style.heightDp);
            params.topMargin = previewTop(style);
            capsulePreview.setLayoutParams(params);
        }
        capsulePreview.setAlpha(style.opacity);
        capsulePreview.setContent("执行命令", "用 执行命令…",
                System.currentTimeMillis() - 42_000, false, "tool-command", "open", 2, 4, 0);
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

    private void refreshCapsuleStatus() {
        if (capsuleStatus == null) return;
        boolean allowed = Settings.canDrawOverlays(this);
        boolean enabled = Prefs.capsuleEnabled(this);
        capsuleStatus.setText("悬浮胶囊：" + (enabled ? "已开启" : "已关闭")
                + "　悬浮窗权限：" + (allowed ? "已授权" : "未授权 · 点这里开启"));
        capsuleStatus.setTextColor(allowed ? 0xFF4CAF50 : ACCENT);
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

    private Button button(String text, int bg, int fg, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(fg);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(16));
        d.setStroke(dp(1), STROKE);
        b.setBackground(d);
        b.setOnClickListener(click);
        b.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dp(10));
        b.setLayoutParams(p);
        return b;
    }

    private LinearLayout.LayoutParams lp(int t, int b) {
        return lp(0, t, 0, b);
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private int dp(float v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
