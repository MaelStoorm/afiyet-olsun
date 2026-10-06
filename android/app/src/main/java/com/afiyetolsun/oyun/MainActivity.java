package com.afiyetolsun.oyun;

import android.Manifest;
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
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView web;
    private int cutL, cutT, cutR, cutB;
    private RewardedAds ads;
    private boolean resumed;
    private String pendingAdResult;

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

    /**
     * Ödüllü reklam köprüsü (window.AndroidAds). Oyun showRewarded() der; sonuç
     * window.onAndroidAd('rewarded' | 'dismissed' | 'failed') ile oyuna geri bildirilir.
     */
    public class AdsBridge {
        @JavascriptInterface
        public void showRewarded() {
            runOnUiThread(new Runnable() { public void run() { ads.show(); } });
        }

        @JavascriptInterface
        public void cancelRewarded() {
            runOnUiThread(new Runnable() { public void run() { ads.cancel(); } });
        }

        @JavascriptInterface
        public boolean privacyOptionsRequired() {
            return ads.privacyOptionsRequired();
        }

        @JavascriptInterface
        public void showPrivacyOptions() {
            runOnUiThread(new Runnable() { public void run() { ads.showPrivacyOptions(); } });
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
        ads = new RewardedAds(this, BuildConfig.ADMOB_REWARDED_ID, new RewardedAds.ResultListener() {
            @Override
            public void onAdResult(String status) { sendAdResult(status); }
        });
        web.addJavascriptInterface(new Bridge(), "AfiyetAndroid");
        web.addJavascriptInterface(new AdsBridge(), "AndroidAds");
        setContentView(web);
        hideBars();
        if (state != null) web.restoreState(state);
        else web.loadUrl("file:///android_asset/index.html");
        // Önce reklam izni (gerekiyorsa Google'ın izin penceresi), ardından bildirim izni sorulur; iki pencere üst üste binmesin.
        ads.gatherConsent(new Runnable() {
            @Override
            public void run() { askNotificationPermission(); }
        });
    }

    /** Reklam sonucu oyuna bildirilir. Reklam ekranı kapanırken oyun henüz duraklatılmış olabilir; o zaman dönüşte iletilir. */
    private void sendAdResult(String status) {
        if (resumed) runAdResult(status);
        else pendingAdResult = status;
    }

    private void runAdResult(String status) {
        if (web == null) return;
        web.evaluateJavascript("window.onAndroidAd&&window.onAndroidAd('" + status + "')", null);
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || isFinishing()) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences p = getSharedPreferences(ReminderReceiver.PREFS, MODE_PRIVATE);
        if (p.getBoolean("asked", false)) return;
        p.edit().putBoolean("asked", true).apply();
        requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 1);
    }

    /**
     * Android 15+ oyunu ekranın kenarına kadar, kamera deliğinin altına da çizer.
     * Deliğin payını oyunun CSS değişkenlerine (--sal/--sat/--sar/--sab) bildir ki düğmeler deliğin altında kalmasın.
     */
    private void readCutout(WindowInsets in) {
        int l = 0, t = 0, r = 0, b = 0;
        if (Build.VERSION.SDK_INT >= 28) {
            DisplayCutout dc = in.getDisplayCutout();
            if (dc != null) {
                l = dc.getSafeInsetLeft();
                t = dc.getSafeInsetTop();
                r = dc.getSafeInsetRight();
                b = dc.getSafeInsetBottom();
            }
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
        resumed = false;
        web.onPause();
        web.pauseTimers();
        ReminderReceiver.schedule(this, true);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        web.resumeTimers();
        web.onResume();
        ReminderReceiver.cancel(this);
        if (pendingAdResult != null) {
            String status = pendingAdResult;
            pendingAdResult = null;
            runAdResult(status);
        }
    }

    @Override
    protected void onDestroy() {
        ads.destroy();
        web.destroy();
        web = null;
        super.onDestroy();
    }
}
