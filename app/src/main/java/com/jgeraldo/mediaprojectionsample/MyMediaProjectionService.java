package com.jgeraldo.mediaprojectionsample;

import static com.jgeraldo.mediaprojectionsample.MainActivity.ACTION_MEDIA_PROJECTION_STARTED;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MyMediaProjectionService extends Service {

    private static final String TAG = "MediaProjectionSvc";
    private static final int NOTIFICATION_ID = 1667;
    private static final String CHANNEL_ID = "media_projection_channel";

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
        try {
            if (intent == null) {
                Log.w(TAG, "intent is null, stopping");
                stopSelf();
                return START_NOT_STICKY;
            }

            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");

            if (data == null) {
                Log.w(TAG, "data is null, cannot start MediaProjection");
                stopSelf();
                return START_NOT_STICKY;
            }

            // ✅ تشغيل الخدمة كـ foreground service قبل أي شيء
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Notification notification = buildNotification();
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                Log.d(TAG, "✅ Service started as foreground");
            }

            // ✅ إرسال الـ data إلى MainActivity عبر LocalBroadcast
            Intent broadcastIntent = new Intent(ACTION_MEDIA_PROJECTION_STARTED);
            broadcastIntent.putExtra("resultCode", resultCode);
            broadcastIntent.putExtra("data", data);

            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
            Log.d(TAG, "✅ Broadcast sent to MainActivity");

        } catch (Exception e) {
            Log.e(TAG, "❌ Service error: " + e.getMessage(), e);
            // ننشر broadcast بالفشل عشان MainActivity تعرف وتظهر رسالة
            Intent failIntent = new Intent(ACTION_MEDIA_PROJECTION_STARTED);
            failIntent.putExtra("resultCode", -1);
            failIntent.putExtra("error", e.getMessage());
            LocalBroadcastManager.getInstance(this).sendBroadcast(failIntent);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_channel_name))
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
                channel.setShowBadge(false);

                NotificationManager manager =
                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                    Log.d(TAG, "✅ Notification channel created");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to create notification channel: " + e.getMessage());
            }
        }
    }
}
