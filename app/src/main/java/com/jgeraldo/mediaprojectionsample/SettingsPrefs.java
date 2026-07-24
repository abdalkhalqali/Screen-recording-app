package com.jgeraldo.mediaprojectionsample;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages app settings persistence using SharedPreferences.
 * Saves/restores audio config, capture mode, region, timer, and resolution.
 */
public class SettingsPrefs {

    private static final String PREFS_NAME = "screen_recorder_prefs";

    // Keys
    private static final String KEY_AUDIO_SOURCE = "audio_source";
    private static final String KEY_SAMPLE_RATE = "sample_rate";
    private static final String KEY_AUDIO_QUALITY = "audio_quality";
    private static final String KEY_NOISE_SUPPRESSION = "noise_suppression";
    private static final String KEY_CAPTURE_MODE = "capture_mode";
    private static final String KEY_DELAY_SECONDS = "delay_seconds";
    private static final String KEY_MAX_DURATION_SECONDS = "max_duration_seconds";
    private static final String KEY_VIDEO_RESOLUTION = "video_resolution";
    // Video Config keys
    private static final String KEY_VIDEO_FRAME_RATE = "video_frame_rate";
    private static final String KEY_VIDEO_QUALITY = "video_quality";
    private static final String KEY_VIDEO_I_FRAME_INTERVAL = "video_i_frame_interval";
    private static final String KEY_VIDEO_CODEC_PROFILE = "video_codec_profile";

    private static final String KEY_REGION_LOCKED = "region_locked";
    private static final String KEY_REGION_LEFT = "region_left";
    private static final String KEY_REGION_TOP = "region_top";
    private static final String KEY_REGION_RIGHT = "region_right";
    private static final String KEY_REGION_BOTTOM = "region_bottom";

    private final SharedPreferences prefs;

    // Resolution options
    public enum VideoResolution {
        RES_480P(640, 480, "480p"),
        RES_720P(1280, 720, "720p (HD)"),
        RES_1080P(1920, 1080, "1080p (Full HD)");

        public final int width;
        public final int height;
        public final String label;
        VideoResolution(int width, int height, String label) {
            this.width = width;
            this.height = height;
            this.label = label;
        }
    }

