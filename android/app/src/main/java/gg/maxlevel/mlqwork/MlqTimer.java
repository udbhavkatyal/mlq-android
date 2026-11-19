package gg.maxlevel.mlqwork;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONObject;

// The Workday timer on the phone: one ongoing notification. The header runs this session's clock; the content shows the
// task and today's hours (a live clock while it runs, "of 6h"), with Pause / Resume. Drawn from the state MLQ sends
// whenever the timer changes anywhere (timer = on / paused / off). Admin only; nobody else gets these.
public class MlqTimer {
    static final String CHANNEL = "workday";
    static final String TAG = "workday";
    static final int ID = 7;

    private static String val(Map<String, String> d, String k) {
        String v = d.get(k);
        return v == null ? "" : v;
    }

    private static long num(Map<String, String> d, String k) {
        try { return Long.parseLong(val(d, k)); } catch (NumberFormatException e) { return 0; }
    }

    // "3h 05m" / "45m"
    static String hm(long ms) {
        long m = Math.max(0, ms) / 60000, h = m / 60;
        return h > 0 ? h + "h " + (m % 60 < 10 ? "0" : "") + (m % 60) + "m" : (m % 60) + "m";
    }

    // "6h" / "6½h" / "7h 10m"
    static String plan(long ms) {
        long m = Math.round(ms / 60000.0), h = m / 60, r = m % 60;
        if (r == 0) return h + "h";
        if (r == 30) return h + "½h";
        return hm(ms);
    }

    static void channel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Workday Timer", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("The running timer, with Pause and Resume");
        ch.setShowBadge(false);
        ch.enableVibration(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }

    static void render(Context ctx, Map<String, String> d) { render(ctx, d, false); }

    // the last state shown (for Pause / Resume to flip straight away, before MLQ answers)
    static Map<String, String> last(Context ctx) {
        Map<String, String> m = new HashMap<>();
        try {
            JSONObject j = new JSONObject(ctx.getSharedPreferences("mlq_timer", Context.MODE_PRIVATE).getString("last", "{}"));
            for (Iterator<String> it = j.keys(); it.hasNext(); ) { String k = it.next(); m.put(k, j.optString(k, "")); }
        } catch (Throwable ignored) { }
        return m;
    }

    static synchronized void render(Context ctx, Map<String, String> d, boolean instant) {
        // (messages can arrive out of order: an older state never replaces a newer one)
        long at = num(d, "at");
        SharedPreferences sp = ctx.getSharedPreferences("mlq_timer", Context.MODE_PRIVATE);
        if (!instant) {
            if (at > 0 && at < sp.getLong("at", 0)) return;
            try { sp.edit().putLong("at", at).putString("last", new JSONObject(d).toString()).apply(); } catch (Throwable ignored) { }
        }

        String state = val(d, "timer");
        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
        if (!"on".equals(state) && !"paused".equals(state)) { nm.cancel(TAG, ID); return; }
        channel(ctx);
        String task = val(d, "task").isEmpty() ? "Just Working" : val(d, "task");
        String type = val(d, "type");
        String key = val(d, "key");
        boolean on = "on".equals(state);
        long now = System.currentTimeMillis();
        long planMs = num(d, "plan");
        long worked = num(d, "worked") + (on && at > 0 ? Math.max(0, now - at) : 0);   // (as of now, on this phone's clock)
        String of = planMs > 0 ? " of " + plan(planMs) : "";
        String tail = of + (type.isEmpty() ? "" : " · " + type);

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.mlq_timer);
        v.setTextViewText(R.id.mt_task, task);
        String flat;
        if (on) {
            v.setTextViewText(R.id.mt_lbl, "Today ");
            v.setViewVisibility(R.id.mt_today, View.VISIBLE);
            v.setChronometer(R.id.mt_today, SystemClock.elapsedRealtime() - worked, null, true);
            v.setTextViewText(R.id.mt_rest, tail);
            flat = "Today " + hm(worked) + tail;
        } else {
            v.setViewVisibility(R.id.mt_today, View.GONE);
            v.setTextViewText(R.id.mt_lbl, "Paused · " + hm(worked) + (planMs > 0 ? of : " today"));
            v.setTextViewText(R.id.mt_rest, type.isEmpty() ? "" : " · " + type);
            flat = "Paused · " + hm(worked) + tail;
        }

        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse("mlqwork://open").buildUpon().appendQueryParameter("link", "#/mytime").build(), ctx, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(ctx, 7001, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mlq)
            .setColor(on ? 0xFF1F9D63 : 0xFF8A91A3)
            .setContentTitle(task)                         // (what shows where custom content can't: lock screen, watch)
            .setContentText(flat)
            .setCustomContentView(v)
            .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
            .setCategory("stopwatch")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(tap);
        long started = num(d, "started");
        if (on) {
            b.setOngoing(true).setAutoCancel(false);
            if (started > 0) b.setWhen(started).setShowWhen(true).setUsesChronometer(true);   // this session's clock
        } else {
            b.setOngoing(false).setAutoCancel(true).setShowWhen(false);
        }
        if (!key.isEmpty()) {
            String act = on ? MlqTimerReceiver.PAUSE : MlqTimerReceiver.RESUME;
            Intent i = new Intent(ctx, MlqTimerReceiver.class).setAction(act).putExtra("key", key);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, on ? 7002 : 7003, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(0, on ? "Pause" : "Resume", pi);
        }
        try { nm.notify(TAG, ID, b.build()); } catch (SecurityException ignored) { }
    }
}
