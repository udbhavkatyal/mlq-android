package gg.maxlevel.mlqwork;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

// Reply / Mark As Read, right from a chat's notification (like WhatsApp). The reply goes to MLQ in the background with
// the chat's reply key (it came with the notification, for this person and this chat only), so MLQ doesn't have to open.
public class MlqReplyReceiver extends BroadcastReceiver {
    static final String KEY_TEXT = "mlq_reply_text";
    static final String ACTION_REPLY = "gg.maxlevel.mlqwork.REPLY";
    static final String ACTION_READ = "gg.maxlevel.mlqwork.READ";
    private static final String FN = "https://cgzqtrtczgqkuvtiofku.supabase.co/functions/v1/mlq-push";
    private static final String KEY = "sb_publishable_mJbv-X7Y21cWvrHzEoWg2Q__U51g6u6";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        final String tag = intent.getStringExtra("tag");
        final String reply = intent.getStringExtra("reply");
        final String conv = intent.getStringExtra("conv");
        String text = "";
        if (ACTION_REPLY.equals(intent.getAction())) {
            Bundle in = RemoteInput.getResultsFromIntent(intent);
            CharSequence cs = in == null ? null : in.getCharSequence(KEY_TEXT);
            text = cs == null ? "" : cs.toString().trim();
            if (text.isEmpty()) return;
        }
        if (reply == null || tag == null) return;
        final String body = text;
        final PendingResult done = goAsync();
        new Thread(() -> {
            boolean ok = false;
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(FN).openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(15000);
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setRequestProperty("content-type", "application/json");
                con.setRequestProperty("apikey", KEY);
                con.setRequestProperty("x-reply", reply);
                JSONObject j = new JSONObject();
                j.put("text", body);
                try (OutputStream os = con.getOutputStream()) { os.write(j.toString().getBytes(StandardCharsets.UTF_8)); }
                ok = con.getResponseCode() == 200;
            } catch (Throwable ignored) {
            } finally {
                if (con != null) con.disconnect();
            }
            try {
                NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
                if (ok) nm.cancel(tag, 1);                       // sent (and read): the notification goes, as on WhatsApp
                else failed(ctx, tag, conv, body);
            } catch (Throwable ignored) {
            } finally {
                done.finish();
            }
        }).start();
    }

    // didn't go (no internet, the key expired): said plainly, with what they wrote, and a tap opens the chat
    private void failed(Context ctx, String tag, String conv, String body) {
        Uri.Builder link = Uri.parse("mlqwork://open").buildUpon();
        if (conv != null && !conv.isEmpty()) link.appendQueryParameter("conv", conv);
        Intent open = new Intent(Intent.ACTION_VIEW, link.build(), ctx, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(ctx, tag.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String what = body.isEmpty() ? "Couldn't mark it as read" : "Not sent: " + body;
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, "messages")
            .setSmallIcon(R.drawable.ic_stat_mlq)
            .setColor(0xFFE5484D)
            .setContentTitle("Couldn't Send · Tap To Open The Chat")
            .setContentText(what)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(what))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap);
        try { NotificationManagerCompat.from(ctx).notify(tag, 1, b.build()); } catch (SecurityException ignored) { }
    }
}
