# Release builds

Release builds are shrunk with R8. Like every build, they connect over HTTPS, or over plain http only to a server on the phone or the local network. Google Play signs the app it ships with its own key (Play App Signing); you sign what you upload with an upload key that only you have.

## Create the upload key

Once, outside this repository:

```bash
keytool -genkeypair -v -keystore ~/keys/conatus-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

It asks for a store password, your name and organisation, and a key password. Back up the `.jks` file and both passwords somewhere safe: if the upload key is lost, Play Console can reset it, but only after a request that takes days.

## Tell Gradle about it

Create `keystore.properties` in the project root:

```
storeFile=/home/you/keys/conatus-upload.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

`keystore.properties`, `*.jks` and `*.keystore` are in `.gitignore`. Never commit them.

## Build

```bash
./gradlew bundleRelease
```

The signed bundle is `app/build/outputs/bundle/release/app-release.aab`, ready for Play Console. Without `keystore.properties` the same command still works and produces an unsigned bundle.
