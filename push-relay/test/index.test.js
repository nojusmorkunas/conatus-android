import assert from "node:assert/strict";
import { test } from "node:test";

import relay from "../src/index.js";

const keys = await crypto.subtle.generateKey(
  { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
  true,
  ["sign", "verify"],
);
const pkcs8 = Buffer.from(await crypto.subtle.exportKey("pkcs8", keys.privateKey)).toString("base64");

const allow = { limit: async () => ({ success: true }) };
const env = {
  FIREBASE_SERVICE_ACCOUNT: JSON.stringify({
    project_id: "conatus-test",
    client_email: "relay@conatus-test.iam.gserviceaccount.com",
    private_key: `-----BEGIN PRIVATE KEY-----\n${pkcs8.match(/.{1,64}/g).join("\n")}\n-----END PRIVATE KEY-----\n`,
  }),
  DEVICE_LIMIT: allow,
  IP_LIMIT: allow,
};
const fid = "cVr1zjxdRBe6Q0hW2kK3Zo";

// Stands in for Google: checks the signed assertion, then answers FCM with
// whatever the test asks for.
const calls = [];
let fcmAnswer = () => new Response("{}", { status: 200 });
globalThis.fetch = async (url, init) => {
  calls.push({ url, init });
  if (url === "https://oauth2.googleapis.com/token") {
    const [header, claims, signature] = new URLSearchParams(init.body).get("assertion").split(".");
    const valid = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      keys.publicKey,
      Buffer.from(signature, "base64url"),
      new TextEncoder().encode(`${header}.${claims}`),
    );
    assert.ok(valid, "assertion signature");
    assert.equal(JSON.parse(Buffer.from(claims, "base64url")).scope, "https://www.googleapis.com/auth/firebase.messaging");
    return Response.json({ access_token: "access", expires_in: 3600 });
  }
  return fcmAnswer();
};

const post = (path = `/${fid}`, body = "ignored") =>
  relay.fetch(new Request(`https://relay.example${path}`, { method: "POST", body }), env);

test("sends a sync data message to the installation and ignores the body", async () => {
  const response = await post(undefined, JSON.stringify({ message: { notification: { title: "spam" } } }));
  assert.equal(response.status, 204);
  const send = calls.find((call) => call.url.startsWith("https://fcm.googleapis.com/"));
  assert.equal(send.url, "https://fcm.googleapis.com/v1/projects/conatus-test/messages:send");
  assert.equal(send.init.headers.Authorization, "Bearer access");
  const { message } = JSON.parse(send.init.body);
  assert.equal(message.fid, fid);
  assert.deepEqual(message.data, { t: "sync" });
  assert.equal(message.notification, undefined);
});

test("reuses the access token", async () => {
  calls.length = 0;
  assert.equal((await post()).status, 204);
  assert.equal(calls.filter((call) => call.url.includes("oauth2")).length, 0);
});

test("answers 410 when FCM says the installation is gone", async () => {
  fcmAnswer = () => Response.json(
    { error: { status: "NOT_FOUND", details: [{ "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", errorCode: "UNREGISTERED" }] } },
    { status: 404 },
  );
  assert.equal((await post()).status, 410);
});

test("keeps the address on other FCM failures", async () => {
  fcmAnswer = () => Response.json({ error: { status: "UNAVAILABLE" } }, { status: 503 });
  assert.equal((await post()).status, 502);
});

test("never answers 404, which would make the server forget the address", async () => {
  assert.equal((await post("/")).status, 400);
  assert.equal((await post("/short")).status, 400);
  assert.equal((await relay.fetch(new Request(`https://relay.example/${fid}`), env)).status, 405);
});

test("rate limits per device and per IP", async () => {
  const deny = { limit: async () => ({ success: false }) };
  for (const limits of [{ DEVICE_LIMIT: deny }, { IP_LIMIT: deny }]) {
    const response = await relay.fetch(new Request(`https://relay.example/${fid}`, { method: "POST" }), { ...env, ...limits });
    assert.equal(response.status, 429);
  }
});
