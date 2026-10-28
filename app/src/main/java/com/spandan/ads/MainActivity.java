package com.spandan.ads;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;

public class MainActivity extends BaseActivity {
    static final int BRAND = Color.parseColor("#4F7A43");
    static final int INK = Color.parseColor("#1E2B1A");

    private static final String INDEX_URL =
            "https://raw.githubusercontent.com/" + BuildConfig.REPO + "/main/index.json";

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private TextView status;
    private ProgressBar spinner;
    private TextView updateBanner;
    private Item.Index index;
    private final List<Runnable> savedRefreshers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(header());

        updateBanner = new TextView(this);
        updateBanner.setBackgroundColor(Color.parseColor("#FFF3C4"));
        updateBanner.setTextColor(INK);
        updateBanner.setPadding(dp(16), dp(10), dp(16), dp(10));
        updateBanner.setText(L.t("New version available: tap here to download (if it won't install, uninstall the old app first)",
                "नया वर्ज़न उपलब्ध है: यहाँ टैप करके डाउनलोड करें (इंस्टॉल न हो तो पुराना ऐप हटाकर इंस्टॉल करें)"));
        updateBanner.setVisibility(View.GONE);
        root.addView(updateBanner);

        spinner = new ProgressBar(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(36), dp(36));
        sp.gravity = Gravity.CENTER_HORIZONTAL;
        sp.topMargin = dp(12);
        root.addView(spinner, sp);

        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(24), dp(8), dp(24), dp(8));
        status.setTextColor(Color.DKGRAY);
        status.setVisibility(View.GONE);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(4), dp(10), dp(24));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        String cached = prefs().getString("index", null);
        if (cached != null) {
            try {
                show(Item.Index.parse(cached));
            } catch (Exception ignored) {
            }
        }
        refresh();
    }

    private View header() {
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(BRAND);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(10), dp(8), dp(10));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        bar.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, 0, 0);
        TextView t = new TextView(this);
        t.setText("Spandan Hospital");
        t.setTextColor(Color.WHITE);
        t.setTextSize(19);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        TextView s = new TextView(this);
        s.setText(L.t("Daily posters & reels • Full HD", "रोज़ के पोस्टर और रील • Full HD"));
        s.setTextColor(Color.parseColor("#DDEBD6"));
        s.setTextSize(13);
        titles.addView(t);
        titles.addView(s);
        bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        Button lang = new Button(this, null, android.R.attr.borderlessButtonStyle);
        lang.setText(L.isHindi() ? "English" : "हिंदी");
        lang.setAllCaps(false);
        lang.setTextColor(Color.WHITE);
        lang.setTextSize(14);
        GradientDrawable lb = new GradientDrawable();
        lb.setStroke(dp(1), Color.WHITE);
        lb.setCornerRadius(dp(16));
        lang.setBackground(lb);
        lang.setPadding(dp(10), 0, dp(10), 0);
        lang.setMinHeight(dp(32));
        lang.setMinimumHeight(dp(32));
        lang.setOnClickListener(v -> {
            L.toggle(this);
            recreate();
        });
        bar.addView(lang, new LinearLayout.LayoutParams(-2, dp(32)));

        Button refresh = new Button(this, null, android.R.attr.borderlessButtonStyle);
        refresh.setText("⟳");
        refresh.setTextSize(24);
        refresh.setTextColor(Color.WHITE);
        refresh.setOnClickListener(v -> refresh());
        bar.addView(refresh, new LinearLayout.LayoutParams(dp(56), dp(48)));
        return bar;
    }

    private void refresh() {
        spinner.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String json = Net.getText(INDEX_URL + "?t=" + System.currentTimeMillis() / 60000);
                Item.Index idx = Item.Index.parse(json);
                prefs().edit().putString("index", json).apply();
                main.post(() -> {
                    spinner.setVisibility(View.GONE);
                    show(idx);
                });
            } catch (Exception e) {
                main.post(() -> {
                    spinner.setVisibility(View.GONE);
                    status.setText(index == null
                            ? L.t("Couldn't load the list. Check internet and tap ⟳.", "लिस्ट लोड नहीं हो पाई। इंटरनेट चेक करें और ⟳ दबाएँ।")
                            : L.t("No internet, showing the saved list.", "इंटरनेट नहीं है, पुरानी लिस्ट दिख रही है।"));
                    status.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void show(Item.Index idx) {
        index = idx;
        list.removeAllViews();
        savedRefreshers.clear();
        if (idx.days.isEmpty()) {
            status.setText(L.t("No batches yet.", "अभी कोई बैच नहीं है।"));
            status.setVisibility(View.VISIBLE);
        }
        for (Item.Day day : idx.days) list.addView(dayCard(day));

        int mine = 0;
        try {
            mine = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception ignored) {
        }
        if (idx.appVersionCode > mine && !idx.appApkUrl.isEmpty()) {
            updateBanner.setVisibility(View.VISIBLE);
            updateBanner.setOnClickListener(v ->
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(idx.appApkUrl))));
        } else {
            updateBanner.setVisibility(View.GONE);
        }
    }

    private View dayCard(Item.Day day) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        card.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        card.setLayoutParams(lp);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView date = new TextView(this);
        date.setText(prettyDate(day.date));
        date.setTextSize(17);
        date.setTypeface(Typeface.DEFAULT_BOLD);
        date.setTextColor(INK);
        head.addView(date, new LinearLayout.LayoutParams(0, -2, 1f));
        Button all = pillButton(L.t("⬇ Save all (", "⬇ सब सेव करें (") + day.items.size() + ")");
        all.setOnClickListener(v -> saveItems(day.items));
        head.addView(all);
        card.addView(head);

        List<Item> photos = new ArrayList<>();
        List<Item> videos = new ArrayList<>();
        for (Item it : day.items) (it.isVideo() ? videos : photos).add(it);
        if (!photos.isEmpty()) {
            card.addView(sectionLabel(L.t("Posters (", "पोस्टर (") + photos.size() + ")"));
            addGrid(card, day, photos, 1350f / 1080f);
        }
        if (!videos.isEmpty()) {
            card.addView(sectionLabel(L.t("Reels (", "रील (") + videos.size() + ")"));
            addGrid(card, day, videos, 1920f / 1080f);
        }
        return card;
    }

    private void addGrid(LinearLayout card, Item.Day day, List<Item> items, float ratio) {
        int tileWidth = (getResources().getDisplayMetrics().widthPixels - dp(60)) / 2;
        for (int i = 0; i < items.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            for (int j = i; j < i + 2; j++) {
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
                p.setMargins(dp(4), dp(4), dp(4), dp(4));
                if (j < items.size()) {
                    row.addView(tile(day, items.get(j), ratio, tileWidth), p);
                } else {
                    row.addView(new View(this), p);
                }
            }
            card.addView(row);
        }
    }

    private View tile(Item.Day day, Item item, float ratio, int width) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        AspectFrame frame = new AspectFrame(this, ratio);
        GradientDrawable ph = new GradientDrawable();
        ph.setColor(Color.parseColor("#E3ECDF"));
        ph.setCornerRadius(dp(8));
        frame.setBackground(ph);
        frame.setClipToOutline(true);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(img, new FrameLayout.LayoutParams(-1, -1));
        if (!item.thumb.isEmpty()) ImageLoader.get(this).load(item.thumb, img, width);
        if (item.isVideo()) {
            TextView play = new TextView(this);
            play.setText("▶");
            play.setTextSize(26);
            play.setTextColor(Color.WHITE);
            play.setGravity(Gravity.CENTER);
            GradientDrawable c = new GradientDrawable();
            c.setShape(GradientDrawable.OVAL);
            c.setColor(Color.parseColor("#99000000"));
            play.setBackground(c);
            frame.addView(play, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        }
        frame.setOnClickListener(v -> ViewerActivity.open(this, day.items, day.items.indexOf(item)));
        box.addView(frame);

        Button save = pillButton("");
        Runnable mark = () -> save.setText(Saver.isSaved(this, item) ? L.t("✓ Saved", "✓ सेव हुआ") : L.t("⬇ Save", "⬇ सेव करें"));
        mark.run();
        savedRefreshers.add(mark);
        save.setOnClickListener(v -> {
            List<Item> one = new ArrayList<>();
            one.add(item);
            saveItems(one);
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(40));
        sp.topMargin = dp(6);
        box.addView(save, sp);
        return box;
    }

    private Button pillButton(String text) {
        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setMinHeight(dp(36));
        b.setMinimumHeight(dp(36));
        GradientDrawable g = new GradientDrawable();
        g.setColor(BRAND);
        g.setCornerRadius(dp(20));
        b.setBackground(g);
        return b;
    }

    private TextView sectionLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(BRAND);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), dp(12), 0, dp(2));
        return t;
    }

    static String prettyDate(String ymd) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
            Date d = in.parse(ymd);
            String today = in.format(new Date());
            SimpleDateFormat out = new SimpleDateFormat("d MMM yyyy, EEEE", L.isHindi() ? new Locale("hi", "IN") : Locale.ENGLISH);
            out.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
            return (ymd.equals(today) ? L.t("Today • ", "आज • ") : "") + out.format(d);
        } catch (Exception e) {
            return ymd;
        }
    }

    @Override
    protected void onSavedStateChanged() {
        for (Runnable r : savedRefreshers) r.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        onSavedStateChanged();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("main", MODE_PRIVATE);
    }

}
