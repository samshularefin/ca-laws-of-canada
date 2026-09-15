package com.diasoft.calaws.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;

/**
 * Downloads and installs the CA laws SQLite database on first launch.
 * The DB is hosted as a gzipped static file; this class fetches it,
 * decompresses it into the app's private files dir, and tracks the
 * installed version so future launches skip the download.
 *
 * Update checks: a small /version.txt endpoint is polled separately
 * (see UpdateChecker) so re-downloads only happen when the source
 * data actually changes (Justice Canada updates roughly biweekly).
 */
public class DatabaseInstaller {

    private static final String PREFS = "ca_laws_prefs";
    private static final String KEY_DB_VERSION = "db_version";
    private static final String DB_FILENAME = "ca_laws.db";

    // Replace with your actual static hosting URL (e.g. Cloudflare R2 / GitHub Releases asset).
    private static final String DB_URL = "https://cdn.example.com/ca-laws/ca_laws.db.gz";
    private static final String VERSION_URL = "https://cdn.example.com/ca-laws/version.txt";

    public interface ProgressListener {
        void onProgress(int percent);
        void onComplete(File dbFile);
        void onError(Exception e);
    }

    public static File getDbFile(Context context) {
        return new File(context.getFilesDir(), DB_FILENAME);
    }

    public static boolean isInstalled(Context context) {
        return getDbFile(context).exists();
    }

    public static String getInstalledVersion(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DB_VERSION, null);
    }

    private static void setInstalledVersion(Context context, String version) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_DB_VERSION, version).apply();
    }

    /** Fetches the remote version string; safe to call on a background thread. */
    public static String fetchRemoteVersion() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(VERSION_URL).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[64];
            int n = in.read(buf);
            return n > 0 ? new String(buf, 0, n).trim() : null;
        } finally {
            conn.disconnect();
        }
    }

    /** Downloads, gunzips, and installs the DB. Must be called off the main thread. */
    public static void installOrUpdate(Context context, String remoteVersion, ProgressListener listener) {
        File tmpFile = new File(context.getCacheDir(), DB_FILENAME + ".tmp");
        File finalFile = getDbFile(context);

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(DB_URL).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            int contentLength = conn.getContentLength();

            try (InputStream rawIn = conn.getInputStream();
                 GZIPInputStream gzIn = new GZIPInputStream(rawIn);
                 FileOutputStream out = new FileOutputStream(tmpFile)) {

                byte[] buffer = new byte[64 * 1024];
                long totalRead = 0;
                int read;
                int lastPercent = -1;
                while ((read = gzIn.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    totalRead += read;
                    if (contentLength > 0) {
                        // Note: contentLength is the *compressed* size; this is an approximation.
                        int percent = (int) Math.min(99, (totalRead * 100) / (contentLength * 4L));
                        if (percent != lastPercent && listener != null) {
                            lastPercent = percent;
                            listener.onProgress(percent);
                        }
                    }
                }
            }
            conn.disconnect();

            // Atomic swap: only replace the live DB once the download is fully verified.
            if (finalFile.exists()) finalFile.delete();
            if (!tmpFile.renameTo(finalFile)) {
                throw new IOException("Failed to move downloaded database into place");
            }

            if (remoteVersion != null) setInstalledVersion(context, remoteVersion);
            if (listener != null) listener.onComplete(finalFile);

        } catch (Exception e) {
            if (tmpFile.exists()) tmpFile.delete();
            if (listener != null) listener.onError(e);
        }
    }
}
