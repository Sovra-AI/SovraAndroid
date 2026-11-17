# Google Play release

How the Play Store build is made and what the owner does in Play Console. The app id is
`ai.sovra.remote`, the same id the Expo app reserved, so this native app is the one that
ships under it.

## Build

```sh
scripts/android-release.sh
```

That builds the Rust transport for `arm64-v8a` and `x86_64` (the ABIs in `abiFilters`), runs
the unit tests, and writes `app/build/outputs/bundle/release/app-release.aab` signed with the
upload key. Bump `versionCode` (and `versionName` for user-visible releases) in
[app/build.gradle.kts](../app/build.gradle.kts) before every upload; Play rejects a reused
`versionCode`.

### Upload key

| Item | Where |
| --- | --- |
| Keystore | `~/.config/sovra/android-upload.jks` (PKCS12, alias `upload`, RSA 4096) |
| Password | macOS Keychain, service `sovra-android-upload` |
| SHA-256 | `9B:CA:3D:50:3C:0A:10:0E:17:75:6E:2F:6A:CD:A7:6B:47:FB:44:F6:33:5F:42:93:BB:58:68:BF:02:AB:16:1E` |

Play App Signing keeps the key that signs what users install; this key only proves uploads
come from us. Back the keystore and password up to the team password manager. If they are
lost, Play Console → **App integrity** → **Request upload key reset**. Never commit either.
Elsewhere (CI), set `SOVRA_UPLOAD_KEYSTORE` and `SOVRA_UPLOAD_PASSWORD`. Without them,
release builds fall back to the debug key, which is fine for local installs and rejected
by Play.

## Owner steps in Play Console

1. **Developer account** at [play.google.com/console/signup](https://play.google.com/console/signup):
   one-time US$25 and identity verification.
   - **Organization** (needs a D-U-N-S number for Sovra AI): production release is
     available once the app passes review.
   - **Personal**: accounts made after November 2023 must run a **closed test with at least
     12 testers opted in for 14 continuous days** before applying for production access.
2. **Create app**: name *Sovra*, default language English (United States), App, Free.
   Accept the declarations.
3. **Release → Testing → Internal testing → Create release**: opt in to Play App Signing
   (the default), upload `app-release.aab`, and add yourself as a tester. This is the first
   upload and fixes the app id to `ai.sovra.remote` for this account.
4. Fill in **App content** (below) and the **Store listing**.
5. Personal account: promote the build to **Closed testing**, get 12+ testers to opt in, wait
   14 days, then **Apply for production**. Organization account: promote to **Production**.

## App content answers

| Section | Answer |
| --- | --- |
| Privacy policy | **TODO** URL (required even though no data is collected) |
| Ads | No ads |
| App access | All functionality needs a paired computer. Give the reviewer the steps and a video (see *Review notes*) |
| Content rating | Questionnaire: Utility/Productivity, no objectionable content (expect Everyone / PEGI 3) |
| Target audience | 18+ |
| News app | No |
| Government app | No |
| Financial features | None |
| Health | None |
| Data safety | See below |

### Data safety

- **Data collected:** none. The app has no account, no analytics, and no crash reporting,
  and it sends nothing to Sovra's servers.
- **Data shared:** none.
- Chats, page contents and typed text travel only between the phone and the user's own
  computer, end-to-end encrypted (Iroh QUIC with the computer's key pinned). Google's
  definition of *collected* covers data sent to the developer or third parties, so this
  traffic is not collection.
- **Encrypted in transit:** yes.
- **Deletion:** no data is held off the device. **Settings → Forget this computer** removes
  the pairing.
- Note for the owner: Iroh's public relays (run by n0) and address lookup see the phone's IP
  address and connection metadata, not content. If legal counsel treats IP addresses seen by
  relays as shared data, declare *Device or other IDs* → *Shared* → *App functionality*.

## Store listing

| Field | Value |
| --- | --- |
| App name (30) | Sovra |
| Short description (80) | Use Sovra on your computer from your phone: chat, skills and every page. |
| Category | Productivity |
| Contact email | **TODO** |
| Website | **TODO** |

**Full description (4000):**

```
Sovra on Android is a remote for Sovra on your own computer.

Pair once by scanning the code Sovra shows on your computer. After that, opening the app
connects to that computer on its own, whether you're on the same Wi-Fi or on mobile data.
The connection is end-to-end encrypted.

CHAT
Read the conversation with Sovra's formatting, write messages that go into Sovra's own chat
box, pick / skills, and press Stop.

CHATS
Browse every chat in Sovra's sidebar, search them, open older ones, or start a new one.

SKILLS
Search Sovra's / skills. Picking one starts a message with it.

MORE
Every other part of Sovra, like Team, Schedules and Models, and pages such as /help, made
for your phone: tap buttons, flip switches and type into boxes.

The app controls the Sovra window only, not your operating system, files or shell. To
revoke a phone, choose Forget for it in Sovra on your computer.

Requires Sovra on a Mac or PC.
```

**Graphics:**

| Asset | Size |
| --- | --- |
| App icon | [assets/store/play-icon-512.png](../assets/store/play-icon-512.png), 512×512, no alpha |
| Feature graphic | [assets/store/play-feature-1024x500.png](../assets/store/play-feature-1024x500.png), 1024×500 |
| Phone screenshots | 2–8, 16:9 or 9:16, 320–3840 px per side. Suggested: Pair, Chat, Chats, Skills, More. Done: [play-phone-pair.png](../assets/store/play-phone-pair.png) (1200×2400, padded to 2:1 because Play rejects anything longer); the rest need a phone paired with a running desktop Sovra |

## Review notes

Put these under **App access** → *All or some functionality is restricted*:

```
Sovra on Android is a remote control for Sovra, a desktop app that runs on the user's own
Mac or PC. The app does nothing until it has been paired with that desktop app.

1. Install Sovra on a Mac or PC: TODO <desktop download link>.
2. Open Sovra, then go to Skills → "Use Sovra from your phone". A pairing QR code appears.
   It works once and expires after five minutes.
3. In the app, tap "Scan the code" and scan it. Allow camera access when asked.
4. A short tour explains the app, then the Chat screen opens. Send a message, then open
   Chats, Skills and More.

Video of pairing and use: TODO <video link>.
Camera: only to scan the pairing code. No account, no analytics, no data collected.
```
