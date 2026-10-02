package io.github.rabbitwingz.cleargate;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Settings, stats and a short activity log, all in SharedPreferences. */
final class Store {
    private static final String PREFS = "cleargate";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LOG = "activity_log";
    private static final String KEY_SKIPPED = "skipped_count";
    private static final String KEY_LAST_SKIPPED = "last_skipped_at";
    private static final String KEY_ONBOARDED = "onboarded";
    private static final int LOG_SIZE = 40;

    // Activity log entry kinds.
    static final String LOG_ANSWERED = "answered";
    static final String LOG_SKIPPED = "skipped";
    static final String LOG_FALLBACK = "fallback";

    private Store() {}

    /** Package-private so the UI can listen for changes made by the service. */
    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Removes data older versions kept (per-screen log with on-screen text, the screen block list). */
    static void dropLegacyData(Context c) {
        prefs(c).edit().remove("screen_log").remove("blocked_classes").apply();
    }

    static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, true);
    }

    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_ENABLED, on).apply();
    }

    static int skippedCount(Context c) {
        return prefs(c).getInt(KEY_SKIPPED, 0);
    }

    static void incrementSkipped(Context c) {
        prefs(c).edit()
                .putInt(KEY_SKIPPED, skippedCount(c) + 1)
                .putLong(KEY_LAST_SKIPPED, System.currentTimeMillis())
                .apply();
    }

    /** Wall-clock time of the last skipped ad, or 0 if none yet. */
    static long lastSkippedAt(Context c) {
        return prefs(c).getLong(KEY_LAST_SKIPPED, 0);
    }

    static boolean isOnboarded(Context c) {
        return prefs(c).getBoolean(KEY_ONBOARDED, false);
    }

    static void setOnboarded(Context c, boolean done) {
        prefs(c).edit().putBoolean(KEY_ONBOARDED, done).apply();
    }

    /** Newest entry first. Each entry: {t: millis, kind: one of the LOG_* kinds, detail: e.g. the button you tapped}. */
    static JSONArray log(Context c) {
        try {
            return new JSONArray(prefs(c).getString(KEY_LOG, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static void addLog(Context c, String kind, String detail) {
        JSONArray old = log(c);
        JSONArray out = new JSONArray();
        try {
            JSONObject entry = new JSONObject();
            entry.put("t", System.currentTimeMillis());
            entry.put("kind", kind);
            entry.put("detail", detail == null ? "" : detail);
            out.put(entry);
            for (int i = 0; i < old.length() && out.length() < LOG_SIZE; i++) out.put(old.get(i));
        } catch (JSONException ignored) {
        }
        prefs(c).edit().putString(KEY_LOG, out.toString()).apply();
    }

    static void clearLog(Context c) {
        prefs(c).edit().remove(KEY_LOG).apply();
    }
}
