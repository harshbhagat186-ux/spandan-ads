package com.spandan.ads;

import android.content.Context;

/** App language: English (default) or Hindi, switched from the top bar. */
final class L {
    private static final String PREFS = "lang";
    private static boolean hindi;

    private L() {}

    static void load(Context ctx) {
        hindi = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("hindi", false);
    }

    static void toggle(Context ctx) {
        hindi = !hindi;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("hindi", hindi).apply();
    }

    static boolean isHindi() {
        return hindi;
    }

    static String t(String en, String hi) {
        return hindi ? hi : en;
    }
}
