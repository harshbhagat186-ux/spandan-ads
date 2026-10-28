package com.spandan.ads;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

/**
 * Saves the original full-HD file straight into the phone gallery
 * (Pictures/Spandan for photos, Movies/Spandan for reels) via the system DownloadManager.
 */
final class Saver {
    static final String FOLDER = "Spandan";
    private static final String PREFS = "saved";

    private Saver() {}

    static long save(Context ctx, Item item) {
        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(item.url));
        r.setTitle(item.title);
        r.setDescription("Spandan Hospital " + item.date);
        r.setMimeType(item.isVideo() ? "video/mp4" : mimeFor(item.file));
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        String dir = item.isVideo() ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES;
        r.setDestinationInExternalPublicDir(dir, FOLDER + "/" + item.date + "_" + item.file);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            //noinspection deprecation
            r.allowScanningByMediaScanner();
        }
        long id = dm.enqueue(r);
        prefs(ctx).edit().putBoolean(item.key(), true).apply();
        return id;
    }

    static boolean isSaved(Context ctx, Item item) {
        return prefs(ctx).getBoolean(item.key(), false);
    }

    static void toast(Context ctx, String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }

    private static String mimeFor(String file) {
        String f = file.toLowerCase();
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".webp")) return "image/webp";
        return "image/png";
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
