package com.dafeiyu.workisland;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.Log;

public final class Notifier {
    public static final String ACTION_START = "com.dafeiyu.workisland.START";
    public static final String ACTION_UPDATE = "com.dafeiyu.workisland.UPDATE";
    public static final String ACTION_DONE = "com.dafeiyu.workisland.DONE";
    public static final String ACTION_CANCEL = "com.dafeiyu.workisland.CANCEL";

    public static final int ID = 1001;
    private static final String CHANNEL = "work";
    private static final String EXTRA_PROMOTED = "android.requestPromotedOngoing";
    private static final int ACCENT = 0xFFD97757;

    private Notifier() {}

    static Notification build(Context c, String title, String text, long since, boolean done, String state) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        ensureChannel(nm);

        String st = state == null || state.isEmpty() ? (done ? "done" : "working") : state;
        Icon small = smallIcon(c, st);
        Icon large = largeIcon(c);
        int color = statusColor(st);

        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.ProgressStyle style = new Notification.ProgressStyle()
                .setProgressTrackerIcon(small)
                .setStyledByProgress(true);
        if (done) {
            style.setProgress(100);
            style.setProgressEndIcon(Icon.createWithResource(c, R.drawable.ic_done));
        } else {
            style.setProgressIndeterminate(true);
        }

        if (title == null || title.isEmpty()) title = done ? "任务完成" : "正在干活";
        if (text == null) text = "";

        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(small)
                .setLargeIcon(large)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText("cccodroid")
                .setColor(color)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(done ? 8000L : 30 * 60_000L)
                .setShowWhen(true)
                .setWhen(since > 0 ? since : System.currentTimeMillis())
                .setUsesChronometer(!done)
                .setContentIntent(pi)
                .setStyle(style);

        Bundle extras = new Bundle();
        extras.putBoolean(EXTRA_PROMOTED, true);
        b.addExtras(extras);
        try {
            Notification.Builder.class.getMethod("setRequestPromotedOngoing", boolean.class).invoke(b, true);
        } catch (Throwable ignored) {}

        Notification n = b.build();
        Log.d("WorkIsland", "build title=" + title + " text=" + text + " state=" + st
                + " icon=" + Prefs.icon(c)
                + " promotable=" + n.hasPromotableCharacteristics()
                + " canPost=" + nm.canPostPromotedNotifications());
        return n;
    }

    /** 收工后留在通知栏的安静版前台通知：不再请求上岛。 */
    static Notification buildIdle(Context c, String title, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        ensureChannel(nm);
        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(c, CHANNEL)
                .setSmallIcon(smallIcon(c, "idle"))
                .setLargeIcon(largeIcon(c))
                .setContentTitle(title == null || title.isEmpty() ? "工作岛" : title)
                .setContentText(text == null || text.isEmpty() ? "在后台等下一轮" : text)
                .setColor(0xFF8A8A8A)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(pi)
                .build();
    }

    /** 小图标：当前选中的模型图标（白色蒙版）+ 右下角状态标记。 */
    private static Icon smallIcon(Context c, String state) {
        Drawable d = iconDrawable(c, Prefs.icon(c));
        int size = 128;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        d.setBounds(0, 0, size, size);
        d.setColorFilter(new PorterDuffColorFilter(statusColor(state), PorterDuff.Mode.SRC_IN));
        d.draw(canvas);

        if (!"idle".equals(state)) {
            Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
            clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            canvas.drawCircle(size - 30, size - 30, 26, clear);
            drawMarker(canvas, state, size - 30, size - 30);
        }
        return Icon.createWithBitmap(bmp);
    }

    private static void drawMarker(Canvas c, String state, float cx, float cy) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        if ("done".equals(state)) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7f);
            p.setStrokeCap(Paint.Cap.ROUND);
            Path path = new Path();
            path.moveTo(cx - 12, cy + 1);
            path.lineTo(cx - 3, cy + 10);
            path.lineTo(cx + 14, cy - 12);
            c.drawPath(path, p);
        } else if ("error".equals(state)) {
            p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(cx - 3.5f, cy - 14, cx + 3.5f, cy + 3, 4, 4, p);
            c.drawCircle(cx, cy + 11, 3.5f, p);
        } else if ("waiting".equals(state)) {
            p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(cx - 9, cy - 11, cx - 4, cy + 11, 3, 3, p);
            c.drawRoundRect(cx + 4, cy - 11, cx + 9, cy + 11, 3, 3, p);
        } else {
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, 9, p);
        }
    }

    private static Drawable iconDrawable(Context c, String choice) {
        int res;
        switch (choice == null ? "hud" : choice) {
            case "deepseek": res = R.drawable.model_deepseek; break;
            case "claude": res = R.drawable.model_claude; break;
            case "openclaw": res = R.drawable.model_openclaw; break;
            case "fish": res = R.drawable.ic_fish; break;
            default: res = R.drawable.ic_hud;
        }
        return c.getDrawable(res);
    }

    private static Icon largeIcon(Context c) {
        String choice = Prefs.icon(c);
        int res;
        switch (choice == null ? "hud" : choice) {
            case "deepseek": res = R.drawable.model_deepseek; break;
            case "claude": res = R.drawable.model_claude; break;
            case "openclaw": res = R.drawable.model_openclaw; break;
            case "fish": res = R.drawable.ic_fish; break;
            default: res = R.mipmap.ic_launcher;
        }
        return Icon.createWithResource(c, res);
    }

    private static int statusColor(String state) {
        switch (state == null ? "" : state) {
            case "thinking": return 0xFF5B8DEF;
            case "reading": return 0xFF7C6CF0;
            case "writing": return 0xFFD97757;
            case "running": return 0xFF4C9AFF;
            case "searching": return 0xFF00A6A6;
            case "waiting": return 0xFFE6A23C;
            case "done": return 0xFF4CAF50;
            case "error": return 0xFFE5484D;
            case "idle": return 0xFF8A8A8A;
            default: return ACCENT;
        }
    }

    static void post(Context c, String title, String text, long since, boolean done, String state) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.notify(ID, build(c, title, text, since, done, state));
    }

    static void post(Context c, String title, String text, long since, boolean done) {
        post(c, title, text, since, done, done ? "done" : "working");
    }

    static void cancel(Context c) {
        c.getSystemService(NotificationManager.class).cancel(ID);
    }

    private static void ensureChannel(NotificationManager nm) {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "工作状态", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Claude 正在工作、任务完成时显示在灵动岛");
        ch.setShowBadge(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }
}
