package gg.maxlevel.mlqwork;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.messaging.RemoteMessage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

// MLQ's phone notifications, drawn the same way as on Windows: the person's photo for messages and mentions,
// a coloured icon for each kind of notice (review, changes, ZAP, due soon…), one notification per chat or card
// that updates in place, and a tap opens exactly that chat or card.
public class MlqMessagingService extends com.capacitorjs.plugins.pushnotifications.MessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);                 // MLQ itself (when open) hears about it too
        Map<String, String> d = message.getData();
        if (d == null || !"1".equals(d.get("mlq"))) return;
        if (MainActivity.inForeground) return;            // on screen: MLQ's own pop-up shows it
        try {
            show(d);
        } catch (Exception ignored) {
        }
    }

    private static String val(Map<String, String> d, String k, String def) {
        String v = d.get(k);
        return v == null || v.isEmpty() ? def : v;
    }

    private void channels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        String[][] all = {
            { "messages", "Messages", "Chats and mentions", "5" },
            { "cards", "Cards", "Assigned, reviews, changes, due soon", "4" },
            { "zap", "ZAP", "Super urgent priority", "5" },
            { "quiet", "Updates", "Everything else", "3" },
        };
        for (String[] c : all) {
            if (nm.getNotificationChannel(c[0]) != null) continue;
            NotificationChannel ch = new NotificationChannel(c[0], c[1], Integer.parseInt(c[3]));
            ch.setDescription(c[2]);
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
    }

    private Bitmap photo(String url) {
        if (url == null || !url.startsWith("http")) return null;
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(4000);
            con.setReadTimeout(4000);
            InputStream in = con.getInputStream();
            Bitmap src = BitmapFactory.decodeStream(in);
            in.close();
            if (src == null) return null;
            int size = Math.min(src.getWidth(), src.getHeight());
            Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - size) / 2, (src.getHeight() - size) / 2, size, size);
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new BitmapShader(sq, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            cv.drawCircle(size / 2f, size / 2f, size / 2f, p);
            return out;
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private Bitmap kindIcon(String kind) {
        int id = getResources().getIdentifier("ic_k_" + kind, "drawable", getPackageName());
        if (id == 0) id = getResources().getIdentifier("ic_k_alert", "drawable", getPackageName());
        return id == 0 ? null : BitmapFactory.decodeResource(getResources(), id);
    }

    private void show(Map<String, String> d) {
        if (Build.VERSION.SDK_INT >= 33
            && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        channels();
        String title = val(d, "title", "MLQ Work");
        String body = val(d, "body", "");
        String kind = val(d, "kind", "alert");
        String channel = val(d, "channel", "messages");
        String tag = val(d, "tag", "mlq-" + val(d, "inbox", String.valueOf(System.currentTimeMillis())));

        Uri.Builder link = Uri.parse("mlqwork://open").buildUpon();
        for (String k : new String[] { "conv", "task", "link", "inbox" }) {
            String v = d.get(k);
            if (v != null && !v.isEmpty()) link.appendQueryParameter(k, v);
        }
        Intent open = new Intent(Intent.ACTION_VIEW, link.build(), this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(this, tag.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Bitmap big = photo(d.get("avatar"));
        if (big == null) big = kindIcon(kind);

        int color = 0xFF6C4CF5;
        try { color = Color.parseColor(val(d, "color", "#6C4CF5")); } catch (Exception ignored) { }

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_stat_mlq)
            .setColor(color)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority("zap".equals(kind) ? NotificationCompat.PRIORITY_MAX : NotificationCompat.PRIORITY_HIGH)
            .setCategory("chat".equals(kind) || "mention".equals(kind) || "comment".equals(kind)
                ? NotificationCompat.CATEGORY_MESSAGE : NotificationCompat.CATEGORY_EVENT);
        if (big != null) b.setLargeIcon(big);
        try {
            int n = Integer.parseInt(val(d, "count", "0"));
            if (n > 0) b.setNumber(n);
        } catch (Exception ignored) { }

        NotificationManagerCompat.from(this).notify(tag, 1, b.build());
    }
}
