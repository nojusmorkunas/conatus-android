# Contributing to Conatus for Android

This guide covers building the app, running it against a local Conatus server and the checks a change needs to pass. Keep `main` releasable: work on a short-lived branch and open a pull request.

## Setup

Install JDK 21 and the Android SDK. Android Studio sets up the SDK and writes `local.properties` with its location; without Android Studio, create that file yourself:

```properties
sdk.dir=/home/you/Android/Sdk
```

Build and install the debug variant on an emulator or a phone:

```bash
./gradlew installDebug
```

## A server to sync with

The app works without a server: choose "Use on this phone" on the welcome screen. To work on syncing, run a Conatus server from [its repository](https://github.com/nojusmorkunas/conatus):

```bash
npm run dev:local
```

That starts the server on port 3000. `npm run db:seed` fills it with demo projects and tasks, with an `admin` account whose password is `admin12345`. See the server's [`CONTRIBUTING.md`](https://github.com/nojusmorkunas/conatus/blob/main/CONTRIBUTING.md) for the details.

From the emulator, the server is at `http://10.0.2.2:3000`. From a phone on the same Wi-Fi, use your computer's address on the network, and allow port 3000 through its firewall. Plain http works in both cases, because the server is on the local network. On Android 17 the app asks for the Nearby devices permission before it connects to a server on the local network.

## Checks

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

CI runs the same on every push and pull request.

## Conventions

- Changes are written to the database on the phone first and sent to the server from a queue. Screens never wait for the network, and sync stays silent unless a change needs a decision.
- The three layouts share everything except navigation, the task row and how details and quick add open. A change to anything else should look and work the same in all three.
- When a feature needs something the server doesn't offer yet, raise it on the [server repository](https://github.com/nojusmorkunas/conatus) and work around the gap in the app until it lands.
- Look up the current version of a library before adding it.
