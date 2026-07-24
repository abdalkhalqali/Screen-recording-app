package com.jgeraldo.mediaprojectionsample;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
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
    private static final int VIRTUAL_DISPLAY_WIDTH = 720;
    private static final int VIRTUAL_DISPLAY_HEIGHT = 1280;
    private static final int FRAME_RATE = 30;
    private static final int BIT_RATE = 4_000_000;
    private static final int I_FRAME_INTERVAL = 1;

    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private Handler backgroundHandler;
    private ContentResolver contentResolver;

    // Video encoding
    private MediaCodec videoCodec;
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
        AudioSource(String displayName) { this.displayName = displayName; }
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

    // Broadcast actions for floating control
    public static final String ACTION_PAUSE = "com.jgeraldo.mediaprojectionsample.PAUSE";
    public static final String ACTION_RESUME = "com.jgeraldo.mediaprojectionsample.RESUME";
    public static final String ACTION_STOP = "com.jgeraldo.mediaprojectionsample.STOP";
    public static final String ACTION_UPDATE_STATE = "com.jgeraldo.mediaprojectionsample.STATE";
    public static final String EXTRA_IS_RECORDING = "is_recording";
    public static final String EXTRA_IS_PAUSED = "is_paused";
    public static final String EXTRA_ELAPSED_TIME = "elapsed_time";

    private OnCaptureListener listener;

    private RectF lastNormalizedRegion = new RectF(0, 0, 1, 1);

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

    public void setOnCaptureListener(OnCaptureListener listener) {
        this.listener = listener;
    }

    public void setCaptureRegion(RectF normalizedRegion) {
        if (normalizedRegion != null) {
            this.lastNormalizedRegion.set(normalizedRegion);
        }
    }

    /**
     * Take a screenshot of the selected region
     */
    public void captureScreenshot(int displayWidth, int displayHeight) {
        createImageReader(displayWidth, displayHeight);

        imageReader.setOnImageAvailableListener(reader -> {
            try (Image image = reader.acquireLatestImage()) {
                if (image != null) {
                    Bitmap fullBitmap = imageToBitmap(image, displayWidth, displayHeight);
                    Bitmap croppedBitmap = cropBitmap(fullBitmap,
                            lastNormalizedRegion, displayWidth, displayHeight);
                    String savedUri = saveBitmapToGallery(croppedBitmap);

                    if (fullBitmap != null && !fullBitmap.isRecycled()) fullBitmap.recycle();
                    if (croppedBitmap != null && !croppedBitmap.isRecycled()) croppedBitmap.recycle();

                    if (listener != null) {
                        if (savedUri != null) {
                            listener.onScreenshotSaved(Uri.parse(savedUri),
                                    "تم حفظ لقطة الشاشة بنجاح");
                        } else {
                            listener.onCaptureError("فشل حفظ لقطة الشاشة");
                        }
                    }
                    releaseImageReader();
                }
            } catch (Exception e) {
                Log.e(TAG, "Screenshot error: " + e.getMessage(), e);
                if (listener != null) listener.onCaptureError("خطأ في التقاط الشاشة: " + e.getMessage());
                releaseImageReader();
            }
        }, backgroundHandler);

        createVirtualDisplay(imageReader.getSurface(), displayWidth, displayHeight);

        // Capture after a short delay to ensure the frame is ready
        backgroundHandler.postDelayed(() -> {
            // ImageReader callback will handle it
        }, 200);
    }

    /**
     * Set audio source type (INTERNAL, EXTERNAL, BOTH, NONE)
     */
    public void setAudioSource(AudioSource source) {
        this.audioSource = source;
    }

    public AudioSource getAudioSource() {
        return audioSource;
    }

    public void setAudioConfig(AudioConfig config) {
        if (config != null) this.audioConfig = config;
    }

    public AudioConfig getAudioConfig() {
        return audioConfig;
    }

    public boolean isAudioEnabled() {
        return audioSource != AudioSource.NONE;
    }

    /**
     * Start video recording of the selected region
     */
    public void startVideoCapture(int displayWidth, int displayHeight) {
        if (isVideoCapturing) return;
        isVideoCapturing = true;

        createImageReader(displayWidth, displayHeight);

        try {
            initMediaEncoder(displayWidth, displayHeight);
        } catch (IOException e) {
            Log.e(TAG, "Failed to init encoder: " + e.getMessage(), e);
            if (listener != null) listener.onCaptureError("فشل بدء تسجيل الفيديو");
            isVideoCapturing = false;
            return;
        }

        // Start audio recording based on source
        if (audioSource == AudioSource.EXTERNAL) {
            startExternalAudioRecording();
        } else if (audioSource == AudioSource.INTERNAL) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startInternalAudioRecording();
            } else {
                Log.w(TAG, "Internal audio requires Android 10+");
                if (listener != null)
                    listener.onCaptureError("الصوت الداخلي يحتاج Android 10 أو أحدث");
            }
        } else if (audioSource == AudioSource.BOTH) {
            // Start both sources and mix them into one audio track
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startMixedAudioRecording();
            } else {
                Log.w(TAG, "BOTH audio requires Android 10+");
                startExternalAudioRecording(); // Fallback to mic only
            }
        }

        imageReader.setOnImageAvailableListener(reader -> {
            if (!isVideoCapturing && isEncoding) {
                stopEncoding();
                return;
            }

            try (Image image = reader.acquireLatestImage()) {
                if (image != null && isEncoding) {
                    if (!isPaused) {
                        Bitmap fullBitmap = imageToBitmap(image, displayWidth, displayHeight);
                        Bitmap croppedBitmap = cropBitmap(fullBitmap,
                                lastNormalizedRegion, displayWidth, displayHeight);

                        if (croppedBitmap != null) {
                            encodeFrame(croppedBitmap);
                            croppedBitmap.recycle();
                        }
                        if (fullBitmap != null && !fullBitmap.isRecycled()) fullBitmap.recycle();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Video frame error: " + e.getMessage());
            }
        }, backgroundHandler);

        createVirtualDisplay(imageReader.getSurface(), displayWidth, displayHeight);
        isEncoding = true;

        if (listener != null) listener.onRecordingStarted();
    }

    /**
     * Stop video recording
     */
    public void stopVideoCapture() {
        isVideoCapturing = false;

        // Post to allow last frames to be processed
        backgroundHandler.postDelayed(() -> {
            stopEncoding();
            releaseVirtualDisplay();
            releaseImageReader();

            if (currentVideoPath != null) {
                // Scan the file so it appears in gallery
                Uri uri = Uri.parse(currentVideoPath);
                if (listener != null) listener.onVideoSaved(uri, "تم حفظ تسجيل الفيديو بنجاح");
                currentVideoPath = null;
            }
        }, 500);

        if (listener != null) listener.onRecordingStopped();
    }

    public boolean isCapturing() {
        return isVideoCapturing;
    }

    public boolean isPaused() {
        return isPaused;
    }

    /**
     * Pause video recording - stop encoding frames but keep the encoder alive
     */
    public void pauseVideoCapture() {
        if (!isVideoCapturing || isPaused) return;
        isPaused = true;
        if (listener != null) {
            listener.onRecordingPaused();
            listener.onRecordingStateUpdated(true, lastPresentationTimeUs / 1000);
        }
    }

    /**
     * Resume video recording - continue encoding frames
     */
    public void resumeVideoCapture() {
        if (!isVideoCapturing || !isPaused) return;
        isPaused = false;
        if (listener != null) {
            listener.onRecordingResumed();
            listener.onRecordingStateUpdated(false, lastPresentationTimeUs / 1000);
        }
    }

    public long getElapsedTimeMs() {
        return lastPresentationTimeUs / 1000;
    }

    public void release() {
        isVideoCapturing = false;
        isEncoding = false;
        isAudioRecording.set(false);
        stopEncoding();
        stopAudioCapture();
        releaseVirtualDisplay();
        releaseImageReader();
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    // ---------- Private Helpers ----------

    private void createImageReader(int width, int height) {
        releaseImageReader();
        imageReader = ImageReader.newInstance(
                width, height, PixelFormat.RGBA_8888, 2);
    }

    private void releaseImageReader() {
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader.close();
            imageReader = null;
        }
    }

    private void createVirtualDisplay(Surface surface, int width, int height) {
        releaseVirtualDisplay();
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "RegionCapture",
                width, height,
                DisplayManager.DEFAULT_DISPLAY_DPI,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                backgroundHandler
        );
    }

    private void releaseVirtualDisplay() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }

    private Bitmap imageToBitmap(Image image, int width, int height) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        Bitmap bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);

        if (rowPadding == 0) {
            return bitmap;
        }

        // Crop out padding
        return Bitmap.createBitmap(bitmap, 0, 0, width, height);
    }

    private Bitmap cropBitmap(Bitmap source, RectF normalizedRegion,
                              int displayWidth, int displayHeight) {
        if (source == null) return null;

        int cropLeft = (int) (normalizedRegion.left * displayWidth);
        int cropTop = (int) (normalizedRegion.top * displayHeight);
        int cropRight = (int) (normalizedRegion.right * displayWidth);
        int cropBottom = (int) (normalizedRegion.bottom * displayHeight);

        // Clamp to bitmap bounds
        cropLeft = Math.max(0, Math.min(cropLeft, source.getWidth() - 1));
        cropTop = Math.max(0, Math.min(cropTop, source.getHeight() - 1));
        cropRight = Math.max(cropLeft + 1, Math.min(cropRight, source.getWidth()));
        cropBottom = Math.max(cropTop + 1, Math.min(cropBottom, source.getHeight()));

        int cropWidth = cropRight - cropLeft;
        int cropHeight = cropBottom - cropTop;

        return Bitmap.createBitmap(source, cropLeft, cropTop, cropWidth, cropHeight);
    }

    private String saveBitmapToGallery(Bitmap bitmap) {
        if (bitmap == null) return null;

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "Screenshot_" + timestamp + ".jpg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/مسجل الشاشة");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return null;

        try (FileOutputStream out = (FileOutputStream) contentResolver.openOutputStream(uri)) {
            if (out != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                contentResolver.update(uri, values, null, null);
            }

            return uri.toString();
        } catch (IOException e) {
            Log.e(TAG, "Failed to save screenshot: " + e.getMessage(), e);
            contentResolver.delete(uri, null, null);
            return null;
        }
    }

    // ---------- Video + Audio Encoding ----------

    private void initMediaEncoder(int displayWidth, int displayHeight) throws IOException {
        // Calculate cropped dimensions
        int cropWidth = (int) (displayWidth * lastNormalizedRegion.width());
        int cropHeight = (int) (displayHeight * lastNormalizedRegion.height());

        if (cropWidth % 2 != 0) cropWidth++;
        if (cropHeight % 2 != 0) cropHeight++;

        // Create output file
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "ScreenRecord_" + timestamp + ".mp4";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/مسجل الشاشة");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
        }

        Uri uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Failed to create MediaStore entry");

        currentVideoPath = uri.toString();

        // Setup MediaMuxer
        mediaMuxer = new MediaMuxer(
                getFileForUri(uri),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        );

        // Setup Video MediaCodec
        MediaFormat videoFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, cropWidth, cropHeight);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        videoCodec.start();

        // Initialize audio encoder if any audio source is enabled
        if (isAudioEnabled()) {
            initAudioEncoder();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            contentResolver.update(uri, values, null, null);
        }
    }

    private void initAudioEncoder() throws IOException {
        int sampleRate = audioConfig.getSampleRateValue();
        int bitrate = audioConfig.getBitrate();

        MediaFormat audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, AUDIO_CHANNELS);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

        audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        audioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioCodec.start();
    }

    private void encodeFrame(Bitmap bitmap) {
        if (videoCodec == null || !isEncoding) return;

        try {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int inputIndex = videoCodec.dequeueInputBuffer(10000);
            if (inputIndex >= 0) {
                ByteBuffer inputBuffer = videoCodec.getInputBuffer(inputIndex);
                if (inputBuffer != null) {
                    inputBuffer.clear();
                    inputBuffer.put(bitmapToNV21(bitmap));
                    videoCodec.queueInputBuffer(inputIndex, 0, inputBuffer.position(),
                            lastPresentationTimeUs, MediaCodec.BUFFER_FLAG_KEY_FRAME);
                }
            }

            int outputIndex = videoCodec.dequeueOutputBuffer(bufferInfo, 10000);
            if (outputIndex >= 0) {
                ByteBuffer outputBuffer = videoCodec.getOutputBuffer(outputIndex);
                if (outputBuffer != null && mediaMuxer != null && videoTrackIndex >= 0) {
                    mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo);
                }
                videoCodec.releaseOutputBuffer(outputIndex, false);
            }

            lastPresentationTimeUs += 1000000 / FRAME_RATE;
        } catch (Exception e) {
            Log.e(TAG, "Video encode error: " + e.getMessage());
        }
    }

    /**
     * Start external (mic) audio recording
     */
    private void startExternalAudioRecording() {
        if (audioRecord != null) return;

        int sampleRate = audioConfig.getSampleRateValue();
        int bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        int audioRecorderSource = MediaRecorder.AudioSource.MIC;
        if (audioConfig.getNoiseSuppression().enabled) {
            audioRecorderSource = audioConfig.getNoiseSuppression().aggressive
                    ? MediaRecorder.AudioSource.CAMCORDER
                    : MediaRecorder.AudioSource.VOICE_COMMUNICATION;
        }

        audioRecord = new AudioRecord(
                audioRecorderSource,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(bufferSize, 4096)
        );

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "External AudioRecord failed to initialize");
            audioRecord = null;
            return;
        }

        isAudioRecording.set(true);
        audioRecord.startRecording();

        audioThread = new Thread(() -> {
            ByteBuffer audioBuffer = ByteBuffer.allocateDirect(4096);
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            while (isAudioRecording.get() && isVideoCapturing) {
                if (isPaused) {
                    try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                    continue;
                }

                audioBuffer.clear();
                int bytesRead = audioRecord.read(audioBuffer, 4096);

                if (bytesRead > 0 && audioCodec != null) {
                    audioBuffer.position(bytesRead);
                    audioBuffer.flip();
                    encodeAudio(audioBuffer, bytesRead);
                }
            }
            stopExternalAudio();
        }, "ExternalAudioThread");
        audioThread.start();
    }

    /**
     * Start internal (device) audio recording (Android 10+)
     */
    private void startInternalAudioRecording() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;

        int sampleRate = audioConfig.getSampleRateValue();
        int bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.REMOTE_SUBMIX,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(bufferSize, 4096)
        );

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "Internal audio not available on this device");
            audioRecord = null;
            return;
        }

        isAudioRecording.set(true);
        audioRecord.startRecording();

        audioThread = new Thread(() -> {
            ByteBuffer audioBuffer = ByteBuffer.allocateDirect(4096);
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            while (isAudioRecording.get() && isVideoCapturing) {
                if (isPaused) {
                    try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                    continue;
                }

                audioBuffer.clear();
                int bytesRead = audioRecord.read(audioBuffer, 4096);

                if (bytesRead > 0 && audioCodec != null) {
                    audioBuffer.position(bytesRead);
                    audioBuffer.flip();
                    encodeAudio(audioBuffer, bytesRead);
                }
            }
            stopExternalAudio();
        }, "InternalAudioThread");
        audioThread.start();
    }

    /**
     * Start BOTH external (mic) AND internal (REMOTE_SUBMIX) with PCM mixing
     * Mixes both audio sources together into a single audio track
     */
    private void startMixedAudioRecording() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;

        int sampleRate = audioConfig.getSampleRateValue();
        int bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        // Create EXTERNAL (mic) AudioRecord
        int micSource = MediaRecorder.AudioSource.MIC;
        if (audioConfig.getNoiseSuppression().enabled) {
            micSource = audioConfig.getNoiseSuppression().aggressive
                    ? MediaRecorder.AudioSource.CAMCORDER
                    : MediaRecorder.AudioSource.VOICE_COMMUNICATION;
        }
        AudioRecord micRecord = new AudioRecord(
                micSource, sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(bufferSize, 4096)
        );

        // Create INTERNAL (REMOTE_SUBMIX) AudioRecord
        AudioRecord internalRecord = new AudioRecord(
                MediaRecorder.AudioSource.REMOTE_SUBMIX,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(bufferSize, 4096)
        );

        if (micRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "Mic AudioRecord failed, falling back to internal only");
            micRecord.release();
            startInternalAudioRecording();
            return;
        }
        if (internalRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "Internal AudioRecord not available, falling back to mic only");
            internalRecord.release();
            startExternalAudioRecording();
            return;
        }

        isAudioRecording.set(true);
        micRecord.startRecording();
        internalRecord.startRecording();

        audioThread = new Thread(() -> {
            ByteBuffer micBuffer = ByteBuffer.allocateDirect(4096);
            ByteBuffer internalBuffer = ByteBuffer.allocateDirect(4096);
            ByteBuffer mixedBuffer = ByteBuffer.allocateDirect(4096);
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            while (isAudioRecording.get() && isVideoCapturing) {
                if (isPaused) {
                    try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                    continue;
                }

                micBuffer.clear();
                internalBuffer.clear();
                mixedBuffer.clear();

                int micRead = micRecord.read(micBuffer, 4096);
                int internalRead = internalRecord.read(internalBuffer, 4096);

                if (micRead > 0 && internalRead > 0 && audioCodec != null) {
                    int bytesToMix = Math.min(micRead, internalRead) / 2 * 2; // Even number
                    mixedBuffer.limit(bytesToMix);

                    micBuffer.flip();
                    internalBuffer.flip();

                    // PCM 16-bit mixing (average to prevent clipping)
                    for (int i = 0; i < bytesToMix / 2; i++) {
                        short micSample = micBuffer.getShort();
                        short internalSample = internalBuffer.getShort();
                        // Average the two samples to prevent clipping
                        short mixed = (short) ((micSample + internalSample) / 2);
                        mixedBuffer.putShort(mixed);
                    }

                    mixedBuffer.flip();
                    encodeAudio(mixedBuffer, bytesToMix);

                } else if (micRead > 0 && audioCodec != null) {
                    // Only mic data available
                    micBuffer.position(micRead);
                    micBuffer.flip();
                    encodeAudio(micBuffer, micRead);
                } else if (internalRead > 0 && audioCodec != null) {
                    // Only internal data available
                    internalBuffer.position(internalRead);
                    internalBuffer.flip();
                    encodeAudio(internalBuffer, internalRead);
                }
            }

            // Cleanup both records
            try {
                if (micRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
                    micRecord.stop();
            } catch (Exception ignored) {}
            micRecord.release();

            try {
                if (internalRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
                    internalRecord.stop();
            } catch (Exception ignored) {}
            internalRecord.release();
        }, "MixedAudioThread");
        audioThread.start();
    }

    /**
     * Encode audio buffer with AAC codec
     */
    private void encodeAudio(ByteBuffer audioData, int size) {
        if (audioCodec == null) return;

        try {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int inputIndex = audioCodec.dequeueInputBuffer(10000);
            if (inputIndex >= 0) {
                ByteBuffer inputBuffer = audioCodec.getInputBuffer(inputIndex);
                if (inputBuffer != null) {
                    inputBuffer.clear();
                    inputBuffer.put(audioData);
                    audioCodec.queueInputBuffer(inputIndex, 0, size,
                            lastPresentationTimeUs, 0);
                }
            }

            int outputIndex = audioCodec.dequeueOutputBuffer(bufferInfo, 10000);
            while (outputIndex >= 0) {
                ByteBuffer outputBuffer = audioCodec.getOutputBuffer(outputIndex);
                if (outputBuffer != null && mediaMuxer != null && audioTrackIndex >= 0) {
                    mediaMuxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo);
                }
                audioCodec.releaseOutputBuffer(outputIndex, false);
                outputIndex = audioCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Audio encode error: " + e.getMessage());
        }
    }

    private void stopAudioCapture() {
        isAudioRecording.set(false);
        stopExternalAudio();
        audioThread = null;
    }

    private void stopExternalAudio() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
                    audioRecord.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping AudioRecord: " + e.getMessage());
            }
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void stopEncoding() {
        if (videoCodec != null) {
            try {
                videoCodec.stop();
                videoCodec.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping video codec: " + e.getMessage());
            }
            videoCodec = null;
        }

        stopAudioCapture();

        if (mediaMuxer != null) {
            try {
                mediaMuxer.stop();
                mediaMuxer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping muxer: " + e.getMessage());
            }
            mediaMuxer = null;
        }

        videoTrackIndex = -1;
        audioTrackIndex = -1;
        lastPresentationTimeUs = 0;
        isEncoding = false;
    }

    /**
     * Simplified bitmap to NV21 conversion.
     * In production, use MediaCodec input surface for better performance.
     */
    private byte[] bitmapToNV21(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] argb = new int[width * height];
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);

        byte[] yuv = new byte[width * height * 3 / 2];
        int yIndex = 0;
        int uvIndex = width * height;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int pixel = argb[j * width + i];
                int R = (pixel >> 16) & 0xFF;
                int G = (pixel >> 8) & 0xFF;
                int B = pixel & 0xFF;

                int Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                yuv[yIndex++] = (byte) clamp(Y, 16, 235);

                if (j % 2 == 0 && i % 2 == 0) {
                    int U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                    int V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;
                    yuv[uvIndex++] = (byte) clamp(V, 16, 240);
                    yuv[uvIndex++] = (byte) clamp(U, 16, 240);
                }
            }
        }
        return yuv;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Get a temp file path from a content URI
     */
    private String getFileForUri(Uri uri) {
        // For simplicity, create a temp file path
        File moviesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES);
        File appDir = new File(moviesDir, "مسجل الشاشة");
        if (!appDir.exists()) appDir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return new File(appDir, "ScreenRecord_" + timestamp + ".mp4").getAbsolutePath();
    }
}
