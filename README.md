<div align="center">

<img src="docs/logo.svg" width="112" alt="ClearGate logo" />

# ClearGate

**Answer the gate. Skip the ad.**

A tiny Android app that gets MyGate's full-screen ad out of your way after you approve or deny a visitor.

[![Download APK](https://img.shields.io/badge/Download-APK-2F6A3F?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Rabbitwingz/ClearGate/releases/latest/download/ClearGate.apk)
[![Build](https://img.shields.io/github/actions/workflow/status/Rabbitwingz/ClearGate/build.yml?branch=main&style=for-the-badge&label=build)](https://github.com/Rabbitwingz/ClearGate/actions)
[![Latest release](https://img.shields.io/github/v/release/Rabbitwingz/ClearGate?style=for-the-badge&label=version)](https://github.com/Rabbitwingz/ClearGate/releases/latest)

</div>

---

## The problem

If your housing society uses [MyGate](https://play.google.com/store/apps/details?id=com.mygate.user), every visitor or delivery pops up a full-screen request on your phone. You tap **Approve** or **Deny**, and MyGate swaps the request for a full-screen ad. Close that and MyGate opens its home screen, which has more ads. That happens every time, for every visitor.

## What ClearGate does

1. **A visitor arrives.** MyGate shows the entry request, as usual.
2. **You tap Approve or Deny.** Your answer goes to the gate exactly as before.
3. **The ad is skipped.** As soon as MyGate confirms your answer ("Entry approved for …"), ClearGate presses **Home** for you. You land back where you were, whether that's your home screen or the lock screen.

It never touches the approve/deny screen itself, and it waits for MyGate's confirmation before acting, so your answer always goes through.

## Private by design

- **Only sees MyGate.** It uses Android's accessibility feature, restricted to the `com.mygate.user` package. Other apps are invisible to it.
- **No internet permission.** It can't send anything anywhere. The activity log stays on your phone.
- **Open source.** Everything it does is in [`AdSkipService.java`](app/src/main/java/io/github/rabbitwingz/cleargate/AdSkipService.java).
- **Pause any time** from the app or the **Pause ClearGate** Quick Settings tile. Pausing switches its accessibility fully off.

## Install

1. On your phone, download **[ClearGate.apk](https://github.com/Rabbitwingz/ClearGate/releases/latest/download/ClearGate.apk)** and open it. Allow "install unknown apps" for your browser if asked.
2. Open **ClearGate** and follow the short intro. It walks you through the two settings it needs:
   - **Accessibility → ClearGate → On**, so it can see MyGate's screens.
   - **Run unrestricted in the background**, so your phone doesn't stop it.

Updates use the same link and install over the previous version.

## Troubleshooting

<details>
<summary><b>My banking app says "Unauthorized app" or "Accessibility permission detected"</b></summary>

Many banking apps (Axis Mobile, ICICI iMobile and others) refuse to run while any accessibility app installed outside the Play Store is switched on. It's an anti-fraud check, and ClearGate can't and won't hide from it. Instead, pause ClearGate while you bank. The **Banking apps** card in ClearGate sets this up:

- **Pause & open:** add your banking apps once. Then open them with **Pause & open** in ClearGate, by long-pressing ClearGate's icon, or from a shortcut on your home screen. ClearGate pauses and the bank opens in one tap.
- **Resume reminders:** while paused, a notification shows **ClearGate is paused · Resume**, and nudges you once if you're still paused after about 10 minutes.
- **Quick Settings tile and home-screen widget:** pause or resume from the notification shade or your home screen.

**Resuming:** tap **Resume** (notification, tile, widget or app). Accessibility settings opens: tap **ClearGate** (on some phones it's under *Installed apps* or *Downloaded apps*), turn it on and tap **Allow**. ClearGate then takes you back to your home screen. Android only lets *you* switch accessibility on, which is why this step can't be a single tap.

While paused, MyGate's ads show as usual.
</details>

<details>
<summary><b>The accessibility switch is greyed out ("Restricted setting")</b></summary>

Android 13 and newer block accessibility for apps installed outside the Play Store. Go to **Settings → Apps → ClearGate → ⋮ (top right) → Allow restricted settings**, then turn it on again. The app's setup screen has a shortcut to App info.
</details>

<details>
<summary><b>It worked, then stopped</b></summary>

Some phones (Xiaomi, Oppo, Vivo, Realme, Samsung and others) aggressively stop background apps. Set ClearGate's battery usage to **Unrestricted** and, if your phone has it, turn on **Autostart**. Also check that it's still on in Accessibility settings.
</details>

<details>
<summary><b>The ad still shows after a MyGate update</b></summary>

ClearGate recognises the screen by its "Entry approved for …" text, so a MyGate redesign can break it. Check **Recent activity**: if you see "You answered a visitor" but no "Ad skipped" after it, [open an issue](https://github.com/Rabbitwingz/ClearGate/issues) with a screenshot of the screen that stayed open.
</details>

<details>
<summary><b>I was using MyGate when a visitor came, and it sent me to the home screen</b></summary>

That's expected: after you answer a request, it always presses Home. Just reopen MyGate.
</details>

## How it works (for developers)

- [`AdSkipService`](app/src/main/java/io/github/rabbitwingz/cleargate/AdSkipService.java) is an `AccessibilityService` limited to `com.mygate.user` ([config](app/src/main/res/xml/accessibility_config.xml)). When the approve/deny screen appears, it starts a 20-second watch. If MyGate's "Entry approved/denied for …" screen appears in that window, it performs `GLOBAL_ACTION_HOME`. It falls back to Back only while MyGate is still in front.
- It never acts outside that window, and never while Approve/Deny buttons are on screen, so using MyGate normally is unaffected. The activity log only records your answer and what ClearGate did, not screen contents.
- The UI is Jetpack Compose with **Material 3 Expressive** (`MaterialExpressiveTheme`, expressive motion, Material shapes, dynamic color).

### Build from source

There's no local setup needed: every push to `main` is built by [GitHub Actions](.github/workflows/build.yml) and published as the `latest` release. To build locally you need JDK 17+, the Android SDK (platform 37) and Gradle 9.8:

```bash
gradle :app:assembleRelease
```

Without the signing secrets, local builds are signed with the debug key.

**Stack:** Kotlin + Java · Jetpack Compose · Material 3 Expressive (`material3` 1.5 alpha) · AGP 9.4 · minSdk 26 (Android 8.0) · targetSdk 36.

## Disclaimer

ClearGate is an independent personal project. It is not affiliated with, endorsed by, or connected to MyGate or Vivish Technologies. It doesn't modify MyGate, block its network traffic, or bypass any of its features: it presses Home for you after you've answered, just as you could yourself.
