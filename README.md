# mot.

a **M**inimum **o**f **t**hings, a launcher.

A text-only launcher for Android 15 and later: your apps as words on your wallpaper, a clock and a date, and nothing else. No icons, no widgets, no internet access, no tracking.

mot. is a rewrite of [Olauncher Lite](https://github.com/pyrocklast/llauncher), a personal fork of [Olauncher](https://github.com/tanujnotes/Olauncher). It keeps their look and behaviour, rebuilt with less code and fewer permissions.

> mot. was made with an AI assistant. See [Made with AI](#made-with-ai) for what that means.

## What it does

**Home screen**
- Up to 8 apps or shortcuts, aligned left, centre or right, in the middle of the screen or at the bottom.
- The time and date, the date in your language's usual short, full or numeric format. Tapping them opens your clock and calendar apps, or ones you choose.
- With the status bar hidden, the battery level follows the date. Both stay up to date while the home screen is showing.
- Swipe up for the app drawer, down for notifications, left and right for two apps of your choice (the camera and phone by default). Long press for settings.
- Long-press a home app for the drawer's menu, plus "Change app" and "Remove from home screen".

**App drawer**
- Type to search: accents, case and spaces don't matter, and when one app is left it opens.
- Long-press an app to rename, hide, see its info or uninstall it. A new name shows everywhere the app is.
- Work apps are marked with a dot. Private Space has its own section: tap to lock or unlock it.
- Pinned shortcuts ("Add to home screen" from other apps) are listed with your apps.

**Settings**
- A standard Android settings screen that follows your phone's dark mode and Material You colours.
- Text colour: matching the wallpaper by default (Android tells launchers whether a wallpaper suits dark or light text), or always dark, always light, or following dark mode. App name and clock sizes on top of Android's font size, bolder names, and whether the status bar shows.

Available in English and French. Android's per-app language setting changes mot.'s language and its date format.

## Install

Add `https://github.com/pyrocklast/mot` to [Obtainium](https://github.com/ImranR98/Obtainium) and it will keep mot. up to date. Or download the APK from [Releases](https://github.com/pyrocklast/mot/releases).

Then make mot. your Home app from its first screen, or in Android's Settings > Apps > Default apps.

Requires Android 15 or later.

## Permissions

| Permission | Used for |
|---|---|
| Expand status bar | Swiping down to open notifications |
| Request delete packages | Uninstalling apps from the drawer (Android asks you to confirm) |
| Set alarm | Opening your alarms when you tap the time |
| Access hidden profiles | Showing Private Space |

mot. can see the apps you can open from a launcher, and no other installed packages.

## Building

On GitHub, two workflows run from the Actions tab:
- **Build** runs on every push: unit tests, Android lint and a release build. Nothing is published.
- **Release** builds a signed APK and publishes it as a GitHub release. It needs the `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD` secrets (`KEY_ALIAS` and `KEY_PASSWORD` are optional).

On a PC with JDK 17 or later and the Android SDK, `./gradlew assembleRelease` builds the APK. To sign it, add a `keystore.properties` file next to `settings.gradle.kts` (it's never committed):

```
storeFile=/path/to/your.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## Made with AI

mot. is vibe-coded. Its code was written by Claude, Anthropic's AI assistant, in conversation with the repository's owner, who decided what it should do and tried each build on their own phone. Each release is built from this source by GitHub, but no person has reviewed the code line by line.

The commits Claude wrote carry a `Co-Authored-By: Claude` line. Expect rough edges: it's a personal project, offered as is under the GPL, without any warranty.

## Credits and licence

mot.'s design and behaviour come from [Olauncher](https://github.com/tanujnotes/Olauncher) by [@tanujnotes](https://github.com/tanujnotes) and its contributors, by way of Olauncher Lite. A few parts are adapted from them: the drawer's search matching and opening animation, and the Private Space handling. mot. isn't affiliated with or endorsed by Olauncher.

Like Olauncher, mot. is licensed under the [GNU GPLv3](LICENSE).
