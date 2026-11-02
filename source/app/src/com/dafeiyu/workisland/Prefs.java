package com.dafeiyu.workisland;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileWriter;

final class Prefs {
    private static final String FILE = "island";
    private static final String TOKEN = "token";
    private static final String PORT = "port";
    private static final String ICON = "icon";
    private static final String SOUL = "soul";
    private static final String ISLAND_ANIM = "islandAnim";
    private static final String KEEP_ALIVE = "keepAlive";
    private static final String SYSTEM_ISLAND = "systemIsland";
    private static final String CAPSULE_ON = "capsuleOn";
    private static final String KEEP_ALIVE_RESET = "keepAliveResetOnce";
    private static final String CAPSULE_TEXT = "capsuleText";
    private static final String CAPSULE_HEIGHT = "capsuleHeight";
    private static final String CAPSULE_Y = "capsuleY";
    private static final String CAPSULE_OPACITY = "capsuleOpacity";

    private Prefs() {}

    static void save(Context c, String token, int port) {
        SharedPreferences p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        p.edit().putString(TOKEN, token).putInt(PORT, port).apply();
    }

    static String token(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(TOKEN, "");
    }

    static int port(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(PORT, 8791);
    }

    static String icon(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(ICON, "hud");
    }

    static void setIcon(Context c, String value) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(ICON, value).apply();
    }

    /** 程序化 bot（四态 + 标识 + 每个 agent 的体征）；关掉就退回旧的简笔图标。 */
    static boolean soul(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(SOUL, true);
    }

    static void setSoul(Context c, boolean value) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(SOUL, value).apply();
    }

    /** 岛上动画：0 关闭 / 1 标准（4 帧）/ 2 流畅（8 帧）。应用内预览始终 60fps，不受影响。 */
    static int islandAnim(Context c) {
        int v = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(ISLAND_ANIM, 1);
        return v < 0 || v > 2 ? 1 : v;
    }

    static void setIslandAnim(Context c, int value) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(ISLAND_ANIM, value).apply();
        writeAnimTier(c);
    }

    /**
     * driver 跑在 cccodroid 里，读不到本应用的 SharedPreferences；
     * 用 Android/media 下的一个小文件当信道（本应用可写、对方有读存储权限）。
     */
    static void writeAnimTier(Context c) {
        writeMedia(c, "anim.conf", String.valueOf(islandAnim(c)));
    }

    /**
     * 保活开关：外部的看门狗读这个文件决定是否拉起 / 停止 driver。
     * 0.5.14 用进程内订阅，不需要旧 driver，所以默认关闭；
     * 0.4.2–0.4.6 的用户可以手动打开。
     */
    static boolean keepAlive(Context c) {
        SharedPreferences p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        // 从旧版升级时强制关一次：0.5.14 不再需要独立 driver，旧偏好可能是 true。
        if (!p.getBoolean(KEEP_ALIVE_RESET, false)) {
            p.edit().putBoolean(KEEP_ALIVE_RESET, true)
                    .putBoolean(KEEP_ALIVE, false).commit();
            writeMedia(c, "keepalive.conf", "0");
        }
        return p.getBoolean(KEEP_ALIVE, false);
    }

    static void setKeepAlive(Context c, boolean value) {
        // ColorOS 会很快冻结后台应用；apply() 的异步落盘可能还没完成进程就被杀，
        // 服务重建时会读到旧的 true，又把 keepalive.conf 写回 1。这里必须同步写。
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putBoolean(KEEP_ALIVE, value).commit();
        writeKeepAlive(c);
    }

    static void writeKeepAlive(Context c) {
        writeMedia(c, "keepalive.conf", keepAlive(c) ? "1" : "0");
    }

    /** 系统流体云 / 灵动岛：是否请求 Android 16 的 promoted 实时更新。 */
    static boolean systemIsland(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getBoolean(SYSTEM_ISLAND, true);
    }

    static void setSystemIsland(Context c, boolean value) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putBoolean(SYSTEM_ISLAND, value).apply();
    }

    /**
     * 应用自己画的悬浮胶囊。第一次使用时按设备能力给默认值：
     * 系统拿不到 promoted 实时更新（多数没有流体云的设备）就默认打开兜底，
     * 能上岛的设备默认关闭，避免和系统岛重复。用户手动拨过之后以开关为准。
     */
    static boolean capsuleEnabled(Context c) {
        SharedPreferences p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        if (p.contains(CAPSULE_ON)) return p.getBoolean(CAPSULE_ON, false);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm == null || !nm.canPostPromotedNotifications();
    }

    static void setCapsuleEnabled(Context c, boolean value) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putBoolean(CAPSULE_ON, value).apply();
    }

    /** 悬浮胶囊的字号 / 高度 / 垂直位置 / 不透明度。 */
    static CapsuleStyle capsuleStyle(Context c) {
        SharedPreferences p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        return new CapsuleStyle(
                p.getFloat(CAPSULE_TEXT, CapsuleStyle.DEFAULT_TEXT_SP),
                p.getFloat(CAPSULE_HEIGHT, CapsuleStyle.DEFAULT_HEIGHT_DP),
                p.getFloat(CAPSULE_Y, CapsuleStyle.DEFAULT_Y),
                p.getFloat(CAPSULE_OPACITY, CapsuleStyle.DEFAULT_OPACITY));
    }

    static void setCapsuleStyle(Context c, CapsuleStyle style) {
        CapsuleStyle safe = style == null ? CapsuleStyle.defaults() : style;
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putFloat(CAPSULE_TEXT, safe.textSp)
                .putFloat(CAPSULE_HEIGHT, safe.heightDp)
                .putFloat(CAPSULE_Y, safe.y)
                .putFloat(CAPSULE_OPACITY, safe.opacity)
                .apply();
    }

    private static void writeMedia(Context c, String name, String value) {
        try {
            File[] dirs = c.getExternalMediaDirs();
            if (dirs == null || dirs.length == 0 || dirs[0] == null) return;
            File dir = dirs[0];
            if (!dir.exists()) dir.mkdirs();
            try (FileWriter w = new FileWriter(new File(dir, name))) {
                w.write(value);
            }
        } catch (Throwable ignored) {}
    }
}
