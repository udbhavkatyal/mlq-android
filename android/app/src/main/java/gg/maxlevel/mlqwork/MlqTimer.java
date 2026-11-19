package gg.maxlevel.mlqwork;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.util.Map;

// The Workday timer on the phone: one ongoing notification with the running clock and the task, and Pause / Resume on it.
// Drawn from the state MLQ sends (a data message: timer = on / paused / off). Admin only; nobody else gets these.
public class MlqTimer {
    static final String CHANNEL = "workday";
    static final String TAG = "workday";
    static final int ID = 7;

    private static String val(Map<String, String> d, String k) {
        String v = d.get(k);
        return v == null ? "" : v;
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

    static void render(Context ctx, Map<String, String> d) {
        String state = val(d, "timer");
        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
        if (!"on".equals(state) && !"paused".equals(state)) { nm.cancel(TAG, ID); return; }
        channel(ctx);
        String task = val(d, "task").isEmpty() ? "Just Working" : val(d, "task");
        String type = val(d, "type");
        String key = val(d, "key");
        boolean on = "on".equals(state);

        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse("mlqwork://open").buildUpon().appendQueryParameter("link", "#/mytime").build(), ctx, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(ctx, 7001, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mlq)
            .setColor(on ? 0xFF1F9D63 : 0xFF8A91A3)
            .setContentTitle(task)
            .setContentText(on ? (type.isEmpty() ? "Workday" : type) : "Paused" + (type.isEmpty() ? "" : " · " + type))
            .setCategory("stopwatch")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(tap);
        if (on) {
            long started = 0;
            try { started = Long.parseLong(val(d, "started")); } catch (NumberFormatException ignored) { }
            b.setOngoing(true).setAutoCancel(false);
            if (started > 0) b.setWhen(started).setShowWhen(true).setUsesChronometer(true);
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
