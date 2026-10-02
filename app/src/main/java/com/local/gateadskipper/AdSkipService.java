package com.local.gateadskipper;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Gets MyGate out of the way once you've answered a visitor request.
 *
 * After you tap Approve/Deny, MyGate swaps the request screen for "Entry approved for ..." plus a full-screen ad, and
 * closing that (X or Back) opens MyGate's home screen. So instead we press Home as soon as the "Entry approved/denied"
 * screen appears: by then MyGate has confirmed your answer, and MyGate just drops into the background.
 *
 * Also handled the same way: known ad-SDK screens, screens you blocked in the app, and screens labelled as ads
 * that appear within ARM_WINDOW_MS of the request screen.
 */
public class AdSkipService extends AccessibilityService {
    static final String TARGET_PACKAGE = "com.mygate.user";

    private static final long ARM_WINDOW_MS = 20_000;
    private static final long DISMISS_COOLDOWN_MS = 1_200;
    private static final long CONTENT_CHECK_INTERVAL_MS = 300;
    private static final long IDLE_CHECK_INTERVAL_MS = 1_000;
    /** If MyGate is somehow still showing the ad after Home, press Back at these times. */
    private static final long[] FALLBACK_DELAYS_MS = {800, 2_000, 3_500};
    private static final int MAX_NODES = 600;

    static final Set<String> KNOWN_AD_ACTIVITIES = new HashSet<>(Arrays.asList(
            "com.google.android.gms.ads.AdActivity",
            "com.google.android.gms.ads.OutOfContextTestingActivity",
            "com.facebook.ads.AudienceNetworkActivity",
            "com.facebook.ads.internal.ipc.RemoteANActivity",
            "com.applovin.adview.AppLovinFullscreenActivity",
            "com.applovin.adview.AppLovinInterstitialActivity",
            "com.inmobi.ads.rendering.InMobiAdActivity",
            "com.inmobi.rendering.InMobiAdActivity",
            "com.unity3d.services.ads.adunit.AdUnitActivity",
            "com.ironsource.sdk.controller.ControllerActivity",
            "com.vungle.warren.AdActivity",
            "com.mbridge.msdk.activity.MBCommonActivity"));

    /** Button labels on the gate request screen ("Approve Entry", "Deny Entry", ...). */
    private static final String[] ANSWER_WORDS = {
            "approve", "allow", "deny", "reject", "decline",
            "accept", "let in", "leave at gate", "wait at gate", "collect at gate", "send in"};

    /**
     * Text on MyGate's post-decision screen, which is where the ad sits
     * ("Entry approved for <name>", the ad card, "Upgrade to Premium to enjoy ... ad-free experience").
     */
    private static final String[] RESULT_SCREEN_TEXTS = {
            "entry approved for", "entry denied for", "entry rejected for", "entry declined for",
            "entry allowed for", "upgrade to premium to enjoy", "ad-free experience"};

    /** Exact (case-insensitive) texts that label an ad. */
    private static final Set<String> AD_LABELS = new HashSet<>(Arrays.asList(
            "ad", "ads", "sponsored", "advertisement", "promoted", "promotion", "ad •", "• ad"));

    private static final String[] AD_ID_PARTS = {"interstitial", "ad_view", "adview", "ad_container",
            "ad_image", "ad_banner", "native_ad", "sponsor", "promo", "ad_close", "adclose", "advertimage"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long armedUntil;
    private long lastDismissAt;
    private long lastContentCheck;
    private String currentClass;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null || !TARGET_PACKAGE.contentEquals(event.getPackageName())) return;
        if (!Store.isEnabled(this)) return;

