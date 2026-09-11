package com.radio24online.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends Activity {
    private TextView status;
    private TextView songTitle;
    private TextView artistName;
    private ImageView artwork;
    private TextView playCircle;
    private Button playButton;
    private Button sleepButton;
    private boolean playing = false;
    private volatile String lastArtworkQuery = "";

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            applyBroadcast(intent);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermission();
        buildUi();
        syncFromService();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(8, 12, 19));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(30));
        root.setBackgroundColor(Color.rgb(8, 12, 19));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        ImageView brand = new ImageView(this);
        brand.setImageResource(R.drawable.logo_radio24_official);
        brand.setAdjustViewBounds(true);
        brand.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(-1, dp(190));
        root.addView(brand, brandLp);

        TextView tagline = new TextView(this);
        tagline.setText("Siempre contigo");
        tagline.setTextColor(Color.rgb(190, 200, 214));
        tagline.setTextSize(19);
        tagline.setGravity(Gravity.CENTER);
        tagline.setTypeface(null, 1);
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(-1, -2);
        tagLp.topMargin = dp(2);
        root.addView(tagline, tagLp);

        status = new TextView(this);
        status.setText("LISTO PARA ESCUCHAR");
        status.setTextColor(Color.rgb(239, 68, 68));
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        status.setTypeface(null, 1);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(20);
        root.addView(status, statusLp);

        artwork = new ImageView(this);
        artwork.setImageResource(R.drawable.logo_radio24_official);
        artwork.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        artwork.setPadding(dp(20), dp(20), dp(20), dp(20));
        artwork.setBackgroundResource(R.drawable.button_secondary);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(dp(220), dp(220));
        artLp.topMargin = dp(18);
        root.addView(artwork, artLp);

        TextView now = new TextView(this);
        now.setText("AHORA SUENA");
        now.setTextColor(Color.rgb(239, 68, 68));
        now.setTextSize(11);
        now.setTypeface(null, 1);
        now.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nowLp = new LinearLayout.LayoutParams(-1, -2);
        nowLp.topMargin = dp(18);
        root.addView(now, nowLp);

        songTitle = new TextView(this);
        songTitle.setText("Radio24Online en directo");
        songTitle.setTextColor(Color.WHITE);
        songTitle.setTextSize(21);
        songTitle.setTypeface(null, 1);
        songTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams songLp = new LinearLayout.LayoutParams(-1, -2);
        songLp.topMargin = dp(6);
        root.addView(songTitle, songLp);

        artistName = new TextView(this);
        artistName.setText("Emisión en directo");
        artistName.setTextColor(Color.rgb(167, 179, 196));
        artistName.setTextSize(15);
        artistName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams artistLp = new LinearLayout.LayoutParams(-1, -2);
        artistLp.topMargin = dp(4);
        root.addView(artistName, artistLp);

        playCircle = new TextView(this);
        playCircle.setText("▶");
        playCircle.setTextColor(Color.WHITE);
        playCircle.setTextSize(42);
        playCircle.setGravity(Gravity.CENTER);
        playCircle.setBackgroundResource(R.drawable.button_primary);
        LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(dp(92), dp(92));
        circleLp.topMargin = dp(24);
        root.addView(playCircle, circleLp);
        playCircle.setOnClickListener(v -> togglePlayback());

        playButton = new Button(this);
        playButton.setText("ESCUCHAR AHORA");
        playButton.setTextColor(Color.WHITE);
        playButton.setTextSize(16);
        playButton.setAllCaps(false);
        playButton.setBackgroundResource(R.drawable.button_primary);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(-1, dp(58));
        playLp.topMargin = dp(16);
        root.addView(playButton, playLp);
        playButton.setOnClickListener(v -> togglePlayback());

        sleepButton = new Button(this);
        sleepButton.setText("Temporizador de apagado");
        sleepButton.setTextColor(Color.WHITE);
        sleepButton.setTextSize(14);
        sleepButton.setAllCaps(false);
        sleepButton.setBackgroundResource(R.drawable.button_secondary);
        LinearLayout.LayoutParams sleepLp = new LinearLayout.LayoutParams(-1, dp(52));
        sleepLp.topMargin = dp(12);
        root.addView(sleepButton, sleepLp);
        sleepButton.setOnClickListener(v -> showSleepTimer());

        Button web = new Button(this);
        web.setText("Visitar radio24online.com");
        web.setTextColor(Color.WHITE);
        web.setTextSize(14);
        web.setAllCaps(false);
        web.setBackgroundResource(R.drawable.button_secondary);
        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(-1, dp(52));
        webLp.topMargin = dp(10);
        root.addView(web, webLp);
        web.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://radio24online.com/"))));

        TextView footer = new TextView(this);
        footer.setText("Radio24Online · Android v1.1.1\n© Geodesical Technology");
        footer.setTextColor(Color.rgb(92, 104, 122));
        footer.setGravity(Gravity.CENTER);
        footer.setTextSize(12);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
        footerLp.topMargin = dp(28);
        root.addView(footer, footerLp);

        setContentView(scroll);
    }

    private void applyBroadcast(Intent intent) {
        String state = intent.getStringExtra("state");
        if (state != null) applyState(state);

        if (intent.hasExtra("title") || "metadata".equals(state)) {
            updateMetadata(
                    intent.getStringExtra("title"),
                    intent.getStringExtra("artist"),
                    intent.getStringExtra("song"));
        }

        if (intent.hasExtra("sleepMinutes") || "sleep_set".equals(state)) {
            updateSleepLabel(intent.getIntExtra("sleepMinutes", 0));
        }
    }

    private void applyState(String state) {
        if ("playing".equals(state)) {
            playing = true;
            status.setText("● EN DIRECTO");
            playButton.setText("PAUSAR");
            playCircle.setText("Ⅱ");
        } else if ("buffering".equals(state)) {
            status.setText("CONECTANDO…");
            playButton.setText("CONECTANDO…");
            playCircle.setText("…");
        } else if ("stopped".equals(state)) {
            playing = false;
            status.setText("LISTO PARA ESCUCHAR");
            playButton.setText("ESCUCHAR AHORA");
            playCircle.setText("▶");
            updateSleepLabel(0);
        } else if ("error".equals(state)) {
            playing = false;
            status.setText("NO SE PUDO CONECTAR");
            playButton.setText("REINTENTAR");
            playCircle.setText("▶");
        }
    }

    private void updateMetadata(String raw, String artist, String song) {
        String safeRaw = raw == null ? "" : raw.trim();
        String safeArtist = artist == null ? "" : artist.trim();
        String safeSong = song == null ? "" : song.trim();

        if (!safeSong.isEmpty()) songTitle.setText(safeSong);
        else if (!safeRaw.isEmpty()) songTitle.setText(safeRaw);
        else songTitle.setText("Radio24Online en directo");

        if (!safeArtist.isEmpty()) artistName.setText(safeArtist);
        else artistName.setText("Emisión en directo");

        if (!safeArtist.isEmpty() && !safeSong.isEmpty()) loadArtwork(safeArtist, safeSong);
    }

    private void loadArtwork(String artist, String song) {
        final String query = (artist + " " + song).trim();
        if (query.isEmpty() || query.equals(lastArtworkQuery)) return;
        lastArtworkQuery = query;
        artwork.setImageResource(R.drawable.logo_radio24_official);
        artwork.setPadding(dp(20), dp(20), dp(20), dp(20));
        artwork.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                String q = URLEncoder.encode(query, "UTF-8");
                URL api = new URL("https://itunes.apple.com/search?media=music&entity=song&limit=1&term=" + q);
                c = (HttpURLConnection) api.openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.setRequestProperty("User-Agent", "Radio24Online-Android/1.1.1");

                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) json.append(line);
                r.close();

                JSONObject root = new JSONObject(json.toString());
                JSONArray results = root.optJSONArray("results");
                if (results == null || results.length() == 0) return;
                String artUrl = results.getJSONObject(0).optString("artworkUrl100", "");
                if (artUrl.isEmpty()) return;
                artUrl = artUrl.replace("100x100", "600x600");

                HttpURLConnection img = (HttpURLConnection) new URL(artUrl).openConnection();
                img.setConnectTimeout(5000);
                img.setReadTimeout(5000);
                final Bitmap bitmap = BitmapFactory.decodeStream(img.getInputStream());
                img.disconnect();
                if (bitmap != null) runOnUiThread(() -> {
                    if (query.equals(lastArtworkQuery)) {
                        artwork.setPadding(0, 0, 0, 0);
                        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        artwork.setImageBitmap(bitmap);
                    }
                });
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private void togglePlayback() {
        Intent i = new Intent(this, RadioService.class);
        i.setAction(playing ? RadioService.ACTION_STOP : RadioService.ACTION_PLAY);
        if (Build.VERSION.SDK_INT >= 26 && !playing) startForegroundService(i); else startService(i);
    }

    private void showSleepTimer() {
        if (!playing) {
            Toast.makeText(this, "Primero inicia Radio24Online", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] options = {"15 minutos", "30 minutos", "60 minutos", "90 minutos", "Cancelar temporizador"};
        new AlertDialog.Builder(this)
                .setTitle("Temporizador de apagado")
                .setItems(options, (dialog, which) -> {
                    int minutes;
                    if (which == 0) minutes = 15;
                    else if (which == 1) minutes = 30;
                    else if (which == 2) minutes = 60;
                    else if (which == 3) minutes = 90;
                    else minutes = 0;
                    Intent i = new Intent(this, RadioService.class);
                    i.setAction(RadioService.ACTION_SLEEP);
                    i.putExtra("minutes", minutes);
                    startService(i);
                }).show();
    }

    private void updateSleepLabel(int minutes) {
        if (minutes > 0) sleepButton.setText("Apagado automático: " + minutes + " min");
        else sleepButton.setText("Temporizador de apagado");
    }

    private void syncFromService() {
        applyState(RadioService.getCurrentState());
        updateMetadata(
                RadioService.getCurrentTitle(),
                RadioService.getCurrentArtist(),
                RadioService.getCurrentSong());
        updateSleepLabel(RadioService.getSleepMinutesRemaining());
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(RadioService.BROADCAST_STATUS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(statusReceiver, f);
        syncFromService();
    }

    @Override protected void onStop() {
        super.onStop();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
