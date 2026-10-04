# What's My Phone Number

A tiny (~15 KB), dependency-free Android app that shows the phone number(s) of the SIM card(s) in your phone.

- Lists every active SIM (dual-SIM / eSIM supported) with its carrier name
- Tap a number to copy it to the clipboard
- No internet permission, no ads, no tracking — nothing leaves your device
- Works on Android 5.1 (API 22) and up, follows the system light/dark theme

## Get the APK

Every push builds an APK with GitHub Actions. Open the **Actions** tab, pick the latest
**Build APK** run, and download the `whats-my-phone-number-apk` artifact. Unzip it and install
`app-release.apk` on your phone (you'll need to allow installing from unknown sources).

> The release APK is signed with the debug key so it's installable as-is. Use your own
> signing key before distributing it on a store.

## Build it yourself

Requires JDK 17+ and the Android SDK (`ANDROID_HOME` set, or `sdk.dir` in `local.properties`).

```sh
./gradlew assembleRelease      # -> app/build/outputs/apk/release/app-release.apk
./gradlew installDebug         # install on a connected device
```

## Why does it sometimes say "Number not stored on this SIM"?

Android can only report the number if your carrier writes it to the SIM (or provides it over the
network). Many carriers don't. In that case no app can read it — check
**Settings → About phone → SIM status**, your carrier's app, or call/text a friend.

## How it works

`MainActivity` asks for `READ_PHONE_STATE` and `READ_PHONE_NUMBERS`, lists the active
subscriptions via `SubscriptionManager`, and for each one tries, in order:

1. `SubscriptionManager.getPhoneNumber()` (Android 13+)
2. `SubscriptionInfo.getNumber()`
3. `TelephonyManager.getLine1Number()` for that subscription
