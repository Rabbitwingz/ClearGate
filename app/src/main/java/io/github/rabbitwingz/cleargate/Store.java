package io.github.rabbitwingz.cleargate;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Settings, stats and a short activity log, all in SharedPreferences. */
final class Store {
    private static final String PREFS = "cleargate";
    private static final String KEY_PAUSED = "paused_for_banking";
    private static final String KEY_TILE_ADDED = "tile_added";
    private static final String KEY_TILE_TRACKED = "tile_add_remove_tracked";
    private static final String KEY_RESUME_REQUESTED = "resume_requested_at";
    private static final String KEY_RESUME_TO_APP = "resume_returns_to_app";
    private static final String KEY_BANKS = "bank_packages";
    private static final String KEY_REMINDERS = "resume_reminders";
    private static final String KEY_WIDGET_PROMO_DISMISSED = "widget_promo_dismissed";
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

    /**
     * Removes data older versions kept: the per-screen log with on-screen text, the screen block list, and the old
     * in-app on/off switch (replaced by pausing for banking, which switches accessibility off).
     */
    static void dropLegacyData(Context c) {
        prefs(c).edit().remove("screen_log").remove("blocked_classes").remove("enabled").apply();
    }

    /** True after the user paused ClearGate for banking, until accessibility is switched back on. */
    static boolean isPaused(Context c) {
        return prefs(c).getBoolean(KEY_PAUSED, false);
    }

    static void setPaused(Context c, boolean paused) {
        prefs(c).edit().putBoolean(KEY_PAUSED, paused).apply();
    }

    /**
     * When the user last asked to resume (tile, notification, widget or app), so that when accessibility comes back on
     * the service can take them out of Settings. 0 if no resume is pending.
     */
    static long resumeRequestedAt(Context c) {
        return prefs(c).getLong(KEY_RESUME_REQUESTED, 0);
    }

    /**
     * Records a request to switch ClearGate on. {@code returnToApp}: it came from inside ClearGate (setup, status
     * card), so once it's on the user goes back to ClearGate; otherwise (tile, widget, notification) to the home
     * screen.
     */
    static void setResumeRequest(Context c, long millis, boolean returnToApp) {
        prefs(c).edit().putLong(KEY_RESUME_REQUESTED, millis).putBoolean(KEY_RESUME_TO_APP, returnToApp).apply();
    }

    static boolean resumeReturnsToApp(Context c) {
        return prefs(c).getBoolean(KEY_RESUME_TO_APP, false);
    }

    /** Whether to show the "paused · Resume" notification and the 10-minute nudge (Settings › Resume reminders). */
    static boolean remindersEnabled(Context c) {
        return prefs(c).getBoolean(KEY_REMINDERS, true);
    }

    static void setRemindersEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_REMINDERS, enabled).apply();
    }

    /** The home screen's one-time "Use banking apps? Add the widget" card was dismissed. */
    static boolean isWidgetPromoDismissed(Context c) {
        return prefs(c).getBoolean(KEY_WIDGET_PROMO_DISMISSED, false);
    }

    static void setWidgetPromoDismissed(Context c, boolean dismissed) {
        prefs(c).edit().putBoolean(KEY_WIDGET_PROMO_DISMISSED, dismissed).apply();
    }

    /** Package names of the banking apps the user picked for "Pause & open" shortcuts. */
    static Set<String> bankPackages(Context c) {
        return new HashSet<>(prefs(c).getStringSet(KEY_BANKS, new HashSet<>()));
    }

    static void setBankPackages(Context c, Set<String> packages) {
        prefs(c).edit().putStringSet(KEY_BANKS, new HashSet<>(packages)).apply();
    }

    /** Whether the pause tile is in Quick Settings, as far as we know (only Android 13+'s add prompt reports it). */
    static boolean isTileAdded(Context c) {
        return prefs(c).getBoolean(KEY_TILE_ADDED, false);
    }

    static void setTileAdded(Context c, boolean added) {
        prefs(c).edit().putBoolean(KEY_TILE_ADDED, added).apply();
    }

    /**
     * Versions before 1.35 couldn't tell when the tile was removed, so their "added" flag may be stale. Reset it once;
     * from then on the tile service tracks adds and removes (and Android's add prompt answers "already added").
     */
    static void resetStaleTileFlagOnce(Context c) {
        if (prefs(c).getBoolean(KEY_TILE_TRACKED, false)) return;
        prefs(c).edit().putBoolean(KEY_TILE_ADDED, false).putBoolean(KEY_TILE_TRACKED, true).apply();
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
