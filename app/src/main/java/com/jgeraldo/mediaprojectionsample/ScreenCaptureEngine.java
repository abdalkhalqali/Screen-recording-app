package com.jgeraldo.mediaprojectionsample;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.display.DisplayManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenCaptureEngine {

    public static final String TAG = "ScreenCaptureEngine";
    public static final String ACTION_UPDATE_STATE =
            "com.jgeraldo.mediaprojectionsample.UPDATE_STATE";

    // مصدر الصوت
    public enum AudioSource {
        NONE("بدون", false),
        INTERNAL("داخلي", true),
        EXTERNAL("خارجي", true),
        BOTH("داخلي+خارجي", true);

        private final String displayName;
        public final boolean hasAudio;

        AudioSource(String displayName, boolean hasAudio) {
            this.displayName = displayName;
            this.hasAudio = hasAudio;
        }

        public String getDisplayName() { return displayName; }
    }

    // وضع الإيقاف التلقائي
    public enum AutoPauseMode {
        OFF("🚫 معطل", false),
        LIGHT("🟢 خفيف", true),
        MEDIUM("🟡 متوسط", true),
        AGGRESSIVE("🔴 قوي", true);

        public final String label;
        public final boolean enabled;

        AutoPauseMode(String label, boolean enabled) {
            this.label = label;
            this.enabled = enabled;
        }
    }

    public interface OnCaptureListener {
        void onScreenshotSaved(Uri uri, String message);
        void onVideoSaved(Uri uri, String message);
        void onCaptureError(String error);
        void onRecordingStarted();
        void onRecordingStopped();
        void onRecordingPaused();
        void onRecordingResumed();
        void onRecordingStateUpdated(boolean paused, long elapsedMs);
    }

    private final MediaProjection mediaProjection;
    private final ContentResolver contentResolver;

    private MediaCodec videoCodec;
    private MediaCodec audioCodec;
    private MediaMuxer mediaMuxer;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;

    private ImageReader screenshotReader;
    private Surface inputSurface;
    private volatile boolean isVideoCapturing = false;
    private volatile boolean isAudioCapturing = false;
    private volatile boolean isPaused = false;
    private volatile boolean isEncoding = false;

    private AudioRecord audioRecord;
    private Thread audioThread;

    private AudioSource audioSource = AudioSource.EXTERNAL;
    private AutoPauseMode autoPauseMode = AutoPauseMode.OFF;

    private long lastPresentationTimeUs = 0;
    private long startTimeMs = 0;
    private long pausedTimeMs = 0;
    private final AtomicBoolean isCapturing = new AtomicBoolean(false);

    private OnCaptureListener listener;
    private Handler mainHandler;
    private int videoWidth = 720;
    private int videoHeight = 1280;
    private int bitRate = 4000000;
    private int frameRate = 30;

    public ScreenCaptureEngine(MediaProjection mediaProjection, ContentResolver contentResolver) {
        this.mediaProjection = mediaProjection;
        this.contentResolver = contentResolver;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setOnCaptureListener(OnCaptureListener listener) {
        this.listener = listener;
    }

    public void setAudioSource(AudioSource source) {
        this.audioSource = source;
    }

    public void setAutoPauseMode(AutoPauseMode mode) {
        this.autoPauseMode = mode;
    }

    public boolean isCapturing() {
        return isCapturing.get();
    }

    public long getElapsedTimeMs() {
        return lastPresentationTimeUs / 1000;
    }

    // ===================== البدء/الإيقاف =====================

    public void startVideoCapture(int width, int height) {
        if (isCapturing.getAndSet(true)) return;

        this.videoWidth = width;
        this.videoHeight = height;

        try {
            startTimeMs = System.currentTimeMillis();
            lastPresentationTimeUs = 0;
            prepareVideoEncoder();
            startAudioCapture();

            if (listener != null) {
                mainHandler.post(listener::onRecordingStarted);
            }

            // تأخير 300ms للتأكد من جاهزية inputSurface قبل إنشاء VirtualDisplay
            mainHandler.postDelayed(() -> {
                if (mediaProjection != null && inputSurface != null && isCapturing.get()) {
                    try {
                        mediaProjection.createVirtualDisplay(
                                "VideoCapture", videoWidth, videoHeight, 160,
                                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                                inputSurface, null, mainHandler);
                        Log.d(TAG, "VirtualDisplay created successfully");
                    } catch (Exception e) {
                        Log.e(TAG, "فشل VirtualDisplay: " + e.getMessage());
                        isCapturing.set(false);
                        notifyError("فشل بدء التسجيل: " + e.getMessage());
                    }
                }
            }, 300);
        } catch (Exception e) {
            Log.e(TAG, "فشل بدء التسجيل: " + e.getMessage(), e);
            isCapturing.set(false);
            notifyError("فشل بدء التسجيل: " + e.getMessage());
        }
    }

    public void stopVideoCapture() {
        if (!isCapturing.getAndSet(false)) return;
        isVideoCapturing = false;
        isAudioCapturing = false;

        stopEncoding();

        if (listener != null) {
            mainHandler.post(() -> {
                listener.onRecordingStopped();
                // إعلام المستخدم بحفظ الفيديو
                if (videoFilePath != null) {
                    listener.onVideoSaved(
                            Uri.fromFile(new File(videoFilePath)),
                            "✅ تم حفظ الفيديو في المعرض");
                }
            });
        }
    }

    public void pauseVideoCapture() {
        if (!isCapturing.get()) return;
        isPaused = true;
        pausedTimeMs = System.currentTimeMillis();

        if (listener != null) {
            mainHandler.post(listener::onRecordingPaused);
        }
    }

    public void resumeVideoCapture() {
        if (!isCapturing.get()) return;
        isPaused = false;
        if (pausedTimeMs > 0) {
            startTimeMs += System.currentTimeMillis() - pausedTimeMs;
            pausedTimeMs = 0;
        }

        if (listener != null) {
            mainHandler.post(listener::onRecordingResumed);
        }
    }

    public void release() {
        stopVideoCapture();
        releaseScreenshotReader();
    }

    // ===================== لقطة شاشة =====================

    public void captureScreenshot(int width, int height) {
        try {
            releaseScreenshotReader();
            screenshotReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2);

            Surface surface = screenshotReader.getSurface();
            mediaProjection.createVirtualDisplay(
                    "ScreenshotCapture", width, height, 160,
                    0, surface, null, null);

            screenshotReader.setOnImageAvailableListener(reader -> {
                try (Image image = reader.acquireLatestImage()) {
                    if (image != null) {
                        saveScreenshot(image, width, height);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "خطأ في اللقطة: " + e.getMessage());
                }
            }, mainHandler);

            // إغلاق VirtualDisplay بعد فترة قصيرة
            mainHandler.postDelayed(() -> {
                releaseScreenshotReader();
            }, 500);

        } catch (Exception e) {
            Log.e(TAG, "فشل أخذ لقطة: " + e.getMessage());
            notifyError("فشل أخذ لقطة: " + e.getMessage());
        }
    }

    // ===================== تجهيزات الفيديو =====================

    private void prepareVideoEncoder() throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

        videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        videoCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = videoCodec.createInputSurface();
        videoCodec.start();

        // إنشاء ملف الإخراج
        String fileName = createVideoFileName();
        mediaMuxer = new MediaMuxer(fileName,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        // إضافة مسار الفيديو
        videoTrackIndex = mediaMuxer.addTrack(format);
        mediaMuxer.start();
        isEncoding = true;
        isVideoCapturing = true;
    }

    // تم إلغاء startVideoFrameCapture - نستخدم inputSurface من MediaCodec مباشرة

    // ===================== تسجيل الصوت =====================

    private void startAudioCapture() {
        if (audioSource == AudioSource.NONE) return;

        try {
            int sampleRate = 44100;
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

            int audioSourceType = audioSource == AudioSource.EXTERNAL
                    ? MediaRecorder.AudioSource.MIC
                    : MediaRecorder.AudioSource.REMOTE_SUBMIX;

            if (audioSource == AudioSource.BOTH && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startMixedAudioCapture();
                return;
            }

            audioRecord = new AudioRecord(audioSourceType, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(bufferSize, 4096));

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                audioRecord = null;
                return;
            }

            prepareAudioEncoder(sampleRate);
            isAudioCapturing = true;
            audioRecord.startRecording();

            audioThread = new Thread(() -> {
                ByteBuffer buf = ByteBuffer.allocateDirect(4096);
                while (isAudioCapturing && isCapturing.get()) {
                    if (isPaused) {
                        try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                        continue;
                    }
                    buf.clear();
                    int read = audioRecord.read(buf, 4096);
                    if (read > 0 && audioCodec != null) {
                        buf.position(read);
                        buf.flip();
                        encodeAudio(buf, read);
                    }
                }
                stopAudioCapture();
            }, "AudioCapture");
            audioThread.start();

        } catch (Exception e) {
            Log.w(TAG, "فشل تشغيل الصوت: " + e.getMessage());
        }
    }

    private void startMixedAudioCapture() {
        // إصدار مبسط - يستخدم الميكروفون فقط للتسجيل المختلط
        try {
            int sampleRate = 44100;
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(bufferSize, 4096));

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                audioRecord = null;
                return;
            }

            prepareAudioEncoder(sampleRate);
            isAudioCapturing = true;
            audioRecord.startRecording();

            audioThread = new Thread(() -> {
                ByteBuffer buf = ByteBuffer.allocateDirect(4096);
                while (isAudioCapturing && isCapturing.get()) {
                    if (isPaused) {
                        try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                        continue;
                    }
                    buf.clear();
                    int read = audioRecord.read(buf, 4096);
                    if (read > 0 && audioCodec != null) {
                        buf.position(read);
                        buf.flip();
                        encodeAudio(buf, read);
                    }
                }
                stopAudioCapture();
            }, "MixAudio");
            audioThread.start();

        } catch (Exception e) {
            Log.w(TAG, "فشل تشغيل الصوت المختلط: " + e.getMessage());
        }
    }

    private void prepareAudioEncoder(int sampleRate) throws Exception {
        MediaFormat format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);

        audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        audioCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioCodec.start();

        if (mediaMuxer != null) {
            audioTrackIndex = mediaMuxer.addTrack(format);
        }
    }

    private void encodeAudio(ByteBuffer data, int size) {
        if (audioCodec == null) return;
        try {
            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            int idx = audioCodec.dequeueInputBuffer(10000);
            if (idx >= 0) {
                ByteBuffer ib = audioCodec.getInputBuffer(idx);
                if (ib != null) {
                    ib.clear();
                    ib.put(data);
                    audioCodec.queueInputBuffer(idx, 0, size,
                            lastPresentationTimeUs, 0);
                }
            }

            int oi;
            while ((oi = audioCodec.dequeueOutputBuffer(bi, 10000)) >= 0) {
                ByteBuffer ob = audioCodec.getOutputBuffer(oi);
                if (ob != null && mediaMuxer != null && audioTrackIndex >= 0) {
                    mediaMuxer.writeSampleData(audioTrackIndex, ob, bi);
                }
                audioCodec.releaseOutputBuffer(oi, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "تشفير الصوت: " + e.getMessage());
        }
    }

    // ===================== حفظ الفيديو =====================

    private String videoFilePath;

    private String createVideoFileName() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date());
        String fileName = "ScreenRecord_" + timeStamp + ".mp4";

        // نستخدم مجلد Movies/ScreenRecords مباشرة (يعمل على جميع الإصدارات)
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), "ScreenRecords");
        if (!dir.exists()) dir.mkdirs();
        
        videoFilePath = new File(dir, fileName).getAbsolutePath();
        return videoFilePath;
    }

    private void addVideoToGallery() {
        if (videoFilePath == null || contentResolver == null) return;
        
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DATA, videoFilePath);
            values.put(MediaStore.Video.Media.TITLE, 
                    new File(videoFilePath).getName());
            values.put(MediaStore.Video.Media.DISPLAY_NAME, 
                    new File(videoFilePath).getName());
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.DATE_ADDED, 
                    System.currentTimeMillis() / 1000);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Video.Media.IS_PENDING, 0);
                values.put(MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/ScreenRecords");
            }
            
            contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            Log.d(TAG, "تم إضافة الفيديو للمعرض: " + videoFilePath);
        } catch (Exception e) {
            Log.w(TAG, "فشل إضافة الفيديو للمعرض: " + e.getMessage());
        }
    }

    // ===================== التوقف والتنظيف =====================

    private void stopEncoding() {
        isEncoding = false;
        try {
            // إنهاء video codec
            if (videoCodec != null) {
                try {
                    videoCodec.signalEndOfInputStream();
                    MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                    int idx;
                    while ((idx = videoCodec.dequeueOutputBuffer(bi, 10000)) >= 0) {
                        ByteBuffer buf = videoCodec.getOutputBuffer(idx);
                        if (buf != null && mediaMuxer != null && videoTrackIndex >= 0
                                && (bi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            mediaMuxer.writeSampleData(videoTrackIndex, buf, bi);
                        }
                        videoCodec.releaseOutputBuffer(idx, false);
                        if ((bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "إيقاف video codec: " + e.getMessage());
                }
                try { videoCodec.stop(); } catch (Exception ignored) {}
                try { videoCodec.release(); } catch (Exception ignored) {}
                videoCodec = null;
            }

            // إنهاء audio codec
            stopAudioCapture();
            if (audioCodec != null) {
                try { audioCodec.stop(); } catch (Exception ignored) {}
                try { audioCodec.release(); } catch (Exception ignored) {}
                audioCodec = null;
            }

            // إنهاء MediaMuxer وحفظ الملف
            if (mediaMuxer != null) {
                try { mediaMuxer.stop(); } catch (Exception ignored) {}
                try { mediaMuxer.release(); } catch (Exception ignored) {}
                mediaMuxer = null;
                
                // 🖼️ إضافة الفيديو للمعرض
                addVideoToGallery();
            }

            videoTrackIndex = -1;
            audioTrackIndex = -1;
            lastPresentationTimeUs = 0;

        } catch (Exception e) {
            Log.e(TAG, "خطأ في التنظيف: " + e.getMessage());
        }
    }

    private void stopAudioCapture() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
                    audioRecord.stop();
            } catch (Exception ignored) {}
            audioRecord.release();
            audioRecord = null;
        }
        isAudioCapturing = false;
    }

    private void releaseScreenshotReader() {
        if (screenshotReader != null) {
            try { screenshotReader.setOnImageAvailableListener(null, null); } catch (Exception ignored) {}
            try { screenshotReader.close(); } catch (Exception ignored) {}
            screenshotReader = null;
        }
    }

    // ===================== حفظ لقطة =====================

    private void saveScreenshot(Image image, int width, int height) {
        try {
            // تحويل YUV_420_888 إلى Bitmap
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            // حفظ كملف JPEG
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            String fileName = "Screenshot_" + timeStamp + ".jpg";

            Uri uri = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/ScreenRecords");
                values.put(MediaStore.Images.Media.TITLE, "لقطة شاشة " + timeStamp);
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);

                uri = contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                if (uri != null) {
                    try (OutputStream out = contentResolver.openOutputStream(uri)) {
                        if (out != null) {
                            Bitmap bitmap = yuvToBitmap(bytes, width, height);
                            if (bitmap != null) {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                                bitmap.recycle();
                            }
                        }
                    }
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "ScreenRecords");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, fileName);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    Bitmap bitmap = yuvToBitmap(bytes, width, height);
                    if (bitmap != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                        bitmap.recycle();
                    }
                }
                uri = Uri.fromFile(file);
            }

            if (uri != null && listener != null) {
                Uri finalUri = uri;
                mainHandler.post(() ->
                        listener.onScreenshotSaved(finalUri, "✅ تم حفظ اللقطة"));
            }

        } catch (Exception e) {
            Log.e(TAG, "فشل حفظ اللقطة: " + e.getMessage());
            notifyError("فشل حفظ اللقطة");
        }
    }

    private Bitmap yuvToBitmap(byte[] yuvData, int width, int height) {
        try {
            // تحويل بسيط إلى Bitmap - يستخدم Y channel فقط (تدرج رمادي)
            // في الإنتاج سنستخدم RenderScript للتحويل الكامل
            int[] pixels = new int[width * height];
            for (int i = 0; i < width * height; i++) {
                int y = yuvData[i] & 0xFF;
                pixels[i] = 0xFF000000 | (y << 16) | (y << 8) | y;
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            Log.e(TAG, "فشل تحويل YUV: " + e.getMessage());
            return null;
        }
    }

    private void notifyError(String error) {
        if (listener != null) {
            mainHandler.post(() -> listener.onCaptureError(error));
        }
    }
}
