package com.jgeraldo.mediaprojectionsample;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements VideoRecorder.RecordingCallback {

    public static final String ACTION_MEDIA_PROJECTION_STARTED = "com.jgeraldo.mediaprojectionsample.ACTION_MEDIA_PROJECTION_STARTED";
    public static final String TAG = "MediaProjectionSample";
    private static final int PERMISSION_REQUEST_CODE = 1000;

    private boolean isReceiverRegistered = false;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private Button mButtonStartProjection;
    private Button mButtonRecord;
    private Button mButtonRefresh;
    private TextView mRecordingStatus;
    private TextView mEmptyMessage;
    private Surface mSurface;
    private Handler mHandler;
    private ActivityResultLauncher<Intent> startMediaProjectionActivity;
    private VideoRecorder videoRecorder;
    private RecyclerView videosRecyclerView;
    private VideoAdapter videoAdapter;
    private List<VideoItem> videoItems;
    private boolean isProjectionActive = false;
    private Runnable timeoutFallback = null;

    // Creating a custom BroadcastReceiver class
    public class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "BroadcastReceiver onReceive called");
            if (ACTION_MEDIA_PROJECTION_STARTED.equals(intent.getAction())) {
                Log.d(TAG, "Received ACTION_MEDIA_PROJECTION_STARTED");
                int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
                Intent data = intent.getParcelableExtra("data");

                Log.d(TAG, "BroadcastReceiver resultCode: " + resultCode);
                Log.d(TAG, "BroadcastReceiver data is null: " + (data == null));

                MediaProjectionManager projectionManager =
                        (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mMediaProjection = projectionManager.getMediaProjection(resultCode, data);

                if (mMediaProjection != null) {
                    Log.d(TAG, "MediaProjection obtained from service, starting screen capture");
                    startScreenCapture();
                } else {
                    Log.e(TAG, "MediaProjection from service is null");
                }
            }
        }
    }

    private final MyBroadcastReceiver receiver = new MyBroadcastReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupRecyclerView();
        setupVideoRecorder();
        checkPermissions();
        loadRecordedVideos();
    }

    private void initViews() {
        SurfaceView mSurfaceView = findViewById(R.id.surface);
        mSurface = mSurfaceView.getHolder().getSurface();
        mHandler = new Handler(Looper.getMainLooper());

        // Add SurfaceHolder callback to ensure surface is ready
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                Log.d(TAG, "Surface created");
                mSurface = holder.getSurface();
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "Surface changed: " + width + "x" + height);
                mSurface = holder.getSurface();
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                Log.d(TAG, "Surface destroyed");
            }
        });

        mButtonStartProjection = findViewById(R.id.button_start_projection);
        mButtonRecord = findViewById(R.id.button_record);
        mButtonRefresh = findViewById(R.id.button_refresh);
        mRecordingStatus = findViewById(R.id.recording_status);
        mEmptyMessage = findViewById(R.id.empty_message);
        videosRecyclerView = findViewById(R.id.videos_recycler_view);

        mButtonStartProjection.setOnClickListener(view -> {
            if (!isProjectionActive) {
                requestScreenCapturePermission();
            } else {
                stopScreenCapture();
            }
        });

        mButtonRecord.setOnClickListener(view -> {
            if (!videoRecorder.isRecording()) {
                startRecording();
            } else {
                stopRecording();
            }
        });

        mButtonRefresh.setOnClickListener(view -> loadRecordedVideos());
    }

    private void setupRecyclerView() {
        videoItems = new ArrayList<>();
        videoAdapter = new VideoAdapter(this, videoItems);
        videosRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        videosRecyclerView.setAdapter(videoAdapter);
    }

    private void setupVideoRecorder() {
        videoRecorder = new VideoRecorder(this);
    }

    private void checkPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }

        // Android 14+ = API 34+ (including Android 15 = API 35)
        if (Build.VERSION.SDK_INT >= 30) { // Android 11+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION);
            }
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            Log.d(TAG, "Requesting permissions: " + permissionsNeeded.toString());
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "All permissions already granted");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        startMediaProjectionActivity = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    int resultCode = result.getResultCode();
                    Log.d(TAG, "Media projection permission result: " + resultCode);
                    Log.d(TAG, "Result OK: " + (resultCode == Activity.RESULT_OK));
                    Log.d(TAG, "Android version: " + Build.VERSION.SDK_INT);

                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        Log.d(TAG, "Intent data is null: " + (data == null));

                        if (Build.VERSION.SDK_INT >= 35) {
                            Log.d(TAG, "Using Android 15+ service approach (API " + Build.VERSION.SDK_INT + ")");
                            // Android 15+ - Service approach required
                            try {
                                Intent serviceIntent = new Intent(this, MyMediaProjectionService.class);
                                serviceIntent.putExtra("resultCode", resultCode);
                                serviceIntent.putExtra("data", data);

                                Log.d(TAG, "Starting MediaProjection service...");
                                ContextCompat.startForegroundService(this, serviceIntent);
                                Log.d(TAG, "Service start command sent");

                                // Check if service is actually running
                                mHandler.postDelayed(() -> {
                                    Log.d(TAG, "Checking if service is running...");
                                    // You should see service logs by now if it started successfully
                                }, 1000);

                                // Add timeout fallback in case service approach fails
                                timeoutFallback = new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!isProjectionActive) {
                                            Log.w(TAG, "Service approach timeout, trying direct approach as fallback");
                                            try {
                                                MediaProjectionManager projectionManager =
                                                        (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                                                mMediaProjection = projectionManager.getMediaProjection(resultCode, data);

                                                if (mMediaProjection != null) {
                                                    Log.d(TAG, "Fallback MediaProjection obtained successfully");
                                                    startScreenCapture();
                                                } else {
                                                    Log.e(TAG, "Fallback MediaProjection is null");
                                                    Toast.makeText(MainActivity.this, "Unable to start screen recording", Toast.LENGTH_LONG).show();
                                                }
                                            } catch (Exception ex) {
                                                Log.e(TAG, "Fallback approach also failed: " + ex.getMessage());
                                                Toast.makeText(MainActivity.this, "Screen recording not supported on this device", Toast.LENGTH_LONG).show();
                                            }
                                        }
                                        timeoutFallback = null;
                                    }
                                };
                                mHandler.postDelayed(timeoutFallback, 3000); // 3 second timeout

                            } catch (RuntimeException e) {
                                Log.w(TAG, "Service approach failed: " + e.getMessage());
                                // Try direct approach immediately as fallback
                                try {
                                    Log.d(TAG, "Service failed, trying direct approach as immediate fallback");
                                    MediaProjectionManager projectionManager =
                                            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                                    mMediaProjection = projectionManager.getMediaProjection(resultCode, data);

                                    if (mMediaProjection != null) {
                                        Log.d(TAG, "Immediate fallback MediaProjection obtained successfully");
                                        startScreenCapture();
                                    } else {
                                        Log.e(TAG, "Immediate fallback MediaProjection is null");
                                        Toast.makeText(this, R.string.unable_start_recording_device, Toast.LENGTH_LONG).show();
                                    }
                                } catch (Exception ex) {
                                    Log.e(TAG, "Immediate fallback also failed: " + ex.getMessage());
                                    Toast.makeText(this, R.string.unable_start_recording_device, Toast.LENGTH_LONG).show();
                                }
                            }
                        } else if (Build.VERSION.SDK_INT >= 30) {
                            Log.d(TAG, "Using Android 11-14 service approach (API " + Build.VERSION.SDK_INT + ")");
                            // Android 11-14 - Service approach 
                            try {
                                Intent serviceIntent = new Intent(this, MyMediaProjectionService.class);
                                serviceIntent.putExtra("resultCode", resultCode);
                                serviceIntent.putExtra("data", data);

                                Log.d(TAG, "Starting MediaProjection service...");
                                ContextCompat.startForegroundService(this, serviceIntent);
                                Log.d(TAG, "Service start command sent");
                            } catch (RuntimeException e) {
                                Log.w(TAG, "Service approach failed: " + e.getMessage());
                                Toast.makeText(this, R.string.unable_start_recording, Toast.LENGTH_SHORT).show();
                            }
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Log.d(TAG, "Using Android 10 direct approach");
                            // Android 10 - Direct approach
                            try {
                                MediaProjectionManager projectionManager =
                                        (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                                mMediaProjection = projectionManager.getMediaProjection(resultCode, data);

                                if (mMediaProjection != null) {
                                    Log.d(TAG, "Direct MediaProjection obtained successfully");
                                    startScreenCapture();
                                } else {
                                    Log.e(TAG, "Direct MediaProjection is null");
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Direct approach failed: " + e.getMessage());
                                Toast.makeText(this, R.string.unable_start_recording, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.d(TAG, "Using direct approach for Android < 10");
                            MediaProjectionManager projectionManager =
                                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                            mMediaProjection = projectionManager.getMediaProjection(resultCode, data);

                            if (mMediaProjection != null) {
                                Log.d(TAG, "MediaProjection obtained successfully");
                                startScreenCapture();
                            } else {
                                Log.e(TAG, "MediaProjection is null");
                            }
                        }
                    } else {
                        Log.w(TAG, "Media projection permission denied");
                        Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                    }
                });

        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_MEDIA_PROJECTION_STARTED);
            filter.addCategory(Intent.CATEGORY_DEFAULT);

            LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
            isReceiverRegistered = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (isReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            isReceiverRegistered = false;
        }

        if (videoRecorder.isRecording()) {
            videoRecorder.stopRecording();
        }

        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private void requestScreenCapturePermission() {
        if (startMediaProjectionActivity != null) {
            Log.d(TAG, "REQUESTING SCREEN CAPTURE INTENT PERMISSION");
            mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            Log.d(TAG, "CREATING THE SCREEN CAPTURE INTENT");
            startMediaProjectionActivity.launch(captureIntent);
        }
    }

    public void startScreenCapture() {
        Log.d(TAG, "startScreenCapture() called");
        Log.d(TAG, "mMediaProjection is null: " + (mMediaProjection == null));
        Log.d(TAG, "mSurface is null: " + (mSurface == null));

        if (timeoutFallback != null) {
            Log.d(TAG, "Cancelling timeout fallback because screen capture has started");
            mHandler.removeCallbacks(timeoutFallback);
            timeoutFallback = null;
        }

        if (mMediaProjection == null) {
            Log.e(TAG, "MediaProjection is null, cannot start screen capture");
            Toast.makeText(this, "MediaProjection not available", Toast.LENGTH_SHORT).show();
            return;
        }

        MediaProjection.Callback callback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
                Log.d(TAG, "MediaProjection stopped");
                runOnUiThread(() -> {
                    isProjectionActive = false;
                    mButtonStartProjection.setText(R.string.start_preview);
                    mButtonRecord.setEnabled(false);
                    if (videoRecorder.isRecording()) {
                        videoRecorder.stopRecording();
                    }
                });
                // Remove service stopping from here - it should only be called when user explicitly stops
            }
        };

        mMediaProjection.registerCallback(callback, null);

        try {
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    720,
                    1080,
                    getResources().getDisplayMetrics().densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    mSurface,
                    null,
                    mHandler);

            Log.d(TAG, "VirtualDisplay created successfully: " + (mVirtualDisplay != null));

            isProjectionActive = true;
            mButtonStartProjection.setText(R.string.stop_preview);
            mButtonRecord.setEnabled(true);

            Log.d(TAG, "Screen capture started successfully");
            Toast.makeText(this, "Screen capture started", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Error creating VirtualDisplay: " + e.getMessage());
            Toast.makeText(this, "Error starting screen capture: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopScreenCapture() {
        if (mVirtualDisplay == null) {
            return;
        }

        if (videoRecorder.isRecording()) {
            videoRecorder.stopRecording();
        }

        mVirtualDisplay.release();
        mVirtualDisplay = null;
        isProjectionActive = false;
        mButtonStartProjection.setText(R.string.start_preview);
        mButtonRecord.setEnabled(false);

        // Stop the MediaProjectionService if needed
        if (Build.VERSION.SDK_INT >= 30) {
            Log.d(TAG, "Stopping MyMediaProjectionService as stopScreenCapture called");
            Intent stopIntent = new Intent(this, MyMediaProjectionService.class);
            stopIntent.setAction("com.jgeraldo.mediaprojectionsample.ACTION_STOP_SERVICE");
            ContextCompat.startForegroundService(this, stopIntent);
        }
    }

    private void startRecording() {
        if (mMediaProjection != null && !videoRecorder.isRecording()) {
            videoRecorder.startRecording(this, mMediaProjection);
        }
    }

    private void stopRecording() {
        videoRecorder.stopRecording();
    }

    private void loadRecordedVideos() {
        File moviesDir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "ScreenRecordings");
        videoItems.clear();

        if (moviesDir.exists() && moviesDir.isDirectory()) {
            File[] files = moviesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
            if (files != null) {
                Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                for (File file : files) {
                    videoItems.add(new VideoItem(file));
                }
            }
        }

        videoAdapter.updateVideos(videoItems);
        if (videoItems.isEmpty()) {
            videosRecyclerView.setVisibility(View.GONE);
            mEmptyMessage.setVisibility(View.VISIBLE);
        } else {
            videosRecyclerView.setVisibility(View.VISIBLE);
            mEmptyMessage.setVisibility(View.GONE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    // VideoRecorder.RecordingCallback implementation
    @Override
    public void onRecordingStarted(String fileName) {
        runOnUiThread(() -> {
            mButtonRecord.setText(R.string.stop_recording);
            mRecordingStatus.setText(getString(R.string.recording_status, fileName));
            mRecordingStatus.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onRecordingStopped() {
        runOnUiThread(() -> {
            mButtonRecord.setText(R.string.start_recording);
            mRecordingStatus.setVisibility(View.GONE);
            Toast.makeText(this, R.string.recording_saved, Toast.LENGTH_SHORT).show();
            loadRecordedVideos();
        });
    }

    @Override
    public void onRecordingError(String error) {
        runOnUiThread(() -> {
            mButtonRecord.setText(R.string.start_recording);
            mRecordingStatus.setVisibility(View.GONE);
            Toast.makeText(this, getString(R.string.recording_error, error), Toast.LENGTH_LONG).show();
        });
    }
}