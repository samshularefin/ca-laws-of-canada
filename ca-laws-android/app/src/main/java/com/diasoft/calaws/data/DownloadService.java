package com.diasoft.calaws.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import com.diasoft.calaws.R;
import com.diasoft.calaws.ui.SplashActivity;

import java.io.File;

/** Foreground service so the database download survives the app going to the background. */
public class DownloadService extends Service {

    public interface Listener {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        void onComplete();
        void onError(String message);
    }

    private static final String CHANNEL_ID = "db_download";
    private static final int NOTIF_ID = 1001;
    private static final int NOTIF_DONE_ID = 1002;

    private static volatile Listener listener;
    public static volatile boolean running;
    public static volatile int percent;
    public static volatile long downloaded;
    public static volatile long total;
    public static volatile String error;

    public static void setListener(Listener l) {
        listener = l;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_NOT_STICKY;
        running = true;
        error = null;
        percent = 0;
        downloaded = 0;
        total = 0;

        createChannel();
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(0, 0, 0, true),
                Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0);

        new Thread(this::download).start();
        return START_NOT_STICKY;
    }

    private void download() {
        String remoteVersion = null;
        try {
            remoteVersion = DatabaseInstaller.fetchRemoteVersion();
        } catch (Exception ignored) {
            // Proceed without version tracking if the version endpoint is unreachable.
        }

        DatabaseInstaller.installOrUpdate(this, remoteVersion, new DatabaseInstaller.ProgressListener() {
            @Override
            public void onProgress(int p, long d, long t) {
                percent = p;
                downloaded = d;
                total = t;
                notifyProgress(p, d, t);
                Listener l = listener;
                if (l != null) l.onProgress(p, d, t);
            }

            @Override
            public void onComplete(File dbFile) {
                running = false;
                finish(true);
                Listener l = listener;
                if (l != null) l.onComplete();
            }

            @Override
            public void onError(Exception e) {
                running = false;
                error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                finish(false);
                Listener l = listener;
                if (l != null) l.onError(error);
            }
        });
    }

    private void finish(boolean ok) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        Intent open = new Intent(this, SplashActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(ok ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_notify_error)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(ok ? R.string.download_complete : R.string.download_failed_short))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            getSystemService(NotificationManager.class).notify(NOTIF_DONE_ID, n);
        } catch (SecurityException ignored) {
            // Notification permission denied; the splash screen already reflects the result.
        }
        stopSelf();
    }

    private void notifyProgress(int p, long d, long t) {
        try {
            getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification(p, d, t, false));
        } catch (SecurityException ignored) {
            // Notification permission denied; the service keeps running regardless.
        }
    }

    private Notification buildNotification(int p, long d, long t, boolean indeterminate) {
        Intent open = new Intent(this, SplashActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = indeterminate
                ? getString(R.string.downloading_database)
                : getString(R.string.download_progress, p, d / 1048576f, t / 1048576f);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, p, indeterminate)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