    public SettingsPrefs(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Audio Source ---
    public void setAudioSource(int ordinal) {
        prefs.edit().putInt(KEY_AUDIO_SOURCE, ordinal).apply();
    }
    public int getAudioSource(int defaultVal) {
        return prefs.getInt(KEY_AUDIO_SOURCE, defaultVal);
    }

    // --- Sample Rate ---
    public void setSampleRate(int ordinal) {
        prefs.edit().putInt(KEY_SAMPLE_RATE, ordinal).apply();
    }
    public int getSampleRate(int defaultVal) {
        return prefs.getInt(KEY_SAMPLE_RATE, defaultVal);
    }

    // --- Audio Quality ---
    public void setAudioQuality(int ordinal) {
        prefs.edit().putInt(KEY_AUDIO_QUALITY, ordinal).apply();
    }
    public int getAudioQuality(int defaultVal) {
        return prefs.getInt(KEY_AUDIO_QUALITY, defaultVal);
    }

    // --- Noise Suppression ---
    public void setNoiseSuppression(int ordinal) {
        prefs.edit().putInt(KEY_NOISE_SUPPRESSION, ordinal).apply();
    }
    public int getNoiseSuppression(int defaultVal) {
        return prefs.getInt(KEY_NOISE_SUPPRESSION, defaultVal);
    }

    // --- Capture Mode (0=SCREENSHOT, 1=VIDEO, 2=BOTH) ---
    public void setCaptureMode(int mode) {
        prefs.edit().putInt(KEY_CAPTURE_MODE, mode).apply();
    }
    public int getCaptureMode(int defaultVal) {
        return prefs.getInt(KEY_CAPTURE_MODE, defaultVal);
    }

    // --- Delay Timer (seconds) ---
    public void setDelaySeconds(int seconds) {
        prefs.edit().putInt(KEY_DELAY_SECONDS, seconds).apply();
    }
    public int getDelaySeconds(int defaultVal) {
        return prefs.getInt(KEY_DELAY_SECONDS, defaultVal);
    }

    // --- Max Duration (seconds, 0 = no limit) ---
    public void setMaxDurationSeconds(int seconds) {
        prefs.edit().putInt(KEY_MAX_DURATION_SECONDS, seconds).apply();
    }
    public int getMaxDurationSeconds(int defaultVal) {
        return prefs.getInt(KEY_MAX_DURATION_SECONDS, defaultVal);
    }

    // --- Video Resolution ---
    public void setVideoResolution(int ordinal) {
        prefs.edit().putInt(KEY_VIDEO_RESOLUTION, ordinal).apply();
    }
    public int getVideoResolution(int defaultVal) {
        return prefs.getInt(KEY_VIDEO_RESOLUTION, defaultVal);
    }

    // --- Region Lock ---
    public void setRegionLocked(boolean locked) {
        prefs.edit().putBoolean(KEY_REGION_LOCKED, locked).apply();
    }
    public boolean isRegionLocked(boolean defaultVal) {
        return prefs.getBoolean(KEY_REGION_LOCKED, defaultVal);
    }

    // --- Region coordinates (normalized 0-1) ---
    public void saveRegion(float left, float top, float right, float bottom) {
        prefs.edit()
                .putFloat(KEY_REGION_LEFT, left)
                .putFloat(KEY_REGION_TOP, top)
                .putFloat(KEY_REGION_RIGHT, right)
                .putFloat(KEY_REGION_BOTTOM, bottom)
                .apply();
    }

    public float[] loadRegion() {
        float left = prefs.getFloat(KEY_REGION_LEFT, -1f);
        if (left < 0) return null; // No saved region
        return new float[]{
                left,
                prefs.getFloat(KEY_REGION_TOP, 0.2f),
                prefs.getFloat(KEY_REGION_RIGHT, 0.8f),
                prefs.getFloat(KEY_REGION_BOTTOM, 0.8f)
        };
    }

    /** Save all AudioConfig settings at once */
    public void saveAudioConfig(AudioConfig config) {
        prefs.edit()
                .putInt(KEY_SAMPLE_RATE, config.getSampleRate().ordinal())
                .putInt(KEY_AUDIO_QUALITY, config.getQuality().ordinal())
                .putInt(KEY_NOISE_SUPPRESSION, config.getNoiseSuppression().ordinal())
                .apply();
    }

    /** Load saved AudioConfig or return the default */
    public AudioConfig loadAudioConfig() {
        int sampleRateOrd = prefs.getInt(KEY_SAMPLE_RATE, -1);
        int qualityOrd = prefs.getInt(KEY_AUDIO_QUALITY, -1);
        int noiseOrd = prefs.getInt(KEY_NOISE_SUPPRESSION, -1);

        if (sampleRateOrd < 0) return new AudioConfig(); // default

        AudioConfig.SampleRate sr = AudioConfig.SampleRate.values()[
                Math.min(sampleRateOrd, AudioConfig.SampleRate.values().length - 1)];
        AudioConfig.AudioQuality q = AudioConfig.AudioQuality.values()[
                Math.min(qualityOrd, AudioConfig.AudioQuality.values().length - 1)];
        AudioConfig.NoiseSuppression ns = AudioConfig.NoiseSuppression.values()[
                Math.min(noiseOrd, AudioConfig.NoiseSuppression.values().length - 1)];

        return new AudioConfig(sr, q, ns);
    }

    // --- Video Config ---

    /** Save all VideoConfig settings at once */
    public void saveVideoConfig(VideoConfig config) {
        prefs.edit()
                .putInt(KEY_VIDEO_FRAME_RATE, config.getFrameRate().ordinal())
                .putInt(KEY_VIDEO_QUALITY, config.getQuality().ordinal())
                .putInt(KEY_VIDEO_I_FRAME_INTERVAL, config.getIFrameInterval().ordinal())
                .putInt(KEY_VIDEO_CODEC_PROFILE, config.getCodecProfile().ordinal())
                .apply();
    }

    /** Load saved VideoConfig or return the default */
    public VideoConfig loadVideoConfig() {
        int frameRateOrd = prefs.getInt(KEY_VIDEO_FRAME_RATE, -1);
        int qualityOrd = prefs.getInt(KEY_VIDEO_QUALITY, -1);
        int iFrameOrd = prefs.getInt(KEY_VIDEO_I_FRAME_INTERVAL, -1);
        int codecOrd = prefs.getInt(KEY_VIDEO_CODEC_PROFILE, -1);

        if (frameRateOrd < 0) return new VideoConfig(); // default

        VideoConfig.FrameRate fr = VideoConfig.FrameRate.values()[
                Math.min(frameRateOrd, VideoConfig.FrameRate.values().length - 1)];
        VideoConfig.VideoQuality q = VideoConfig.VideoQuality.values()[
                Math.min(qualityOrd, VideoConfig.VideoQuality.values().length - 1)];
        VideoConfig.IFrameInterval ifi = VideoConfig.IFrameInterval.values()[
                Math.min(iFrameOrd, VideoConfig.IFrameInterval.values().length - 1)];
        VideoConfig.CodecProfile cp = VideoConfig.CodecProfile.values()[
                Math.min(codecOrd, VideoConfig.CodecProfile.values().length - 1)];

        return new VideoConfig(fr, q, ifi, cp);
    }

    /** Clear all settings */
    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
