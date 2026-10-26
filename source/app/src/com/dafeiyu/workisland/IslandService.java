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
    public void onCreate() {
        super.onCreate();
        // 只在服务创建时写一次档位文件；别在每条 UPDATE 上重写，否则会盖掉调试时手改的值
        Prefs.writeAnimTier(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 系统在进程被杀后会用 null intent 重启 START_STICKY 服务；没有意图就没有状态可画。
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
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
        int step = intent.getIntExtra("step", 0);
        int todoDone = intent.getIntExtra("todoDone", 0);
        int todoTotal = intent.getIntExtra("todoTotal", 0);
        int morph = intent.getIntExtra("morph", -1);
        int anim = intent.getIntExtra("anim", 0);
        if (Notifier.ACTION_DONE.equals(action)) {
            finishRun(intent.getStringExtra("title"), intent.getStringExtra("text"),
                    step, todoDone, todoTotal);
            return START_STICKY;
        }

        startWorking(intent.getLongExtra("since", System.currentTimeMillis()),
                intent.getStringExtra("text"), intent.getStringExtra("title"),
                intent.getStringExtra("state"), intent.getStringExtra("face"),
                step, todoDone, todoTotal, morph, anim);
        return START_STICKY;
    }

    private synchronized void startWorking(long at, String text, String title, String state, String face,
                                           int step, int todoDone, int todoTotal, int morph, int anim) {
        if (working) {
            updateWorking(text, title, state, face, step, todoDone, todoTotal, morph, anim);
            return;
        }
        working = true;
        startedAt = at > 0 ? at : System.currentTimeMillis();
        lastKey = "";
        String t = title == null || title.isEmpty() ? "正在干活" : title;
        String x = text == null ? "正在处理…" : text;
        String st = state == null || state.isEmpty() ? "working" : state;
        String f = face == null || face.isEmpty() ? "open" : face;
        Notification n = Notifier.build(this, t, x, startedAt, false, st, f, step, todoDone, todoTotal, morph, anim);
        post(n);
        // 先让新通知上岛，再撤掉待机的旧通知：反过来会空一小段，胶囊就先收回去再弹出来。
        getSystemService(NotificationManager.class).cancel(IDLE_ID);
        lastKey = t + "|" + x + "|" + st + "|" + f + "|" + step + "|" + todoDone + "/" + todoTotal + "|" + morph + "|" + anim;
        Log.d("WorkIsland", "work start " + lastKey);
    }

    private synchronized void updateWorking(String text, String title, String state, String face,
                                            int step, int todoDone, int todoTotal, int morph, int anim) {
        if (!working) {
            startWorking(System.currentTimeMillis(), text, title, state, face, step, todoDone, todoTotal, morph, anim);
            return;
        }
        String t = title == null || title.isEmpty() ? "正在干活" : title;
        String x = text == null ? "" : text;
        String st = state == null || state.isEmpty() ? "working" : state;
        String f = face == null || face.isEmpty() ? "open" : face;
        String key = t + "|" + x + "|" + st + "|" + f + "|" + step + "|" + todoDone + "/" + todoTotal + "|" + morph + "|" + anim;
        if (key.equals(lastKey)) return;
        lastKey = key;
        Notification n = Notifier.build(this, t, x, startedAt, false, st, f, step, todoDone, todoTotal, morph, anim);
        post(n);
        Log.d("WorkIsland", "work update " + key);
    }

    /**
     * 永远用同一个通知 ID：换 ID 时胶囊会收回再弹出（主人叫它「抽搐」）。
     * 同一个 ID 的更新本来就能换前导图标（thinking 和 error 两态实测像素不同），
     * 所以思考时只换 bitmap，不动通知的身份。
     */
    private void post(Notification n) {
        startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    private synchronized void finishRun(String title, String text, int step, int todoDone, int todoTotal) {
        if (!working) return;
        working = false;
        long ms = Math.max(0, System.currentTimeMillis() - startedAt);
        long sec = Math.round(ms / 1000.0);
        String human = sec < 60 ? sec + " 秒" : (sec / 60) + " 分 " + (sec % 60) + " 秒";
        String fallback = "用时 " + human;
        String t = title == null || title.isEmpty() ? "任务完成" : title;
        String x = text == null || text.isEmpty() ? fallback : text;
        getSystemService(NotificationManager.class).cancel(IDLE_ID);
        Notification n = Notifier.build(this, t, x, System.currentTimeMillis(), true, "done", "happy",
                step, todoTotal > 0 ? todoTotal : todoDone, todoTotal);
        post(n);
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
