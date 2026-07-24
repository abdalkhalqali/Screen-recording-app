package com.jgeraldo.mediaprojectionsample;

/**
 * Video configuration for screen recording.
 * Controls frame rate, H.264/AAC bitrate (quality), and I-frame interval.
 */
public class VideoConfig {

    /** Available frame rates */
    public enum FrameRate {
        FPS_15(15, "15 fps - توفير"),
        FPS_24(24, "24 fps - سينمائي"),
        FPS_30(30, "30 fps - قياسي (افتراضي)"),
        FPS_60(60, "60 fps - سلس");

        public final int value;
        public final String label;
        FrameRate(int value, String label) { this.value = value; this.label = label; }
    }

    /** Available H.264 video bitrates (quality) */
    public enum VideoQuality {
        LOW(1_000_000, "1 Mbps - منخفض"),
        MEDIUM(2_500_000, "2.5 Mbps - متوسط"),
        HIGH(4_000_000, "4 Mbps - عالي (افتراضي)"),
        VERY_HIGH(8_000_000, "8 Mbps - عالي جداً"),
        EXTREME(12_000_000, "12 Mbps - فائق");

        public final int bitrate;
        public final String label;
        VideoQuality(int bitrate, String label) { this.bitrate = bitrate; this.label = label; }
    }

    /** I-frame (keyframe) interval options */
    public enum IFrameInterval {
        HALF_SECOND(0.5f, "0.5 ثانية - دقيق"),
        ONE_SECOND(1, "1 ثانية (افتراضي)"),
        TWO_SECONDS(2, "2 ثوانٍ - موفر"),
        FIVE_SECONDS(5, "5 ثوانٍ - اقتصادي");

        public final float value;
        public final String label;
        IFrameInterval(float value, String label) { this.value = Math.round(value); this.label = label; }
    }

    /** H.264 profile level */
    public enum CodecProfile {
        BASELINE("Baseline - توافق واسع"),
        MAIN("Main - جودة متوسطة"),
        HIGH("High - جودة عالية (افتراضي)");

        public final String label;
        CodecProfile(String label) { this.label = label; }

        public int getCodecProfileLevel() {
            switch (this) {
                case BASELINE: return android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
                case MAIN: return android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileMain;
                case HIGH: return android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
                default: return android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
            }
        }
    }

    private FrameRate frameRate = FrameRate.FPS_30;
    private VideoQuality quality = VideoQuality.HIGH;
    private IFrameInterval iFrameInterval = IFrameInterval.ONE_SECOND;
    private CodecProfile codecProfile = CodecProfile.HIGH;

    public VideoConfig() {}

    public VideoConfig(FrameRate frameRate, VideoQuality quality,
                       IFrameInterval iFrameInterval, CodecProfile codecProfile) {
        this.frameRate = frameRate;
        this.quality = quality;
        this.iFrameInterval = iFrameInterval;
        this.codecProfile = codecProfile;
    }

    public FrameRate getFrameRate() { return frameRate; }
    public void setFrameRate(FrameRate frameRate) { this.frameRate = frameRate; }

    public VideoQuality getQuality() { return quality; }
    public void setQuality(VideoQuality quality) { this.quality = quality; }

    public IFrameInterval getIFrameInterval() { return iFrameInterval; }
    public void setIFrameInterval(IFrameInterval iFrameInterval) { this.iFrameInterval = iFrameInterval; }

    public CodecProfile getCodecProfile() { return codecProfile; }
    public void setCodecProfile(CodecProfile codecProfile) { this.codecProfile = codecProfile; }

    /** Get frame rate integer value */
    public int getFrameRateValue() { return frameRate.value; }

    /** Get video bitrate */
    public int getBitrate() { return quality.bitrate; }

    /** Get I-frame interval as integer (seconds) */
    public int getIFrameIntervalValue() { return Math.round(iFrameInterval.value); }

    @Override
    public String toString() {
        return String.format("%s | %s | %s | %s",
                frameRate.label, quality.label, iFrameInterval.label, codecProfile.label);
    }
}
