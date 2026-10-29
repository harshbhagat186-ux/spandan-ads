package com.spandan.ads;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Paid access, configured by subscription.json in the repo (price, trial, UPI ID, members).
 * After the free trial a pop-up asks for a UPI payment; Harsh activates the device's app code
 * by adding it to "members" with an expiry date (tools/activate.py).
 */
final class Subscription {
    static final String URL =
            "https://raw.githubusercontent.com/" + BuildConfig.REPO + "/main/subscription.json";
    private static final long DAY = 24L * 60 * 60 * 1000;

    boolean enabled;
    int price;
    int periodDays = 30;
    int trialDays = 7;
    String upiId = "";
    String payee = "Spandan Hospital";
    String whatsapp = "";
    String expiry = ""; // this device's paid-until date (yyyy-MM-dd), empty if not a member

    static Subscription parse(Context ctx, String json) {
        Subscription s = new Subscription();
        try {
            JSONObject o = new JSONObject(json);
            s.enabled = o.optBoolean("enabled");
            s.price = o.optInt("price");
            s.periodDays = o.optInt("period_days", 30);
            s.trialDays = o.optInt("trial_days", 7);
            s.upiId = o.optString("upi_id");
            s.payee = o.optString("payee", s.payee);
            s.whatsapp = o.optString("whatsapp");
            JSONObject members = o.optJSONObject("members");
            if (members != null) s.expiry = members.optString(code(ctx).replace("-", ""), "");
        } catch (Exception ignored) {
        }
        return s;
    }

    /** Last config fetched; unconfigured (free) until the first successful fetch. */
    static Subscription cached(Context ctx) {
        return parse(ctx, prefs(ctx).getString("config", "{}"));
    }

    static void store(Context ctx, String json) {
        prefs(ctx).edit().putString("config", json).apply();
    }

