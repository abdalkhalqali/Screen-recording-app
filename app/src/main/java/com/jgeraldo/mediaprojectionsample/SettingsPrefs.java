package com.jgeraldo.mediaprojectionsample;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * حفظ واسترجاع إعدادات التطبيق
 */
public class SettingsPrefs {
    private static final String PREFS_NAME = "screen_recorder_prefs";
    private final SharedPreferences prefs;

    public SettingsPrefs(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setAudioSource(int value) {
        prefs.edit().putInt("audio_source", value).apply();
    }

    public int getAudioSource(int defaultValue) {
        return prefs.getInt("audio_source", defaultValue);
    }

    public void setCaptureMode(int value) {
        prefs.edit().putInt("capture_mode", value).apply();
    }

    public int getCaptureMode(int defaultValue) {
        return prefs.getInt("capture_mode", defaultValue);
    }

    public void setVideoResolution(int value) {
        prefs.edit().putInt("video_resolution", value).apply();
    }

    public int getVideoResolution(int defaultValue) {
        return prefs.getInt("video_resolution", defaultValue);
    }

    public void setDelaySeconds(int value) {
        prefs.edit().putInt("delay_seconds", value).apply();
    }

    public int getDelaySeconds(int defaultValue) {
        return prefs.getInt("delay_seconds", defaultValue);
    }

    public void setMaxDurationSeconds(int value) {
        prefs.edit().putInt("max_duration", value).apply();
    }

    public int getMaxDurationSeconds(int defaultValue) {
        return prefs.getInt("max_duration", defaultValue);
    }

    public void saveRegion(float left, float top, float right, float bottom) {
        prefs.edit()
                .putFloat("region_left", left)
                .putFloat("region_top", top)
                .putFloat("region_right", right)
                .putFloat("region_bottom", bottom)
                .apply();
    }

    public float[] loadRegion() {
        if (!prefs.contains("region_left")) return null;
        return new float[]{
                prefs.getFloat("region_left", 0),
                prefs.getFloat("region_top", 0),
                prefs.getFloat("region_right", 1),
                prefs.getFloat("region_bottom", 1)
        };
    }

    public void setRegionLocked(boolean locked) {
        prefs.edit().putBoolean("region_locked", locked).apply();
    }

    public boolean isRegionLocked(boolean defaultValue) {
        return prefs.getBoolean("region_locked", defaultValue);
    }

    public void setAutoPauseMode(int value) {
        prefs.edit().putInt("auto_pause_mode", value).apply();
    }

    public int getAutoPauseMode(int defaultValue) {
        return prefs.getInt("auto_pause_mode", defaultValue);
    }
}
