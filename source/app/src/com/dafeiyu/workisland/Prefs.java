package com.dafeiyu.workisland;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static final String FILE = "island";
    private static final String TOKEN = "token";
    private static final String PORT = "port";
    private static final String ICON = "icon";
    private static final String SOUL = "soul";

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
}
