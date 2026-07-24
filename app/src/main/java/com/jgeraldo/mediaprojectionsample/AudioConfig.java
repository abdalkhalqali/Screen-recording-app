package com.jgeraldo.mediaprojectionsample;

/**
 * Audio configuration for screen recording.
 * Controls sample rate, bitrate (quality), and noise suppression.
 */
public class AudioConfig {

    /** Available sample rates */
    public enum SampleRate {
        RATE_8000(8000, "8 kHz - اتصال"),
        RATE_11025(11025, "11 kHz - مكالمات"),
        RATE_16000(16000, "16 kHz - صوت معقول"),
        RATE_22050(22050, "22 kHz - راديو"),
        RATE_44100(44100, "44.1 kHz - CD (افتراضي)"),
        RATE_48000(48000, "48 kHz - DVD");

        public final int value;
        public final String label;
        SampleRate(int value, String label) { this.value = value; this.label = label; }
    }

    /** Available AAC bitrates (quality) */
    public enum AudioQuality {
        LOW(64_000, "64 kbps - منخفض"),
        MEDIUM(96_000, "96 kbps - متوسط"),
        HIGH(128_000, "128 kbps - عالي (افتراضي)"),
        VERY_HIGH(192_000, "192 kbps - عالي جداً"),
        EXTREME(256_000, "256 kbps - فائق");

        public final int bitrate;
        public final String label;
        AudioQuality(int bitrate, String label) { this.bitrate = bitrate; this.label = label; }
    }

    /** Noise suppression mode */
    public enum NoiseSuppression {
        OFF(false, false, "🚫 إيقاف"),
        STANDARD(true, false, "🔇 عادي"),
        AGGRESSIVE(true, true, "🔇 قوي");

        public final boolean enabled;
        public final boolean aggressive;
        public final String label;
        NoiseSuppression(boolean enabled, boolean aggressive, String label) {
            this.enabled = enabled;
            this.aggressive = aggressive;
            this.label = label;
        }
    }

    private SampleRate sampleRate = SampleRate.RATE_44100;
    private AudioQuality quality = AudioQuality.HIGH;
    private NoiseSuppression noiseSuppression = NoiseSuppression.OFF;

    public AudioConfig() {}

    public AudioConfig(SampleRate sampleRate, AudioQuality quality, NoiseSuppression noiseSuppression) {
        this.sampleRate = sampleRate;
        this.quality = quality;
        this.noiseSuppression = noiseSuppression;
    }

    public SampleRate getSampleRate() { return sampleRate; }
    public void setSampleRate(SampleRate sampleRate) { this.sampleRate = sampleRate; }

    public AudioQuality getQuality() { return quality; }
    public void setQuality(AudioQuality quality) { this.quality = quality; }

    public NoiseSuppression getNoiseSuppression() { return noiseSuppression; }
    public void setNoiseSuppression(NoiseSuppression noiseSuppression) {
        this.noiseSuppression = noiseSuppression;
    }

    /** Get the sample rate integer value */
    public int getSampleRateValue() { return sampleRate.value; }

    /** Get AAC bitrate */
    public int getBitrate() { return quality.bitrate; }

    @Override
    public String toString() {
        return String.format("%s | %s | %s",
                sampleRate.label, quality.label, noiseSuppression.label);
    }
}
