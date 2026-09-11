package com.radio24online.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class RadioService extends Service {
    public static final String ACTION_PLAY = "com.radio24online.app.PLAY";
    public static final String ACTION_STOP = "com.radio24online.app.STOP";
    public static final String BROADCAST_STATUS = "com.radio24online.app.STATUS";

    private static final String STREAM_URL = "http://icecast2.geodesicalradio.com:8000/radio24online.mp3";
    private static final String CHANNEL_ID = "radio24_playback";
    private static final int NOTIFICATION_ID = 2401;

    private MediaPlayer player;
    private volatile boolean loading = false;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) stopPlayback();
        else if (ACTION_PLAY.equals(action)) startPlayback();
        return START_STICKY;
    }

    private synchronized void startPlayback() {
        if (loading || (player != null && player.isPlaying())) return;
        loading = true;
        sendState("buffering");
        startForeground(NOTIFICATION_ID, buildNotification("Conectando…", false));

        try {
            MediaPlayer p = new MediaPlayer();
            p.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            p.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            p.setDataSource(STREAM_URL);
            player = p;

            p.setOnPreparedListener(mp -> {
                synchronized (RadioService.this) {
                    if (!loading || player != mp) {
                        safeRelease(mp);
                        return;
                    }
                    loading = false;
                    mp.start();
                    sendState("playing");
                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    nm.notify(NOTIFICATION_ID, buildNotification("En directo", true));
                }
            });

            p.setOnErrorListener((mp, what, extra) -> {
                synchronized (RadioService.this) {
                    loading = false;
                    safeRelease(mp);
                    if (player == mp) player = null;
                    sendState("error");
                    stopForeground(true);
                    stopSelf();
                }
                return true;
            });

            p.prepareAsync();
        } catch (Exception e) {
            loading = false;
            if (player != null) {
                safeRelease(player);
                player = null;
            }
            sendState("error");
            stopForeground(true);
            stopSelf();
        }
    }

    private synchronized void stopPlayback() {
        loading = false;
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            safeRelease(player);
            player = null;
        }
        sendState("stopped");
        stopForeground(true);
        stopSelf();
    }

    private Notification buildNotification(String text, boolean playing) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent toggle = new Intent(this, RadioService.class);
        toggle.setAction(playing ? ACTION_STOP : ACTION_PLAY);
        PendingIntent control = PendingIntent.getService(this, 2, toggle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_radio_icon)
                .setContentTitle("Radio24Online")
                .setContentText(text)
                .setContentIntent(content)
                .setOngoing(playing)
                .addAction(new Notification.Action.Builder(
                        playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Detener" : "Escuchar",
                        control).build());
        return b.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Reproducción de Radio24Online", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Controles de reproducción de la radio en directo");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }

    private void sendState(String state) {
        Intent i = new Intent(BROADCAST_STATUS);
        i.setPackage(getPackageName());
        i.putExtra("state", state);
        sendBroadcast(i);
    }

    private void safeRelease(MediaPlayer mp) {
        try { mp.reset(); } catch (Exception ignored) {}
        try { mp.release(); } catch (Exception ignored) {}
    }

    @Override public void onDestroy() {
        loading = false;
        if (player != null) {
            safeRelease(player);
            player = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
