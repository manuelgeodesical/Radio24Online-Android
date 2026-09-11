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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class RadioService extends Service {
    public static final String ACTION_PLAY = "com.radio24online.app.PLAY";
    public static final String ACTION_STOP = "com.radio24online.app.STOP";
    public static final String ACTION_SLEEP = "com.radio24online.app.SLEEP";
    public static final String BROADCAST_STATUS = "com.radio24online.app.STATUS";

    private static final String STREAM_URL = "http://icecast2.geodesicalradio.com:8000/radio24online.mp3";
    private static final String STATUS_URL = "http://icecast2.geodesicalradio.com:8000/status-json.xsl";
    private static final String CHANNEL_ID = "radio24_playback";
    private static final int NOTIFICATION_ID = 2401;

    private MediaPlayer player;
    private volatile boolean loading = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable sleepRunnable;
    private Runnable metadataRunnable;

    private static volatile String currentState = "stopped";
    private static volatile String currentTitle = "";
    private static volatile String currentArtist = "";
    private static volatile String currentSong = "";
    private static volatile int currentListeners = -1;
    private static volatile long sleepEndMillis = 0L;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopPlayback();
        } else if (ACTION_SLEEP.equals(action)) {
            int minutes = intent != null ? intent.getIntExtra("minutes", 0) : 0;
            setSleepTimer(minutes);
        } else if (ACTION_PLAY.equals(action)) {
            startPlayback();
        }
        return START_NOT_STICKY;
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
                    updateNotification();
                    startMetadataPolling();
                }
            });

            p.setOnErrorListener((mp, what, extra) -> {
                synchronized (RadioService.this) {
                    loading = false;
                    stopMetadataPolling();
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
        stopMetadataPolling();
        cancelSleepTimer(false);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            safeRelease(player);
            player = null;
        }
        sendState("stopped");
        stopForeground(true);
        stopSelf();
    }

    private void startMetadataPolling() {
        stopMetadataPolling();
        metadataRunnable = new Runnable() {
            @Override public void run() {
                if (!"playing".equals(currentState)) return;
                new Thread(() -> fetchMetadata()).start();
                handler.postDelayed(this, 15000);
            }
        };
        handler.post(metadataRunnable);
    }

    private void stopMetadataPolling() {
        if (metadataRunnable != null) {
            handler.removeCallbacks(metadataRunnable);
            metadataRunnable = null;
        }
    }

    private void fetchMetadata() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(STATUS_URL).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "Radio24Online-Android/1.1");

            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) data.append(line);
            r.close();

            JSONObject root = new JSONObject(data.toString());
            JSONObject stats = root.optJSONObject("icestats");
            if (stats == null) return;
            JSONObject source = chooseSource(stats.opt("source"));
            if (source == null) return;

            String raw = source.optString("title", "").trim();
            int listeners = source.optInt("listeners", -1);
            String artist = "";
            String song = raw;
            int split = raw.indexOf(" - ");
            if (split > 0 && split < raw.length() - 3) {
                artist = raw.substring(0, split).trim();
                song = raw.substring(split + 3).trim();
            }

            currentTitle = raw;
            currentArtist = artist;
            currentSong = song;
            currentListeners = listeners;
            broadcastMetadata();
            updateNotification();
        } catch (Exception ignored) {
            // Si Icecast no publica metadatos, la reproducción sigue funcionando.
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private JSONObject chooseSource(Object sourceObj) {
        if (sourceObj instanceof JSONObject) return (JSONObject) sourceObj;
        if (!(sourceObj instanceof JSONArray)) return null;
        JSONArray array = (JSONArray) sourceObj;
        JSONObject fallback = null;
        for (int i = 0; i < array.length(); i++) {
            JSONObject source = array.optJSONObject(i);
            if (source == null) continue;
            if (fallback == null) fallback = source;
            String listen = source.optString("listenurl", "").toLowerCase();
            if (listen.contains("radio24online")) return source;
        }
        return fallback;
    }

    private void setSleepTimer(int minutes) {
        cancelSleepTimer(false);
        if (minutes <= 0) {
            sleepEndMillis = 0L;
            broadcastSleep(0);
            return;
        }
        if (!"playing".equals(currentState)) return;
        sleepEndMillis = System.currentTimeMillis() + (minutes * 60_000L);
        sleepRunnable = () -> {
            sleepEndMillis = 0L;
            stopPlayback();
        };
        handler.postDelayed(sleepRunnable, minutes * 60_000L);
        broadcastSleep(minutes);
    }

    private void cancelSleepTimer(boolean broadcast) {
        if (sleepRunnable != null) {
            handler.removeCallbacks(sleepRunnable);
            sleepRunnable = null;
        }
        sleepEndMillis = 0L;
        if (broadcast) broadcastSleep(0);
    }

    private void updateNotification() {
        if (!"playing".equals(currentState)) return;
        String text = currentTitle != null && !currentTitle.isEmpty() ? currentTitle : "En directo";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(text, true));
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
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(
                        playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Pausar" : "Escuchar",
                        control).build());
        return b.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Reproducción de Radio24Online", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Controles de reproducción y canción actual");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }

    private void sendState(String state) {
        currentState = state;
        Intent i = baseBroadcast(state);
        sendBroadcast(i);
    }

    private void broadcastMetadata() {
        Intent i = baseBroadcast("metadata");
        sendBroadcast(i);
    }

    private void broadcastSleep(int minutes) {
        Intent i = baseBroadcast("sleep_set");
        i.putExtra("sleepMinutes", minutes);
        sendBroadcast(i);
    }

    private Intent baseBroadcast(String state) {
        Intent i = new Intent(BROADCAST_STATUS);
        i.setPackage(getPackageName());
        i.putExtra("state", state);
        i.putExtra("title", currentTitle);
        i.putExtra("artist", currentArtist);
        i.putExtra("song", currentSong);
        i.putExtra("listeners", currentListeners);
        return i;
    }

    private void safeRelease(MediaPlayer mp) {
        try { mp.reset(); } catch (Exception ignored) {}
        try { mp.release(); } catch (Exception ignored) {}
    }

    public static String getCurrentState() { return currentState; }
    public static String getCurrentTitle() { return currentTitle; }
    public static String getCurrentArtist() { return currentArtist; }
    public static String getCurrentSong() { return currentSong; }
    public static int getCurrentListeners() { return currentListeners; }
    public static int getSleepMinutesRemaining() {
        long remaining = sleepEndMillis - System.currentTimeMillis();
        if (remaining <= 0) return 0;
        return (int) Math.ceil(remaining / 60000.0);
    }

    @Override public void onDestroy() {
        loading = false;
        stopMetadataPolling();
        cancelSleepTimer(false);
        if (player != null) {
            safeRelease(player);
            player = null;
        }
        currentState = "stopped";
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
