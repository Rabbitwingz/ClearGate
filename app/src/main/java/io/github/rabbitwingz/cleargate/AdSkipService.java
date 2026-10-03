package io.github.rabbitwingz.cleargate;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Gets MyGate out of the way once you've answered a visitor request.
 *
 * After you tap Approve/Deny, MyGate swaps the request screen for "Entry approved for ..." plus a full-screen ad, and
 * closing that (X or Back) opens MyGate's home screen. So instead we press Home as soon as the "Entry approved/denied"
 * screen appears: by then MyGate has confirmed your answer, and MyGate just drops into the background.
 *
 * It only acts within ARM_WINDOW_MS of seeing the request screen (or your tap on its buttons), so using MyGate
 * normally is never affected.
 */
public class AdSkipService extends AccessibilityService {
    static final String TARGET_PACKAGE = "com.mygate.user";

    private static final long ARM_WINDOW_MS = 20_000;
    private static final long DISMISS_COOLDOWN_MS = 1_200;
    private static final long CONTENT_CHECK_INTERVAL_MS = 300;
    private static final long IDLE_CHECK_INTERVAL_MS = 1_000;
    /** If MyGate is somehow still showing the result screen after Home, press Back at these times. */
    private static final long[] FALLBACK_DELAYS_MS = {800, 2_000, 3_500};
    private static final int MAX_NODES = 600;
    /** How long after tapping Resume we still take the user out of Settings once they switch ClearGate on. */
    private static final long RESUME_WINDOW_MS = 5 * 60_000;
    /** Lets Android's "Allow" dialog close before pressing Home. */
    private static final long LEAVE_SETTINGS_DELAY_MS = 700;

    /** Button labels on the gate request screen ("Approve Entry", "Deny Entry", ...). */
    private static final String[] ANSWER_WORDS = {
            "approve", "allow", "deny", "reject", "decline",
            "accept", "let in", "leave at gate", "wait at gate", "collect at gate", "send in"};

    /**
     * Text on MyGate's post-decision screen, which is where the ad sits. Visitor requests say "Entry approved for
     * <name>" (plus the ad card and "Upgrade to Premium to enjoy ... ad-free experience"); delivery pre-approvals
     * from apps like Swiggy/Instamart say "Pre-approved by <you>". Only counts within the watch window after a
     * request screen, so these phrases can't trigger anywhere else in MyGate.
     */
    private static final String[] RESULT_SCREEN_TEXTS = {
            "entry approved for", "entry denied for", "entry rejected for", "entry declined for",
            "entry allowed for", "upgrade to premium to enjoy", "ad-free experience",
            "approved by", "declined by", "denied by", "rejected by", "entry declined"};

    /** The running service, so the pause tile can switch it off (see PauseControl). Null while disabled. */
    private static AdSkipService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long armedUntil;
    private long lastDismissAt;
    private long lastContentCheck;

