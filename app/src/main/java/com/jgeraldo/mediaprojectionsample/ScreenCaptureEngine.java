package com.jgeraldo.mediaprojectionsample;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenCaptureEngine {

    private static final String TAG = "ScreenCaptureEngine";
    private static final int MAX_IMAGES = 2;
    // Video config (replaces old constants)
    private VideoConfig videoConfig = new VideoConfig();

    private MediaProjection mediaProjection;
    private ImageReader videoReader;      // YUV_420_888 for video encoding
    private ImageReader screenshotReader;  // RGBA_8888 for screenshots
    private VirtualDisplay virtualDisplay;
    private Handler backgroundHandler;
    private ContentResolver contentResolver;

    // Video encoding
    private MediaCodec videoCodec;
    private MediaMuxer mediaMuxer;
    private int videoTrackIndex = -1;
    private long lastPresentationTimeUs = 0;
    private boolean isEncoding = false;
    private boolean isPaused = false;
    private boolean isVideoCapturing = false;
    private String currentVideoPath;

    // Audio recording
    public enum AudioSource {
        NONE("🔇 بدون"),
        INTERNAL("🔊 داخلي"),
        EXTERNAL("🎤 خارجي"),
        BOTH("🔊+🎤 كليهما");
        private final String displayName;
        AudioSource(String dn) { displayName = dn; }
        public String getDisplayName() { return displayName; }
    }

    private AudioRecord audioRecord;
    private MediaCodec audioCodec;
    private int audioTrackIndex = -1;
    private Thread audioThread;
    private AtomicBoolean isAudioRecording = new AtomicBoolean(false);
    private AudioSource audioSource = AudioSource.EXTERNAL;
    private AudioConfig audioConfig = new AudioConfig();
    private static final int AUDIO_CHANNELS = 1;

    // Actions for floating control
    public static final String ACTION_PAUSE = "com.jgeraldo.mediaprojectionsample.PAUSE";
    public static final String ACTION_RESUME = "com.jgeraldo.mediaprojectionsample.RESUME";
    public static final String ACTION_STOP = "com.jgeraldo.mediaprojectionsample.STOP";
    public static final String ACTION_UPDATE_STATE = "com.jgeraldo.mediaprojectionsample.STATE";
    public static final String EXTRA_IS_RECORDING = "is_recording";
    public static final String EXTRA_IS_PAUSED = "is_paused";
    public static final String EXTRA_ELAPSED_TIME = "elapsed_time";

    private OnCaptureListener listener;
    private RectF lastNormalizedRegion = new RectF(0, 0, 1, 1);

    // Pre-allocated buffers for YUV frames to reduce GC
    private byte[] yuvFrameBuffer;
    private byte[] croppedYuvBuffer;

    // ===================== AUTO-PAUSE ON IDLE (Motion Detection) =====================

    /** Auto-pause sensitivity modes */
    public enum AutoPauseMode {
        OFF("🚫 إيقاف", 0, false),
        SENSITIVE("🔹 حساس", 10, true),
        BALANCED("🔸 متوازن", 15, true),
        RELAXED("🔻 هادئ", 25, true);

        public final String label;
        public final int threshold;   // Lower = more sensitive
        public final boolean enabled;
        AutoPauseMode(String label, int threshold, boolean enabled) {
            this.label = label;
            this.threshold = threshold;
            this.enabled = enabled;
        }
    }

    private AutoPauseMode autoPauseMode = AutoPauseMode.OFF;
    private byte[] previousYSample;           // Subsampled Y from previous frame
    private int frameCounter = 0;              // Counter for periodic sampling
    private int idleFrameCount = 0;            // Consecutive idle frames
    private boolean wasIdlePaused = false;     // Track if we auto-paused
    private boolean wasIdle = false;           // Currently idle state
    private static final int MOTION_CHECK_INTERVAL = 5;  // Check every N frames
    private static final int Y_SAMPLE_STEP = 16;         // Subsampling step (every 16th pixel)
    private static final int IDLE_TRIGGER_FRAMES = 4;    // Idle frames before pausing
    private static final int MOVEMENT_RESUME_FRAMES = 2; // Motion frames before resuming

    public void setAutoPauseMode(AutoPauseMode mode) { this.autoPauseMode = mode; }
    public AutoPauseMode getAutoPauseMode() { return autoPauseMode; }

    /**
     * Detect motion by comparing subsampled Y (luma) planes.
     * Uses Mean Absolute Difference (MAD) on 1/16th of pixels for speed.
     * Returns true if significant motion is detected.
     */
    private boolean detectMotion(byte[] yuvData, int width, int height) {
        if (yuvData == null || width <= 0 || height <= 0) return false;

        int sampleW = width / Y_SAMPLE_STEP;
        int sampleH = height / Y_SAMPLE_STEP;
        int sampleSize = sampleW * sampleH;

        // Extract subsample of Y plane
        byte[] currentSample;
        if (previousYSample == null || previousYSample.length != sampleSize) {
            currentSample = new byte[sampleSize];
            previousYSample = new byte[sampleSize];
            // Fill current sample, keep previous as zeros (first frame = no motion)
            int idx = 0;
            for (int row = 0; row < height; row += Y_SAMPLE_STEP) {
                int rowOffset = row * width;
                for (int col = 0; col < width; col += Y_SAMPLE_STEP) {
                    currentSample[idx++] = yuvData[rowOffset + col];
                }
            }
            System.arraycopy(currentSample, 0, previousYSample, 0, sampleSize);
            return true; // First frame = motion
        }

        currentSample = new byte[sampleSize];
        int idx = 0;
        for (int row = 0; row < height; row += Y_SAMPLE_STEP) {
            int rowOffset = row * width;
            for (int col = 0; col < width; col += Y_SAMPLE_STEP) {
                currentSample[idx++] = yuvData[rowOffset + col];
            }
        }

        // Calculate Mean Absolute Difference
        long totalDiff = 0;
        for (int i = 0; i < sampleSize; i++) {
            int diff = (currentSample[i] & 0xFF) - (previousYSample[i] & 0xFF);
            totalDiff += Math.abs(diff);
        }
        float avgDiff = (float) totalDiff / sampleSize;

        // Store for next comparison
        System.arraycopy(currentSample, 0, previousYSample, 0, sampleSize);

        return avgDiff > autoPauseMode.threshold;
    }

    /**
     * Check motion state and auto-pause/resume based on idle detection.
     * Should be called from the frame listener for every frame.
     */
    private void checkAutoPause(byte[] yuvData, int width, int height) {
        if (!autoPauseMode.enabled || !isVideoCapturing) return;

        frameCounter++;
        if (frameCounter % MOTION_CHECK_INTERVAL != 0) return;

        boolean hasMotion = detectMotion(yuvData, width, height);

        if (!hasMotion) {
            idleFrameCount++;
            if (!wasIdle && idleFrameCount >= IDLE_TRIGGER_FRAMES) {
                wasIdle = true;
                // Auto-pause if not already paused by user
                if (!isPaused) {
                    wasIdlePaused = true;
                    pauseVideoCapture();
                    Log.d(TAG, "Auto-paused due to inactivity");
                }
            }
        } else {
            idleFrameCount = 0;
            if (wasIdle || wasIdlePaused) {
                wasIdle = false;
                // Auto-resume if we were auto-paused
                if (wasIdlePaused && isPaused) {
                    wasIdlePaused = false;
                    resumeVideoCapture();
                    Log.d(TAG, "Auto-resumed due to motion detected");
                }
            }
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
        void onRecordingStateUpdated(boolean isPaused, long elapsedMs);
    }

    public ScreenCaptureEngine(MediaProjection projection, ContentResolver resolver) {
        this.mediaProjection = projection;
        this.contentResolver = resolver;
        this.backgroundHandler = new Handler(Looper.getMainLooper());
    }

    public void setOnCaptureListener(OnCaptureListener listener) { this.listener = listener; }
    public void setCaptureRegion(RectF normalizedRegion) {
        if (normalizedRegion != null) lastNormalizedRegion.set(normalizedRegion);
    }

    // ===================== SCREENSHOTS (RGBA) =====================

    public void captureScreenshot(int displayWidth, int displayHeight) {
        releaseScreenshotReader();
        screenshotReader = ImageReader.newInstance(
                displayWidth, displayHeight, PixelFormat.RGBA_8888, 1);

        screenshotReader.setOnImageAvailableListener(reader -> {
            try (Image image = reader.acquireLatestImage()) {
                if (image != null) {
                    Bitmap fullBitmap = imageToBitmap(image, displayWidth, displayHeight);
                    Bitmap cropped = cropBitmap(fullBitmap, lastNormalizedRegion,
                            displayWidth, displayHeight);
                    String savedUri = saveBitmapToGallery(cropped);
                    if (fullBitmap != null && !fullBitmap.isRecycled()) fullBitmap.recycle();
                    if (cropped != null && !cropped.isRecycled()) cropped.recycle();
                    if (listener != null) {
                        if (savedUri != null)
                            listener.onScreenshotSaved(Uri.parse(savedUri), "تم حفظ لقطة الشاشة بنجاح");
                        else listener.onCaptureError("فشل حفظ لقطة الشاشة");
                    }
                    releaseScreenshotReader();
                }
            } catch (Exception e) {
                Log.e(TAG, "Screenshot: " + e.getMessage(), e);
                if (listener != null) listener.onCaptureError("خطأ: " + e.getMessage());
                releaseScreenshotReader();
            }
        }, backgroundHandler);

        createVirtualDisplay(screenshotReader.getSurface(), displayWidth, displayHeight);
        backgroundHandler.postDelayed(() -> {}, 200);
    }

    // ===================== VIDEO ENCODING (YUV Pipeline) =====================

    public void setAudioSource(AudioSource source) { this.audioSource = source; }
    public AudioSource getAudioSource() { return audioSource; }
    public void setAudioConfig(AudioConfig config) { if (config != null) this.audioConfig = config; }
    public AudioConfig getAudioConfig() { return audioConfig; }
    public void setVideoConfig(VideoConfig config) { if (config != null) this.videoConfig = config; }
    public VideoConfig getVideoConfig() { return videoConfig; }
    public boolean isAudioEnabled() { return audioSource != AudioSource.NONE; }

    public void startVideoCapture(int displayWidth, int displayHeight) {
        if (isVideoCapturing) return;
        isVideoCapturing = true;

        try {
            initMediaEncoder(displayWidth, displayHeight);
        } catch (IOException e) {
            Log.e(TAG, "init encoder: " + e.getMessage(), e);
            if (listener != null) listener.onCaptureError("فشل بدء تسجيل الفيديو");
            isVideoCapturing = false;
            return;
        }

        // Start audio
        if (audioSource == AudioSource.EXTERNAL) startExternalAudioRecording();
        else if (audioSource == AudioSource.INTERNAL) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startInternalAudioRecording();
            else { Log.w(TAG, "Internal audio needs Android 10+"); }
        } else if (audioSource == AudioSource.BOTH) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startMixedAudioRecording();
            else startExternalAudioRecording();
        }

        // === OPTIMIZED: YUV direct pipeline ===
        // Video reader uses YUV_420_888 to avoid Bitmap conversion
        releaseVideoReader();
        videoReader = ImageReader.newInstance(
                displayWidth, displayHeight, ImageFormat.YUV_420_888, MAX_IMAGES);

        // Pre-allocate buffers for max possible frame size
        int frameSize = displayWidth * displayHeight * 3 / 2;
        yuvFrameBuffer = new byte[frameSize];
        int rawCropW = (int) (displayWidth * lastNormalizedRegion.width());
        int rawCropH = (int) (displayHeight * lastNormalizedRegion.height());
        final int finalCropW = (rawCropW % 2 != 0) ? rawCropW + 1 : rawCropW;
        final int finalCropH = (rawCropH % 2 != 0) ? rawCropH + 1 : rawCropH;
        croppedYuvBuffer = new byte[finalCropW * finalCropH * 3 / 2];

        videoReader.setOnImageAvailableListener(reader -> {
            if (!isVideoCapturing && isEncoding) { stopEncoding(); return; }
            try (Image image = reader.acquireLatestImage()) {
                if (image != null && isEncoding) {
                    // Direct YUV extraction - NO Bitmap creation
                    byte[] yuvData = imageToYuv420(image, displayWidth, displayHeight, yuvFrameBuffer);
                    if (yuvData != null) {
                        // Auto-pause detection before cropping (on full frame for accuracy)
                        if (autoPauseMode.enabled && isVideoCapturing) {
                            checkAutoPause(yuvData, displayWidth, displayHeight);
                        }

                        // Only encode/crop if not paused
                        if (!isPaused) {
                            byte[] croppedYuv = cropYuv420(yuvData, displayWidth, displayHeight,
                                    lastNormalizedRegion, croppedYuvBuffer);
                            if (croppedYuv != null) {
                                encodeYuvFrame(croppedYuv, finalCropW, finalCropH);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Frame error: " + e.getMessage());
            }
        }, backgroundHandler);

        createVirtualDisplay(videoReader.getSurface(), displayWidth, displayHeight);
        isEncoding = true;
        if (listener != null) listener.onRecordingStarted();
    }

    public void stopVideoCapture() {
        isVideoCapturing = false;
        backgroundHandler.postDelayed(() -> {
            stopEncoding();
            releaseVirtualDisplay();
            releaseVideoReader();
            if (currentVideoPath != null) {
                if (listener != null)
                    listener.onVideoSaved(Uri.parse(currentVideoPath), "تم حفظ تسجيل الفيديو بنجاح");
                currentVideoPath = null;
            }
        }, 500);
        if (listener != null) listener.onRecordingStopped();
    }

    public boolean isCapturing() { return isVideoCapturing; }
    public boolean isPaused() { return isPaused; }

    public void pauseVideoCapture() {
        if (!isVideoCapturing || isPaused) return;
        isPaused = true;
        if (listener != null) {
            listener.onRecordingPaused();
            listener.onRecordingStateUpdated(true, lastPresentationTimeUs / 1000);
        }
    }

    public void resumeVideoCapture() {
        if (!isVideoCapturing || !isPaused) return;
        isPaused = false;
        if (listener != null) {
            listener.onRecordingResumed();
            listener.onRecordingStateUpdated(false, lastPresentationTimeUs / 1000);
        }
    }

    public long getElapsedTimeMs() { return lastPresentationTimeUs / 1000; }

    public void release() {
        isVideoCapturing = false;
        isEncoding = false;
        isAudioRecording.set(false);
        stopEncoding();
        stopAudioCapture();
        releaseVirtualDisplay();
        releaseVideoReader();
        releaseScreenshotReader();
        if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; }
    }

    // ===================== YUV DIRECT EXTRACTION (OPTIMIZED) =====================

    /**
     * Extract YUV_420_888 planes into a contiguous NV21 byte array.
     * NO Bitmap creation - directly reads from Image planes.
     * ~10x faster than Bitmap→NV21 conversion.
     */
    private byte[] imageToYuv420(Image image, int width, int height, byte[] output) {
        Image.Plane[] planes = image.getPlanes();
        if (planes.length < 3) return null;

        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int yRowStride = planes[0].getRowStride();
        int uRowStride = planes[1].getRowStride();
        int vRowStride = planes[2].getRowStride();

        int yPixelStride = planes[0].getPixelStride();
        int uPixelStride = planes[1].getPixelStride();
        int vPixelStride = planes[2].getPixelStride();

        // Copy Y plane (luma) - pixel-by-pixel with stride support
        int yIndex = 0;
        if (yPixelStride == 1 && yRowStride == width) {
            // Optimal case: contiguous Y data
            yBuffer.get(output, 0, width * height);
            yIndex = width * height;
        } else {
            // Handle row padding
            yBuffer.rewind();
            for (int row = 0; row < height; row++) {
                yBuffer.position(row * yRowStride);
                yBuffer.get(output, yIndex, width);
                yIndex += width;
            }
        }

        // Copy UV planes (chroma) - interleave V,U for NV21 format
        int uvIndex = width * height;
        int uvHeight = height / 2;
        int uvWidth = width / 2;

        // In YUV_420_888, U and V planes have half resolution
        // We need to produce NV21 format: YYYY... then VUVUVU...
        // Where U and V are interleaved

        if (uPixelStride == 2 && vPixelStride == 2 && uRowStride == width && vRowStride == width) {
            // Semi-planar case (NV21-like): U and V are already interleaved planes
            uBuffer.rewind();
            vBuffer.rewind();
            for (int row = 0; row < uvHeight; row++) {
                for (int col = 0; col < uvWidth; col++) {
                    int uPos = row * uRowStride + col * uPixelStride;
                    int vPos = row * vRowStride + col * vPixelStride;
                    output[uvIndex++] = vBuffer.get(vPos); // V first (NV21)
                    output[uvIndex++] = uBuffer.get(uPos); // U second
                }
            }
        } else {
            // Generic case: read samples from both planes
            uBuffer.rewind();
            vBuffer.rewind();
            for (int row = 0; row < uvHeight; row++) {
                for (int col = 0; col < uvWidth; col++) {
                    int uPos = row * uRowStride + col * uPixelStride;
                    int vPos = row * vRowStride + col * vPixelStride;
                    byte uSample = (uPos < uBuffer.capacity()) ? uBuffer.get(uPos) : (byte) 128;
                    byte vSample = (vPos < vBuffer.capacity()) ? vBuffer.get(vPos) : (byte) 128;
                    output[uvIndex++] = vSample; // V first (NV21)
                    output[uvIndex++] = uSample; // U second
                }
            }
        }

        return output;
    }

    /**
     * Crop a contiguous NV21 frame by copying only the region of interest.
     * Much faster than creating a cropped Bitmap.
     */
    private byte[] cropYuv420(byte[] source, int srcWidth, int srcHeight,
                              RectF normalizedRegion, byte[] output) {
        int cropLeft = (int) (normalizedRegion.left * srcWidth) & ~1; // even
        int cropTop = (int) (normalizedRegion.top * srcHeight) & ~1;
        int cropRight = (int) (normalizedRegion.right * srcWidth) & ~1;
        int cropBottom = (int) (normalizedRegion.bottom * srcHeight) & ~1;

        cropLeft = Math.max(0, Math.min(cropLeft, srcWidth - 2));
        cropTop = Math.max(0, Math.min(cropTop, srcHeight - 2));
        cropRight = Math.max(cropLeft + 2, Math.min(cropRight, srcWidth));
        cropBottom = Math.max(cropTop + 2, Math.min(cropBottom, srcHeight));

        int cropW = cropRight - cropLeft;
        int cropH = cropBottom - cropTop;

        // Crop Y plane
        int outY = 0;
        for (int row = cropTop; row < cropBottom; row++) {
            System.arraycopy(source, row * srcWidth + cropLeft, output, outY, cropW);
            outY += cropW;
        }

        // Crop UV plane (NV21: interleaved V,U at half resolution)
        int srcUvOffset = srcWidth * srcHeight;
        int outUvOffset = cropW * cropH;
        int uvSrcWidth = srcWidth;
        int uvCropLeft = cropLeft / 2;
        int uvCropTop = cropTop / 2;
        int uvCropW = cropW / 2;
        int uvCropH = cropH / 2;

        for (int row = 0; row < uvCropH; row++) {
            int srcRow = (uvCropTop + row) * uvSrcWidth + uvCropLeft * 2;
            int destRow = row * uvCropW * 2;
            System.arraycopy(source, srcUvOffset + srcRow, output, outUvOffset + destRow, uvCropW * 2);
        }

        return output;
    }

    // ===================== BITMAP (for screenshots only) =====================

    private Bitmap imageToBitmap(Image image, int width, int height) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        Bitmap bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);

        if (rowPadding == 0) return bitmap;
        return Bitmap.createBitmap(bitmap, 0, 0, width, height);
    }

    private Bitmap cropBitmap(Bitmap source, RectF norm, int dispW, int dispH) {
        if (source == null) return null;
        int l = Math.max(0, Math.min((int)(norm.left * dispW), source.getWidth()-1));
        int t = Math.max(0, Math.min((int)(norm.top * dispH), source.getHeight()-1));
        int r = Math.max(l+1, Math.min((int)(norm.right * dispW), source.getWidth()));
        int b = Math.max(t+1, Math.min((int)(norm.bottom * dispH), source.getHeight()));
        return Bitmap.createBitmap(source, l, t, r-l, b-t);
    }

    private String saveBitmapToGallery(Bitmap bitmap) {
        if (bitmap == null) return null;
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ContentValues vals = new ContentValues();
        vals.put(MediaStore.Images.Media.DISPLAY_NAME, "Screenshot_" + ts + ".jpg");
        vals.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        vals.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/مسجل الشاشة");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) vals.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, vals);
        if (uri == null) return null;

        try (FileOutputStream out = (FileOutputStream) contentResolver.openOutputStream(uri)) {
            if (out != null) bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vals.clear(); vals.put(MediaStore.Images.Media.IS_PENDING, 0);
                contentResolver.update(uri, vals, null, null);
            }
            return uri.toString();
        } catch (IOException e) {
            Log.e(TAG, "save ss: " + e.getMessage(), e);
            contentResolver.delete(uri, null, null);
            return null;
        }
    }

    // ===================== MEDIA CODEC ENCODING =====================

    private void initMediaEncoder(int displayWidth, int displayHeight) throws IOException {
        int cropW = (int) (displayWidth * lastNormalizedRegion.width());
        int cropH = (int) (displayHeight * lastNormalizedRegion.height());
        if (cropW % 2 != 0) cropW++;
        if (cropH % 2 != 0) cropH++;

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ContentValues vals = new ContentValues();
        vals.put(MediaStore.Video.Media.DISPLAY_NAME, "ScreenRecord_" + ts + ".mp4");
        vals.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        vals.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/مسجل الشاشة");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) vals.put(MediaStore.Video.Media.IS_PENDING, 1);

        Uri uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, vals);
        if (uri == null) throw new IOException("Failed to create MediaStore entry");
        currentVideoPath = uri.toString();

        // Use file path for MediaMuxer (MediaMuxer needs file path, not content URI)
        String filePath = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES) + "/مسجل الشاشة",
                "ScreenRecord_" + ts + ".mp4").getAbsolutePath();

        mediaMuxer = new MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        // === OPTIMIZED: Use YUV420 flexible format instead of COLOR_FormatSurface ===
        // COLOR_FormatSurface requires surface input (createInputSurface) which doesn't
        // support region cropping. COLOR_FormatYUV420Flexible lets us feed byte buffers.
        MediaFormat videoFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, cropW, cropH);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoConfig.getBitrate());
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, videoConfig.getFrameRateValue());
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, videoConfig.getIFrameIntervalValue());

        videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        videoCodec.start();

        // Add video track to muxer
        MediaFormat outputFormat = videoCodec.getOutputFormat();
        videoTrackIndex = mediaMuxer.addTrack(outputFormat);
        mediaMuxer.start();

        if (isAudioEnabled()) initAudioEncoder();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vals.clear(); vals.put(MediaStore.Video.Media.IS_PENDING, 0);
            contentResolver.update(uri, vals, null, null);
        }
    }

    private void initAudioEncoder() throws IOException {
        int sampleRate = audioConfig.getSampleRateValue();
        int bitrate = audioConfig.getBitrate();
        MediaFormat af = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, AUDIO_CHANNELS);
        af.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        af.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        af.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        audioCodec.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioCodec.start();

        MediaFormat audioOutputFormat = audioCodec.getOutputFormat();
        audioTrackIndex = mediaMuxer.addTrack(audioOutputFormat);
    }

    /**
     * Encode a YUV420 (NV21) frame buffer directly - NO Bitmap involved!
     * This is the optimized path that replaces the old Bitmap-based encodeFrame.
     */
    private void encodeYuvFrame(byte[] yuvData, int width, int height) {
        if (videoCodec == null || !isEncoding) return;
        try {
            MediaCodec.BufferInfo bufInfo = new MediaCodec.BufferInfo();
            int inputIndex = videoCodec.dequeueInputBuffer(10000);
            if (inputIndex >= 0) {
                ByteBuffer inputBuf = videoCodec.getInputBuffer(inputIndex);
                if (inputBuf != null) {
                    inputBuf.clear();
                    inputBuf.put(yuvData, 0, width * height * 3 / 2);
                    videoCodec.queueInputBuffer(inputIndex, 0, width * height * 3 / 2,
                            lastPresentationTimeUs, MediaCodec.BUFFER_FLAG_KEY_FRAME);
                }
            }

            int outputIndex = videoCodec.dequeueOutputBuffer(bufInfo, 10000);
            if (outputIndex >= 0) {
                ByteBuffer outputBuf = videoCodec.getOutputBuffer(outputIndex);
                if (outputBuf != null && mediaMuxer != null && videoTrackIndex >= 0) {
                    mediaMuxer.writeSampleData(videoTrackIndex, outputBuf, bufInfo);
                }
                videoCodec.releaseOutputBuffer(outputIndex, false);
            }

            lastPresentationTimeUs += 1000000 / videoConfig.getFrameRateValue();
        } catch (Exception e) {
            Log.e(TAG, "YUV encode: " + e.getMessage());
        }
    }

    // ===================== AUDIO RECORDING =====================

    private void startExternalAudioRecording() {
        if (audioRecord != null) return;
        int sr = audioConfig.getSampleRateValue();
        int bs = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int src = audioConfig.getNoiseSuppression().enabled
                ? (audioConfig.getNoiseSuppression().aggressive
                    ? MediaRecorder.AudioSource.CAMCORDER : MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                : MediaRecorder.AudioSource.MIC;

        audioRecord = new AudioRecord(src, sr, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(bs, 4096));
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) { audioRecord = null; return; }

        isAudioRecording.set(true);
        audioRecord.startRecording();
        audioThread = new Thread(() -> {
            ByteBuffer buf = ByteBuffer.allocateDirect(4096);
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            while (isAudioRecording.get() && isVideoCapturing) {
                if (isPaused) { try { Thread.sleep(50); } catch (InterruptedException e) { break; } continue; }
                buf.clear();
                int read = audioRecord.read(buf, 4096);
                if (read > 0 && audioCodec != null) { buf.position(read); buf.flip(); encodeAudio(buf, read); }
            }
            stopExternalAudio();
        }, "ExtAudio");
        audioThread.start();
    }

    private void startInternalAudioRecording() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        int sr = audioConfig.getSampleRateValue();
        int bs = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.REMOTE_SUBMIX, sr,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(bs, 4096));
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) { audioRecord = null; return; }

        isAudioRecording.set(true);
        audioRecord.startRecording();
        audioThread = new Thread(() -> {
            ByteBuffer buf = ByteBuffer.allocateDirect(4096);
            while (isAudioRecording.get() && isVideoCapturing) {
                if (isPaused) { try { Thread.sleep(50); } catch (InterruptedException e) { break; } continue; }
                buf.clear();
                int read = audioRecord.read(buf, 4096);
                if (read > 0 && audioCodec != null) { buf.position(read); buf.flip(); encodeAudio(buf, read); }
            }
            stopExternalAudio();
        }, "IntAudio");
        audioThread.start();
    }

    private void startMixedAudioRecording() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        int sr = audioConfig.getSampleRateValue();
        int bs = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int micSrc = audioConfig.getNoiseSuppression().enabled
                ? (audioConfig.getNoiseSuppression().aggressive
                    ? MediaRecorder.AudioSource.CAMCORDER : MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                : MediaRecorder.AudioSource.MIC;

        AudioRecord micRec = new AudioRecord(micSrc, sr, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(bs, 4096));
        AudioRecord intRec = new AudioRecord(MediaRecorder.AudioSource.REMOTE_SUBMIX, sr,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(bs, 4096));

        if (micRec.getState() != AudioRecord.STATE_INITIALIZED) { micRec.release(); startInternalAudioRecording(); return; }
        if (intRec.getState() != AudioRecord.STATE_INITIALIZED) { intRec.release(); startExternalAudioRecording(); return; }

        isAudioRecording.set(true);
        micRec.startRecording();
        intRec.startRecording();

        audioThread = new Thread(() -> {
            ByteBuffer micB = ByteBuffer.allocateDirect(4096);
            ByteBuffer intB = ByteBuffer.allocateDirect(4096);
            ByteBuffer mixB = ByteBuffer.allocateDirect(4096);
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            while (isAudioRecording.get() && isVideoCapturing) {
                if (isPaused) { try { Thread.sleep(50); } catch (InterruptedException e) { break; } continue; }
                micB.clear(); intB.clear(); mixB.clear();
                int mr = micRec.read(micB, 4096);
                int ir = intRec.read(intB, 4096);

                if (mr > 0 && ir > 0 && audioCodec != null) {
                    int n = Math.min(mr, ir) / 2 * 2;
                    mixB.limit(n); micB.flip(); intB.flip();
                    for (int i = 0; i < n / 2; i++)
                        mixB.putShort((short) ((micB.getShort() + intB.getShort()) / 2));
                    mixB.flip(); encodeAudio(mixB, n);
                } else if (mr > 0 && audioCodec != null) { micB.position(mr); micB.flip(); encodeAudio(micB, mr); }
                else if (ir > 0 && audioCodec != null) { intB.position(ir); intB.flip(); encodeAudio(intB, ir); }
            }
            try { if (micRec.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) micRec.stop(); } catch (Exception ignored) {}
            micRec.release();
            try { if (intRec.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) intRec.stop(); } catch (Exception ignored) {}
            intRec.release();
        }, "MixAudio");
        audioThread.start();
    }

    private void encodeAudio(ByteBuffer data, int size) {
        if (audioCodec == null) return;
        try {
            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            int idx = audioCodec.dequeueInputBuffer(10000);
            if (idx >= 0) {
                ByteBuffer ib = audioCodec.getInputBuffer(idx);
                if (ib != null) { ib.clear(); ib.put(data); audioCodec.queueInputBuffer(idx, 0, size, lastPresentationTimeUs, 0); }
            }
            int oi = audioCodec.dequeueOutputBuffer(bi, 10000);
            while (oi >= 0) {
                ByteBuffer ob = audioCodec.getOutputBuffer(oi);
                if (ob != null && mediaMuxer != null && audioTrackIndex >= 0)
                    mediaMuxer.writeSampleData(audioTrackIndex, ob, bi);
                audioCodec.releaseOutputBuffer(oi, false);
                oi = audioCodec.dequeueOutputBuffer(bi, 0);
            }
        } catch (Exception e) { Log.e(TAG, "Audio encode: " + e.getMessage()); }
    }

    // ===================== CLEANUP =====================

    private void stopAudioCapture() { isAudioRecording.set(false); stopExternalAudio(); audioThread = null; }

    private void stopExternalAudio() {
        if (audioRecord != null) {
            try { if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) audioRecord.stop(); }
            catch (Exception e) { Log.e(TAG, "stop audiorecord: " + e.getMessage()); }
            audioRecord.release(); audioRecord = null;
        }
    }

    private void stopEncoding() {
        if (videoCodec != null) {
            try { videoCodec.stop(); videoCodec.release(); } catch (Exception e) { Log.e(TAG, "stop vcodec: " + e.getMessage()); }
            videoCodec = null;
        }
        if (audioCodec != null) {
            try { audioCodec.stop(); audioCodec.release(); } catch (Exception e) { Log.e(TAG, "stop acodec: " + e.getMessage()); }
            audioCodec = null;
        }
        stopAudioCapture();
        if (mediaMuxer != null) {
            try { mediaMuxer.stop(); mediaMuxer.release(); } catch (Exception e) { Log.e(TAG, "stop muxer: " + e.getMessage()); }
            mediaMuxer = null;
        }
        videoTrackIndex = -1;
        audioTrackIndex = -1;
        lastPresentationTimeUs = 0;
        isEncoding = false;
    }

    private void createVideoReader(int width, int height) {
        releaseVideoReader();
        videoReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, MAX_IMAGES);
    }

    private void releaseVideoReader() {
        if (videoReader != null) { videoReader.setOnImageAvailableListener(null, null); videoReader.close(); videoReader = null; }
    }

    private void releaseScreenshotReader() {
        if (screenshotReader != null) { screenshotReader.setOnImageAvailableListener(null, null); screenshotReader.close(); screenshotReader = null; }
    }

    private void createVirtualDisplay(Surface surface, int width, int height) {
        releaseVirtualDisplay();
        virtualDisplay = mediaProjection.createVirtualDisplay("RegionCapture",
                width, height, 160, // default MDPI density
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, backgroundHandler);
    }

    private void releaseVirtualDisplay() {
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
    }
}
