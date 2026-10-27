package com.dafeiyu.workisland;

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

    /** 保活开关：外部的看门狗读这个文件决定是否拉起 / 停止 driver。 */
    static boolean keepAlive(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEEP_ALIVE, true);
    }

    static void setKeepAlive(Context c, boolean value) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEEP_ALIVE, value).apply();
        writeKeepAlive(c);
    }

    static void writeKeepAlive(Context c) {
        writeMedia(c, "keepalive.conf", keepAlive(c) ? "1" : "0");
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
