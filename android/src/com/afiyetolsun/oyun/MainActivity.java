package com.afiyetolsun.oyun;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final String NOTIF_PERMISSION = "android.permission.POST_NOTIFICATIONS";
    private WebView web;

    /** Oyunun JavaScript tarafından çağrılır (window.AfiyetAndroid). */
    public class Bridge {
        @JavascriptInterface
        public void setReminder(String title, String messages) {
            getSharedPreferences(ReminderReceiver.PREFS, MODE_PRIVATE).edit()
                .putString("title", title).putString("messages", messages).apply();
        }

        @JavascriptInterface
        public void setReminderOn(boolean on) {
            getSharedPreferences(ReminderReceiver.PREFS, MODE_PRIVATE).edit().putBoolean("off", !on).apply();
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            try {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) v.vibrate(Math.max(5, Math.min(ms, 300)));
            } catch (Exception e) { }
        }

        @JavascriptInterface
        public void setOrientation(final String mode) {
            runOnUiThread(new Runnable() {
                public void run() {
                    int o = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
                    if ("portrait".equals(mode)) o = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
                    else if ("landscape".equals(mode)) o = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
                    setRequestedOrientation(o);
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.parseColor("#6f421d"));
        getWindow().setNavigationBarColor(Color.parseColor("#f2dcc0"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#f2dcc0"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AfiyetAndroid");
        setContentView(web);
        hideBars();
        if (state != null) web.restoreState(state);
        else web.loadUrl("file:///android_asset/index.html");
        askNotificationPermission();
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(NOTIF_PERMISSION) == PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences p = getSharedPreferences(ReminderReceiver.PREFS, MODE_PRIVATE);
        if (p.getBoolean("asked", false)) return;
        p.edit().putBoolean("asked", true).apply();
        requestPermissions(new String[] { NOTIF_PERMISSION }, 1);
    }

    /** Oyun tam ekran: üst çubuk ve alt tuşlar gizli, kenardan kaydırınca kısa süre görünür. */
    @SuppressWarnings("deprecation")
    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        web.onPause();
        web.pauseTimers();
        ReminderReceiver.schedule(this, true);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.resumeTimers();
        web.onResume();
        ReminderReceiver.cancel(this);
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