    /** Short code that identifies this phone, shown to the user and sent with the payment proof. */
    static String code(Context ctx) {
        String id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null) id = "unknown";
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(("spandan:" + id).getBytes("UTF-8"));
            String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i == 4) sb.append('-');
                sb.append(alphabet.charAt((h[i] & 0xff) % alphabet.length()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "XXXX-XXXX";
        }
    }

    int trialDaysLeft(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long first = p.getLong("first_open", 0);
        if (first == 0) {
            first = System.currentTimeMillis();
            p.edit().putLong("first_open", first).apply();
        }
        long left = trialDays - (System.currentTimeMillis() - first) / DAY;
        return (int) Math.max(0, left);
    }

    /** Days of paid access left including today, 0 if not paid or expired. */
    int paidDaysLeft() {
        if (expiry.isEmpty()) return 0;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
            long end = f.parse(expiry).getTime() + DAY;
            long left = (end - System.currentTimeMillis() + DAY - 1) / DAY;
            return (int) Math.max(0, left);
        } catch (Exception e) {
            return 0;
        }
    }

    boolean isLocked(Context ctx) {
        return enabled && paidDaysLeft() == 0 && trialDaysLeft(ctx) == 0;
    }

    /** Pop-up after the trial (blocking), or a once-a-day reminder near the end of the trial or plan. */
    void maybeShow(Activity a, Runnable onCheckAgain) {
        if (!enabled) return;
        if (isLocked(a)) {
            showPaywall(a, true, onCheckAgain);
            return;
        }
        int left = paidDaysLeft() > 0 ? paidDaysLeft() : trialDaysLeft(a);
        if (left > 3) return;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        SharedPreferences p = prefs(a);
        if (today.equals(p.getString("reminded", ""))) return;
        p.edit().putString("reminded", today).apply();
        showPaywall(a, false, onCheckAgain);
    }

    void showPaywall(Activity a, boolean blocking, Runnable onCheckAgain) {
        String code = code(a);
        int pad = dp(a, 20);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, dp(a, 8));

        TextView title = new TextView(a);
        title.setText(blocking
                ? L.t("Subscribe to keep getting daily ads", "रोज़ के ऐड पाने के लिए सब्सक्राइब करें")
                : L.t("Your access ends soon", "आपका एक्सेस जल्द खत्म होगा"));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(MainActivity.INK);
        box.addView(title);

        int left = paidDaysLeft() > 0 ? paidDaysLeft() : trialDaysLeft(a);
        TextView body = new TextView(a);
        body.setText((blocking
                ? L.t("Your free trial has ended.", "आपका फ्री ट्रायल खत्म हो गया है।")
                : L.t(left + " day(s) left.", left + " दिन बाकी हैं।"))
                + "\n\n" + L.t("5 posters + 2 reels every day, full HD, ready to post.",
                        "हर दिन 5 पोस्टर + 2 रील, फुल HD, पोस्ट करने के लिए तैयार।"));
        body.setTextSize(15);
        body.setTextColor(Color.DKGRAY);
        body.setPadding(0, dp(a, 8), 0, dp(a, 12));
        box.addView(body);

        TextView priceView = new TextView(a);
        priceView.setText(priceText() + " / " + periodText());
        priceView.setTextSize(26);
        priceView.setTypeface(Typeface.DEFAULT_BOLD);
        priceView.setTextColor(MainActivity.BRAND);
        priceView.setGravity(Gravity.CENTER);
        box.addView(priceView);

        TextView codeView = new TextView(a);
        codeView.setText(L.t("Your app code: ", "आपका ऐप कोड: ") + code
                + (upiId.isEmpty() ? "" : "\nUPI ID: " + upiId));
        codeView.setTextIsSelectable(true);
        codeView.setGravity(Gravity.CENTER);
        codeView.setPadding(0, dp(a, 6), 0, dp(a, 12));
        box.addView(codeView);

        Button pay = bigButton(a, L.t("Pay " + priceText() + " with UPI", "UPI से " + priceText() + " भरें"), MainActivity.BRAND, Color.WHITE);
        pay.setOnClickListener(v -> payUpi(a, code));
        pay.setEnabled(!upiId.isEmpty());
        box.addView(pay, buttonParams(a));

        Button proof = bigButton(a, L.t("Send payment proof on WhatsApp", "पेमेंट का सबूत WhatsApp पर भेजें"),
                Color.parseColor("#25D366"), Color.WHITE);
        proof.setOnClickListener(v -> sendProof(a, code));
        box.addView(proof, buttonParams(a));

        TextView note = new TextView(a);
        note.setText(L.t("After we check the payment, your app unlocks. Tap \"Check again\" to refresh.",
                "पेमेंट चेक होने के बाद ऐप खुल जाएगा। \"फिर से चेक करें\" दबाएँ।"));
        note.setTextSize(12);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(a, 10), 0, 0);
        box.addView(note);

        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setView(box)
                .setCancelable(!blocking)
                .setPositiveButton(L.t("Check again", "फिर से चेक करें"), (d, w) -> onCheckAgain.run());
        if (blocking) {
            b.setNegativeButton(L.t("Close app", "ऐप बंद करें"), (d, w) -> a.finish());
        } else {
            b.setNegativeButton(L.t("Later", "बाद में"), null);
        }
        b.show();
    }

    /** e.g. "₹16,000" */
    String priceText() {
        return "₹" + java.text.NumberFormat.getIntegerInstance(new Locale("en", "IN")).format(price);
    }

    /** e.g. "3 months" or "45 days" */
    String periodText() {
        if (periodDays % 30 == 0) {
            int m = periodDays / 30;
            return m == 1 ? L.t("1 month", "1 महीना") : L.t(m + " months", m + " महीने");
        }
        return L.t(periodDays + " days", periodDays + " दिन");
    }

    private void payUpi(Activity a, String code) {
        Uri uri = new Uri.Builder().scheme("upi").authority("pay")
                .appendQueryParameter("pa", upiId)
                .appendQueryParameter("pn", payee)
                .appendQueryParameter("am", price + ".00")
                .appendQueryParameter("cu", "INR")
                .appendQueryParameter("tn", "Spandan Ads " + code)
                .build();
        try {
            a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, uri),
                    L.t("Pay with", "इससे भरें")));
        } catch (ActivityNotFoundException e) {
            Saver.toast(a, L.t("No UPI app found. Pay to " + upiId, "कोई UPI ऐप नहीं मिला। " + upiId + " पर भरें"));
        }
    }

    private void sendProof(Activity a, String code) {
        String text = "Hello, I paid " + priceText() + " for Spandan Ads (" + periodText() + ").\nApp code: " + code
                + "\n(Attaching payment screenshot)";
        Uri uri = Uri.parse("https://wa.me/" + whatsapp.replaceAll("[^0-9]", "") + "?text=" + Uri.encode(text));
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Saver.toast(a, "WhatsApp: +" + whatsapp);
        }
    }

    private static Button bigButton(Context c, String text, int bg, int fg) {
        Button b = new Button(c, null, android.R.attr.borderlessButtonStyle);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(c, 24));
        b.setBackground(g);
        return b;
    }

    private static LinearLayout.LayoutParams buttonParams(Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(c, 48));
        p.topMargin = dp(c, 8);
        return p;
    }

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("subscription", Context.MODE_PRIVATE);
    }
}
