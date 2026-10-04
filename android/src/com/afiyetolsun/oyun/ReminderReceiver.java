package com.afiyetolsun.oyun;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.Calendar;
import java.util.Random;

/** Oyuncu oyuna dönmeyince akşamüstü hatırlatma bildirimi gösterir (en fazla 3 gün üst üste). */
public class ReminderReceiver extends BroadcastReceiver {
    static final String PREFS = "afiyet_reminder";
    private static final String CHANNEL = "hatirlatma";
    private static final int MAX_IN_A_ROW = 3;
    private static final int FLAG_IMMUTABLE = 0x04000000;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int sent = p.getInt("sent", 0);
        if (sent >= MAX_IN_A_ROW) return;
        String title = p.getString("title", "Afiyet Olsun");
        String[] msgs = p.getString("messages", "Müşteriler kapıda bekliyor, dükkanı aç usta! 🍽️").split("\\|");
        String text = msgs[new Random().nextInt(msgs.length)];
        show(ctx, title, text);
        p.edit().putInt("sent", sent + 1).apply();
        schedule(ctx, false);
    }

    static void schedule(Context ctx, boolean resetCount) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (resetCount) p.edit().putInt("sent", 0).apply();
        if (p.getBoolean("off", false)) { cancel(ctx); return; }
        if (p.getInt("sent", 0) >= MAX_IN_A_ROW) return;
        Calendar c = Calendar.getInstance();
        long now = c.getTimeInMillis();
        c.set(Calendar.HOUR_OF_DAY, 18);
        c.set(Calendar.MINUTE, 30);
        c.set(Calendar.SECOND, 0);
        // bugün en az 3 saat sonrası değilse yarına kur
        while (c.getTimeInMillis() < now + 3L * 60 * 60 * 1000) c.add(Calendar.DAY_OF_YEAR, 1);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pending(ctx));
    }

    static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(ctx));
    }

    private static PendingIntent pending(Context ctx) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        return PendingIntent.getBroadcast(ctx, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
    }

    @SuppressWarnings("deprecation")
    private static void show(Context ctx, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                // NotificationChannel API 26'da geldi; derleme API 23'e karşı yapıldığı için yansıma ile kullanılıyor
                Class<?> chCls = Class.forName("android.app.NotificationChannel");
                Object ch = chCls.getConstructor(String.class, CharSequence.class, int.class).newInstance(CHANNEL, "Hatırlatmalar", 3);
                NotificationManager.class.getMethod("createNotificationChannel", chCls).invoke(nm, ch);
                b = Notification.Builder.class.getConstructor(Context.class, String.class).newInstance(ctx, CHANNEL);
            } else {
                b = new Notification.Builder(ctx);
            }
            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, 8, open, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
            b.setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setColor(0xFF1D4FA0).setAutoCancel(true).setContentIntent(pi);
            nm.notify(1, b.build());
        } catch (Exception e) {
            // bildirim gösterilemezse oyun etkilenmesin
        }
    }
}
