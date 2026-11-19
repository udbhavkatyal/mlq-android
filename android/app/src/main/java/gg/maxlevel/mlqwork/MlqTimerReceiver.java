package gg.maxlevel.mlqwork;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONObject;

// Pause / Resume from the Workday notification: sent to MLQ in the background with the timer key that came with it,
// then the notification redraws from the answer (the other devices hear about it too).
public class MlqTimerReceiver extends BroadcastReceiver {
    static final String PAUSE = "gg.maxlevel.mlqwork.TIMER_PAUSE";
    static final String RESUME = "gg.maxlevel.mlqwork.TIMER_RESUME";
    private static final String FN = "https://cgzqtrtczgqkuvtiofku.supabase.co/functions/v1/mlq-push";
    private static final String KEY = "sb_publishable_mJbv-X7Y21cWvrHzEoWg2Q__U51g6u6";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        final String key = intent.getStringExtra("key");
        final String action = PAUSE.equals(intent.getAction()) ? "pause" : RESUME.equals(intent.getAction()) ? "resume" : null;
        if (key == null || action == null) return;
        final PendingResult done = goAsync();
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(FN).openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(15000);
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setRequestProperty("content-type", "application/json");
                con.setRequestProperty("apikey", KEY);
                con.setRequestProperty("x-timer", key);
                JSONObject j = new JSONObject();
                j.put("action", action);
                try (OutputStream os = con.getOutputStream()) { os.write(j.toString().getBytes(StandardCharsets.UTF_8)); }
                if (con.getResponseCode() == 200) {
                    byte[] buf;
                    try (InputStream in = con.getInputStream()) { buf = readAll(in); }
                    JSONObject r = new JSONObject(new String(buf, StandardCharsets.UTF_8));
                    Map<String, String> d = new HashMap<>();
                    for (Iterator<String> it = r.keys(); it.hasNext(); ) { String k = it.next(); d.put(k, r.optString(k, "")); }
                    d.put("key", key);
                    MlqTimer.render(ctx, d);
                }
            } catch (Throwable ignored) {
            } finally {
                if (con != null) con.disconnect();
                done.finish();
            }
        }).start();
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        return out.toByteArray();
    }
}
