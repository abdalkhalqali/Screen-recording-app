package com.jgeraldo.mediaprojectionsample;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenCaptureEngine {

    public static final String TAG = "ScreenCaptureEngine";

    public interface OnCaptureListener {
        void onRecordingStarted();
        void onRecordingStopped(Uri videoUri, String message);
        void onCaptureError(String error);
    }

    private final MediaProjection mediaProjection;
    private final ContentResolver contentResolver;

    private MediaCodec videoCodec;
    private MediaMuxer mediaMuxer;
    private int videoTrackIndex = -1;

    private Surface inputSurface;
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;

    private String videoFilePath;
    private Handler mainHandler;
    private HandlerThread encodingThread;
    private Handler encodingHandler;

    private OnCaptureListener listener;

    public ScreenCaptureEngine(MediaProjection mediaProjection, ContentResolver contentResolver) {
        this.mediaProjection = mediaProjection;
        this.contentResolver = contentResolver;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setOnCaptureListener(OnCaptureListener listener) {
        this.listener = listener;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void startRecording(int width, int height) {
        if (isRunning) return;

        try {
            isRunning = true;

            // 1. إنشاء اسم الملف
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "ScreenRecord_" + timeStamp + ".mp4";
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES), "ScreenRecords");
            if (!dir.exists()) dir.mkdirs();
            videoFilePath = new File(dir, fileName).getAbsolutePath();

            // 2. إعداد MediaCodec (H.264)
            MediaFormat videoFormat = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 4000000);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

            videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = videoCodec.createInputSurface();
            videoCodec.start();

            // 3. إعداد MediaMuxer
            mediaMuxer = new MediaMuxer(videoFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            videoTrackIndex = mediaMuxer.addTrack(videoFormat);
            mediaMuxer.start();

            // 4. إنشاء VirtualDisplay
            mediaProjection.createVirtualDisplay(
                    "ScreenRecorder", width, height, 160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface, null, null);

            // 5. بدء thread لتفريغ MediaCodec (الأهم!)
            startEncodingLoop();

            // 6. إعلام المستخدم
            if (listener != null) {
                mainHandler.post(listener::onRecordingStarted);
            }

            Log.d(TAG, "✅ بدأ التسجيل بنجاح");

        } catch (Exception e) {
            Log.e(TAG, "❌ فشل بدء التسجيل: " + e.getMessage(), e);
            isRunning = false;
            cleanup();
            notifyError("فشل بدء التسجيل: " + e.getMessage());
        }
    }

    public void stopRecording() {
        if (!isRunning) return;
        isRunning = false;

        try {
            // إنهاء encoding loop
            if (encodingThread != null) {
                encodingThread.quitSafely();
                try { encodingThread.join(1000); } catch (InterruptedException ignored) {}
                encodingThread = null;
                encodingHandler = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "خطأ في إيقاف thread: " + e.getMessage());
        }

        cleanup();

        // إعلام المستخدم
        if (listener != null) {
            Uri uri = Uri.fromFile(new File(videoFilePath));
            mainHandler.post(() -> listener.onRecordingStopped(uri, "✅ تم حفظ الفيديو"));
        }

        Log.d(TAG, "✅ تم إيقاف التسجيل");
    }

    public void pauseRecording() {
        isPaused = true;
    }

    public void resumeRecording() {
        isPaused = false;
    }

    public void release() {
        stopRecording();
    }

    // ========== حلقة التشفير (الأهم) ==========

    private void startEncodingLoop() {
        encodingThread = new HandlerThread("VideoEncoding");
        encodingThread.start();
        encodingHandler = new Handler(encodingThread.getLooper());

        encodingHandler.post(new Runnable() {
            final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            @Override
            public void run() {
                if (!isRunning) return;

                try {
                    // تفريغ output buffers من MediaCodec
                    int outputIndex = videoCodec.dequeueOutputBuffer(bufferInfo, 10000);

                    if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // لا توجد بيانات الآن -> إعادة المحاولة
                        if (isRunning) encodingHandler.post(this);
                        return;
                    }

                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // تغيير التنسيق - تجاهل (لدينا track بالفعل)
                        if (isRunning) encodingHandler.post(this);
                        return;
                    }

                    if (outputIndex >= 0) {
                        ByteBuffer outputBuffer = videoCodec.getOutputBuffer(outputIndex);
                        if (outputBuffer != null) {
                            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                // كتابة البيانات إلى MediaMuxer
                                mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo);
                            }
                        }
                        videoCodec.releaseOutputBuffer(outputIndex, false);

                        // إذا كانت هذه هي النهاية -> لا تعاود
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            return;
                        }
                    }

                    // إعادة المحاولة
                    if (isRunning) encodingHandler.post(this);

                } catch (Exception e) {
                    Log.e(TAG, "خطأ في encoding loop: " + e.getMessage());
                    if (isRunning) encodingHandler.post(this);
                }
            }
        });
    }

    // ========== التنظيف ==========

    private void cleanup() {
        try {
            // إنهاء MediaCodec
            if (videoCodec != null) {
                try { videoCodec.signalEndOfInputStream(); } catch (Exception ignored) {}
                try { videoCodec.stop(); } catch (Exception ignored) {}
                try { videoCodec.release(); } catch (Exception ignored) {}
                videoCodec = null;
            }

            // إنهاء MediaMuxer
            if (mediaMuxer != null) {
                try { mediaMuxer.stop(); } catch (Exception ignored) {}
                try { mediaMuxer.release(); } catch (Exception ignored) {}
                mediaMuxer = null;
            }

            videoTrackIndex = -1;
            inputSurface = null;

            // إضافة الفيديو إلى المعرض (Android 10+)
            addVideoToGallery();

        } catch (Exception e) {
            Log.e(TAG, "خطأ في التنظيف: " + e.getMessage());
        }
    }

    private void addVideoToGallery() {
        if (videoFilePath == null || contentResolver == null) return;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DATA, videoFilePath);
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/ScreenRecords");
                values.put(MediaStore.Video.Media.IS_PENDING, 0);
            }
            contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            Log.d(TAG, "📱 تمت إضافة الفيديو للمعرض");
        } catch (Exception e) {
            Log.w(TAG, "فشل إضافة للمعرض: " + e.getMessage());
        }
    }

    private void notifyError(String error) {
        if (listener != null) {
            mainHandler.post(() -> listener.onCaptureError(error));
        }
    }
}
