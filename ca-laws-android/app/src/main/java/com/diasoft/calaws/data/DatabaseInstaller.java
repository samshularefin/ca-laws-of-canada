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

    // "latest" always redirects to the most recent release's asset, so future
    // updates just need a new release — no app-side URL change required.
    private static final String BASE_URL = "https://github.com/samshularefin/ca-laws-of-canada/releases/latest/download/";
    private static final String VERSION_URL = BASE_URL + "version.txt";

    public interface ProgressListener {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        void onComplete(File dbFile);
        void onError(Exception e);
    }

    // English keeps the original file name and version key so existing installs are untouched.
    private static String dbFilename(String lang) {
        return "fr".equals(lang) ? "ca_laws_fr.db" : "ca_laws.db";
    }

    private static String versionKey(String lang) {
        return "fr".equals(lang) ? "db_version_fr" : "db_version";
    }

    public static File getDbFile(Context context) {
        return new File(context.getFilesDir(), dbFilename(Prefs.getLang(context)));
    }

    public static boolean isInstalled(Context context) {
        return getDbFile(context).exists();
    }

    public static String getInstalledVersion(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(versionKey(Prefs.getLang(context)), null);
    }

    private static void setInstalledVersion(Context context, String lang, String version) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(versionKey(lang), version).apply();
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
        final String lang = Prefs.getLang(context);
        File tmpFile = new File(context.getCacheDir(), dbFilename(lang) + ".tmp");
        File finalFile = new File(context.getFilesDir(), dbFilename(lang));

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + dbFilename(lang) + ".gz").openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            int contentLength = conn.getContentLength();

            final long[] downloaded = {0};
            try (InputStream rawIn = new java.io.FilterInputStream(conn.getInputStream()) {
                     @Override public int read(byte[] b, int off, int len) throws IOException {
                         int n = super.read(b, off, len);
                         if (n > 0) downloaded[0] += n;
                         return n;
                     }
                 };
                 GZIPInputStream gzIn = new GZIPInputStream(rawIn);
                 FileOutputStream out = new FileOutputStream(tmpFile)) {

                byte[] buffer = new byte[64 * 1024];
                int read;
                int lastPercent = -1;
                while ((read = gzIn.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (contentLength > 0 && listener != null) {
                        int percent = (int) Math.min(99, (downloaded[0] * 100) / contentLength);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            listener.onProgress(percent, downloaded[0], contentLength);
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

            if (remoteVersion != null) setInstalledVersion(context, lang, remoteVersion);
            if (listener != null) listener.onComplete(finalFile);

        } catch (Exception e) {
            if (tmpFile.exists()) tmpFile.delete();
            if (listener != null) listener.onError(e);
        }
    }
}
