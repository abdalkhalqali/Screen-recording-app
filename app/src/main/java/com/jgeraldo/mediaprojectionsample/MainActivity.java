package com.jgeraldo.mediaprojectionsample;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.FrameLayout;

import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_MEDIA_PROJECTION_STARTED =
            "com.jgeraldo.mediaprojectionsample.ACTION_MEDIA_PROJECTION_STARTED";
    public static final String TAG = "MediaProjectionSample";

    // Capture modes
    private enum CaptureMode { SCREENSHOT, VIDEO, BOTH }

    private boolean isReceiverRegistered = false;
    private boolean isRecording = false;

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;

    private SurfaceView mSurfaceView;
    private RegionOverlayView regionOverlay;
    private Button mButtonToggle;
    private MaterialButtonToggleGroup modeToggleGroup;
    private LinearLayout captureInfoPanel;
    private TextView captureInfoText;
    private MaterialCardView controlPanel;
    private MaterialButton btnScreenshotQuick;
    private MaterialButton btnMic, btnSettings, btnGallery, btnTimer, btnLock, btnResolution;
    private ScreenCaptureEngine.AudioSource audioSourceMode = ScreenCaptureEngine.AudioSource.EXTERNAL;
    private AudioConfig audioConfig = new AudioConfig();
    private VideoConfig videoConfig = new VideoConfig();

    private Surface mSurface;
    private Handler mHandler;
    private ActivityResultLauncher<Intent> startMediaProjectionActivity;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> overlaySettingsLauncher;

    private ScreenCaptureEngine captureEngine;
    private CaptureMode currentMode = CaptureMode.BOTH;

    // Settings persistence
    private SettingsPrefs settingsPrefs;
    private SettingsPrefs.VideoResolution currentResolution = SettingsPrefs.VideoResolution.RES_720P;
    private boolean isRegionLocked = false;
    private int delaySeconds = 0;
    private int maxDurationSeconds = 0;
    private CountDownTimer delayTimer, autoStopTimer;
    private ScreenCaptureEngine.AutoPauseMode autoPauseMode = ScreenCaptureEngine.AutoPauseMode.OFF;

    private int displayWidth = 720, displayHeight = 1280;

    // Permission tracking
    private boolean isCheckingPermissions = false;
    private View permissionStatusBar;
    private TextView permissionStatusText, permissionDot;
    private boolean permissionsReady = false;

    // BroadcastReceiver to handle service messages
    public class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_MEDIA_PROJECTION_STARTED.equals(intent.getAction())) {
                int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
                Intent data = intent.getParcelableExtra("data");
                MediaProjectionManager projectionManager =
                        (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mMediaProjection = projectionManager.getMediaProjection(resultCode, data);
                if (mMediaProjection != null) startScreenCapture();
            }
        }
    }

    private final MyBroadcastReceiver receiver = new MyBroadcastReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Try to set the main layout. If it fails, show error on screen.
        try {
            setContentView(R.layout.activity_main);
        } catch (Throwable t) {
            Log.e(TAG, "LAYOUT FAILED: " + t, t);
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            android.widget.TextView tv = new android.widget.TextView(this);
            tv.setText("🔴 ERROR: " + t.getClass().getSimpleName() + "\n\n" +
                    (t.getMessage() != null ? t.getMessage() : "") + "\n\n" +
                    "📋 Stack:\n" + android.util.Log.getStackTraceString(t));
            tv.setTextColor(0xFFFF4444);
            tv.setTextSize(13f);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setPadding(24, 48, 24, 24);
            tv.setBackgroundColor(0xFF1A1A2E);
            sv.setBackgroundColor(0xFF1A1A2E);
            sv.addView(tv);
            setContentView(sv);
            return;
        }

            mHandler = new Handler(Looper.getMainLooper());

            // Register ALL activity result launchers HERE (before STARTED state)
            requestPermissionLauncher = registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(), isGranted -> {
                        checkAllPermissionsAndUpdateUI();
                        if (isGranted) Log.d(TAG, "Permission granted");
                    });

            overlaySettingsLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(), result ->
                            checkAllPermissionsAndUpdateUI());

            startMediaProjectionActivity = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(), result -> {
                        if (result == null) return;
                        int resultCode = result.getResultCode();
                        if (resultCode == Activity.RESULT_OK) {
                            Intent data = result.getData();
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                MediaProjectionManager pm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                                if (pm != null) {
                                    mMediaProjection = pm.getMediaProjection(resultCode, data);
                                    if (mMediaProjection != null) startScreenCapture();
                                }
                            } else {
                                try {
                                    Intent si = new Intent(this, MyMediaProjectionService.class);
                                    si.putExtra("resultCode", resultCode);
                                    si.putExtra("data", data);
                                    ContextCompat.startForegroundService(this, si);
                                } catch (RuntimeException e) { Log.w(TAG, "Error: " + e.getMessage()); }
                            }
                        } else Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show();
                    });

            // Init settings
            settingsPrefs = new SettingsPrefs(this);

            // Init views (MUST be before loadSavedSettings!)
            mSurfaceView = findViewById(R.id.surface);
            regionOverlay = findViewById(R.id.regionOverlay);
            mButtonToggle = findViewById(R.id.button);
            modeToggleGroup = findViewById(R.id.modeToggle);
            captureInfoPanel = findViewById(R.id.captureInfoPanel);
            captureInfoText = findViewById(R.id.captureInfoText);
            controlPanel = findViewById(R.id.controlPanel);
            btnScreenshotQuick = findViewById(R.id.btnScreenshotQuick);
            btnMic = findViewById(R.id.btnMic);
            btnSettings = findViewById(R.id.btnSettings);
            btnGallery = findViewById(R.id.btnGallery);
            btnTimer = findViewById(R.id.btnTimer);
            btnLock = findViewById(R.id.btnLock);
            btnResolution = findViewById(R.id.btnResolution);
            permissionStatusBar = findViewById(R.id.permissionStatusBar);
            permissionStatusText = findViewById(R.id.permissionStatusText);
            permissionDot = findViewById(R.id.permissionDot);

            // Load saved settings NOW (after all views are initialized)
            loadSavedSettings();

            if (permissionStatusBar != null)
                permissionStatusBar.setOnClickListener(v -> onPermissionStatusBarClick());

            if (mSurfaceView != null) {
                mSurface = mSurfaceView.getHolder() != null ? mSurfaceView.getHolder().getSurface() : null;
            }

            if (regionOverlay != null) {
                regionOverlay.setOnRegionChangedListener(region -> {
                    if (captureEngine != null)
                        captureEngine.setCaptureRegion(regionOverlay.getNormalizedRegion());
                    updateCaptureInfo();
                });
            }

            if (modeToggleGroup != null) {
                modeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                    if (!isChecked) return;
                    if (checkedId == R.id.modeScreenshot) currentMode = CaptureMode.SCREENSHOT;
                    else if (checkedId == R.id.modeVideo) currentMode = CaptureMode.VIDEO;
                    else if (checkedId == R.id.modeBoth) currentMode = CaptureMode.BOTH;
                    updateCaptureInfo();
                });
                modeToggleGroup.check(R.id.modeBoth);

                // Restore saved capture mode safely
                int savedMode = settingsPrefs != null ? settingsPrefs.getCaptureMode(2) : 2;
                if (savedMode == 0) modeToggleGroup.check(R.id.modeScreenshot);
                else if (savedMode == 1) modeToggleGroup.check(R.id.modeVideo);
                else modeToggleGroup.check(R.id.modeBoth);
                if (savedMode >= 0 && savedMode < CaptureMode.values().length) {
                    currentMode = CaptureMode.values()[savedMode];
                } else {
                    currentMode = CaptureMode.BOTH;
                }
            }

            // Main button
            if (mButtonToggle != null) {
                mButtonToggle.setOnClickListener(view -> {
                    if (!isRecording) {
                        if (!permissionsReady) { checkAllPermissionsAndUpdateUI(); return; }
                        if (delaySeconds > 0) startDelayTimer();
                        else requestScreenCapturePermission();
                    } else stopCapture();
                });
            }

            // Audio source
            if (btnMic != null) {
                btnMic.setOnClickListener(v -> {
                    ScreenCaptureEngine.AudioSource[] sources = ScreenCaptureEngine.AudioSource.values();
                    audioSourceMode = sources[(audioSourceMode.ordinal() + 1) % sources.length];
                    updateAudioSourceButton();
                    if (settingsPrefs != null) settingsPrefs.setAudioSource(audioSourceMode.ordinal());
                });
            }
            updateAudioSourceButton();

            // Settings button - choose Audio or Video settings
            if (btnSettings != null) btnSettings.setOnClickListener(v -> showSettingsChoiceDialog());

            // Screenshot quick
            if (btnScreenshotQuick != null) {
                btnScreenshotQuick.setOnClickListener(v -> {
                    if (captureEngine != null && mMediaProjection != null && !isRecording) {
                        captureEngine.captureScreenshot(displayWidth, displayHeight);
                        animateButton(v);
                    } else if (isRecording) takeScreenshotDuringRecording();
                    else Toast.makeText(this, getString(R.string.start_first), Toast.LENGTH_SHORT).show();
                });
            }

            // Gallery button
            if (btnGallery != null) {
                btnGallery.setOnClickListener(v -> {
                    Intent intent = new Intent(this, MediaGalleryActivity.class);
                    startActivity(intent);
                });
            }

            // Timer button
            if (btnTimer != null) btnTimer.setOnClickListener(v -> showTimerDialog());

            // Lock region
            if (btnLock != null) btnLock.setOnClickListener(v -> toggleRegionLock());

            // Resolution
            if (btnResolution != null) btnResolution.setOnClickListener(v -> showResolutionPicker());

            // Animate
            if (controlPanel != null) {
                controlPanel.setAlpha(0f);
                controlPanel.setTranslationY(100f);
                controlPanel.animate().alpha(1f).translationY(0f).setDuration(600).setStartDelay(200).start();
            }

            updateCaptureInfo();
            mHandler.postDelayed(() -> checkAllPermissionsAndUpdateUI(), 500);
        } catch (Throwable t) {
            Log.e(TAG, "Startup crashed", t);
            Toast.makeText(this, "🔴 خطأ أثناء فتح التطبيق: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_MEDIA_PROJECTION_STARTED);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
            isReceiverRegistered = true;
        }
        registerFloatingControlReceiver();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            isReceiverRegistered = false;
        }
        if (!isRecording && mMediaProjection != null) { mMediaProjection.stop(); mMediaProjection = null; }
        unregisterFloatingControlReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (captureEngine != null) { captureEngine.release(); captureEngine = null; }
        if (mVirtualDisplay != null) { mVirtualDisplay.release(); mVirtualDisplay = null; }
        if (delayTimer != null) delayTimer.cancel();
        if (autoStopTimer != null) autoStopTimer.cancel();
    }

    // ---- Settings ----
    private void loadSavedSettings() {
        audioConfig = settingsPrefs.loadAudioConfig();
        videoConfig = settingsPrefs.loadVideoConfig();
        int apOrd = settingsPrefs.getAutoPauseMode(ScreenCaptureEngine.AutoPauseMode.OFF.ordinal());
        autoPauseMode = EnumUtils.getSafeEnumValue(
                ScreenCaptureEngine.AutoPauseMode.values(),
                apOrd,
                ScreenCaptureEngine.AutoPauseMode.OFF);
        int sourceOrd = settingsPrefs.getAudioSource(ScreenCaptureEngine.AudioSource.EXTERNAL.ordinal());
        audioSourceMode = EnumUtils.getSafeEnumValue(
                ScreenCaptureEngine.AudioSource.values(),
                sourceOrd,
                ScreenCaptureEngine.AudioSource.EXTERNAL);

        int resOrd = settingsPrefs.getVideoResolution(SettingsPrefs.VideoResolution.RES_720P.ordinal());
        currentResolution = EnumUtils.getSafeEnumValue(
                SettingsPrefs.VideoResolution.values(),
                resOrd,
                SettingsPrefs.VideoResolution.RES_720P);
        displayWidth = currentResolution.width;
        displayHeight = currentResolution.height;

        delaySeconds = settingsPrefs.getDelaySeconds(0);
        maxDurationSeconds = settingsPrefs.getMaxDurationSeconds(0);
        isRegionLocked = settingsPrefs.isRegionLocked(false);

        float[] savedRegion = settingsPrefs.loadRegion();
        if (savedRegion != null && regionOverlay != null) {
            regionOverlay.setSelectedRegion(new RectF(savedRegion[0], savedRegion[1], savedRegion[2], savedRegion[3]));
        }

        updateResolutionButton();
        updateLockButton();
    }

    // ---- Delay Timer ----
    private void startDelayTimer() {
        Toast.makeText(this, getString(R.string.recording_starts_in, delaySeconds), Toast.LENGTH_SHORT).show();
        mButtonToggle.setEnabled(false);
        mButtonToggle.setText("⏳ " + delaySeconds);

        delayTimer = new CountDownTimer(delaySeconds * 1000L, 1000) {
            @Override public void onTick(long millisUntilFinished) {
                mButtonToggle.setText("⏳ " + (millisUntilFinished / 1000 + 1));
            }
            @Override public void onFinish() {
                mButtonToggle.setEnabled(true);
                mButtonToggle.setText(R.string.button_stop);
                requestScreenCapturePermission();
            }
        }.start();
    }

    private void cancelDelayTimer() {
        if (delayTimer != null) { delayTimer.cancel(); delayTimer = null; }
    }

    // ---- Auto-Stop Timer ----
    private void startAutoStopTimer() {
        if (maxDurationSeconds <= 0) return;
        autoStopTimer = new CountDownTimer(maxDurationSeconds * 1000L, 1000) {
            @Override public void onTick(long millisUntilFinished) {
                if (captureEngine != null && captureEngine.isCapturing()) {
                    int remaining = (int) (millisUntilFinished / 1000);
                    if (remaining <= 5 && remaining > 0) {
                        Toast.makeText(MainActivity.this, "⏰ " + remaining + "...", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            @Override public void onFinish() {
                Toast.makeText(MainActivity.this, R.string.recording_stopped, Toast.LENGTH_SHORT).show();
                stopCapture();
            }
        }.start();
    }

    private void cancelAutoStopTimer() {
        if (autoStopTimer != null) { autoStopTimer.cancel(); autoStopTimer = null; }
    }

    // ---- Timer Dialog ----
    private void showTimerDialog() {
        final String[] delayOptions = {getString(R.string.no_delay), getString(R.string.seconds_3),
                getString(R.string.seconds_5), getString(R.string.seconds_10)};
        final int[] delayValues = {0, 3, 5, 10};

        final String[] maxOptions = {getString(R.string.no_limit), getString(R.string.min_1),
                getString(R.string.min_3), getString(R.string.min_5),
                getString(R.string.min_10), getString(R.string.min_30)};
        final int[] maxValues = {0, 60, 180, 300, 600, 1800};

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_audio_settings, null);
        new MaterialAlertDialogBuilder(this)
                .setTitle("⏱️ المؤقتات")
                .setItems(new String[]{
                        "⏱️ تأخير: " + (delaySeconds > 0 ? delaySeconds + "ث" : "بدون"),
                        "⏰ إيقاف: " + (maxDurationSeconds > 0 ? (maxDurationSeconds / 60) + "د" : "بدون")
                }, (dialog, which) -> {
                    if (which == 0) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(getString(R.string.delay_timer))
                                .setSingleChoiceItems(delayOptions, getDelayIndex(delaySeconds), (d, w) -> {
                                    delaySeconds = delayValues[w];
                                    settingsPrefs.setDelaySeconds(delaySeconds);
                                    updateTimerButton();
                                    d.dismiss();
                                }).setNegativeButton("إلغاء", null).show();
                    } else {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(getString(R.string.auto_stop))
                                .setSingleChoiceItems(maxOptions, getMaxIndex(maxDurationSeconds), (d, w) -> {
                                    maxDurationSeconds = maxValues[w];
                                    settingsPrefs.setMaxDurationSeconds(maxDurationSeconds);
                                    updateTimerButton();
                                    d.dismiss();
                                }).setNegativeButton("إلغاء", null).show();
                    }
                }).setNegativeButton("تم", null).show();
    }

    private int getDelayIndex(int secs) {
        if (secs == 0) return 0; if (secs == 3) return 1; if (secs == 5) return 2; return 3;
    }
    private int getMaxIndex(int secs) {
        if (secs == 0) return 0; if (secs == 60) return 1; if (secs == 180) return 2;
        if (secs == 300) return 3; if (secs == 600) return 4; return 5;
    }
    private void updateTimerButton() {
        if (btnTimer == null) return;
        String text = "⏱️";
        if (delaySeconds > 0 || maxDurationSeconds > 0) {
            if (delaySeconds > 0) text += delaySeconds + "ث";
            if (maxDurationSeconds > 0) text += "/" + (maxDurationSeconds / 60) + "د";
        }
        btnTimer.setText(text);
    }

    // ---- Region Lock ----
    private void toggleRegionLock() {
        isRegionLocked = !isRegionLocked;
        regionOverlay.setEnabled(!isRegionLocked);
        settingsPrefs.setRegionLocked(isRegionLocked);
        updateLockButton();
        Toast.makeText(this, isRegionLocked ? R.string.region_locked : R.string.region_unlocked, Toast.LENGTH_SHORT).show();
    }
    private void updateLockButton() {
        if (btnLock == null) return;
        btnLock.setText(isRegionLocked ? "🔒" : "🔓");
        btnLock.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                isRegionLocked ? 0x1AEF4444 : 0x1A6366F1));
    }

    // ---- Resolution ----
    private void showResolutionPicker() {
        SettingsPrefs.VideoResolution[] values = SettingsPrefs.VideoResolution.values();
        String[] labels = new String[values.length];
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i].label;
            if (values[i] == currentResolution) selected = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.resolution_label)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    currentResolution = values[which];
                    displayWidth = currentResolution.width;
                    displayHeight = currentResolution.height;
                    settingsPrefs.setVideoResolution(which);
                    updateResolutionButton();
                    dialog.dismiss();
                    Toast.makeText(this, "✅ " + currentResolution.label, Toast.LENGTH_SHORT).show();
                }).setNegativeButton("إلغاء", null).show();
    }
    private void updateResolutionButton() {
        if (btnResolution == null || currentResolution == null) return;
        btnResolution.setText(currentResolution.label.replace(" (HD)", "").replace(" (Full HD)", "").replace(" (SD)", ""));
    }

    // ---- Screen Capture ----
    private void requestScreenCapturePermission() {
        if (startMediaProjectionActivity != null) {
            mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startMediaProjectionActivity.launch(mediaProjectionManager.createScreenCaptureIntent());
        }
    }

    private void startScreenCapture() {
        if (mMediaProjection == null) return;

        // Save region and settings
        settingsPrefs.saveRegion(
                regionOverlay.getNormalizedRegion().left,
                regionOverlay.getNormalizedRegion().top,
                regionOverlay.getNormalizedRegion().right,
                regionOverlay.getNormalizedRegion().bottom
        );
        settingsPrefs.saveAudioConfig(audioConfig);
        settingsPrefs.setCaptureMode(currentMode.ordinal());
        settingsPrefs.setAudioSource(audioSourceMode.ordinal());

        captureEngine = new ScreenCaptureEngine(mMediaProjection, getContentResolver());
        captureEngine.setCaptureRegion(regionOverlay.getNormalizedRegion());
        captureEngine.setAudioSource(audioSourceMode);
        captureEngine.setAudioConfig(audioConfig);
        captureEngine.setVideoConfig(videoConfig);
        captureEngine.setAutoPauseMode(autoPauseMode);
        captureEngine.setOnCaptureListener(new ScreenCaptureEngine.OnCaptureListener() {
            @Override public void onScreenshotSaved(Uri uri, String message) {
                runOnUiThread(() -> { Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show(); showSavedNotification(uri, "image/*"); });
            }
            @Override public void onVideoSaved(Uri uri, String message) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    showSavedNotification(uri, "video/*");
                    isRecording = false;
                    mButtonToggle.setText(R.string.button_start);
                    regionOverlay.setVisibility(View.VISIBLE);
                    showCaptureStats();
                });
            }
            @Override public void onCaptureError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                    if (isRecording) { isRecording = false; mButtonToggle.setText(R.string.button_start); regionOverlay.setVisibility(View.VISIBLE); }
                });
            }
            @Override public void onRecordingStarted() {
                runOnUiThread(() -> {
                    isRecording = true;
                    mButtonToggle.setText(R.string.button_stop);
                    modeToggleGroup.setEnabled(false);
                    regionOverlay.setVisibility(View.GONE);
                    btnScreenshotQuick.setVisibility(View.VISIBLE);
                    animateButton(mButtonToggle);
                    showFloatingControl(false, 0);
                    startAutoStopTimer();
                });
            }
            @Override public void onRecordingStopped() {
                runOnUiThread(() -> {
                    modeToggleGroup.setEnabled(true);
                    btnScreenshotQuick.setVisibility(View.GONE);
                    hideFloatingControl();
                    cancelAutoStopTimer();
                });
            }
            @Override public void onRecordingPaused() {
                runOnUiThread(() -> { Toast.makeText(MainActivity.this, R.string.recording_paused, Toast.LENGTH_SHORT).show(); mButtonToggle.setText("⏸️"); });
            }
            @Override public void onRecordingResumed() {
                runOnUiThread(() -> { Toast.makeText(MainActivity.this, R.string.recording_resumed, Toast.LENGTH_SHORT).show(); mButtonToggle.setText(R.string.button_stop); });
            }
            @Override public void onRecordingStateUpdated(boolean paused, long elapsedMs) {
                runOnUiThread(() -> updateFloatingState(paused, elapsedMs));
            }
        });

        mMediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { super.onStop(); }
        }, null);

        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                getString(R.string.screen_capture_title), displayWidth, displayHeight,
                getResources().getDisplayMetrics().densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mSurface, null, mHandler);

        startCaptureByMode();
        mButtonToggle.setText(R.string.button_stop);
    }

    private void startCaptureByMode() {
        switch (currentMode) {
            case SCREENSHOT: captureEngine.captureScreenshot(displayWidth, displayHeight); break;
            case VIDEO: captureEngine.startVideoCapture(displayWidth, displayHeight); break;
            case BOTH:
                captureEngine.setCaptureRegion(regionOverlay.getNormalizedRegion());
                captureEngine.captureScreenshot(displayWidth, displayHeight);
                mHandler.postDelayed(() -> {
                    if (captureEngine != null) {
                        captureEngine.setCaptureRegion(regionOverlay.getNormalizedRegion());
                        captureEngine.startVideoCapture(displayWidth, displayHeight);
                    }
                }, 300);
                break;
        }
    }

    private void stopCapture() {
        cancelDelayTimer();
        cancelAutoStopTimer();
        if (captureEngine != null && captureEngine.isCapturing()) captureEngine.stopVideoCapture();
        else stopFullCapture();
    }

    private void stopFullCapture() {
        if (mVirtualDisplay != null) { mVirtualDisplay.release(); mVirtualDisplay = null; }
        if (mMediaProjection != null) { mMediaProjection.stop(); mMediaProjection = null; }
        isRecording = false; mButtonToggle.setText(R.string.button_start);
        regionOverlay.setVisibility(View.VISIBLE); modeToggleGroup.setEnabled(true); hideFloatingControl();
    }

    private void takeScreenshotDuringRecording() {
        if (captureEngine != null) {
            Toast.makeText(this, R.string.screenshot_during_recording, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- Capture Stats Dialog ----
    private void showCaptureStats() {
        long elapsed = captureEngine != null ? captureEngine.getElapsedTimeMs() / 1000 : 0;
        String duration = String.format("%d:%02d", elapsed / 60, elapsed % 60);

        new MaterialAlertDialogBuilder(this)
                .setTitle("📊 إحصائيات التسجيل")
                .setMessage(
                        "🎬 المدة: " + duration + "\n" +
                        "📺 الدقة: " + displayWidth + "×" + displayHeight + "\n" +
                        "🔊 الصوت: " + audioSourceMode.getDisplayName() + "\n" +
                        "🎚️ الصوت: " + audioConfig.getQuality().label + "\n" +
                        "🎞️ الفيديو: " + videoConfig.getFrameRate().label + " | " + videoConfig.getQuality().label
                )
                .setPositiveButton("📤 مشاركة", (d, w) -> {
                    if (captureEngine != null) {
                        // Share via intent
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("video/mp4");
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_video)));
                    }
                })
                .setNegativeButton("تم", null)
                .show();
    }

    // ---- Permissions System (unchanged) ----
    private boolean checkAllPermissionsAndUpdateUI() {
        permissionsReady = false;
        String[] missing = getMissingPermissions();
        if (permissionDot != null) {
            permissionDot.setBackgroundResource(missing.length == 0 ? R.drawable.permission_dot_green : R.drawable.permission_dot_amber);
        }
        if (missing.length == 0) {
            permissionsReady = true;
            if (permissionStatusBar != null) permissionStatusBar.setVisibility(View.GONE);
            if (mButtonToggle != null) {
                mButtonToggle.setAlpha(1f);
                mButtonToggle.setEnabled(true);
            }
            return true;
        }
        if (permissionStatusBar != null) {
            permissionStatusBar.setVisibility(View.VISIBLE);
            StringBuilder msg = new StringBuilder("⚠️ ");
            for (int i = 0; i < missing.length; i++) {
                msg.append(getPermissionDisplayName(missing[i]));
                if (i < missing.length - 1) msg.append("، ");
            }
            if (permissionStatusText != null) permissionStatusText.setText(msg.toString());
        }
        if (mButtonToggle != null) {
            mButtonToggle.setAlpha(1f);
            mButtonToggle.setEnabled(true);
        }
        return false;
    }

    private String[] getMissingPermissions() {
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.POST_NOTIFICATIONS);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.READ_MEDIA_IMAGES);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this))
            missing.add(Manifest.permission.SYSTEM_ALERT_WINDOW);
        return missing.toArray(new String[0]);
    }

    private String getPermissionDisplayName(String permission) {
        switch (permission) {
            case Manifest.permission.POST_NOTIFICATIONS: return "الإشعارات";
            case Manifest.permission.READ_MEDIA_IMAGES: return "قراءة الصور";
            case Manifest.permission.READ_MEDIA_VIDEO: return "قراءة الفيديو";
            case Manifest.permission.WRITE_EXTERNAL_STORAGE: return "التخزين";
            case Manifest.permission.RECORD_AUDIO: return "الميكروفون";
            case Manifest.permission.SYSTEM_ALERT_WINDOW: return "النوافذ العائمة";
            default: return permission;
        }
    }

    private void requestAllPermissions() {
        if (isCheckingPermissions) return;
        isCheckingPermissions = true;
        String[] missing = getMissingPermissions();
        if (missing.length == 0) { isCheckingPermissions = false; checkAllPermissionsAndUpdateUI(); return; }
        requestPermissionWithExplanation(missing[0], 0);
    }

    private void requestPermissionWithExplanation(String permission, int index) {
        if (permission.equals(Manifest.permission.SYSTEM_ALERT_WINDOW)) {
            showOverlayPermissionDialog(); return;
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            showPermissionExplanationDialog(permission, () -> { requestPermissionLauncher.launch(permission); isCheckingPermissions = false; });
        } else { requestPermissionLauncher.launch(permission); isCheckingPermissions = false; }
    }

    private void showPermissionExplanationDialog(String permission, Runnable onConfirm) {
        String title, message; int icon;
        switch (permission) {
            case Manifest.permission.POST_NOTIFICATIONS:
                title = "إذن الإشعارات"; message = "نحتاج لإذن الإشعارات لعرض إشعار التسجيل."; icon = android.R.drawable.ic_dialog_info; break;
            case Manifest.permission.READ_MEDIA_IMAGES:
                title = "إذن قراءة الصور"; message = "نحتاج لحفظ لقطات الشاشة في المعرض."; icon = android.R.drawable.ic_menu_camera; break;
            case Manifest.permission.READ_MEDIA_VIDEO:
                title = "إذن قراءة الفيديو"; message = "نحتاج لحفظ تسجيلات الفيديو."; icon = android.R.drawable.ic_menu_gallery; break;
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                title = "إذن التخزين"; message = "نحتاج للوصول للتخزين."; icon = android.R.drawable.ic_menu_save; break;
            case Manifest.permission.RECORD_AUDIO:
                title = "إذن الميكروفون"; message = "نحتاج لتسجيل الصوت مع الفيديو."; icon = android.R.drawable.ic_btn_speak_now; break;
            default:
                title = "إذن مطلوب"; message = "هذا الإذن ضروري."; icon = android.R.drawable.ic_dialog_alert;
        }
        new MaterialAlertDialogBuilder(this).setIcon(icon).setTitle(title).setMessage(message).setCancelable(false)
                .setPositiveButton("✅ السماح", (d, w) -> { if (onConfirm != null) onConfirm.run(); })
                .setNegativeButton("🚫 لاحقاً", (d, w) -> { isCheckingPermissions = false; checkAllPermissionsAndUpdateUI(); }).show();
    }

    private void showOverlayPermissionDialog() {
        new MaterialAlertDialogBuilder(this).setIcon(android.R.drawable.ic_dialog_info)
                .setTitle("إذن النوافذ العائمة")
                .setMessage("نحتاج لعرض لوحة التحكم العائمة أثناء التسجيل.\n\nسيتم تحويلك للإعدادات.")
                .setCancelable(false)
                .setPositiveButton("⚙️ فتح الإعدادات", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    overlaySettingsLauncher.launch(intent); isCheckingPermissions = false;
                })
                .setNegativeButton("🚫 لاحقاً", (d, w) -> { isCheckingPermissions = false; checkAllPermissionsAndUpdateUI(); }).show();
    }

    private void onPermissionStatusBarClick() {
        String[] missing = getMissingPermissions();
        if (missing.length > 0) requestAllPermissions();
        else checkAllPermissionsAndUpdateUI();
    }

    // ---- Settings Choice Dialog ----
    private void showSettingsChoiceDialog() {
        new MaterialAlertDialogBuilder(this)
                .setIcon(android.R.drawable.ic_menu_manage)
                .setTitle("⚙️ الإعدادات")
                .setItems(new String[]{
                        "🎤 إعدادات الصوت",
                        "🎬 إعدادات الفيديو",
                        "🛑 إيقاف تلقائي: " + getAutoPauseLabel(),
                        "📊 الإحصائيات الحالية"
                }, (dialog, which) -> {
                    if (which == 0) showAudioSettingsDialog();
                    else if (which == 1) showVideoSettingsDialog();
                    else if (which == 2) cycleAutoPauseMode();
                    else showSettingsSummary();
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private String getAutoPauseLabel() {
        return autoPauseMode.label;
    }

    private void cycleAutoPauseMode() {
        ScreenCaptureEngine.AutoPauseMode[] modes = ScreenCaptureEngine.AutoPauseMode.values();
        autoPauseMode = modes[(autoPauseMode.ordinal() + 1) % modes.length];
        settingsPrefs.setAutoPauseMode(autoPauseMode.ordinal());
        if (captureEngine != null) captureEngine.setAutoPauseMode(autoPauseMode);
        String msg = autoPauseMode.enabled
                ? "🛑 إيقاف تلقائي: " + autoPauseMode.label
                : "✅ تم إيقاف الإيقاف التلقائي";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        // Update the summary if showing
    }

    private void showSettingsSummary() {
        new MaterialAlertDialogBuilder(this)
                .setIcon(android.R.drawable.ic_menu_info_details)
                .setTitle("📊 ملخص الإعدادات")
                .setMessage(
                        "🎬 وضع الالتقاط: " + currentMode.name() + "\n" +
                        "📺 الدقة: " + displayWidth + "×" + displayHeight + "\n" +
                        "🎞️ الإطارات: " + videoConfig.getFrameRate().label + "\n" +
                        "💎 جودة الفيديو: " + videoConfig.getQuality().label + "\n" +
                        "🔑 I-Frame: " + videoConfig.getIFrameInterval().label + "\n" +
                        "🛡️ البروفايل: " + videoConfig.getCodecProfile().label + "\n" +
                        "🛑 إيقاف تلقائي (حركة): " + autoPauseMode.label + "\n" +
                        "🔊 الصوت: " + audioSourceMode.getDisplayName() + "\n" +
                        "🎚️ العينة: " + audioConfig.getSampleRate().label + "\n" +
                        "🎧 جودة AAC: " + audioConfig.getQuality().label + "\n" +
                        "🔇 الضوضاء: " + audioConfig.getNoiseSuppression().label + "\n" +
                        "⏱️ تأخير: " + (delaySeconds > 0 ? delaySeconds + "ث" : "بدون") + "\n" +
                        "⏰ إيقاف زمني: " + (maxDurationSeconds > 0 ? (maxDurationSeconds / 60) + "د" : "بدون")
                )
                .setPositiveButton("تم", null)
                .show();
    }

    // ---- Video Settings Dialog ----
    private void showVideoSettingsDialog() {
        VideoConfig.FrameRate currentFrameRate = videoConfig.getFrameRate();
        VideoConfig.VideoQuality currentQuality = videoConfig.getQuality();
        VideoConfig.IFrameInterval currentIFrame = videoConfig.getIFrameInterval();
        VideoConfig.CodecProfile currentCodec = videoConfig.getCodecProfile();

        final String[] frameRates = new String[VideoConfig.FrameRate.values().length];
        final String[] qualities = new String[VideoConfig.VideoQuality.values().length];
        final String[] iFrames = new String[VideoConfig.IFrameInterval.values().length];
        final String[] codecs = new String[VideoConfig.CodecProfile.values().length];
        int selFr = 0, selQ = 0, selIF = 0, selCp = 0;

        for (int i = 0; i < frameRates.length; i++) {
            frameRates[i] = VideoConfig.FrameRate.values()[i].label;
            if (VideoConfig.FrameRate.values()[i] == currentFrameRate) selFr = i;
        }
        for (int i = 0; i < qualities.length; i++) {
            qualities[i] = VideoConfig.VideoQuality.values()[i].label;
            if (VideoConfig.VideoQuality.values()[i] == currentQuality) selQ = i;
        }
        for (int i = 0; i < iFrames.length; i++) {
            iFrames[i] = VideoConfig.IFrameInterval.values()[i].label;
            if (VideoConfig.IFrameInterval.values()[i] == currentIFrame) selIF = i;
        }
        for (int i = 0; i < codecs.length; i++) {
            codecs[i] = VideoConfig.CodecProfile.values()[i].label;
            if (VideoConfig.CodecProfile.values()[i] == currentCodec) selCp = i;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_video_settings, null);
        androidx.appcompat.widget.AppCompatTextView tvFrameRate = dialogView.findViewById(R.id.tvFrameRate);
        androidx.appcompat.widget.AppCompatTextView tvVideoQuality = dialogView.findViewById(R.id.tvVideoQuality);
        androidx.appcompat.widget.AppCompatTextView tvIFrame = dialogView.findViewById(R.id.tvIFrame);
        androidx.appcompat.widget.AppCompatTextView tvCodec = dialogView.findViewById(R.id.tvCodec);

        if (tvFrameRate == null) { showSimpleVideoSettingsDialog(); return; }

        final int[] fSelFr = {selFr}, fSelQ = {selQ}, fSelIF = {selIF}, fSelCp = {selCp};
        tvFrameRate.setText(frameRates[fSelFr[0]]);
        tvVideoQuality.setText(qualities[fSelQ[0]]);
        tvIFrame.setText(iFrames[fSelIF[0]]);
        tvCodec.setText(codecs[fSelCp[0]]);

        dialogView.findViewById(R.id.btnFrameRatePrev).setOnClickListener(v -> { fSelFr[0] = (fSelFr[0] - 1 + frameRates.length) % frameRates.length; tvFrameRate.setText(frameRates[fSelFr[0]]); });
        dialogView.findViewById(R.id.btnFrameRateNext).setOnClickListener(v -> { fSelFr[0] = (fSelFr[0] + 1) % frameRates.length; tvFrameRate.setText(frameRates[fSelFr[0]]); });
        dialogView.findViewById(R.id.btnQualityPrev).setOnClickListener(v -> { fSelQ[0] = (fSelQ[0] - 1 + qualities.length) % qualities.length; tvVideoQuality.setText(qualities[fSelQ[0]]); });
        dialogView.findViewById(R.id.btnVideoQualityNext).setOnClickListener(v -> { fSelQ[0] = (fSelQ[0] + 1) % qualities.length; tvVideoQuality.setText(qualities[fSelQ[0]]); });
        dialogView.findViewById(R.id.btnIFramePrev).setOnClickListener(v -> { fSelIF[0] = (fSelIF[0] - 1 + iFrames.length) % iFrames.length; tvIFrame.setText(iFrames[fSelIF[0]]); });
        dialogView.findViewById(R.id.btnIFrameNext).setOnClickListener(v -> { fSelIF[0] = (fSelIF[0] + 1) % iFrames.length; tvIFrame.setText(iFrames[fSelIF[0]]); });
        dialogView.findViewById(R.id.btnCodecPrev).setOnClickListener(v -> { fSelCp[0] = (fSelCp[0] - 1 + codecs.length) % codecs.length; tvCodec.setText(codecs[fSelCp[0]]); });
        dialogView.findViewById(R.id.btnCodecNext).setOnClickListener(v -> { fSelCp[0] = (fSelCp[0] + 1) % codecs.length; tvCodec.setText(codecs[fSelCp[0]]); });

        new MaterialAlertDialogBuilder(this).setIcon(android.R.drawable.ic_menu_manage).setTitle("🎬 إعدادات الفيديو المتقدمة").setView(dialogView)
                .setPositiveButton("✅ حفظ", (d, w) -> {
                    videoConfig = new VideoConfig(
                            VideoConfig.FrameRate.values()[fSelFr[0]],
                            VideoConfig.VideoQuality.values()[fSelQ[0]],
                            VideoConfig.IFrameInterval.values()[fSelIF[0]],
                            VideoConfig.CodecProfile.values()[fSelCp[0]]);
                    settingsPrefs.saveVideoConfig(videoConfig);
                    Toast.makeText(this, "✅ تم حفظ إعدادات الفيديو", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("🚫 إلغاء", null).show();
    }

    private void showSimpleVideoSettingsDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("🎬 إعدادات الفيديو")
                .setItems(new String[]{
                        "🎞️ الإطارات: " + videoConfig.getFrameRate().label,
                        "💎 الجودة: " + videoConfig.getQuality().label,
                        "🔑 I-Frame: " + videoConfig.getIFrameInterval().label,
                        "🛡️ البروفايل: " + videoConfig.getCodecProfile().label
                }, (dialog, which) -> {
                    if (which == 0) showFrameRatePicker();
                    else if (which == 1) showVideoQualityPicker();
                    else if (which == 2) showIFramePicker();
                    else showCodecPicker();
                }).setPositiveButton("تم", null).show();
    }

    private void showFrameRatePicker() {
        VideoConfig.FrameRate[] values = VideoConfig.FrameRate.values();
        String[] labels = new String[values.length];
        int selected = 0;
        for (int i = 0; i < values.length; i++) { labels[i] = values[i].label; if (values[i] == videoConfig.getFrameRate()) selected = i; }
        new MaterialAlertDialogBuilder(this).setTitle("🎞️ معدل الإطارات")
                .setSingleChoiceItems(labels, selected, (d, w) -> { videoConfig.setFrameRate(values[w]); settingsPrefs.saveVideoConfig(videoConfig); d.dismiss(); })
                .setNegativeButton("إلغاء", null).show();
    }

    private void showVideoQualityPicker() {
        VideoConfig.VideoQuality[] values = VideoConfig.VideoQuality.values();
        String[] labels = new String[values.length];
        int selected = 0;
        for (int i = 0; i < values.length; i++) { labels[i] = values[i].label; if (values[i] == videoConfig.getQuality()) selected = i; }
        new MaterialAlertDialogBuilder(this).setTitle("💎 جودة الفيديو (H.264)")
                .setSingleChoiceItems(labels, selected, (d, w) -> { videoConfig.setQuality(values[w]); settingsPrefs.saveVideoConfig(videoConfig); d.dismiss(); })
                .setNegativeButton("إلغاء", null).show();
    }

    private void showIFramePicker() {
        VideoConfig.IFrameInterval[] values = VideoConfig.IFrameInterval.values();
        String[] labels = new String[values.length];
        int selected = 0;
        for (int i = 0; i < values.length; i++) { labels[i] = values[i].label; if (values[i] == videoConfig.getIFrameInterval()) selected = i; }
        new MaterialAlertDialogBuilder(this).setTitle("🔑 الإطارات الرئيسية")
                .setSingleChoiceItems(labels, selected, (d, w) -> { videoConfig.setIFrameInterval(values[w]); settingsPrefs.saveVideoConfig(videoConfig); d.dismiss(); })
                .setNegativeButton("إلغاء", null).show();
    }

    private void showCodecPicker() {
        VideoConfig.CodecProfile[] values = VideoConfig.CodecProfile.values();
        String[] labels = new String[values.length];
        int selected = 0;
        for (int i = 0; i < values.length; i++) { labels[i] = values[i].label; if (values[i] == videoConfig.getCodecProfile()) selected = i; }
        new MaterialAlertDialogBuilder(this).setTitle("🛡️ بروفايل H.264")
                .setSingleChoiceItems(labels, selected, (d, w) -> { videoConfig.setCodecProfile(values[w]); settingsPrefs.saveVideoConfig(videoConfig); d.dismiss(); })
                .setNegativeButton("إلغاء", null).show();
    }

    // ---- Audio Settings Dialog ----
    private void showAudioSettingsDialog() {
        AudioConfig.SampleRate currentSampleRate = audioConfig.getSampleRate();
        AudioConfig.AudioQuality currentQuality = audioConfig.getQuality();
        AudioConfig.NoiseSuppression currentNoise = audioConfig.getNoiseSuppression();

        final String[] sampleRates = new String[AudioConfig.SampleRate.values().length];
        final String[] qualities = new String[AudioConfig.AudioQuality.values().length];
        final String[] noises = new String[AudioConfig.NoiseSuppression.values().length];
        int selRate = 0, selQuality = 0, selNoise = 0;

        for (int i = 0; i < sampleRates.length; i++) {
            sampleRates[i] = AudioConfig.SampleRate.values()[i].label;
            if (AudioConfig.SampleRate.values()[i] == currentSampleRate) selRate = i;
        }
        for (int i = 0; i < qualities.length; i++) {
            qualities[i] = AudioConfig.AudioQuality.values()[i].label;
            if (AudioConfig.AudioQuality.values()[i] == currentQuality) selQuality = i;
        }
        for (int i = 0; i < noises.length; i++) {
            noises[i] = AudioConfig.NoiseSuppression.values()[i].label;
            if (AudioConfig.NoiseSuppression.values()[i] == currentNoise) selNoise = i;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_audio_settings, null);
        androidx.appcompat.widget.AppCompatTextView tvSampleRate = dialogView.findViewById(R.id.tvSampleRate);
        androidx.appcompat.widget.AppCompatTextView tvQuality = dialogView.findViewById(R.id.tvQuality);
        androidx.appcompat.widget.AppCompatTextView tvNoise = dialogView.findViewById(R.id.tvNoise);

        if (tvSampleRate == null) { showSimpleAudioSettingsDialog(); return; }

        final int[] fSelRate = {selRate}, fSelQuality = {selQuality}, fSelNoise = {selNoise};
        tvSampleRate.setText(sampleRates[fSelRate[0]]);
        tvQuality.setText(qualities[fSelQuality[0]]);
        tvNoise.setText(noises[fSelNoise[0]]);

        dialogView.findViewById(R.id.btnRatePrev).setOnClickListener(v -> { fSelRate[0] = (fSelRate[0] - 1 + sampleRates.length) % sampleRates.length; tvSampleRate.setText(sampleRates[fSelRate[0]]); });
        dialogView.findViewById(R.id.btnRateNext).setOnClickListener(v -> { fSelRate[0] = (fSelRate[0] + 1) % sampleRates.length; tvSampleRate.setText(sampleRates[fSelRate[0]]); });
        dialogView.findViewById(R.id.btnQualityPrev).setOnClickListener(v -> { fSelQuality[0] = (fSelQuality[0] - 1 + qualities.length) % qualities.length; tvQuality.setText(qualities[fSelQuality[0]]); });
        dialogView.findViewById(R.id.btnQualityNext).setOnClickListener(v -> { fSelQuality[0] = (fSelQuality[0] + 1) % qualities.length; tvQuality.setText(qualities[fSelQuality[0]]); });
        dialogView.findViewById(R.id.btnNoisePrev).setOnClickListener(v -> { fSelNoise[0] = (fSelNoise[0] - 1 + noises.length) % noises.length; tvNoise.setText(noises[fSelNoise[0]]); });
        dialogView.findViewById(R.id.btnNoiseNext).setOnClickListener(v -> { fSelNoise[0] = (fSelNoise[0] + 1) % noises.length; tvNoise.setText(noises[fSelNoise[0]]); });

        new MaterialAlertDialogBuilder(this).setIcon(android.R.drawable.ic_menu_manage).setTitle("⚙️ إعدادات الصوت المتقدمة").setView(dialogView)
                .setPositiveButton("✅ حفظ", (d, w) -> {
                    audioConfig = new AudioConfig(
                            AudioConfig.SampleRate.values()[fSelRate[0]],
                            AudioConfig.AudioQuality.values()[fSelQuality[0]],
                            AudioConfig.NoiseSuppression.values()[fSelNoise[0]]);
                    settingsPrefs.saveAudioConfig(audioConfig);
                    Toast.makeText(this, "✅ تم حفظ الإعدادات", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("🚫 إلغاء", null).show();
    }

    private void showSimpleAudioSettingsDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("⚙️ إعدادات الصوت")
                .setItems(new String[]{"معدل العينة: " + audioConfig.getSampleRate().label,
                        "جودة AAC: " + audioConfig.getQuality().label,
                        "كتم الضوضاء: " + audioConfig.getNoiseSuppression().label
                }, (dialog, which) -> {
                    if (which == 0) showSampleRatePicker();
                    else if (which == 1) showQualityPicker();
                    else showNoisePicker();
                }).setPositiveButton("تم", null).show();
    }

    private void showSampleRatePicker() {
        AudioConfig.SampleRate[] values = AudioConfig.SampleRate.values();
        String[] labels = new String[values.length];
        int selected = 0;
        for (int i = 0; i < values.length; i++) { labels[i] = values[i].label; if (values[i] == audioConfig.getSampleRate()) selected = i; }
        new MaterialAlertDialogBuilder(this).setTitle("🎚️ معدل العينة")
                .setSingleChoiceItems(labels, selected, (d, w) -> { audioConfig.setSampleRate(values[w]); settingsPrefs.saveAudioConfig(audioConfig); d.dismiss(); })
                .setNegativeButton("إلغاء", null).show();
    }

    private void showQualityPicker() {
        AudioConfig.AudioQuality[] values = AudioConfig.AudioQuality.values();
        String[] labels = new String[values.length];
        int selected = 0;
        for (int i = 0; i < values.length; i++) { labels[i] = values[i].label; if (values[i] == audioConfig.getQuality()) selected = i; }
        new MaterialAlertDialogBuilder(this).setTitle("🎧 جودة AAC")
                .setSingleChoiceItems(labels, selected, (d, w) -> { audioConfig.setQuality(values[w]); settingsPrefs.saveAudioConfig(audioConfig); d.dismiss(); })
                .setNegativeButton("إلغاء", null).show();
    }

    private void showNoisePicker() {
        AudioConfig.NoiseSuppression[] values = AudioConfig.NoiseSuppression.values();
        String[] labels = new String[values.length];
        int selected = 0;
        for (int i = 0; i < values.length; i++) { labels[i] = values[i].label; if (values[i] == audioConfig.getNoiseSuppression()) selected = i; }
        new MaterialAlertDialogBuilder(this).setTitle("🔇 كتم الضوضاء")
                .setSingleChoiceItems(labels, selected, (d, w) -> { audioConfig.setNoiseSuppression(values[w]); settingsPrefs.saveAudioConfig(audioConfig); d.dismiss(); })
                .setNegativeButton("إلغاء", null).show();
    }

    // ---- Audio Source ----
    private void updateAudioSourceButton() {
        if (btnMic == null) return;
        switch (audioSourceMode) {
            case NONE: btnMic.setText("🔇"); btnMic.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x1A6B7280)); break;
            case INTERNAL: btnMic.setText("🔊"); btnMic.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x1A6366F1)); break;
            case EXTERNAL: btnMic.setText("🎤"); btnMic.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x1A22C55E)); break;
            case BOTH: btnMic.setText("🔊+🎤"); btnMic.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x1A8B5CF6)); break;
        }
        try {
            Toast.makeText(this, "مصدر الصوت: " + audioSourceMode.getDisplayName(), Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            // Ignore toast errors during startup
        }
    }

    // ---- Capture Info ----
    private void updateCaptureInfo() {
        if (captureInfoText == null || regionOverlay == null || currentResolution == null) return;
        RectF region = regionOverlay.getSelectedRegion();
        String modeText;
        switch (currentMode) { case SCREENSHOT: modeText = "لقطة"; break; case VIDEO: modeText = "فيديو"; break; case BOTH: modeText = "لقطة+فيديو"; break; default: modeText = ""; }
        captureInfoText.setText(String.format("📐 %s | %d×%d | %s",
                modeText, (int) region.width(), (int) region.height(), currentResolution.label));
    }

    private void animateButton(View v) {
        ScaleAnimation anim = new ScaleAnimation(1f, 0.9f, 1f, 0.9f, v.getWidth() / 2f, v.getHeight() / 2f);
        anim.setDuration(100); anim.setRepeatCount(1); anim.setRepeatMode(Animation.REVERSE);
        v.startAnimation(anim);
    }

    private void showSavedNotification(Uri uri, String mimeType) {
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(uri, mimeType);
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (openIntent.resolveActivity(getPackageManager()) != null)
            startActivity(Intent.createChooser(openIntent, "فتح بـ"));
    }

    // ---- Floating Control ----
    private void showFloatingControl(boolean isPaused, long elapsedMs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent si = new Intent(this, FloatingControlService.class);
        si.setAction(FloatingControlService.ACTION_SHOW_FLOATING);
        si.putExtra(FloatingControlService.EXTRA_IS_PAUSED, isPaused);
        si.putExtra(FloatingControlService.EXTRA_ELAPSED, elapsedMs);
        ContextCompat.startForegroundService(this, si);
    }
    private void hideFloatingControl() {
        startService(new Intent(this, FloatingControlService.class).setAction(FloatingControlService.ACTION_HIDE_FLOATING));
    }
    private void updateFloatingState(boolean isPaused, long elapsedMs) {
        Intent si = new Intent(this, FloatingControlService.class);
        si.setAction(FloatingControlService.ACTION_SHOW_FLOATING);
        si.putExtra(FloatingControlService.EXTRA_IS_PAUSED, isPaused);
        si.putExtra(FloatingControlService.EXTRA_ELAPSED, elapsedMs);
        startService(si);
    }

    private void registerFloatingControlReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(FloatingControlService.ACTION_FLOATING_PAUSE);
        filter.addAction(FloatingControlService.ACTION_FLOATING_RESUME);
        filter.addAction(FloatingControlService.ACTION_FLOATING_STOP);
        LocalBroadcastManager.getInstance(this).registerReceiver(floatingControlReceiver, filter);
    }
    private void unregisterFloatingControlReceiver() {
        try { LocalBroadcastManager.getInstance(this).unregisterReceiver(floatingControlReceiver); } catch (Exception ignored) {}
    }

    private final BroadcastReceiver floatingControlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (FloatingControlService.ACTION_FLOATING_PAUSE.equals(action) && captureEngine != null && captureEngine.isCapturing())
                captureEngine.pauseVideoCapture();
            else if (FloatingControlService.ACTION_FLOATING_RESUME.equals(action) && captureEngine != null && captureEngine.isCapturing())
                captureEngine.resumeVideoCapture();
            else if (FloatingControlService.ACTION_FLOATING_STOP.equals(action))
                stopCapture();
        }
    };
}
