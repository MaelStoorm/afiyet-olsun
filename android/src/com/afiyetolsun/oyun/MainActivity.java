package com.afiyetolsun.oyun;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String NOTIF_PERMISSION = "android.permission.POST_NOTIFICATIONS";
    private WebView web;
    private int cutL, cutT, cutR, cutB;

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
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                // gizlilik politikası gibi internet bağlantıları tarayıcıda açılsın, oyun kaybolmasın
                if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView v, String url) { pushInsets(); }
        });
        web.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                readCutout(in);
                return v.onApplyWindowInsets(in);
            }
        });
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

    /**
     * Android 15+ oyunu ekranın kenarına kadar, kamera deliğinin altına da çizer.
     * Deliğin payını oyunun CSS değişkenlerine (--sal/--sat/--sar/--sab) bildir ki düğmeler deliğin altında kalmasın.
     * DisplayCutout API 28'de geldi; derleme API 23'e karşı yapıldığı için yansıma ile okunuyor.
     */
    private void readCutout(WindowInsets in) {
        int l = 0, t = 0, r = 0, b = 0;
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                Object dc = WindowInsets.class.getMethod("getDisplayCutout").invoke(in);
                if (dc != null) {
                    Class<?> c = dc.getClass();
                    l = (Integer) c.getMethod("getSafeInsetLeft").invoke(dc);
                    t = (Integer) c.getMethod("getSafeInsetTop").invoke(dc);
                    r = (Integer) c.getMethod("getSafeInsetRight").invoke(dc);
                    b = (Integer) c.getMethod("getSafeInsetBottom").invoke(dc);
                }
            } catch (Exception e) { }
        }
        if (l != cutL || t != cutT || r != cutR || b != cutB) { cutL = l; cutT = t; cutR = r; cutB = b; pushInsets(); }
    }

    private void pushInsets() {
        if (web == null) return;
        float d = getResources().getDisplayMetrics().density;
        String js = String.format(Locale.US,
            "(function(){var s=document.documentElement.style,v={'--sal':%d,'--sat':%d,'--sar':%d,'--sab':%d};"
            + "for(var k in v){if(v[k]>0)s.setProperty(k,v[k]+'px');else s.removeProperty(k);}})()",
            Math.round(cutL / d), Math.round(cutT / d), Math.round(cutR / d), Math.round(cutB / d));
        web.evaluateJavascript(js, null);
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
