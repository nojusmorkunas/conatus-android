// Turns a Conatus server's wake-up into a Firebase Cloud Messaging data
// message for the Play build. The app registers https://<relay>/<fid> as its
// push address, where fid is its Firebase installation ID, which FCM uses in
// place of registration tokens since 2026. The server POSTs there when
// something changes and the relay sends {t: "sync"} to that installation. The
// request body is ignored, so nothing but a wake-up ever goes through.

const PUSH_PATH = /^\/([A-Za-z0-9_:-]{20,4096})$/;

// FCM answers these when the installation is gone or belongs to another project.
// Every other failure is ours or Google's, and the address should be kept.
const GONE = new Set(["UNREGISTERED", "INVALID_ARGUMENT", "SENDER_ID_MISMATCH"]);

export default {
  async fetch(request, env) {
    const match = PUSH_PATH.exec(new URL(request.url).pathname);
    // Never 404: a Conatus server forgets an address that answers 404 or 410.
    if (!match) return new Response("Not a push address\n", { status: 400 });
    if (request.method !== "POST") {
      return new Response(null, { status: 405, headers: { Allow: "POST" } });
    }

    const fid = match[1];
    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    const [perDevice, perIp] = await Promise.all([
      env.DEVICE_LIMIT.limit({ key: fid }),
      env.IP_LIMIT.limit({ key: ip }),
    ]);
    if (!perDevice.success || !perIp.success) {
      return new Response("Too many wake-ups\n", { status: 429, headers: { "Retry-After": "60" } });
    }

    try {
      return await send(fid, env);
    } catch (error) {
      console.error("Push failed", error);
      return new Response("Push failed\n", { status: 502 });
    }
  },
};

async function send(fid, env) {
  const account = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${await accessToken(account)}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          fid,
          data: { t: "sync" },
          // Normal priority: delivered at once while the phone is awake, and
          // batched in Doze, where nobody is looking. The collapse key keeps
          // only the newest wake-up for a phone that is offline, and after an
          // hour the app's own periodic sync has caught up anyway.
          android: { priority: "normal", collapse_key: "sync", ttl: "3600s" },
        },
      }),
    },
  );
  if (response.ok) return new Response(null, { status: 204 });

  const body = await response.json().catch(() => null);
  const code = body?.error?.details?.find((detail) => detail.errorCode)?.errorCode ?? body?.error?.status;
  if (GONE.has(code)) return new Response("Installation is gone\n", { status: 410 });
  if (response.status === 401) cached = null;
  console.error("FCM answered", response.status, JSON.stringify(body));
  return new Response("FCM failed\n", { status: 502 });
}

// One OAuth access token per isolate, fetched again a minute before it expires.
let cached = null;

async function accessToken(account) {
  if (cached && cached.expiresAt > Date.now() + 60_000) return cached.token;

  const now = Math.floor(Date.now() / 1000);
  const unsigned = [
    { alg: "RS256", typ: "JWT" },
    {
      iss: account.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    },
  ].map((part) => base64url(new TextEncoder().encode(JSON.stringify(part)))).join(".");
  const key = await crypto.subtle.importKey(
    "pkcs8",
    Uint8Array.from(atob(account.private_key.replace(/-----[^-]+-----|\s/g, "")), (c) => c.charCodeAt(0)),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${unsigned}.${base64url(new Uint8Array(signature))}`,
    }),
  });
  if (!response.ok) throw new Error(`Google token endpoint answered ${response.status}: ${await response.text()}`);
  const { access_token: token, expires_in: expiresIn } = await response.json();
  cached = { token, expiresAt: Date.now() + expiresIn * 1000 };
  return token;
}

function base64url(bytes) {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
