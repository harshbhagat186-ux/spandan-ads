package com.spandan.ads;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Full-screen view of one photo or reel of a day, with prev/next and save-to-gallery. */
public class ViewerActivity extends BaseActivity {
    private final List<Item> items = new ArrayList<>();
    private int pos;

    private FrameLayout stage;
    private TextView title;
    private TextView counter;
    private Button save;

    static void open(Context ctx, List<Item> dayItems, int position) {
        JSONArray arr = new JSONArray();
        try {
            for (Item it : dayItems) {
                arr.put(new JSONObject()
                        .put("type", it.type).put("title", it.title).put("file", it.file)
                        .put("url", it.url).put("thumb", it.thumb).put("date", it.date));
            }
        } catch (Exception ignored) {
        }
        ctx.startActivity(new Intent(ctx, ViewerActivity.class)
                .putExtra("items", arr.toString())
                .putExtra("pos", position));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            JSONArray arr = new JSONArray(getIntent().getStringExtra("items"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                items.add(new Item(o.getString("date"), o));
            }
        } catch (Exception e) {
            finish();
            return;
        }
        pos = state != null ? state.getInt("pos") : getIntent().getIntExtra("pos", 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4), dp(4), dp(4), dp(4));
        Button close = textButton("✕");
        close.setOnClickListener(v -> finish());
        top.addView(close);
        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button prev = textButton("‹");
        prev.setOnClickListener(v -> go(-1));
        counter = new TextView(this);
        counter.setTextColor(Color.LTGRAY);
        Button next = textButton("›");
        next.setOnClickListener(v -> go(1));
        top.addView(prev);
        top.addView(counter);
        top.addView(next);
        root.addView(top);

        stage = new FrameLayout(this);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        save = new Button(this, null, android.R.attr.borderlessButtonStyle);
        save.setAllCaps(false);
        save.setTextColor(Color.WHITE);
        save.setTextSize(16);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(MainActivity.BRAND);
        g.setCornerRadius(dp(26));
        save.setBackground(g);
        save.setOnClickListener(v -> {
            List<Item> one = new ArrayList<>();
            one.add(items.get(pos));
            saveItems(one);
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(52));
        sp.setMargins(dp(16), dp(10), dp(16), dp(16));
        root.addView(save, sp);

        setContentView(root);
        render();
    }

    private Button textButton(String t) {
        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(t);
        b.setTextSize(22);
        b.setTextColor(Color.WHITE);
        b.setMinWidth(dp(48));
        b.setMinimumWidth(dp(48));
        return b;
    }

    private void go(int delta) {
        int n = pos + delta;
        if (n < 0 || n >= items.size()) return;
        pos = n;
        render();
    }

    private void render() {
        Item it = items.get(pos);
        title.setText(it.title);
        counter.setText((pos + 1) + "/" + items.size());
        onSavedStateChanged();
        stage.removeAllViews();

        ProgressBar spin = new ProgressBar(this);
        stage.addView(spin, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));

        if (it.isVideo()) {
            VideoView video = new VideoView(this);
            MediaController mc = new MediaController(this);
            mc.setAnchorView(video);
            video.setMediaController(mc);
            video.setOnPreparedListener(mp -> {
                spin.setVisibility(View.GONE);
                mp.setLooping(true);
                video.start();
            });
            video.setOnErrorListener((mp, what, extra) -> {
                spin.setVisibility(View.GONE);
                Saver.toast(this, "वीडियो नहीं चल पाया, इंटरनेट चेक करें");
                return true;
            });
            video.setVideoURI(Uri.parse(it.url));
            stage.addView(video, 0, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        } else {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            // Drawn above the spinner, so the loaded poster covers it.
            stage.addView(img, new FrameLayout.LayoutParams(-1, -1));
            ImageLoader.get(this).load(it.url, img, 1080);
        }
    }

    @Override
    protected void onSavedStateChanged() {
        if (save == null || items.isEmpty()) return;
        Item it = items.get(pos);
        save.setText((Saver.isSaved(this, it) ? "✓ सेव हुआ • फिर से सेव करें" : "⬇ गैलरी में सेव करें")
                + (it.isVideo() ? "  (1080×1920)" : "  (1080×1350)"));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("pos", pos);
    }
}
