package com.dafeiyu.workisland;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int BG = 0xFF1F1E1D;
    private static final int CARD = 0xFF282725;
    private static final int STROKE = 0xFF3B3936;
    private static final int FG = 0xFFFAF9F5;
    private static final int MUTED = 0xFFA9A49B;
    private static final int ACCENT = 0xFFD97757;

    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(24), dp(40), dp(24), dp(24));
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
        root.addView(sub, lp(0, 0, 0, 24));

        status = new TextView(this);
        status.setTextColor(ACCENT);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(dp(3), 1f);
        root.addView(status, lp(0, 0, 0, 22));

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
        ilp.setMargins(0, dp(18), 0, dp(6));
        root.addView(iconLabel, ilp);

        final String[] iconNames = {"Agent HUD", "DeepSeek", "Claude", "OpenClaw", "小鱼"};
        final String[] iconKeys = {"hud", "deepseek", "claude", "openclaw", "fish"};
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
                try {
                    startForegroundService(new Intent(MainActivity.this, IslandService.class)
                            .setAction(IslandService.ACTION_IDLE));
                } catch (Throwable ignored) {}
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(dp(12), 0, dp(12), dp(10));
        root.addView(iconSpinner, slp);

        TextView iconNote = new TextView(this);
        iconNote.setText("模型图标来自 LobeHub（MIT）；商标归各自所有者，非官方关联。");
        iconNote.setTextColor(0xFF6F6B64);
        iconNote.setTextSize(11);
        iconNote.setGravity(Gravity.CENTER);
        root.addView(iconNote, lp(0, 0, 0, 8));

        TextView hint = new TextView(this);
        hint.setText("如果岛上没出现：系统设置 → 通知 → 工作岛 → 允许实时更新");
        hint.setTextColor(0xFF6F6B64);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, lp(0, 26, 0, 0));

        setContentView(root);
        ensureService();

        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
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

    private void demo(String action) {
        Intent i = new Intent(action);
        i.setClass(this, IslandReceiver.class);
        boolean done = Notifier.ACTION_DONE.equals(action);
        i.putExtra("title", done ? "任务完成" : "Claude 正在干活");
        i.putExtra("text", done ? "测试完成" : "正在用 Bash…");
        i.putExtra("state", done ? "done" : "running");
        i.putExtra("since", System.currentTimeMillis());
        sendBroadcast(i);
    }

    private View button(String text, int bg, int fg, View.OnClickListener click) {
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
}
