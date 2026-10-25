package com.dafeiyu.workisland;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.util.Log;

/**
 * 工作岛的前台服务：只负责画。状态由外面的 driver（跑在 cccodroid 网关里）通过
 * am start-foreground-service 送进来。服务自己不做定时器、不订阅网络 ——
 * ColorOS 会在后台把本应用冻住，任何进程内定时器都不可靠。
 */
public class IslandService extends Service {
    public static final String ACTION_IDLE = "com.dafeiyu.workisland.IDLE";
    private static final int ID = Notifier.ID;
    private static final int IDLE_ID = 1002;

    private boolean working = false;
    private long startedAt = 0;
    private String lastKey = "";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (action == null) action = Notifier.ACTION_UPDATE;

        if (Notifier.ACTION_CANCEL.equals(action)) {
            working = false;
            getSystemService(NotificationManager.class).cancel(ID);
            getSystemService(NotificationManager.class).cancel(IDLE_ID);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_IDLE.equals(action)) {
            goIdle(intent.getStringExtra("title"), intent.getStringExtra("text"));
            return START_STICKY;
        }
        if (Notifier.ACTION_DONE.equals(action)) {
            finishRun(intent.getStringExtra("title"), intent.getStringExtra("text"));
            return START_STICKY;
        }

        startWorking(intent.getLongExtra("since", System.currentTimeMillis()),
                intent.getStringExtra("text"), intent.getStringExtra("title"));
        return START_STICKY;
    }

    private synchronized void startWorking(long at, String text, String title) {
        if (working) {
            updateWorking(text, title);
            return;
        }
        working = true;
        startedAt = at > 0 ? at : System.currentTimeMillis();
        lastKey = "";
        getSystemService(NotificationManager.class).cancel(IDLE_ID);
        String t = title == null || title.isEmpty() ? "正在干活" : title;
        String x = text == null ? "正在处理…" : text;
        Notification n = Notifier.build(this, t, x, startedAt, false);
        startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        lastKey = t + "|" + x;
        Log.d("WorkIsland", "work start " + lastKey);
    }

    private synchronized void updateWorking(String text, String title) {
        if (!working) {
            startWorking(System.currentTimeMillis(), text, title);
            return;
        }
        String t = title == null || title.isEmpty() ? "正在干活" : title;
        String x = text == null ? "" : text;
        String key = t + "|" + x;
        if (key.equals(lastKey)) return;
        lastKey = key;
        getSystemService(NotificationManager.class).cancel(IDLE_ID);
        Notification n = Notifier.build(this, t, x, startedAt, false);
        startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        Log.d("WorkIsland", "work update " + key);
    }

    private synchronized void finishRun(String title, String text) {
        if (!working) return;
        working = false;
        long ms = Math.max(0, System.currentTimeMillis() - startedAt);
        long sec = Math.round(ms / 1000.0);
        String human = sec < 60 ? sec + " 秒" : (sec / 60) + " 分 " + (sec % 60) + " 秒";
        String fallback = "用时 " + human;
        String t = title == null || title.isEmpty() ? "任务完成" : title;
        String x = text == null || text.isEmpty() ? fallback : text;
        getSystemService(NotificationManager.class).cancel(IDLE_ID);
        Notification n = Notifier.build(this, t, x, System.currentTimeMillis(), true);
        startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        Log.d("WorkIsland", "work done " + t + " / " + x);
    }

    /** 网关在完成 8 秒后发来 IDLE：换成不上岛的安静通知，岛就收起来了。 */
    private synchronized void goIdle(String title, String text) {
        if (working) return;
        Notification n = Notifier.buildIdle(this, title, text);
        startForeground(IDLE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        getSystemService(NotificationManager.class).cancel(ID);
        lastKey = "";
        Log.d("WorkIsland", "work idle");
    }
}
