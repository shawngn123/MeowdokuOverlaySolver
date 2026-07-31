package com.shawngn123.meowdokuoverlaysolver;

import android.content.Context;
import android.content.SharedPreferences;

final class OverlayPreferences {
    static final String NAME = "overlay_preferences";
    static final String KEY_ALLOW_BUTTON_REPOSITIONING = "allow_button_repositioning";
    static final boolean DEFAULT_ALLOW_BUTTON_REPOSITIONING = true;

    private OverlayPreferences() { }

    static boolean allowButtonRepositioning(Context context) {
        return preferences(context).getBoolean(KEY_ALLOW_BUTTON_REPOSITIONING, DEFAULT_ALLOW_BUTTON_REPOSITIONING);
    }

    static void setAllowButtonRepositioning(Context context, boolean allow) {
        preferences(context).edit().putBoolean(KEY_ALLOW_BUTTON_REPOSITIONING, allow).apply();
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
