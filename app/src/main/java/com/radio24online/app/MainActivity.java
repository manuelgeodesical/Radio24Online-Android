package com.radio24online.app;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private Button playButton;
    private boolean playing = false;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra("state");
            if (state == null) return;
            if ("playing".equals(state)) {
                playing = true;
                status.setText("EN DIRECTO");
                playButton.setText("PAUSAR");
            } else if ("buffering".equals(state)) {
                status.setText("CONECTANDO…");
                playButton.setText("CONECTANDO…");
            } else if ("stopped".equals(state)) {
                playing = false;
                status.setText("LISTO PARA ESCUCHAR");
                playButton.setText("ESCUCHAR AHORA");
            } else if ("error".equals(state)) {
                playing = false;
                status.setText("NO SE PUDO CONECTAR");
                playButton.setText("REINTENTAR");
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermission();
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(40), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(11, 16, 24));

        TextView logo = new TextView(this);
        logo.setText("● RADIO24ONLINE");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(25);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, 1);
        root.addView(logo, new LinearLayout.LayoutParams(-1, -2));

        TextView tagline = new TextView(this);
        tagline.setText("Una Radio para ti\nSiempre Contigo");
        tagline.setTextColor(Color.rgb(170, 181, 197));
        tagline.setTextSize(18);
        tagline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(-1, -2);
        tagLp.topMargin = dp(18);
        root.addView(tagline, tagLp);

        Space spacer = new Space(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        TextView circle = new TextView(this);
        circle.setText("▶");
        circle.setTextColor(Color.WHITE);
        circle.setTextSize(54);
        circle.setGravity(Gravity.CENTER);
        circle.setBackgroundResource(R.drawable.button_primary);
        LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(dp(128), dp(128));
        root.addView(circle, circleLp);
        circle.setOnClickListener(v -> togglePlayback());

        status = new TextView(this);
        status.setText("LISTO PARA ESCUCHAR");
        status.setTextColor(Color.rgb(229, 57, 53));
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setTypeface(null, 1);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(24);
        root.addView(status, statusLp);

        playButton = new Button(this);
        playButton.setText("ESCUCHAR AHORA");
        playButton.setTextColor(Color.WHITE);
        playButton.setTextSize(16);
        playButton.setAllCaps(false);
        playButton.setBackgroundResource(R.drawable.button_primary);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(-1, dp(58));
        playLp.topMargin = dp(18);
        root.addView(playButton, playLp);
        playButton.setOnClickListener(v -> togglePlayback());

        Button web = new Button(this);
        web.setText("Abrir radio24online.com");
        web.setTextColor(Color.WHITE);
        web.setTextSize(14);
        web.setAllCaps(false);
        web.setBackgroundResource(R.drawable.button_secondary);
        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(-1, dp(52));
        webLp.topMargin = dp(12);
        root.addView(web, webLp);
        web.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://radio24online.com/"))));

        Space lower = new Space(this);
        root.addView(lower, new LinearLayout.LayoutParams(1, 0, 0.8f));

        TextView footer = new TextView(this);
        footer.setText("Radio24Online · Android\n© Geodesical Technology");
        footer.setTextColor(Color.rgb(112, 124, 142));
        footer.setGravity(Gravity.CENTER);
        footer.setTextSize(12);
        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private void togglePlayback() {
        Intent i = new Intent(this, RadioService.class);
        i.setAction(playing ? RadioService.ACTION_STOP : RadioService.ACTION_PLAY);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
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
    }

    @Override protected void onStop() {
        super.onStop();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
