package com.jgeraldo.mediaprojectionsample;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
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
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
    private FrameLayout overlayContainer;
    private Button mButtonToggle;
    private MaterialButtonToggleGroup modeToggleGroup;
    private LinearLayout captureInfoPanel;
    private TextView captureInfoText;
    private MaterialCardView controlPanel;
    private ImageButton btnScreenshotQuick, btnSettings;
    private com.google.android.material.button.MaterialButton btnMic;
    private com.google.android.material.button.MaterialButton btnSettings;
    private ScreenCaptureEngine.AudioSource audioSourceMode = ScreenCaptureEngine.AudioSource.EXTERNAL;
    private AudioConfig audioConfig = new AudioConfig();

    private Surface mSurface;
    private Handler mHandler;
    private ActivityResultLauncher<Intent> startMediaProjectionActivity;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> overlaySettingsLauncher;

    private ScreenCaptureEngine captureEngine;
    private CaptureMode currentMode = CaptureMode.BOTH;

    // For tracking display dimensions
    private int displayWidth = 720;
    private int displayHeight = 1280;

    // Permission tracking
    private static final int PERMISSION_REQUEST_NOTIFICATIONS = 1001;
    private boolean isCheckingPermissions = false;
    private View permissionStatusBar;
    private TextView permissionStatusText;
    private TextView permissionDot;
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

                if (mMediaProjection != null) {
                    startScreenCapture();
                }
            }
        }
    }

    private final MyBroadcastReceiver receiver = new MyBroadcastReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler(Looper.getMainLooper());

        // Initialize views
        mSurfaceView = findViewById(R.id.surface);
        regionOverlay = findViewById(R.id.regionOverlay);
        overlayContainer = findViewById(R.id.overlayContainer);
        mButtonToggle = findViewById(R.id.button);
        modeToggleGroup = findViewById(R.id.modeToggle);
        captureInfoPanel = findViewById(R.id.captureInfoPanel);
        captureInfoText = findViewById(R.id.captureInfoText);
        controlPanel = findViewById(R.id.controlPanel);
        btnScreenshotQuick = findViewById(R.id.btnScreenshotQuick);
        btnMic = findViewById(R.id.btnMic);
        btnSettings = findViewById(R.id.btnSettings);
        permissionStatusBar = findViewById(R.id.permissionStatusBar);
        permissionStatusText = findViewById(R.id.permissionStatusText);
        permissionDot = findViewById(R.id.permissionDot);

        // Set click listener for permission status bar
        if (permissionStatusBar != null) {
            permissionStatusBar.setOnClickListener(v -> onPermissionStatusBarClick());
        }

        mSurface = mSurfaceView.getHolder().getSurface();

        // Setup permission launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    checkAllPermissionsAndUpdateUI();
                    if (isGranted) {
                        Log.d(TAG, "Permission granted");
                    }
                }
        );

        // Setup overlay settings launcher
        overlaySettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    checkAllPermissionsAndUpdateUI();
                }
        );

        // Setup region overlay
        regionOverlay.setOnRegionChangedListener(region -> {
            if (captureEngine != null) {
                captureEngine.setCaptureRegion(
                        regionOverlay.getNormalizedRegion());
            }
            updateCaptureInfo();
        });

        // Setup mode toggle
        modeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.modeScreenshot) {
                currentMode = CaptureMode.SCREENSHOT;
            } else if (checkedId == R.id.modeVideo) {
                currentMode = CaptureMode.VIDEO;
            } else if (checkedId == R.id.modeBoth) {
                currentMode = CaptureMode.BOTH;
            }
            updateCaptureInfo();
        });
        // Default: select "BOTH" mode
        modeToggleGroup.check(R.id.modeBoth);

        // Main toggle button
        mButtonToggle.setOnClickListener(view -> {
            if (!isRecording) {
                if (!permissionsReady) {
                    checkAllPermissionsAndUpdateUI();
                    return;
                }
                requestScreenCapturePermission();
            } else {
                stopCapture();
            }
        });

        // Audio source button (cycles through modes)
        if (btnMic != null) {
            btnMic.setOnClickListener(v -> {
                ScreenCaptureEngine.AudioSource[] sources =
                        ScreenCaptureEngine.AudioSource.values();
                int nextIndex = (audioSourceMode.ordinal() + 1) % sources.length;
                audioSourceMode = sources[nextIndex];
                updateAudioSourceButton();
            });
            updateAudioSourceButton();
        }

        // Audio settings button (opens advanced settings dialog)
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showAudioSettingsDialog());
        }

        // Quick screenshot button
        btnScreenshotQuick.setOnClickListener(v -> {
            if (captureEngine != null && mMediaProjection != null && !isRecording) {
                captureEngine.captureScreenshot(displayWidth, displayHeight);
                animateButton(v);
            } else if (isRecording) {
                takeScreenshotDuringRecording();
            } else {
                Toast.makeText(this, getString(R.string.start_first), Toast.LENGTH_SHORT).show();
            }
        });

        // Animate controls in
        controlPanel.setAlpha(0f);
        controlPanel.setTranslationY(100f);
        controlPanel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(200)
                .start();

        updateCaptureInfo();

        // Check permissions on start (delayed for UI readiness)
        mHandler.postDelayed(() -> checkAllPermissionsAndUpdateUI(), 500);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mediaProjectionManager = (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);

        startMediaProjectionActivity =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            int resultCode = result.getResultCode();

                            if (resultCode == Activity.RESULT_OK) {
                                Intent data = result.getData();

                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    MediaProjectionManager projectionManager =
                                            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                                    mMediaProjection = projectionManager.getMediaProjection(resultCode, data);

                                    if (mMediaProjection != null) {
                                        startScreenCapture();
                                    }
                                } else {
                                    try {
                                        Intent serviceIntent = new Intent(this, MyMediaProjectionService.class);
                                        serviceIntent.putExtra("resultCode", resultCode);
                                        serviceIntent.putExtra("data", data);
                                        ContextCompat.startForegroundService(this, serviceIntent);
                                    } catch (RuntimeException e) {
                                        Log.w(TAG, "Error: " + e.getMessage());
                                    }
                                }
                            } else {
                                Toast.makeText(this, getString(R.string.permission_denied),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });

        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_MEDIA_PROJECTION_STARTED);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
            isReceiverRegistered = true;
        }

        // Register floating control receiver
        registerFloatingControlReceiver();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (isReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            isReceiverRegistered = false;
        }

        if (!isRecording && mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }

        // Unregister floating control receiver
        unregisterFloatingControlReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (captureEngine != null) {
            captureEngine.release();
            captureEngine = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    private void requestScreenCapturePermission() {
        if (startMediaProjectionActivity != null) {
            mediaProjectionManager = (MediaProjectionManager)
                    getSystemService(MEDIA_PROJECTION_SERVICE);
            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            startMediaProjectionActivity.launch(captureIntent);
        }
    }

    private void startScreenCapture() {
        if (mMediaProjection == null) return;

        // Initialize capture engine
        captureEngine = new ScreenCaptureEngine(mMediaProjection, getContentResolver());
        captureEngine.setCaptureRegion(regionOverlay.getNormalizedRegion());
        captureEngine.setAudioSource(audioSourceMode);
        captureEngine.setAudioConfig(audioConfig);
        captureEngine.setOnCaptureListener(new ScreenCaptureEngine.OnCaptureListener() {
            @Override
            public void onScreenshotSaved(Uri uri, String message) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    showSavedNotification(uri, "image/*");
                });
            }

            @Override
            public void onVideoSaved(Uri uri, String message) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    showSavedNotification(uri, "video/*");
                    isRecording = false;
                    mButtonToggle.setText(R.string.button_start);
                    regionOverlay.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onCaptureError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                    if (isRecording) {
                        isRecording = false;
                        mButtonToggle.setText(R.string.button_start);
                        regionOverlay.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onRecordingStarted() {
                runOnUiThread(() -> {
                    isRecording = true;
                    mButtonToggle.setText(R.string.button_stop);
                    modeToggleGroup.setEnabled(false);
                    regionOverlay.setVisibility(View.GONE);
                    btnScreenshotQuick.setVisibility(View.VISIBLE);
                    animateButton(mButtonToggle);
                    // Show floating control
                    showFloatingControl(false, 0);
                });
            }

            @Override
            public void onRecordingStopped() {
                runOnUiThread(() -> {
                    modeToggleGroup.setEnabled(true);
                    btnScreenshotQuick.setVisibility(View.GONE);
                    // Hide floating control
                    hideFloatingControl();
                });
            }

            @Override
            public void onRecordingPaused() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "⏸️ تم الإيقاف المؤقت", Toast.LENGTH_SHORT).show();
                    mButtonToggle.setText("⏸️pause");
                });
            }

            @Override
            public void onRecordingResumed() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "▶️ تم الاستئناف", Toast.LENGTH_SHORT).show();
                    mButtonToggle.setText(R.string.button_stop);
                });
            }

            @Override
            public void onRecordingStateUpdated(boolean paused, long elapsedMs) {
                runOnUiThread(() -> {
                    updateFloatingState(paused, elapsedMs);
                });
            }
        });

        // Display the full screen in SurfaceView
        MediaProjection.Callback callback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
            }
        };
        mMediaProjection.registerCallback(callback, null);

        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                getString(R.string.screen_capture_title),
                displayWidth, displayHeight,
                getResources().getDisplayMetrics().densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mSurface,
                null,
                mHandler);

        // Start capture based on mode
        startCaptureByMode();

        mButtonToggle.setText(R.string.button_stop);
    }

    private void startCaptureByMode() {
        switch (currentMode) {
            case SCREENSHOT:
                captureEngine.captureScreenshot(displayWidth, displayHeight);
                break;
            case VIDEO:
                captureEngine.startVideoCapture(displayWidth, displayHeight);
                break;
            case BOTH:
                // Take screenshot first, then start video recording
                captureEngine.setCaptureRegion(regionOverlay.getNormalizedRegion());
                captureEngine.captureScreenshot(displayWidth, displayHeight);

                // Start video after a short delay
                mHandler.postDelayed(() -> {
                    if (captureEngine != null) {
                        captureEngine.setCaptureRegion(
                                regionOverlay.getNormalizedRegion());
                        captureEngine.startVideoCapture(displayWidth, displayHeight);
                    }
                }, 300);
                break;
        }
    }

    private void stopCapture() {
        if (captureEngine != null && captureEngine.isCapturing()) {
            captureEngine.stopVideoCapture();
        } else {
            // Not recording but has virtual display running
            stopFullCapture();
        }
    }

    private void stopFullCapture() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        isRecording = false;
        mButtonToggle.setText(R.string.button_start);
        regionOverlay.setVisibility(View.VISIBLE);
        modeToggleGroup.setEnabled(true);
        hideFloatingControl();
    }

    private void takeScreenshotDuringRecording() {
        if (captureEngine != null) {
            captureEngine.setCaptureRegion(regionOverlay.getNormalizedRegion());
            // For simplicity, log that screenshot was taken
            Toast.makeText(this, "تم التقاط لقطة شاشة أثناء التسجيل", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Permission System ----------

    /**
     * Check all required permissions and update UI accordingly.
     * Returns true if all permissions are granted.
     */
    private boolean checkAllPermissionsAndUpdateUI() {
        permissionsReady = false;
        String[] missingPermissions = getMissingPermissions();

        // Update the permission dot
        if (permissionDot != null) {
            if (missingPermissions.length == 0) {
                permissionDot.setBackgroundResource(R.drawable.permission_dot_green);
            } else {
                permissionDot.setBackgroundResource(R.drawable.permission_dot_amber);
            }
        }

        if (missingPermissions.length == 0) {
            // All permissions granted
            permissionsReady = true;
            if (permissionStatusBar != null) {
                permissionStatusBar.setVisibility(View.GONE);
            }
            mButtonToggle.setAlpha(1f);
            mButtonToggle.setEnabled(true);
            return true;
        }

        // Show permission status
        if (permissionStatusBar != null) {
            permissionStatusBar.setVisibility(View.VISIBLE);
            StringBuilder msg = new StringBuilder("⚠️ ");
            for (int i = 0; i < missingPermissions.length; i++) {
                msg.append(getPermissionDisplayName(missingPermissions[i]));
                if (i < missingPermissions.length - 1) msg.append("، ");
            }
            permissionStatusText.setText(msg.toString());
        }

        // Enable the button (still clickable to trigger request)
        mButtonToggle.setAlpha(1f);
        mButtonToggle.setEnabled(true);

        return false;
    }

    /**
     * Get all missing permissions (not granted yet)
     */
    private String[] getMissingPermissions() {
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();

        // POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // READ_MEDIA_IMAGES (Android 13+) or READ_EXTERNAL_STORAGE (Android 12-)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            // Only needed for Android 10 and below (scoped storage from 11)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        // RECORD_AUDIO (for microphone recording)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECORD_AUDIO);
        }

        // SYSTEM_ALERT_WINDOW (for floating control)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                missing.add(Manifest.permission.SYSTEM_ALERT_WINDOW);
            }
        }

        return missing.toArray(new String[0]);
    }

    /**
     * Get user-friendly display name for a permission
     */
    private String getPermissionDisplayName(String permission) {
        switch (permission) {
            case Manifest.permission.POST_NOTIFICATIONS:
                return "الإشعارات";
            case Manifest.permission.READ_MEDIA_IMAGES:
                return "قراءة الصور";
            case Manifest.permission.READ_MEDIA_VIDEO:
                return "قراءة الفيديو";
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                return "التخزين";
            case Manifest.permission.RECORD_AUDIO:
                return "الميكروفون";
            case Manifest.permission.SYSTEM_ALERT_WINDOW:
                return "النوافذ العائمة";
            default:
                return permission;
        }
    }

    /**
     * Start requesting all missing permissions one by one
     */
    private void requestAllPermissions() {
        if (isCheckingPermissions) return;
        isCheckingPermissions = true;

        String[] missing = getMissingPermissions();
        if (missing.length == 0) {
            isCheckingPermissions = false;
            checkAllPermissionsAndUpdateUI();
            return;
        }

        // Show the first missing permission
        requestPermissionWithExplanation(missing[0], 0);
    }

    /**
     * Request a specific permission with explanation dialog
     */
    private void requestPermissionWithExplanation(String permission, int index) {
        String[] missing = getMissingPermissions();

        if (permission.equals(Manifest.permission.SYSTEM_ALERT_WINDOW)) {
            // SYSTEM_ALERT_WINDOW needs special intent, not normal request
            showOverlayPermissionDialog();
            return;
        }

        // Check if we should show explanation
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            // Show explanation dialog
            showPermissionExplanationDialog(permission, () -> {
                requestPermissionLauncher.launch(permission);
                isCheckingPermissions = false;
            });
        } else {
            // Request directly
            requestPermissionLauncher.launch(permission);
            isCheckingPermissions = false;
        }
    }

    /**
     * Show a Material dialog explaining why a permission is needed
     */
    private void showPermissionExplanationDialog(String permission, Runnable onConfirm) {
        String title, message;
        int icon;

        switch (permission) {
            case Manifest.permission.POST_NOTIFICATIONS:
                title = "إذن الإشعارات";
                message = "نحتاج إلى إذن الإشعارات حتى نتمكن من عرض إشعار التسجيل النشط والتحكم بالتسجيل من الخلفية.";
                icon = android.R.drawable.ic_dialog_info;
                break;
            case Manifest.permission.READ_MEDIA_IMAGES:
                title = "إذن قراءة الصور";
                message = "نحتاج إلى هذا الإذن لحفظ لقطات الشاشة في معرض الصور الخاص بك.";
                icon = android.R.drawable.ic_menu_camera;
                break;
            case Manifest.permission.READ_MEDIA_VIDEO:
                title = "إذن قراءة الفيديو";
                message = "نحتاج إلى هذا الإذن لحفظ تسجيلات الفيديو في معرض الفيديو.";
                icon = android.R.drawable.ic_menu_gallery;
                break;
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                title = "إذن التخزين";
                message = "نحتاج إلى الوصول للتخزين لحفظ لقطات الشاشة وتسجيلات الفيديو.";
                icon = android.R.drawable.ic_menu_save;
                break;
            case Manifest.permission.RECORD_AUDIO:
                title = "إذن الميكروفون";
                message = "نحتاج إلى إذن الميكروفون لتسجيل الصوت مع الفيديو.";
                icon = android.R.drawable.ic_btn_speak_now;
                break;
            default:
                title = "إذن مطلوب";
                message = "هذا الإذن ضروري لتشغيل التطبيق بشكل صحيح.";
                icon = android.R.drawable.ic_dialog_alert;
        }

        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dialog_Alert)
                .setIcon(icon)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("✅ السماح", (dialog, which) -> {
                    if (onConfirm != null) onConfirm.run();
                })
                .setNegativeButton("🚫 لاحقاً", (dialog, which) -> {
                    isCheckingPermissions = false;
                    checkAllPermissionsAndUpdateUI();
                })
                .show();
    }

    /**
     * Show dialog for SYSTEM_ALERT_WINDOW (overlay) permission
     */
    private void showOverlayPermissionDialog() {
        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dialog_Alert)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle("إذن النوافذ العائمة")
                .setMessage("نحتاج إلى إذن \"عرض فوق التطبيقات الأخرى\" حتى نتمكن من عرض لوحة التحكم العائمة أثناء التسجيل. \n\nسيتم تحويلك إلى الإعدادات لتفعيل هذا الإذن.")
                .setCancelable(false)
                .setPositiveButton("⚙️ فتح الإعدادات", (dialog, which) -> {
                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())
                    );
                    overlaySettingsLauncher.launch(intent);
                    isCheckingPermissions = false;
                })
                .setNegativeButton("🚫 لاحقاً", (dialog, which) -> {
                    isCheckingPermissions = false;
                    checkAllPermissionsAndUpdateUI();
                })
                .show();
    }

    /**
     * Called when user clicks the permission status bar
     */
    private void onPermissionStatusBarClick() {
        String[] missing = getMissingPermissions();
        if (missing.length > 0) {
            requestAllPermissions();
        } else {
            checkAllPermissionsAndUpdateUI();
        }
    }

    // ---------- Advanced Audio Settings Dialog ----------

    private void showAudioSettingsDialog() {
        AudioConfig.SampleRate currentSampleRate = audioConfig.getSampleRate();
        AudioConfig.AudioQuality currentQuality = audioConfig.getQuality();
        AudioConfig.NoiseSuppression currentNoise = audioConfig.getNoiseSuppression();

        // Arrays for the multi-selector
        final String[] sampleRates = new String[AudioConfig.SampleRate.values().length];
        final String[] qualities = new String[AudioConfig.AudioQuality.values().length];
        final String[] noises = new String[AudioConfig.NoiseSuppression.values().length];

        int selectedSampleRate = 0;
        int selectedQuality = 0;
        int selectedNoise = 0;

        for (int i = 0; i < sampleRates.length; i++) {
            sampleRates[i] = AudioConfig.SampleRate.values()[i].label;
            if (AudioConfig.SampleRate.values()[i] == currentSampleRate)
                selectedSampleRate = i;
        }
        for (int i = 0; i < qualities.length; i++) {
            qualities[i] = AudioConfig.AudioQuality.values()[i].label;
            if (AudioConfig.AudioQuality.values()[i] == currentQuality)
                selectedQuality = i;
        }
        for (int i = 0; i < noises.length; i++) {
            noises[i] = AudioConfig.NoiseSuppression.values()[i].label;
            if (AudioConfig.NoiseSuppression.values()[i] == currentNoise)
                selectedNoise = i;
        }

        // Inflate custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_audio_settings, null);

        androidx.appcompat.widget.AppCompatTextView tvSampleRate = dialogView.findViewById(R.id.tvSampleRate);
        androidx.appcompat.widget.AppCompatTextView tvQuality = dialogView.findViewById(R.id.tvQuality);
        androidx.appcompat.widget.AppCompatTextView tvNoise = dialogView.findViewById(R.id.tvNoise);
        View ratePrev = dialogView.findViewById(R.id.btnRatePrev);
        View rateNext = dialogView.findViewById(R.id.btnRateNext);
        View qualityPrev = dialogView.findViewById(R.id.btnQualityPrev);
        View qualityNext = dialogView.findViewById(R.id.btnQualityNext);
        View noisePrev = dialogView.findViewById(R.id.btnNoisePrev);
        View noiseNext = dialogView.findViewById(R.id.btnNoiseNext);

        // If layout not found, fallback to simple list dialog
        if (tvSampleRate == null) {
            showSimpleAudioSettingsDialog();
            return;
        }

        final int[] selRate = {selectedSampleRate};
        final int[] selQuality = {selectedQuality};
        final int[] selNoise = {selectedNoise};

        tvSampleRate.setText(sampleRates[selRate[0]]);
        tvQuality.setText(qualities[selQuality[0]]);
        tvNoise.setText(noises[selNoise[0]]);

        ratePrev.setOnClickListener(v -> {
            selRate[0] = (selRate[0] - 1 + sampleRates.length) % sampleRates.length;
            tvSampleRate.setText(sampleRates[selRate[0]]);
        });
        rateNext.setOnClickListener(v -> {
            selRate[0] = (selRate[0] + 1) % sampleRates.length;
            tvSampleRate.setText(sampleRates[selRate[0]]);
        });
        qualityPrev.setOnClickListener(v -> {
            selQuality[0] = (selQuality[0] - 1 + qualities.length) % qualities.length;
            tvQuality.setText(qualities[selQuality[0]]);
        });
        qualityNext.setOnClickListener(v -> {
            selQuality[0] = (selQuality[0] + 1) % qualities.length;
            tvQuality.setText(qualities[selQuality[0]]);
        });
        noisePrev.setOnClickListener(v -> {
            selNoise[0] = (selNoise[0] - 1 + noises.length) % noises.length;
            tvNoise.setText(noises[selNoise[0]]);
        });
        noiseNext.setOnClickListener(v -> {
            selNoise[0] = (selNoise[0] + 1) % noises.length;
            tvNoise.setText(noises[selNoise[0]]);
        });

        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dialog_Alert)
                .setIcon(android.R.drawable.ic_menu_manage)
                .setTitle("⚙️ إعدادات الصوت المتقدمة")
                .setView(dialogView)
                .setPositiveButton("✅ حفظ", (dialog, which) -> {
                    audioConfig = new AudioConfig(
                            AudioConfig.SampleRate.values()[selRate[0]],
                            AudioConfig.AudioQuality.values()[selQuality[0]],
                            AudioConfig.NoiseSuppression.values()[selNoise[0]]
                    );
                    Toast.makeText(this,
                            "✅ تم حفظ الإعدادات: " + audioConfig.toString(),
                            Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("🚫 إلغاء", null)
                .show();
    }

    private void showSimpleAudioSettingsDialog() {
        final String[] options = {
                "معدل العينة: " + audioConfig.getSampleRate().label,
                "جودة AAC: " + audioConfig.getQuality().label,
                "كتم الضوضاء: " + audioConfig.getNoiseSuppression().label
        };

        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dialog_Alert)
                .setTitle("⚙️ إعدادات الصوت")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Sample Rate
                            showSampleRatePicker();
                            break;
                        case 1: // Quality
                            showQualityPicker();
                            break;
                        case 2: // Noise Suppression
                            showNoisePicker();
                            break;
                    }
                })
                .setPositiveButton("تم", null)
                .show();
    }

    private void showSampleRatePicker() {
        final AudioConfig.SampleRate[] values = AudioConfig.SampleRate.values();
        String[] labels = new String[values.length];
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i].label;
            if (values[i] == audioConfig.getSampleRate()) selected = i;
        }

        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dialog_Alert)
                .setTitle("🎚️ معدل العينة")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    audioConfig.setSampleRate(values[which]);
                    dialog.dismiss();
                    Toast.makeText(this, "✅ " + values[which].label, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showQualityPicker() {
        final AudioConfig.AudioQuality[] values = AudioConfig.AudioQuality.values();
        String[] labels = new String[values.length];
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i].label;
            if (values[i] == audioConfig.getQuality()) selected = i;
        }

        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dialog_Alert)
                .setTitle("🎧 جودة AAC")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    audioConfig.setQuality(values[which]);
                    dialog.dismiss();
                    Toast.makeText(this, "✅ " + values[which].label, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showNoisePicker() {
        final AudioConfig.NoiseSuppression[] values = AudioConfig.NoiseSuppression.values();
        String[] labels = new String[values.length];
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i].label;
            if (values[i] == audioConfig.getNoiseSuppression()) selected = i;
        }

        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dialog_Alert)
                .setTitle("🔇 كتم الضوضاء")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    audioConfig.setNoiseSuppression(values[which]);
                    dialog.dismiss();
                    Toast.makeText(this, "✅ " + values[which].label, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // ---------- Audio Source Control ----------

    private void updateAudioSourceButton() {
        if (btnMic == null) return;

        switch (audioSourceMode) {
            case NONE:
                btnMic.setText("🔇");
                btnMic.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0x1A6B7280));
                btnMic.setIconTint(
                        android.content.res.ColorStateList.valueOf(0x806B7280));
                break;
            case INTERNAL:
                btnMic.setText("🔊");
                btnMic.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0x1A6366F1));
                btnMic.setIconTint(
                        android.content.res.ColorStateList.valueOf(0xFF818CF8));
                break;
            case EXTERNAL:
                btnMic.setText("🎤");
                btnMic.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0x1A22C55E));
                btnMic.setIconTint(
                        android.content.res.ColorStateList.valueOf(0xFF22C55E));
                break;
            case BOTH:
                btnMic.setText("🔊+🎤");
                btnMic.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0x1A8B5CF6));
                btnMic.setIconTint(
                        android.content.res.ColorStateList.valueOf(0xFFA78BFA));
                break;
        }

        Toast.makeText(this,
                "مصدر الصوت: " + audioSourceMode.getDisplayName(),
                Toast.LENGTH_SHORT).show();
    }

    // ---------- Capture Info ----------

    private void updateCaptureInfo() {
        RectF region = regionOverlay.getSelectedRegion();
        RectF norm = regionOverlay.getNormalizedRegion();

        String modeText;
        switch (currentMode) {
            case SCREENSHOT: modeText = "لقطة شاشة"; break;
            case VIDEO: modeText = "تسجيل فيديو"; break;
            case BOTH: modeText = "لقطة + فيديو"; break;
            default: modeText = ""; break;
        }

        String info = String.format("📐 %s | %d×%d بكسل | %d%% من الشاشة",
                modeText,
                (int) region.width(),
                (int) region.height(),
                (int) (norm.width() * 100));
        captureInfoText.setText(info);
    }

    private void animateButton(View v) {
        ScaleAnimation anim = new ScaleAnimation(
                1f, 0.9f, 1f, 0.9f,
                v.getWidth() / 2f, v.getHeight() / 2f);
        anim.setDuration(100);
        anim.setRepeatCount(1);
        anim.setRepeatMode(Animation.REVERSE);
        v.startAnimation(anim);
    }

    private void showSavedNotification(Uri uri, String mimeType) {
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(uri, mimeType);
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (openIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(Intent.createChooser(openIntent, "فتح بـ"));
        }
    }

    // ---------- Floating Control Integration ----------

    private void showFloatingControl(boolean isPaused, long elapsedMs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // Request overlay permission
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
                Toast.makeText(this, "الرجاء السماح بعرض النوافذ العائمة", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Intent serviceIntent = new Intent(this, FloatingControlService.class);
        serviceIntent.setAction(FloatingControlService.ACTION_SHOW_FLOATING);
        serviceIntent.putExtra(FloatingControlService.EXTRA_IS_PAUSED, isPaused);
        serviceIntent.putExtra(FloatingControlService.EXTRA_ELAPSED, elapsedMs);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void hideFloatingControl() {
        Intent intent = new Intent(this, FloatingControlService.class);
        intent.setAction(FloatingControlService.ACTION_HIDE_FLOATING);
        startService(intent);
    }

    private void updateFloatingState(boolean isPaused, long elapsedMs) {
        Intent intent = new Intent(this, FloatingControlService.class);
        intent.setAction(FloatingControlService.ACTION_SHOW_FLOATING);
        intent.putExtra(FloatingControlService.EXTRA_IS_PAUSED, isPaused);
        intent.putExtra(FloatingControlService.EXTRA_ELAPSED, elapsedMs);
        startService(intent);
    }

    /**
     * Register broadcast receiver for floating control actions (pause/resume/stop)
     */
    private void registerFloatingControlReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(FloatingControlService.ACTION_FLOATING_PAUSE);
        filter.addAction(FloatingControlService.ACTION_FLOATING_RESUME);
        filter.addAction(FloatingControlService.ACTION_FLOATING_STOP);
        LocalBroadcastManager.getInstance(this).registerReceiver(floatingControlReceiver, filter);
    }

    private void unregisterFloatingControlReceiver() {
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(floatingControlReceiver);
        } catch (Exception e) {
            // Ignore
        }
    }

    private final BroadcastReceiver floatingControlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (FloatingControlService.ACTION_FLOATING_PAUSE.equals(action)) {
                // Pause recording
                if (captureEngine != null && captureEngine.isCapturing()) {
                    captureEngine.pauseVideoCapture();
                }
            } else if (FloatingControlService.ACTION_FLOATING_RESUME.equals(action)) {
                // Resume recording
                if (captureEngine != null && captureEngine.isCapturing()) {
                    captureEngine.resumeVideoCapture();
                }
            } else if (FloatingControlService.ACTION_FLOATING_STOP.equals(action)) {
                // Stop recording
                stopCapture();
            }
        }
    };
}
