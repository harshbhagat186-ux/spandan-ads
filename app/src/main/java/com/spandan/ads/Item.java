package com.spandan.ads;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One photo or reel from a day's batch, as listed in index.json. */
public class Item {
    public final String date;
    public final String type; // "photo" or "video"
    public final String title;
    public final String file;
    public final String url;
    public final String thumb; // may be empty for videos without a poster frame
    public int num; // 1-based position among the day's photos or reels

    Item(String date, JSONObject o) {
        this.date = date;
        this.type = o.optString("type", "photo");
        this.file = o.optString("file");
        this.title = o.optString("title", file);
        this.url = o.optString("url");
        this.thumb = o.optString("thumb", isVideo() ? "" : url);
        this.num = o.optInt("num", 0);
    }

    /** e.g. "Poster 1 · 27 Oct" */
    public String label() {
        String kind = isVideo() ? L.t("Reel", "रील") : L.t("Poster", "पोस्टर");
        return kind + " " + num + " · " + MainActivity.shortDate(date);
    }

    public boolean isVideo() {
        return "video".equals(type);
    }

    /** Unique key used for the "already saved" marker. */
    public String key() {
        return date + "/" + file;
    }

    public static class Day {
        public final String date;
        public final List<Item> items = new ArrayList<>();

        Day(String date) {
            this.date = date;
        }
    }

    public static class Index {
        public final List<Day> days = new ArrayList<>();
        public int appVersionCode;
        public int minVersion;
        public String appApkUrl = "";

        public static Index parse(String json) throws Exception {
            JSONObject root = new JSONObject(json);
            Index idx = new Index();
            JSONArray days = root.optJSONArray("days");
            if (days != null) {
                for (int i = 0; i < days.length(); i++) {
                    JSONObject d = days.getJSONObject(i);
                    Day day = new Day(d.getString("date"));
                    JSONArray items = d.optJSONArray("items");
                    if (items != null) {
                        for (int j = 0; j < items.length(); j++) {
                            day.items.add(new Item(day.date, items.getJSONObject(j)));
                        }
                    }
                    int photos = 0, videos = 0;
                    for (Item it : day.items) it.num = it.isVideo() ? ++videos : ++photos;
                    idx.days.add(day);
                }
            }
            idx.minVersion = root.optInt("min_version");
            JSONObject app = root.optJSONObject("app");
            if (app != null) {
                idx.appVersionCode = app.optInt("versionCode");
                idx.appApkUrl = app.optString("url");
            }
            return idx;
        }
    }
}
