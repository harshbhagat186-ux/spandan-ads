package com.spandan.ads;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.util.TypedValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared save-to-gallery handling: storage permission on old Android, completion toasts. */
public abstract class BaseActivity extends Activity {
    private static final int REQ_STORAGE = 7;

    private final List<Item> pending = new ArrayList<>();
    private final Set<Long> ours = new HashSet<>();

    private final BroadcastReceiver done = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (!ours.remove(id)) return;
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            boolean ok = false;
            try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(id))) {
                if (c != null && c.moveToFirst()) {
                    int col = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    ok = col >= 0 && c.getInt(col) == DownloadManager.STATUS_SUCCESSFUL;
                }
            }
            Saver.toast(BaseActivity.this, ok ? L.t("✓ Saved to gallery", "✓ गैलरी में सेव हो गया")
                    : L.t("Download failed, please try again", "डाउनलोड नहीं हो पाया, फिर से कोशिश करें"));
            onSavedStateChanged();
        }
    };

    @Override
    protected void onCreate(android.os.Bundle state) {
        L.load(this);
        super.onCreate(state);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(done, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(done, f);
        }
    }

    @Override
    protected void onStop() {
        unregisterReceiver(done);
        super.onStop();
    }

    protected void saveItems(List<Item> items) {
        if (items.isEmpty()) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pending.clear();
            pending.addAll(items);
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        try {
            for (Item it : items) ours.add(Saver.save(this, it));
            Saver.toast(this, items.size() == 1 ? L.t("Downloading…", "डाउनलोड शुरू…")
                    : L.t("Downloading " + items.size() + " files…", items.size() + " फाइलें डाउनलोड हो रही हैं…"));
        } catch (Exception e) {
            Saver.toast(this, L.t("Could not save: ", "सेव नहीं हो पाया: ") + e.getMessage());
        }
        onSavedStateChanged();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != REQ_STORAGE) return;
        List<Item> items = new ArrayList<>(pending);
        pending.clear();
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            saveItems(items);
        } else {
            Saver.toast(this, L.t("Allow Storage permission to save to the gallery", "गैलरी में सेव करने के लिए Storage की अनुमति दें"));
        }
    }

    /** Called when the saved markers may have changed. */
    protected void onSavedStateChanged() {}

    protected int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
