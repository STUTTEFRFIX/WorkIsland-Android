package com.dafeiyu.workisland;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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

    static Notification build(Context c, String title, String text, long since, boolean done) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        ensureChannel(nm);

        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.ProgressStyle style = new Notification.ProgressStyle()
                .setProgressTrackerIcon(Icon.createWithResource(c, R.drawable.ic_fish))
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
                .setSmallIcon(R.drawable.ic_fish)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText("cccodroid")
                .setColor(ACCENT)
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
        Log.d("WorkIsland", "build title=" + title + " text=" + text
                + " promotable=" + n.hasPromotableCharacteristics()
                + " canPost=" + nm.canPostPromotedNotifications()
                + " flags=" + Integer.toHexString(n.flags));
        return n;
    }

    static void post(Context c, String title, String text, long since, boolean done) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.notify(ID, build(c, title, text, since, done));
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
                .setSmallIcon(R.drawable.ic_fish)
                .setContentTitle(title == null || title.isEmpty() ? "工作岛" : title)
                .setContentText(text == null || text.isEmpty() ? "在后台等下一轮" : text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(pi)
                .build();
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