        long now = SystemClock.uptimeMillis();
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                String label = eventLabel(event);
                if (containsAny(label, ANSWER_WORDS)) {
                    armedUntil = now + ARM_WINDOW_MS;
                    Store.addLog(this, currentClass, "tapped: " + label, "watching for the ad");
                }
                break;

            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                String cls = event.getClassName() == null ? "" : event.getClassName().toString();
                currentClass = cls;
                AccessibilityNodeInfo root = getRootInActiveWindow();
                String reason = adReason(cls, root, now, false);
                if (reason != null) leaveMyGate(cls, root, reason);
                else Store.addLog(this, cls, summarize(root), "");
                break;

            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                // The request screen and the ad are often drawn after the window opens (or swapped in place),
                // so recheck on content changes: often while armed, occasionally otherwise.
                long interval = now <= armedUntil ? CONTENT_CHECK_INTERVAL_MS : IDLE_CHECK_INTERVAL_MS;
                if (now - lastContentCheck < interval) break;
                lastContentCheck = now;
                AccessibilityNodeInfo r = getRootInActiveWindow();
                String why = adReason(currentClass, r, now, false);
                if (why != null) leaveMyGate(currentClass, r, why);
                break;
        }
    }

    /**
     * Why this screen is an ad, or null if it isn't. Seeing the approve/deny screen arms the watch window,
     * because MyGate swaps it for the ad screen after you answer. {@code recheck} checks without re-arming.
     */
    private String adReason(String cls, AccessibilityNodeInfo root, long now, boolean recheck) {
        if (cls != null && KNOWN_AD_ACTIVITIES.contains(cls)) return "known ad SDK screen";
        if (root == null || !isMyGate(root)) return null;
        Scan s = scan(root);
        // Never leave while the approve/deny buttons are showing, even if this screen is on the block list.
        if (s.hasAnswerButton) {
            if (!recheck) armedUntil = now + ARM_WINDOW_MS;
            return null;
        }
        if (cls != null && Store.blockedClasses(this).contains(cls)) return "on your block list";
        if (!recheck && now > armedUntil) return null;
        if (s.isResultScreen) return "entry approved/denied screen with ad";
        if (s.hasAdLabel) return "labelled as an ad after approve/deny";
        if (s.hasAdId) return "ad view after approve/deny";
        return null;
    }

    private void leaveMyGate(String cls, AccessibilityNodeInfo root, String reason) {
        long now = SystemClock.uptimeMillis();
        if (now - lastDismissAt < DISMISS_COOLDOWN_MS) return;
        lastDismissAt = now;
        armedUntil = 0;
        Store.addLog(this, cls, summarize(root), "AD: " + reason + " → pressed Home");
        Store.incrementSkipped(this);
        performGlobalAction(GLOBAL_ACTION_HOME);

        // Normally Home is enough. If MyGate is somehow still in front showing the ad, fall back to Back,
        // and only then, so Back never lands on another app.
        for (long delay : FALLBACK_DELAYS_MS) {
            handler.postDelayed(() -> {
                AccessibilityNodeInfo r = getRootInActiveWindow();
                if (adReason(currentClass, r, SystemClock.uptimeMillis(), true) == null) return;
                Store.addLog(this, currentClass, "", "still showing after Home → pressed Back");
                performGlobalAction(GLOBAL_ACTION_BACK);
            }, delay);
        }
    }

    private static boolean isMyGate(AccessibilityNodeInfo root) {
        return root.getPackageName() != null && TARGET_PACKAGE.contentEquals(root.getPackageName());
    }

    // ---- node scanning ----

    private static final class Scan {
        boolean hasAdLabel, hasAdId, hasAnswerButton, isResultScreen;
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
            String id = shortId(n.getViewIdResourceName());

            if (AD_LABELS.contains(text) || AD_LABELS.contains(desc)) s.hasAdLabel = true;
            if (containsAny(id, AD_ID_PARTS)) s.hasAdId = true;
            if (containsAny(text, RESULT_SCREEN_TEXTS) || containsAny(desc, RESULT_SCREEN_TEXTS)) s.isResultScreen = true;
            if (n.isVisibleToUser() && (startsWithAny(text, ANSWER_WORDS) || startsWithAny(desc, ANSWER_WORDS))) {
                s.hasAnswerButton = true;
            }

            for (int i = 0; i < n.getChildCount(); i++) queue.add(n.getChild(i));
        }
        return s;
    }

    /** A short list of view ids and texts on screen, shown in the app so you can recognise the ad. */
    private static String summarize(AccessibilityNodeInfo root) {
        if (root == null) return "";
        StringBuilder sb = new StringBuilder();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int seen = 0, items = 0;
        while (!queue.isEmpty() && seen++ < 300 && items < 14) {
            AccessibilityNodeInfo n = queue.poll();
            if (n == null) continue;
            String id = shortId(n.getViewIdResourceName());
            CharSequence t = n.getText() != null ? n.getText() : n.getContentDescription();
            String piece = !id.isEmpty() ? (t != null ? id + "=\"" + clip(t) + "\"" : id)
                    : (t != null ? "\"" + clip(t) + "\"" : null);
            if (piece != null) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(piece);
                items++;
            }
            for (int i = 0; i < n.getChildCount(); i++) queue.add(n.getChild(i));
        }
        return sb.toString();
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

    private static String shortId(String fullId) {
        if (fullId == null) return "";
        int slash = fullId.indexOf('/');
        return (slash >= 0 ? fullId.substring(slash + 1) : fullId).toLowerCase(Locale.ROOT);
    }

    private static String clip(CharSequence cs) {
        String s = cs.toString().replace('\n', ' ').trim();
        return s.length() > 30 ? s.substring(0, 30) + "…" : s;
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
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
