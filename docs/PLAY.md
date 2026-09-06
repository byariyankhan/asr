# Publishing to Google Play

What the code does for a release, and what the founder does in the Play
Console. Everything that could be done in code is done; this is the rest,
with the answers written out so the forms can be filled in from here.

## 1. The upload key, once

Play App Signing holds the key that signs what phones install. The *upload
key* only proves that an upload came from us, and Play can issue a new one
if it is lost. Make it on your own computer, never in the repository:

```bash
keytool -genkeypair -v -keystore asr-upload.jks -alias asr-upload \
  -keyalg RSA -keysize 2048 -validity 10000
# answer the prompts; use one strong password for the store and the key
base64 -w0 asr-upload.jks > asr-upload.jks.b64      # macOS: base64 -i asr-upload.jks | tr -d '\n'
```

Keep `asr-upload.jks` and its password in your password manager. Then add
four repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `ANDROID_UPLOAD_KEYSTORE_B64` | the contents of `asr-upload.jks.b64` |
| `ANDROID_UPLOAD_KEYSTORE_PASSWORD` | the store password |
| `ANDROID_UPLOAD_KEY_ALIAS` | `asr-upload` |
| `ANDROID_UPLOAD_KEY_PASSWORD` | the key password |

Then delete `asr-upload.jks.b64` from your computer. Run the Android
workflow once (Actions → Android → Run workflow); its "Release bundle and
APK" step says `release: signed with the upload key` and prints the upload
key's SHA-256, which you will need in step 3.

## 2. What CI builds

Every Android run builds three things and keeps them as artifacts:

| Artifact | What | Kept |
|---|---|---|
| `asr-debug-apk` | debug build, fixed debug key, for testing | 14 days |
| `asr-release-aab` | the App Bundle Play takes; R8-shrunk; signed with the upload key when the secrets exist | 30 days |
| `asr-release-apk` | the same shrunk code as an APK, for putting a release build on a phone before Play does | 30 days |

`versionCode` is the workflow's run number, so it only ever goes up and no
two uploads can collide. `versionName` is set by hand in
`android/app/build.gradle.kts` (`0.1.0` today); bump it when a release is
worth a new number. **Upload the bundle from the run on `master`**, after the
pull request merged, so what is in the store is what is in the default
branch.

## 3. Creating the app in the Console

1. **Create app**: name `Asr: Protect Your Time & Focus` (30 characters, the
   limit), default language English (United States), App, Free.
2. **App signing**: accept Play App Signing when the first bundle is
   uploaded (Google generates the app signing key; ours is the upload key).
3. **App Links**: under Release → Setup → App signing, copy the *App signing
   key certificate* SHA-256. Set the repository secret `ANDROID_CERT_SHA256`
   to three fingerprints, comma-separated: Play's, the upload key's (from
   step 1), and the debug key's (printed at the end of every Android run).
   Then run the **Bootstrap** workflow: its fill-in step rewrites that one
   line in `/opt/asr/.env` and recreates the API. Check
   `https://api.joinasr.io/.well-known/assetlinks.json` lists all three.
   Without this, an invitation link opens a browser instead of the app.

## 4. Store listing

- **Short description** (80 max): `Set app limits, name a witness, keep your word.`
- **Full description**, a starting point:

  > Asr is for people who want to use their phone less and mean it. Choose
  > the apps that take your time, set a daily limit for each, and make a
  > pact: three days, a week, a month. Then name a witness — a friend, a
  > parent, a partner — who installs Asr, accepts your invitation, and sees
  > how you are doing. If you keep your word, they hear. If you break it,
  > they hear that too.
  >
  > When a limit is reached, Asr blocks the app until tomorrow. You can earn
  > extra minutes by walking or by a focus session, and the price is fixed
  > when the pact starts, so it cannot be renegotiated with yourself later.
  > Your usage is measured on your phone and never uploaded; your witness
  > sees minutes per app for the apps you chose, and nothing else.
  >
  > No ads. No feed. One promise, and somebody who knows you made it.

- **Graphics**: app icon 512×512 PNG; feature graphic 1024×500; at least two
  phone screenshots (up to eight), 9:16, at least 320px on the short side.
  Good ones: the dashboard with a running pact, Choose apps, the block
  screen, the witness circle, Progress.
- **Category**: Productivity. **Tags**: habit, screen time, focus.
- **Contact**: `hi@ariyankhan.com`; website `https://joinasr.io`.
- **Privacy policy**: `https://joinasr.io/privacy`.

