# Conatus push relay

A Cloudflare Worker that wakes the Play build of Conatus for Android when something changes on its server.

The app registers `https://<relay>/<fid>` as its push address with the Conatus server it signs in to, where `fid` is its Firebase installation ID. FCM addresses devices by installation ID since `firebase-messaging` 25.1.0 deprecated registration tokens. When a task, project or other data the device can see changes elsewhere, the server POSTs to that address, and the relay sends the Firebase Cloud Messaging data message `{"t":"sync"}` to that installation. The app then syncs. The relay ignores the request body, so a server can't send anything but a wake-up, and no task data passes through Firebase.

Replies:

- 204 when FCM accepted the message.
- 410 when FCM says the installation is unregistered, invalid or from another Firebase project. The Conatus server then forgets the address.
- 429 when a device or an IP address gets too many wake-ups (20 a minute per device, 600 a minute per IP, counted per Cloudflare location).
- 502 for any other failure. The server keeps the address and retries twice.
- 400 or 405 for anything that isn't a POST to an installation ID. The relay never answers 404, because a Conatus server treats 404 like 410.

The F-Droid build doesn't use this. It will get UnifiedPush, which posts to a distributor such as ntfy directly.

## Firebase setup

1. In the [Firebase console](https://console.firebase.google.com/), create a project, for example "Conatus". Google Analytics isn't needed.
2. Add an Android app to the project for each package name: `com.useconatus.app`, `com.useconatus.app.debug` and `com.useconatus.app.preview`. No SHA-1 is needed for messaging.
3. Download `google-services.json` after adding the last one, so it lists all three, and put it at `app/src/play/google-services.json`.
4. Under **Project settings**, **Cloud Messaging**, check that **Firebase Cloud Messaging API (V1)** is enabled.
5. Create a service account for the relay. In the [Google Cloud console](https://console.cloud.google.com/iam-admin/serviceaccounts), with the Firebase project selected, choose **Create service account**, name it `conatus-push-relay` and give it the role **Firebase Cloud Messaging API Admin**. Then open it, go to **Keys** and choose **Add key**, **Create new key**, **JSON**. Keep the file outside this repository.

The default `firebase-adminsdk` account under **Project settings**, **Service accounts** also works, but it can do far more than send messages.

## Deploy

You need Node.js 22 or later and a Cloudflare account. The free Workers plan covers 100,000 requests a day.

```bash
cd push-relay
npm ci
npx wrangler login
npx wrangler deploy
```

The first deploy asks you to pick a `workers.dev` subdomain if the account has none. Wrangler prints the relay's address, `https://conatus-push-relay.<subdomain>.workers.dev`.

Store the service account key as a secret. Wrangler reads it from standard input and deploys a new version with it:

```bash
npx wrangler secret put FIREBASE_SERVICE_ACCOUNT < /path/to/service-account.json
```

Then delete the key file. Cloudflare holds the only copy you need, and you can create a new key any time.

Check that the relay can reach Firebase. A made-up installation ID should come back as 410, which means Google accepted the relay's credentials and rejected the ID:

```bash
curl -i -X POST https://conatus-push-relay.<subdomain>.workers.dev/not-a-real-installation-id
```

A 502 means the secret is missing or wrong; `npx wrangler tail` shows the error. To see the rate limit work, send 25 requests in a row and look for 429 near the end:

```bash
for i in $(seq 25); do curl -s -o /dev/null -w '%{http_code}\n' -X POST https://conatus-push-relay.<subdomain>.workers.dev/not-a-real-installation-id; done
```

The Play build compiles in the relay's address, so a new subdomain or a custom domain means a new app release.

## Test

```bash
npm test
```

The tests run the Worker in Node.js against a fake Google, with a throwaway signing key.
