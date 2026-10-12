package com.diasoft.calaws.data;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public final class Prefs {

    private static final String FILE = "ca_laws_prefs";

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static float getFontScale(Context c) {
        return p(c).getFloat("font_scale", 1f);
    }

    public static void setFontScale(Context c, float v) {
        p(c).edit().putFloat("font_scale", v).apply();
    }

    public static int getNightMode(Context c) {
        return p(c).getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public static void setNightMode(Context c, int mode) {
        p(c).edit().putInt("night_mode", mode).apply();
    }

    /** "en" or "fr": selects both the law database and the UI language. */
    public static String getLang(Context c) {
        return p(c).getString("lang", "en");
    }

    public static void setLang(Context c, String lang) {
        p(c).edit().putString("lang", lang).apply();
    }
}