    static AdSkipService running() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Store.dropLegacyData(this);
        // Switched back on: no longer paused, so clear the paused notification and nudge.
        PauseControl.INSTANCE.onResumed(this);
        // If the user got here from a ClearGate button, take them out of Settings (they're a few screens deep): back to
        // ClearGate if they started in the app, else to the home screen. Only right after such a request, so a
        // reboot or update never moves anyone.
        long requested = Store.resumeRequestedAt(this);
        if (requested > 0 && System.currentTimeMillis() - requested < RESUME_WINDOW_MS) {
            boolean toApp = Store.resumeReturnsToApp(this);
            Store.setResumeRequest(this, 0, false);
            handler.postDelayed(() -> leaveSettings(toApp), LEAVE_SETTINGS_DELAY_MS);
        }
    }

    private void leaveSettings(boolean toApp) {
        if (toApp) {
            // Brings the existing ClearGate screen back as it was (an accessibility service may start activities).
            startActivity(new Intent(this, MainActivity.class).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            Toast.makeText(this, "ClearGate is on", Toast.LENGTH_SHORT).show();
        } else {
            performGlobalAction(GLOBAL_ACTION_HOME);
            Toast.makeText(this, "ClearGate is back on", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        PauseControl.INSTANCE.refreshAll(this, false);
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null || !TARGET_PACKAGE.contentEquals(event.getPackageName())) return;

        long now = SystemClock.uptimeMillis();
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                String label = eventLabel(event);
                if (containsAny(label, ANSWER_WORDS)) {
                    armedUntil = now + ARM_WINDOW_MS;
                    Store.addLog(this, Store.LOG_ANSWERED, label.replace(" button", ""));
                }
                break;

            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                checkScreen(now, false);
                break;

            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                // The request screen and the ad are often drawn after the window opens (or swapped in place),
                // so recheck on content changes: often while armed, occasionally otherwise.
                long interval = now <= armedUntil ? CONTENT_CHECK_INTERVAL_MS : IDLE_CHECK_INTERVAL_MS;
                if (now - lastContentCheck < interval) break;
                lastContentCheck = now;
                checkScreen(now, false);
                break;
        }
    }

    /**
     * Arms the watch window when the approve/deny screen is showing, and leaves MyGate when the result screen
     * appears inside that window. Returns true if the result screen is showing. {@code recheck} skips both.
     */
    private boolean checkScreen(long now, boolean recheck) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !isMyGate(root)) return false;
        Scan s = scan(root);
        // Never leave while the approve/deny buttons are showing.
        if (s.hasAnswerButton) {
            if (!recheck) armedUntil = now + ARM_WINDOW_MS;
            return false;
        }
        if (!s.isResultScreen) return false;
        if (!recheck && now <= armedUntil) leaveMyGate();
        return true;
    }

    private void leaveMyGate() {
        long now = SystemClock.uptimeMillis();
        if (now - lastDismissAt < DISMISS_COOLDOWN_MS) return;
        lastDismissAt = now;
        armedUntil = 0;
        Store.addLog(this, Store.LOG_SKIPPED, "");
        Store.incrementSkipped(this);
        performGlobalAction(GLOBAL_ACTION_HOME);

        // Normally Home is enough. If MyGate is somehow still in front showing the result screen, fall back to
        // Back, and only then, so Back never lands on another app.
        for (long delay : FALLBACK_DELAYS_MS) {
            handler.postDelayed(() -> {
                if (!checkScreen(SystemClock.uptimeMillis(), true)) return;
                Store.addLog(this, Store.LOG_FALLBACK, "");
                performGlobalAction(GLOBAL_ACTION_BACK);
            }, delay);
        }
    }

    private static boolean isMyGate(AccessibilityNodeInfo root) {
        return root.getPackageName() != null && TARGET_PACKAGE.contentEquals(root.getPackageName());
    }

    // ---- node scanning ----

    private static final class Scan {
        boolean hasAnswerButton, isResultScreen;
    }

    private Scan scan(AccessibilityNodeInfo root) {
        Scan s = new Scan();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int seen = 0;
        while (!queue.isEmpty() && seen++ < MAX_NODES) {
            AccessibilityNodeInfo n = queue.poll();
            if (n == null) continue;
            String text = norm(n.getText());
            String desc = norm(n.getContentDescription());

            if (containsAny(text, RESULT_SCREEN_TEXTS) || containsAny(desc, RESULT_SCREEN_TEXTS)) s.isResultScreen = true;
            if (n.isVisibleToUser() && (startsWithAny(text, ANSWER_WORDS) || startsWithAny(desc, ANSWER_WORDS))) {
                s.hasAnswerButton = true;
            }

            for (int i = 0; i < n.getChildCount(); i++) queue.add(n.getChild(i));
        }
        return s;
    }

    // ---- string helpers ----

    private static String eventLabel(AccessibilityEvent e) {
        StringBuilder sb = new StringBuilder();
        for (CharSequence t : e.getText()) sb.append(t).append(' ');
        if (e.getContentDescription() != null) sb.append(e.getContentDescription());
        return sb.toString().trim().toLowerCase(Locale.ROOT);
    }

    private static String norm(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String s, String[] parts) {
        if (s == null || s.isEmpty()) return false;
        for (String p : parts) if (s.contains(p)) return true;
        return false;
    }

    private static boolean startsWithAny(String s, String[] parts) {
        if (s == null || s.isEmpty()) return false;
        for (String p : parts) if (s.startsWith(p)) return true;
        return false;
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
