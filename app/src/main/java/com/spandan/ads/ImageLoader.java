package com.spandan.ads;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small image loader with a memory cache and a disk cache of the original files. */
final class ImageLoader {
    private static ImageLoader instance;

    private final File dir;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> mem;

    static synchronized ImageLoader get(Context ctx) {
        if (instance == null) instance = new ImageLoader(ctx.getApplicationContext());
        return instance;
    }

    private ImageLoader(Context ctx) {
        dir = new File(ctx.getCacheDir(), "img");
        dir.mkdirs();
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 6);
        mem = new LruCache<String, Bitmap>(kb) {
            @Override
            protected int sizeOf(String key, Bitmap b) {
                return b.getByteCount() / 1024;
            }
        };
    }

    /** Loads url into view, scaled down so its width is at most maxWidth pixels. */
    void load(String url, ImageView view, int maxWidth) {
        String key = url + "#" + maxWidth;
        view.setTag(key);
        Bitmap cached = mem.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        pool.execute(() -> {
            Bitmap b = null;
            try {
                File f = new File(dir, Integer.toHexString(url.hashCode()) + "_" + url.length());
                if (!f.exists()) Net.download(url, f);
                b = decode(f, maxWidth);
            } catch (Exception ignored) {
            }
            Bitmap result = b;
            if (result != null) mem.put(key, result);
            main.post(() -> {
                if (key.equals(view.getTag()) && result != null) view.setImageBitmap(result);
            });
        });
    }

    private static Bitmap decode(File f, int maxWidth) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= maxWidth) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }
}
