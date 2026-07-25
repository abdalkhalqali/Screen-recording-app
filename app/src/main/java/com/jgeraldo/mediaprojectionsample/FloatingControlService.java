package com.jgeraldo.mediaprojectionsample;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class FloatingControlService extends Service {

    public static final String ACTION_SHOW_FLOATING =
            "com.jgeraldo.mediaprojectionsample.SHOW_FLOATING";
    public static final String ACTION_HIDE_FLOATING =
            "com.jgeraldo.mediaprojectionsample.HIDE_FLOATING";
    public static final String ACTION_FLOATING_PAUSE =
            "com.jgeraldo.mediaprojectionsample.FLOATING_PAUSE";
    public static final String ACTION_FLOATING_RESUME =
            "com.jgeraldo.mediaprojectionsample.FLOATING_RESUME";
    public static final String ACTION_FLOATING_STOP =
            "com.jgeraldo.mediaprojectionsample.FLOATING_STOP";
    public static final String EXTRA_IS_PAUSED = "is_paused";
    public static final String EXTRA_ELAPSED = "elapsed_ms";

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;

    private ImageButton btnPauseResume;
    private ImageButton btnStop;
    private TextView timerText;

    private boolean isPaused = false;
    private long elapsedMs = 0;
    private boolean isDragging = false;
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

    private Handler timerHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String action = intent.getAction();
        if (ACTION_SHOW_FLOATING.equals(action)) {
            showFloatingWidget();
            startTimer();
        } else if (ACTION_HIDE_FLOATING.equals(action)) {
            hideFloatingWidget();
            stopSelf();
        }

        // تحديث الحالة إذا كانت موجودة
        if (intent.hasExtra(EXTRA_IS_PAUSED)) {
            isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, false);
            updateButtonState();
        }
        if (intent.hasExtra(EXTRA_ELAPSED)) {
            elapsedMs = intent.getLongExtra(EXTRA_ELAPSED, 0);
            updateTimerDisplay();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        hideFloatingWidget();
        timerHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void showFloatingWidget() {
        // منع التكرار
        if (floatingView != null) return;

        // 🛡️ فحص صلاحية النوافذ العائمة
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

            if (inflater == null || windowManager == null) {
                stopSelf();
                return;
            }

            floatingView = inflater.inflate(R.layout.floating_control, null);

            btnPauseResume = floatingView.findViewById(R.id.btnPauseResume);
            btnStop = floatingView.findViewById(R.id.btnStop);
            timerText = floatingView.findViewById(R.id.timerText);

            if (btnPauseResume == null || btnStop == null) {
                stopSelf();
                return;
            }

            // زر إيقاف مؤقت/استئناف
            btnPauseResume.setOnClickListener(v -> {
                if (isPaused) {
                    // استئناف
                    isPaused = false;
                    LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent(ACTION_FLOATING_RESUME));
                } else {
                    // إيقاف مؤقت
                    isPaused = true;
                    LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent(ACTION_FLOATING_PAUSE));
                }
                updateButtonState();
            });

            // زر إيقاف
            btnStop.setOnClickListener(v -> {
                LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(new Intent(ACTION_FLOATING_STOP));
                hideFloatingWidget();
                stopSelf();
            });

            // جعل اللوحة قابلة للسحب
            setupDraggable();

            // إظهار كخدمة foreground
            startForegroundWithNotification();

            // إعداد معلمات النافذة
            setupLayoutParams();

            // 🛡️ إضافة النافذة مع try-catch
            windowManager.addView(floatingView, params);

            // حركة الظهور
            floatingView.setAlpha(0f);
            floatingView.setScaleX(0.8f);
            floatingView.setScaleY(0.8f);
            floatingView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .start();

            updateButtonState();
            updateTimerDisplay();

        } catch (Exception e) {
            // 🛡️ أي خطأ في إضافة النافذة → إيقاف الخدمة بدون انهيار
            floatingView = null;
            stopSelf();
        }
    }

    private void setupDraggable() {
        if (floatingView == null) return;
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private static final int CLICK_DRAG_TOLERANCE = 10;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isDragging = false;
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float diffX = event.getRawX() - initialTouchX;
                        float diffY = event.getRawY() - initialTouchY;
                        if (Math.abs(diffX) > CLICK_DRAG_TOLERANCE ||
                                Math.abs(diffY) > CLICK_DRAG_TOLERANCE) {
                            isDragging = true;
                            try {
                                params.x = initialX + (int) diffX;
                                params.y = initialY + (int) diffY;
                                windowManager.updateViewLayout(floatingView, params);
                            } catch (Exception ignored) {
                                // تجاهل أخطاء التحديث
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (isDragging) {
                            snapToEdge();
                            isDragging = false;
                            return true;
                        }
                        return false;
                }
                return false;
            }
        });
    }

    private void setupLayoutParams() {
        if (windowManager == null) return;

        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 200;
    }

    private void startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Notification notification = buildNotification();
            startForeground(2001, notification);
        }
    }

    private void hideFloatingWidget() {
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception ignored) {
                // تمت إزالة النافذة مسبقاً
            }
            floatingView = null;
        }
    }

    private void snapToEdge() {
        if (windowManager == null || floatingView == null) return;
        try {
            Point size = new Point();
            windowManager.getDefaultDisplay().getSize(size);

            int halfWidth = floatingView.getWidth() / 2;
            int screenMid = size.x / 2;

            if (params.x + halfWidth > screenMid) {
                params.x = size.x - floatingView.getWidth() - 10;
            } else {
                params.x = 10;
            }

            windowManager.updateViewLayout(floatingView, params);
        } catch (Exception ignored) {
            // تجاهل أخطاء الـ snap
        }
    }

    private void startTimer() {
        timerHandler.removeCallbacksAndMessages(null);
        timerHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isPaused) {
                    elapsedMs += 100;
                    updateTimerDisplay();
                }
                timerHandler.postDelayed(this, 100);
            }
        });
    }

    private void updateTimerDisplay() {
        if (timerText == null) return;
        int totalSeconds = (int) (elapsedMs / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        int millis = (int) (elapsedMs % 1000) / 100;
        timerText.setText(String.format("%02d:%02d.%d", minutes, seconds, millis));
    }

    private void updateButtonState() {
        if (btnPauseResume == null) return;
        try {
            if (isPaused) {
                btnPauseResume.setImageResource(android.R.drawable.ic_media_play);
                btnPauseResume.setContentDescription("استئناف");
            } else {
                btnPauseResume.setImageResource(android.R.drawable.ic_media_pause);
                btnPauseResume.setContentDescription("إيقاف مؤقت");
            }
        } catch (Exception ignored) {
            // تجاهل
        }
    }

    private Notification buildNotification() {
        NotificationChannel channel = new NotificationChannel(
                "floating_control",
                "التحكم بالتسجيل",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);

        return new Notification.Builder(this, "floating_control")
                .setContentTitle("مسجل الشاشة")
                .setContentText(isPaused ? "⏸️ تم الإيقاف المؤقت" : "🎬 جاري التسجيل...")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "floating_control",
                    "التحكم بالتسجيل",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
