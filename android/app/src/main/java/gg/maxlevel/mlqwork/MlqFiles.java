package gg.maxlevel.mlqwork;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
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
}
