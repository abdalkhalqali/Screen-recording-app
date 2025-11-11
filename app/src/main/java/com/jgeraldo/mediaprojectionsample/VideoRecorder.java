package com.jgeraldo.mediaprojectionsample;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VideoRecorder {
    private static final String TAG = "VideoRecorder";
    private static final int VIDEO_WIDTH = 1080;
    private static final int VIDEO_HEIGHT = 1920;
    private static final int VIDEO_BITRATE = 6000000;
    private static final int VIDEO_FRAME_RATE = 30;
    private static final int AUDIO_BITRATE = 128000;
    private static final int AUDIO_SAMPLE_RATE = 44100;

    private MediaRecorder mediaRecorder;
    private VirtualDisplay virtualDisplay;
    private MediaProjection mediaProjection;
    private File outputFile;
    private boolean isRecording = false;

    public interface RecordingCallback {
        void onRecordingStarted(String fileName);

        void onRecordingStopped();

        void onRecordingError(String error);
    }

    private RecordingCallback callback;

    public VideoRecorder(RecordingCallback callback) {
        this.callback = callback;
    }

    public boolean startRecording(Context context, MediaProjection mediaProjection) {
        if (isRecording) {
            return false;
        }

        this.mediaProjection = mediaProjection;

        try {
            // Create output file
            outputFile = createOutputFile(context);

            // Setup MediaRecorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
            mediaRecorder.setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setVideoEncodingBitRate(VIDEO_BITRATE);
            mediaRecorder.setVideoFrameRate(VIDEO_FRAME_RATE);
            mediaRecorder.setAudioEncodingBitRate(AUDIO_BITRATE);
            mediaRecorder.setAudioSamplingRate(AUDIO_SAMPLE_RATE);

            mediaRecorder.prepare();

            // Create virtual display
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenRecorder",
                    VIDEO_WIDTH,
                    VIDEO_HEIGHT,
                    context.getResources().getDisplayMetrics().densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mediaRecorder.getSurface(),
                    null,
                    null
            );

            mediaRecorder.start();
            isRecording = true;

            if (callback != null) {
                callback.onRecordingStarted(outputFile.getName());
            }

            Log.d(TAG, "Recording started: " + outputFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error starting recording", e);
            cleanup();
            if (callback != null) {
                callback.onRecordingError("Failed to start recording: " + e.getMessage());
            }
            return false;
        }
    }

    public void stopRecording() {
        if (!isRecording) {
            return;
        }

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }

            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }

            isRecording = false;

            if (callback != null) {
                callback.onRecordingStopped();
            }

            Log.d(TAG, "Recording stopped: " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording", e);
            if (callback != null) {
                callback.onRecordingError("Error stopping recording: " + e.getMessage());
            }
        }
    }

    private void cleanup() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up MediaRecorder", e);
            }
            mediaRecorder = null;
        }

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        isRecording = false;
    }

    private File createOutputFile(Context context) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "ScreenRecord_" + timestamp + ".mp4";

        // Use app's external files directory
        File moviesDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "ScreenRecordings");
        if (!moviesDir.exists()) {
            moviesDir.mkdirs();
        }

        return new File(moviesDir, fileName);
    }

    public boolean isRecording() {
        return isRecording;
    }

    public File getOutputFile() {
        return outputFile;
    }
}