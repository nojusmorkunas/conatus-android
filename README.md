<h1 align="center">
  <img src="./docs/conatus-logo.svg#gh-light-mode-only" alt="Conatus" width="320">
  <img src="./docs/conatus-logo-dark.svg#gh-dark-mode-only" alt="Conatus" width="320">
</h1>

<p align="center"><em>Every goal starts with a next step.</em></p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0 or newer">
  <a href="https://github.com/nojusmorkunas/conatus"><img src="https://img.shields.io/badge/Conatus-1.3.2%2B-171717" alt="Works with Conatus 1.3.2 or newer"></a>
  <a href="./LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="License: GPL-3.0"></a>
</p>

Conatus for Android is the phone app for [Conatus](https://github.com/nojusmorkunas/conatus), the self-hosted task manager. Your tasks live on the phone and sync with your server in the background, so the app works offline and sends your changes once it has a connection. It also works without a server, as a task list that stays on the phone. It's free software under the GPL.

<p align="center">
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/comfortable.png" alt="Today in the Comfortable layout: large task rows with dates, durations and labels, a round add button and tabs at the bottom" width="32%">
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/compact.png" alt="Today in the Compact layout: one line per task with the date on the right, and the lists as tabs at the bottom" width="32%">
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/web.png" alt="Today in the Web layout: rows like the web app's, with a menu button and an add button" width="32%">
</p>
<p align="center"><sub>The same Today in the three layouts: Comfortable, Compact and Web.</sub></p>

- Three layouts to choose from in Settings. Comfortable has big rows and tabs at the bottom, for one hand. Compact fits a task on each line and puts every list in a tab. Web looks and works like the web app.
- Today, Upcoming with a month calendar, projects with sections and sub-tasks, labels, search, Completed and Trash
- [Quick add](https://github.com/nojusmorkunas/conatus/blob/main/docs/natural-language.md) that reads dates, repeats, `p1`, `#project` and `@label` from the task name, as on the web, offline too
- Reminders as notifications, scheduled on the phone, so they arrive without a connection
- Recurring tasks, deadlines, durations, priorities and comments
- A long press selects tasks, to complete, move, reschedule or delete several at once
- A Today widget, a Quick Settings tile, launcher shortcuts, and text shared from other apps as a new task
- Sign-in with a password or through the browser, which covers single sign-on
- Light and dark themes, large text and TalkBack

<p align="center">
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/details.png" alt="A task's details in a sheet: description, chips for date, repeat, deadline, duration, priority, labels and project, sub-tasks and a comment" width="32%">
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/quickadd.png" alt="Quick add above the keyboard, with the date, time, priority and project highlighted in the text and shown as chips" width="32%">
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/settings.png" alt="Settings with the account, a sketch of each layout to choose from, theme tiles and reminders" width="32%">
</p>

## Requirements

- Android 8.0 (API 26) or newer.
- To sync, a Conatus server running 1.3.2 or newer. 1.3.3 also revokes the phone's sign-in when you sign out of the app.
- The server needs HTTPS, unless it's on your own network. Plain http only works for a server on the local network, so a token never crosses the internet unencrypted.

## Install

Download an APK from the [latest release](https://github.com/nojusmorkunas/conatus-android/releases/latest) and open it on the phone. The first time, Android asks you to allow installing apps from your browser or file manager. Each release has two:

- `app-play-release.apk` syncs within seconds when something changes on the web or another device, through Firebase Cloud Messaging. It needs Google Play services.
- `app-foss-release.apk` has no Google code. It syncs when it opens, after each change and every hour.

Both have the same application ID, so one replaces the other, and later releases install over either as updates. To build the app yourself, see [Build](#build).

## Build

You need JDK 21 and the Android SDK. Gradle finds the SDK through `sdk.dir` in `local.properties`, which Android Studio writes for you, or through `ANDROID_HOME`.

The app comes in two flavors. `play` is the Google Play build, with instant updates: when something changes on the web or another device, the server wakes the phone through Firebase Cloud Messaging and the [push relay](./push-relay/README.md), and it syncs within seconds. `foss` has no Google code and is meant for F-Droid. It syncs when it opens, after each change and every hour.

```bash
./gradlew assemblePlayDebug assembleFossDebug
```

The APKs are in `app/build/outputs/apk/<flavor>/debug/`. The play flavor needs the Firebase project's `google-services.json` in `app/src/play/` for instant updates; without it, it builds and runs like `foss`. Three variants of each flavor install side by side:

| Variant | Launcher name | What it's for |
|---|---|---|
| `release` | Conatus | The real app, shrunk with R8 and signed with the upload key in `keystore.properties`, if there is one |
| `debug` | Debug | Development. It's debuggable, so it feels slower than it is |
| `preview` | Preview | The debug code shrunk with R8 and not debuggable, for judging how the app feels |

## Contributing

Report bugs and ideas in [Issues](https://github.com/nojusmorkunas/conatus-android/issues). See [`CONTRIBUTING.md`](./CONTRIBUTING.md) for running the app against a local server and the checks a change needs to pass.

## License

GPL-3.0. See [`LICENSE`](./LICENSE).