## 5. App content (the declarations)

- **Ads**: no.
- **App access**: "All or some functionality is restricted." Instructions:
  *Sign up in the app with any email address and a password; no email
  confirmation is required to use it. To see enforcement, grant Usage access
  and Display over other apps when asked, start a challenge with a short
  limit, and open a limited app.* Also give a test account (create one from
  the app first, e.g. `playreview@joinasr.io`) so a reviewer can skip
  sign-up.
- **Content rating**: Utility / Productivity. No violence, sexuality,
  language, controlled substances. *Users can interact* — yes (witnesses see
  a person's progress and react). No location sharing. No purchases today.
- **Target audience**: 13 and over. Not designed for families.
- **Data safety**: section 6.
- **Government app**: no. **Financial features**: no. **News**: no.
- **Health**: if the questionnaire lists physical activity or step counting,
  declare it: the walking reward reads the step counter on the phone during
  a walk; the count never leaves the phone.
- **Advertising ID**: no. (The permission is removed in the manifest.)
- **Permissions declaration — Usage access** (`PACKAGE_USAGE_STATS`), core
  functionality: *Asr lets a person set daily time limits on apps they
  choose and blocks each app for the rest of the day once its limit is
  reached. Usage access is how the app knows how long each chosen app has
  been in the foreground today; without it the product does nothing. Usage
  is measured on the device. What leaves the phone is the list of chosen apps
  with their limits, the daily minutes for each chosen app, and the moments a
  limit was reached — shown to the witness the person named. Nothing about
  other apps is read or sent. The permission is requested on a dedicated
  screen that explains exactly this before Settings opens.*
- **Foreground service — Special use** (`FOREGROUND_SERVICE_SPECIAL_USE`),
  the justification the manifest also carries: *Enforces the daily app time
  limits the user set for themselves. The service must run while other apps
  are in the foreground, because that is precisely when a limit can be
  reached.* If asked for a video: record the block screen appearing over a
  limited app.
- **Display over other apps** (`SYSTEM_ALERT_WINDOW`): no form, but
  reviewers look for the in-app explanation. It is the app-blocking
  disclosure screen, shown before Settings opens.

## 6. Data safety, the answers

"Collected" means sent off the phone. "Shared" means handed to a third party
for its own purposes; Firebase and Resend act on our instructions, which is
not sharing in Play's sense.

| Data type | Collected | Shared | Purpose | Required |
|---|---|---|---|---|
| Name | yes | no | account management, app functionality (shown to witnesses) | yes |
| Email address | yes | no | account management | yes |
| User IDs | yes | no | app functionality | yes |
| Photos (profile photo) | yes | no | app functionality (shown to witnesses) | optional |
| Other personal info (date of birth, country, gender) | yes | no | account management, personalisation | yes |
| Installed apps (only the ones the person chose to limit) | yes | no | app functionality | yes |
| App interactions (ten product events, see ANDROID.md) | yes | no | analytics | yes |
| Crash logs | yes | no | analytics, app functionality | yes |
| Diagnostics (app version, whether protection is on) | yes | no | app functionality | yes |
| Device or other IDs (Firebase installation id, push token) | yes | no | app functionality, analytics | yes |
| Location, contacts, messages, files, financial info, health and fitness | not collected | | step counts stay on the phone | |

Security practices: data is encrypted in transit (yes); users can request
that data be deleted (yes, `https://joinasr.io/delete-account`, and in the
app under Personal details); the app follows the Families policy (no, not
designed for children).

## 7. Tracks

Organisation accounts have no mandatory testing period, but do it anyway:
**Internal testing** with the phones from the beta list (Xiaomi, Samsung,
Oppo or Vivo, a Pixel) for a week, then **Production**. Upload the
`asr-release-aab` from the `master` run of the merge you are releasing.

## 8. Before "Publish"

- [ ] Off-site database backup running and restored once (`infra/backup.sh`).
- [ ] The uptime monitor alerts a phone somebody looks at.
- [ ] A password-reset email arrives from `noreply@joinasr.io` (Resend domain verified).
- [ ] `ANDROID_CERT_SHA256` holds all three fingerprints and `assetlinks.json` shows them.
- [ ] A release build (from `asr-release-apk`) ran on a real phone: sign up, start a pact, block, earn time, invite, accept on a second phone.
- [ ] Crashlytics shows that release build's test crash with readable line numbers (the plugin uploads the R8 mapping during the CI build).
- [ ] Play title, short description and the Data safety answers match this document and the privacy policy.
