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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_MEDIA_PROJECTION_STARTED =
            "com.jgeraldo.mediaprojectionsample.ACTION_MEDIA_PROJECTION_STARTED";
    public static final String TAG = "MediaProjectionSample";

    private boolean isReceiverRegistered = false;
    private boolean isCapturing = false;

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;

    private Button mButtonToggle;
    private Surface mSurface;
    private Handler mHandler;

    // محرك التسجيل
    private ScreenCaptureEngine captureEngine;
    private int displayWidth = 720, displayHeight = 1280;

    // الإعدادات
    private SettingsPrefs settingsPrefs;

    private ActivityResultLauncher<Intent> startMediaProjectionActivity;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> overlaySettingsLauncher;

    // واجهة الأذونات
    private LinearLayout permissionStatusBar;
    private TextView permissionStatusText;
    private ImageView permissionDot;
    private boolean permissionsReady = false;

    // BroadcastReceiver للخدمة
    public class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_MEDIA_PROJECTION_STARTED.equals(intent.getAction())) {
                int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
                Intent data = intent.getParcelableExtra("data");
                MediaProjectionManager pm =
                        (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mMediaProjection = pm.getMediaProjection(resultCode, data);
                if (mMediaProjection != null) startScreenCapture();
            }
        }
    }

    private final MyBroadcastReceiver receiver = new MyBroadcastReceiver();

    // مستقبل اللوحة العائمة
    private final BroadcastReceiver floatingControlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (FloatingControlService.ACTION_FLOATING_PAUSE.equals(action)) {
                Toast.makeText(MainActivity.this, "⏸️ تم الإيقاف المؤقت", Toast.LENGTH_SHORT).show();
            } else if (FloatingControlService.ACTION_FLOATING_RESUME.equals(action)) {
                Toast.makeText(MainActivity.this, "▶️ تم الاستئناف", Toast.LENGTH_SHORT).show();
            } else if (FloatingControlService.ACTION_FLOATING_STOP.equals(action)) {
                stopScreenCapture();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // تسجيل Launchers (يجب أن يكون في onCreate قبل STARTED state)
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    checkAllPermissions();
                    if (isGranted) Log.d(TAG, "الصلاحية مفعلة");
                });

        overlaySettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result ->
                        checkAllPermissions());

        startMediaProjectionActivity = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result == null) return;
                    int resultCode = result.getResultCode();
                    if (resultCode == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            MediaProjectionManager pm = (MediaProjectionManager)
                                    getSystemService(Context.MEDIA_PROJECTION_SERVICE);
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
                            } catch (RuntimeException e) {
                                Log.w(TAG, "خطأ: " + e.getMessage());
                            }
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.permission_denied),
                                Toast.LENGTH_SHORT).show();
                    }
                });

        // تهيئة العناصر
        SurfaceView mSurfaceView = findViewById(R.id.surface);
        mSurface = mSurfaceView.getHolder().getSurface();
        mHandler = new Handler(Looper.getMainLooper());

        mButtonToggle = findViewById(R.id.button);
        permissionStatusBar = findViewById(R.id.permissionStatusBar);
        permissionStatusText = findViewById(R.id.permissionStatusText);
        permissionDot = findViewById(R.id.permissionDot);

        // تهيئة الإعدادات
        settingsPrefs = new SettingsPrefs(this);

        // زر البدء/الإيقاف
        mButtonToggle.setOnClickListener(view -> {
            if (!isCapturing) {
                if (!permissionsReady) { checkAllPermissions(); return; }
                requestScreenCapturePermission();
            } else {
                stopScreenCapture();
            }
        });

        // شريط الأذونات - قابل للنقر
        if (permissionStatusBar != null) {
            permissionStatusBar.setOnClickListener(v -> showPermissionsDialog());
        }

        // فحص الأذونات بعد 500ms
        mHandler.postDelayed(this::checkAllPermissions, 500);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mediaProjectionManager = (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);

        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_MEDIA_PROJECTION_STARTED);
            LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
            isReceiverRegistered = true;
        }

        // تسجيل مستقبل اللوحة العائمة
        registerFloatingControlReceiver();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            isReceiverRegistered = false;
        }

        // إلغاء تسجيل مستقبل اللوحة العائمة
        unregisterFloatingControlReceiver();

        if (!isCapturing && mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
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

    // ========== نظام الأذونات ==========

    /**
     * فحص جميع الأذونات وتحديث واجهة المستخدم
     */
    private void checkAllPermissions() {
        String[] missing = getMissingPermissions();
        permissionsReady = (missing.length == 0);

        if (permissionStatusBar == null || permissionStatusText == null || permissionDot == null)
            return;

        if (permissionsReady) {
            // كل الأذونات مفعلة → إخفاء الشريط
            permissionStatusBar.setVisibility(View.GONE);
            permissionDot.setImageResource(R.drawable.permission_dot_green);
            mButtonToggle.setEnabled(true);
            mButtonToggle.setAlpha(1f);
        } else {
            // يوجد أذونات ناقصة → إظهار الشريط
            permissionStatusBar.setVisibility(View.VISIBLE);
            permissionDot.setImageResource(R.drawable.permission_dot_amber);

            StringBuilder msg = new StringBuilder("⚠️ ");
            for (int i = 0; i < missing.length; i++) {
                msg.append(getPermissionDisplayName(missing[i]));
                if (i < missing.length - 1) msg.append("، ");
            }
            permissionStatusText.setText(msg.toString());
            mButtonToggle.setEnabled(true);
            mButtonToggle.setAlpha(1f);
        }
    }

    /**
     * @return مصفوفة بالأذونات الناقصة
     */
    private String[] getMissingPermissions() {
        ArrayList<String> missing = new ArrayList<>();

        // الصوت
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECORD_AUDIO);
        }

        // الإشعارات (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            // الوسائط (Android 13+)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            // التخزين (Android 12-)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        // النوافذ العائمة (Android 6+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(this)) {
            missing.add(Manifest.permission.SYSTEM_ALERT_WINDOW);
        }

        return missing.toArray(new String[0]);
    }

    /**
     * @return الاسم العربي للإذن
     */
    private String getPermissionDisplayName(String permission) {
        switch (permission) {
            case Manifest.permission.RECORD_AUDIO:
                return getString(R.string.perm_record_audio);
            case Manifest.permission.POST_NOTIFICATIONS:
                return getString(R.string.perm_post_notifications);
            case Manifest.permission.READ_MEDIA_IMAGES:
                return getString(R.string.perm_read_images);
            case Manifest.permission.READ_MEDIA_VIDEO:
                return getString(R.string.perm_read_video);
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                return getString(R.string.perm_write_storage);
            case Manifest.permission.SYSTEM_ALERT_WINDOW:
                return getString(R.string.perm_overlay);
            default:
                return permission;
        }
    }

    /**
     * عرض نافذة الأذونات وطلب الأذونات الناقصة
     */
    private void showPermissionsDialog() {
        String[] missing = getMissingPermissions();
        if (missing.length == 0) {
            Toast.makeText(this, R.string.permissions_ok, Toast.LENGTH_SHORT).show();
            checkAllPermissions();
            return;
        }

        // بناء قائمة بأسماء الأذونات
        String[] items = new String[missing.length];
        for (int i = 0; i < missing.length; i++) {
            items[i] = getPermissionDisplayName(missing[i]);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.permissions_title)
                .setItems(items, (dialog, which) -> {
                    requestSpecificPermission(missing[which]);
                })
                .setPositiveButton(R.string.btn_close, null)
                .show();
    }

    /**
     * طلب إذن محدد مع شرح مسبق
     */
    private void requestSpecificPermission(String permission) {
        if (permission.equals(Manifest.permission.SYSTEM_ALERT_WINDOW)) {
            // النوافذ العائمة تحتاج فتح الإعدادات
            showOverlayPermissionDialog();
            return;
        }

        // هل نحتاج شرح؟
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            showPermissionExplanation(permission);
        } else {
            requestPermissionLauncher.launch(permission);
        }
    }

    /**
     * عرض شرح للإذن قبل طلبه
     */
    private void showPermissionExplanation(String permission) {
        String title, message;

        switch (permission) {
            case Manifest.permission.RECORD_AUDIO:
                title = getString(R.string.explain_audio_title);
                message = getString(R.string.explain_audio_msg);
                break;
            case Manifest.permission.POST_NOTIFICATIONS:
                title = getString(R.string.explain_notif_title);
                message = getString(R.string.explain_notif_msg);
                break;
            case Manifest.permission.READ_MEDIA_IMAGES:
            case Manifest.permission.READ_MEDIA_VIDEO:
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                title = getString(R.string.explain_storage_title);
                message = getString(R.string.explain_storage_msg);
                break;
            default:
                title = "إذن مطلوب";
                message = "هذا الإذن ضروري لعمل التطبيق.";
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.btn_allow),
                        (d, w) -> requestPermissionLauncher.launch(permission))
                .setNegativeButton(getString(R.string.btn_later), null)
                .show();
    }

    /**
     * شرح إذن النوافذ العائمة (يتطلب فتح الإعدادات)
     */
    private void showOverlayPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.explain_overlay_title))
                .setMessage(getString(R.string.explain_overlay_msg))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.btn_open_settings), (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    overlaySettingsLauncher.launch(intent);
                })
                .setNegativeButton(getString(R.string.btn_later), null)
                .show();
    }

    // ========== تسجيل الشاشة ==========

    private void requestScreenCapturePermission() {
        if (startMediaProjectionActivity != null) {
            mediaProjectionManager = (MediaProjectionManager)
                    getSystemService(MEDIA_PROJECTION_SERVICE);
            startMediaProjectionActivity.launch(
                    mediaProjectionManager.createScreenCaptureIntent());
        }
    }

    private void startScreenCapture() {
        if (mMediaProjection == null) return;

        try {
            // إنشاء محرك التسجيل
            captureEngine = new ScreenCaptureEngine(mMediaProjection, getContentResolver());

            // تسجيل المستمع
            captureEngine.setOnCaptureListener(new ScreenCaptureEngine.OnCaptureListener() {
                @Override
                public void onRecordingStarted() {
                    runOnUiThread(() -> {
                        isCapturing = true;
                        mButtonToggle.setText(R.string.button_stop);
                        showFloatingControl();
                        Toast.makeText(MainActivity.this, "🎬 بدأ التسجيل", Toast.LENGTH_SHORT).show();
                    });
                }
                @Override
                public void onRecordingStopped(Uri videoUri, String message) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                        isCapturing = false;
                        mButtonToggle.setText(R.string.button_start);
                        hideFloatingControl();
                        openFile(videoUri, "video/*");
                    });
                }
                @Override
                public void onCaptureError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                        if (isCapturing) {
                            isCapturing = false;
                            mButtonToggle.setText(R.string.button_start);
                            hideFloatingControl();
                        }
                    });
                }
            });

            // بدء التسجيل
            captureEngine.startRecording(displayWidth, displayHeight);

        } catch (Exception e) {
            Log.e(TAG, "فشل بدء التسجيل: " + e.getMessage());
            Toast.makeText(this, "❌ فشل بدء التسجيل", Toast.LENGTH_LONG).show();
            stopScreenCapture();
        }
    }

    private void stopScreenCapture() {
        if (captureEngine != null) {
            captureEngine.stopRecording();
            captureEngine.release();
            captureEngine = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        isCapturing = false;
        mButtonToggle.setText(R.string.button_start);
        hideFloatingControl();
    }

    private void openFile(Uri uri, String mimeType) {
        try {
            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(uri, mimeType);
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (openIntent.resolveActivity(getPackageManager()) != null)
                startActivity(Intent.createChooser(openIntent, "فتح بـ"));
        } catch (Exception e) {
            Log.w(TAG, "فشل فتح الملف: " + e.getMessage());
        }
    }

    // ========== اللوحة العائمة ==========

    private void showFloatingControl() {
        try {
            // 🛡️ التحقق من صلاحية النوافذ العائمة
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays(this)) {
                // لم يتم تفعيل الصلاحية → سيطلبها شريط الأذونات
                return;
            }

            Intent si = new Intent(this, FloatingControlService.class);
            si.setAction(FloatingControlService.ACTION_SHOW_FLOATING);
            ContextCompat.startForegroundService(this, si);
        } catch (Exception e) {
            Log.w(TAG, "فشل إظهار اللوحة العائمة: " + e.getMessage());
        }
    }

    private void hideFloatingControl() {
        try {
            Intent si = new Intent(this, FloatingControlService.class);
            si.setAction(FloatingControlService.ACTION_HIDE_FLOATING);
            startService(si);
        } catch (Exception e) {
            Log.w(TAG, "فشل إخفاء اللوحة العائمة: " + e.getMessage());
        }
    }

    private void registerFloatingControlReceiver() {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(FloatingControlService.ACTION_FLOATING_PAUSE);
            filter.addAction(FloatingControlService.ACTION_FLOATING_RESUME);
            filter.addAction(FloatingControlService.ACTION_FLOATING_STOP);
            LocalBroadcastManager.getInstance(this)
                    .registerReceiver(floatingControlReceiver, filter);
        } catch (Exception e) {
            Log.w(TAG, "فشل تسجيل مستقبل اللوحة: " + e.getMessage());
        }
    }

    private void unregisterFloatingControlReceiver() {
        try {
            LocalBroadcastManager.getInstance(this)
                    .unregisterReceiver(floatingControlReceiver);
        } catch (Exception ignored) {
            // غير مسجل أصلاً
        }
    }
}
