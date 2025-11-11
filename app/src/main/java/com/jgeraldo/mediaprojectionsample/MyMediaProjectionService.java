package com.jgeraldo.mediaprojectionsample;

import static com.jgeraldo.mediaprojectionsample.MainActivity.ACTION_MEDIA_PROJECTION_STARTED;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MyMediaProjectionService extends Service {
    public static final String TAG = "MediaProjectionService";
    public static int SERVICE_ID = 1667;

    private Notification notification;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String NOTIFICATION_CHANNEL_ID = "com.jgeraldo.mediaprojectionsample.MyMediaProjectionService";
            String channelName = "Screen Recording Service";
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            chan.setDescription("Service for screen recording functionality");

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(chan);

                notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setContentTitle("Screen Recording")
                        .setContentText("Screen recording service is active")
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .build();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand - Android API: " + Build.VERSION.SDK_INT);

        // Handle stop service action
        if (intent != null && "com.jgeraldo.mediaprojectionsample.ACTION_STOP_SERVICE".equals(intent.getAction())) {
            Log.d(TAG, "Received stop service action");
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            // Check if we have the required permission for Android 14+
            if (Build.VERSION.SDK_INT >= 34) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Missing FOREGROUND_SERVICE_MEDIA_PROJECTION permission");
                    stopSelf();
                    return START_NOT_STICKY;
                }
                Log.d(TAG, "FOREGROUND_SERVICE_MEDIA_PROJECTION permission granted");
            }

            // Start foreground service based on Android version
            if (Build.VERSION.SDK_INT >= 34) {
                Log.d(TAG, "Starting foreground service with MEDIA_PROJECTION type");
                startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "Starting foreground service (Android 10+)");
                startForeground(SERVICE_ID, notification);
            } else {
                Log.d(TAG, "Running as regular service (Android < 10)");
            }

            // Extract data from intent
            int resultCode = intent != null ? intent.getIntExtra("resultCode", -1) : -1;
            Intent data = intent != null ? intent.getParcelableExtra("data") : null;

            Log.d(TAG, "Intent data - resultCode: " + resultCode + ", data null: " + (data == null));

            // resultCode -1 is actually Activity.RESULT_OK, which is what we want
            if (resultCode != Activity.RESULT_OK) {
                Log.e(TAG, "Invalid resultCode received: " + resultCode + " (expected: " + Activity.RESULT_OK + ")");
                stopSelf();
                return START_NOT_STICKY;
            }

            if (data == null) {
                Log.e(TAG, "Intent data is null");
                stopSelf();
                return START_NOT_STICKY;
            }

            Log.d(TAG, "Valid resultCode and data received, proceeding with broadcast");

            // Send broadcast to MainActivity
            Log.d(TAG, "Sending broadcast to MainActivity...");
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(ACTION_MEDIA_PROJECTION_STARTED);
            broadcastIntent.putExtra("resultCode", resultCode);
            broadcastIntent.putExtra("data", data);

            boolean broadcastSent = LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
            Log.d(TAG, "Broadcast sent successfully: " + broadcastSent);

            // DO NOT stop service immediately - keep it running to maintain MediaProjection
            Log.d(TAG, "Service will remain active to support MediaProjection");

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in startForeground: " + e.getMessage());
            e.printStackTrace();

            // For Android 14+, try alternative approach without foreground service
            if (Build.VERSION.SDK_INT >= 34) {
                Log.d(TAG, "Trying alternative approach without foreground service...");
                int resultCode = intent != null ? intent.getIntExtra("resultCode", -1) : -1;
                Intent data = intent != null ? intent.getParcelableExtra("data") : null;

                if (resultCode != -1 && data != null) {
                    Intent broadcastIntent = new Intent();
                    broadcastIntent.setAction(ACTION_MEDIA_PROJECTION_STARTED);
                    broadcastIntent.putExtra("resultCode", resultCode);
                    broadcastIntent.putExtra("data", data);

                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                    Log.d(TAG, "Broadcast sent without foreground service");
                }
            }

            stopSelf();
            return START_NOT_STICKY;
        } catch (Exception e) {
            Log.e(TAG, "Exception in onStartCommand: " + e.getMessage());
            e.printStackTrace();
            stopSelf();
            return START_NOT_STICKY;
        }

        Log.d(TAG, "Service started successfully");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service onDestroy");
    }
}
