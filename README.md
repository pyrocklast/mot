<img src="art/icon-outlined.svg" width="96" alt="">

# mot.

a **M**inimum **o**f **t**hings, a launcher.

Your apps as words on your wallpaper, with a clock and a date. No icons, no widgets, no internet access, no data collected.

For Android 15 and later, in English and French.

## Features

- **Home screen:** up to 8 apps or shortcuts; the time and date in your language's format; the battery level after the date when the status bar is hidden.
- **Gestures:** swipe up for your apps, down for notifications, left and right for two apps of your choice. Long press for settings.
- **App drawer:** search that ignores accents and case, and opens the last app left. Rename, hide, app info and uninstall from a long press. Work apps are marked; Private Space opens as its own list.
- **Look:** text colour that matches your wallpaper; font, weight and size for app names and for the clock, previewed on your wallpaper; settings that follow your phone's theme.

## Install

Add `https://github.com/pyrocklast/mot` to [Obtainium](https://github.com/ImranR98/Obtainium), or download the APK from [Releases](https://github.com/pyrocklast/mot/releases). Then set mot. as your Home app.

## Permissions

| Permission | Used for |
|---|---|
| Expand status bar | Opening notifications |
| Request delete packages | Uninstalling apps from the drawer |
| Set alarm | Opening your alarms |
| Access hidden profiles | Showing Private Space |

## Building

`./gradlew assembleRelease`, with JDK 17 or later and the Android SDK. To sign, add a `keystore.properties` file (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). On GitHub, the Release workflow does this with the `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD` secrets.

## Made with AI

Written by Claude, Anthropic's AI assistant, with the repository's owner, who decided what it does and tests it on their phone. No person has reviewed the code line by line.

## Licence

[GNU GPLv3](LICENSE). Based on [Olauncher](https://github.com/tanujnotes/Olauncher) by [@tanujnotes](https://github.com/tanujnotes) and its contributors, and not affiliated with them.
