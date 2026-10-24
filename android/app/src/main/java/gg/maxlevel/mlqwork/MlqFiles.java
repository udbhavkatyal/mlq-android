package gg.maxlevel.mlqwork;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import java.io.File;
import android.webkit.URLUtil;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Iterator;

// Capacitor.Plugins.MlqFiles.download({ url, name, headers }): saves a file to the phone's Downloads with Android's own
// download notification (progress, then tap to open). Headers carry the sign-in Drive / MLQ files need.
@CapacitorPlugin(name = "MlqFiles")
public class MlqFiles extends Plugin {
    @PluginMethod
    public void download(PluginCall call) {
        try {
            String url = call.getString("url");
            if (url == null || !url.startsWith("https://")) { call.reject("Not a file link"); return; }
            String name = call.getString("name");
            String file = (name == null || name.trim().isEmpty()) ? URLUtil.guessFileName(url, null, null) : name.trim();
            file = file.replaceAll("[\\\\/:*?\"<>|]", "_");
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            JSObject h = call.getObject("headers");
            if (h != null) for (Iterator<String> it = h.keys(); it.hasNext(); ) {
                String k = it.next();
                String v = h.getString(k);
                if (v != null) r.addRequestHeader(k, v);
            }
            r.setTitle(file);
            r.setDescription("MLQ Work");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file);
            DownloadManager dm = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) { call.reject("Downloads aren't available"); return; }
            long id = dm.enqueue(r);
            JSObject ret = new JSObject();
            ret.put("id", id);
            ret.put("name", file);
            call.resolve(ret);
        } catch (Throwable e) {
            call.reject("Couldn't start the download");
        }
    }

    private static long idOf(PluginCall call) {
        Object v = call.getData().opt("id");
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return -1L; }
    }

    // ---------- a new version of the app: downloaded inside MLQ, then Android's installer opens on it
    @PluginMethod
    public void startUpdate(PluginCall call) {
        try {
            String url = call.getString("url");
            if (url == null || !url.startsWith("https://")) { call.reject("Not an app link"); return; }
            File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null) { File[] old = dir.listFiles(); if (old != null) for (File f : old) if (f.getName().endsWith(".apk")) f.delete(); }
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setTitle("MLQ Work update");
            r.setMimeType("application/vnd.android.package-archive");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            r.setDestinationInExternalFilesDir(getContext(), Environment.DIRECTORY_DOWNLOADS, "MLQ-Work-update.apk");
            DownloadManager dm = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
            JSObject ret = new JSObject();
            ret.put("id", dm.enqueue(r));
            call.resolve(ret);
        } catch (Throwable e) { call.reject("Couldn't start the download"); }
    }

    @PluginMethod
    public void updateStatus(PluginCall call) {
        JSObject ret = new JSObject();
        try {
            long id = idOf(call);
            DownloadManager dm = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
            Cursor c = dm.query(new DownloadManager.Query().setFilterById(id));
            if (c == null || !c.moveToFirst()) { ret.put("status", "failed"); call.resolve(ret); return; }
            int st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            ret.put("done", c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
            ret.put("total", c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)));
            ret.put("status", st == DownloadManager.STATUS_SUCCESSFUL ? "done" : st == DownloadManager.STATUS_FAILED ? "failed" : "running");
            c.close();
        } catch (Throwable e) { ret.put("status", "failed"); }
        call.resolve(ret);
    }

    @PluginMethod
    public void installUpdate(PluginCall call) {
        try {
            JSObject ret = new JSObject();
            // Android asks once: "allow MLQ Work to install apps"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getContext().getPackageManager().canRequestPackageInstalls()) {
                Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
                s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(s);
                ret.put("needsPermission", true);
                call.resolve(ret);
                return;
            }
            long id = idOf(call);
            DownloadManager dm = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
            Uri apk = dm.getUriForDownloadedFile(id);
            if (apk == null) { call.reject("The download isn't there any more"); return; }
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(apk, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            ret.put("opened", true);
            call.resolve(ret);
        } catch (Throwable e) { call.reject("Couldn't open the installer"); }
    }
}
