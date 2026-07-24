package com.jgeraldo.mediaprojectionsample;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaScannerConnection;
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

    private MediaCodec mediaCodec;
    private MediaMuxer mediaMuxer;
    private int trackIndex = -1;
    private long lastPresentationTimeUs = 0;
    private boolean isEncoding = false;
    private boolean isPaused = false;
    private boolean isVideoCapturing = false;
    private String currentVideoPath;

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

        imageReader.setOnImageAvailableListener(reader -> {
            if (!isVideoCapturing && isEncoding) {
                // Stop encoding when done
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
        stopEncoding();
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

    // ---------- Video Encoding (MediaCodec) ----------

    private void initMediaEncoder(int displayWidth, int displayHeight) throws IOException {
        // Calculate cropped dimensions
        int cropWidth = (int) (displayWidth * lastNormalizedRegion.width());
        int cropHeight = (int) (displayHeight * lastNormalizedRegion.height());

        // Ensure even dimensions for encoder
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

        // Setup MediaCodec
        MediaFormat format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, cropWidth, cropHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();

        // Update MediaStore entry after video is written
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            contentResolver.update(uri, values, null, null);
        }
    }

    private void encodeFrame(Bitmap bitmap) {
        if (mediaCodec == null || !isEncoding) return;

        try {
            // Get input buffer
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int inputIndex = mediaCodec.dequeueInputBuffer(10000);
            if (inputIndex >= 0) {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputIndex);
                if (inputBuffer != null) {
                    inputBuffer.clear();
                    // Convert bitmap to YUV format (simplified - use Surface input ideally)
                    // For a proper implementation, use MediaCodec input surface
                    // This is a simplified version
                    inputBuffer.put(bitmapToNV21(bitmap));
                    mediaCodec.queueInputBuffer(inputIndex, 0, inputBuffer.position(),
                            lastPresentationTimeUs, MediaCodec.BUFFER_FLAG_KEY_FRAME);
                }
            }

            // Get output buffer
            int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
            if (outputIndex >= 0) {
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputIndex);
                if (outputBuffer != null && mediaMuxer != null && trackIndex >= 0) {
                    mediaMuxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
                }
                mediaCodec.releaseOutputBuffer(outputIndex, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // End of stream
                }
            }

            lastPresentationTimeUs += 1000000 / FRAME_RATE;
        } catch (Exception e) {
            Log.e(TAG, "Encode error: " + e.getMessage());
        }
    }

    private void stopEncoding() {
        if (mediaCodec != null) {
            try {
                mediaCodec.stop();
                mediaCodec.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping codec: " + e.getMessage());
            }
            mediaCodec = null;
        }

        if (mediaMuxer != null) {
            try {
                mediaMuxer.stop();
                mediaMuxer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping muxer: " + e.getMessage());
            }
            mediaMuxer = null;
        }

        trackIndex = -1;
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
