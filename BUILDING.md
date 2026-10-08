# AIO Enroll for Android — build guide

What this app is, what is already written, and what a developer still has to add to finish it.

It is the phone version of the **AIO Enroll** desktop app (`~/aio-mdm/mdm-enroll-app`, GitHub
`ali-aio/mdm-enroll-app`): sign in with an MDM dashboard account, find AIO devices on the same
Wi-Fi, pair with one, and enrol it into the MDM. Wi-Fi only — no USB cable.

Read the desktop app alongside this. Its `ui/app.js` and `enroll-core/` are the reference for
every behaviour described here, and they are already proven against real devices.

---

## 1. State of the project

**Written and complete** (`app/src/main/java/com/aioapp/enroll/`):

| File | What it does |
|---|---|
| `mdm/MdmApi.kt` | Every `/api/v1/app/*` call: login, me, avatar, enroll-status, classify, restaurants, agent, adb-key, enroll-token, agent APK download. Blocking; call it off the main thread. |
| `store/Store.kt` | Encrypted storage (Android Keystore): session, saved accounts and passwords, adb keys, chosen restaurant, the enrolment log. |
| `adb/Keys.kt` | The adb identity: generate this phone's own RSA key, load the fleet key from PEM, and build the self-signed certificate adb's TLS needs. |
| `adb/AdbLink.kt` | One connection to another device's `adbd`: connect, run a shell command, read a property, install an APK. |
| `adb/Probe.kt` | What a device says about itself: serial, model, Android version, Device Owner, accounts, users, firmware and agent versions. Also `blockedReason`. |
| `adb/Discovery.kt` | Watches the Wi-Fi with `NsdManager` for pairing screens, Wireless-debugging ports and plain adb (port 5555). |
| `adb/Enroller.kt` | The eight enrolment steps, same as the desktop app. |

Gradle, the manifest and the launcher icon are also done. `local.properties` points at the SDK on
this machine; change it on yours.

The screens are written too (`ui/`), and the app builds:

| File | What it does |
|---|---|
| `App.kt` | The three shared things: `Store`, `Discovery`, and this phone's own adb identity (made once, kept). `fleetIdentity` reads the fleet key back out of storage. |
| `MainActivity.kt` | Sets the Compose content and nothing else. |
| `ui/State.kt` | The state holder (`EnrollVm`): one `Ui` for the whole app, the pair flow, the classify/status polling, and the enrolment job. Everything blocking runs on `Dispatchers.IO`. |
| `ui/Root.kt` | Bottom navigation, the snackbar, and the code sheet. |
| `ui/SignIn.kt`, `ui/Devices.kt`, `ui/Device.kt`, `ui/Today.kt`, `ui/Account.kt` | The screens from section 4. |
| `ui/Theme.kt`, `ui/Bits.kt` | The Material 3 scheme (option A's colours) and the shared pieces: class labels, chips, glyphs, `agoShort`. |

**It runs.** On the Pixel 6, against stage: sign-in, discovery (10 devices found on the Wi-Fi),
the no-code connect to an AIO T7 in ~100 ms, a full probe, and an enrolment that completed
(AT070AA2600030 is enrolled as kds). Pairing with a 6-digit code is the one path still untried.

---

## 2. Build and install

```
cd ~/aio-mdm/mdm-enroll-android
./gradlew assembleStageDebug          # or assembleLiveDebug, or assembleDebug for both
adb install -r app/build/outputs/apk/stage/debug/app-stage-debug.apk
```

### Two flavours

`live` and `stage` differ only in which server they talk to, and they have different
`applicationId`s so both sit on one phone with their own encrypted stores — a stage session never
mixes with a live one.

| | live | stage |
|---|---|---|
| server (`BuildConfig.SERVER`) | `https://mdm.dev.aioapp.com` | `https://mdm-stage.dev.aioapp.com` |
| applicationId | `com.aioapp.enroll` | `com.aioapp.enroll.stage` |
| label | AIO Enroll | AIO Enroll (stage) |

The FileProvider authority is `${applicationId}.files`; hardcoding it makes the second flavour
refuse to install (`INSTALL_FAILED_CONFLICTING_PROVIDER`).

Needs JDK 17 and Android SDK 35. The test phone is the Pixel 6 (`18121FDF60022T`) on `arch-btw`;
`arch-btw` has no SDK, so build here and install over the network, or copy the APK across.

`minSdk` is 30: Wireless debugging with pairing codes is Android 11 and newer.

---

## 3. How the pieces fit

```
     Compose screens  ──reads──▶  state holder (ViewModel)
            │                          │
            │ user actions             ├──▶ Discovery      (what is on the Wi-Fi)
            ▼                          ├──▶ AdbLink/Probe  (talk to one device)
     state holder                      ├──▶ Enroller       (the eight steps)
                                       ├──▶ MdmApi         (the dashboard)
                                       └──▶ Store          (what to keep)
```

Everything except the screens is blocking I/O. Run it on `Dispatchers.IO` inside
`viewModelScope`, and keep one `AdbLink` per connected device.

### The three kinds of device on the Wi-Fi

`Discovery` reports a `Svc` for each, and the difference decides what the Pair button does:

| mDNS type | `kind` | Means |
|---|---|---|
| `_adb-tls-pairing._tcp` | `pairing` | Its pairing dialog is open right now. Ask for the 6-digit code. |
| `_adb-tls-connect._tcp` | `connect` | Wireless debugging is on. Connects with a key it already trusts. |
| `_adb._tcp` (usually :5555) | `connect` | Plain adb: AIO firmware with the fleet key built in, or after `adb tcpip`. |

### Zero-touch: every address, tried on :5555

This is the point of the app. Devices we don't build ourselves come from other vendors, and we ask
them for one change to their image: **our adb key in it, and 5555 always open**. Then no pairing
code is ever needed.

So `autoConnect` in `ui/State.kt` tries the fleet key on :5555 against **every address discovery
reports**, four at a time. Whatever opens is one of ours by definition, gets probed, and lands
under **Ready to enrol** with an Enrol button. Whatever refuses (`AdbAuthenticationFailedException`)
or has the port shut is left alone for a minute and keeps its Pair button. Nothing in that path
looks for AIO firmware, which is what makes it work for a vendor's device as well as a T7.

The sections on the Devices screen are, in order: pairing requests, **Ready to enrol** (opened on
their own, not enrolled yet), **Enrolled**, **Connected** (paired by hand with a code), and
everything else on the Wi-Fi.

### Connect before asking for a code

This is the behaviour the user asked for, and the desktop app does it in `openPairFor` in
`ui/app.js`. When Pair is tapped:

1. Try `AdbLink` against the device's advertised address, and against `<ip>:5555`, using **the
   fleet key** first and then **this phone's own key**.
2. If `adbd` accepts either, you are connected and no code was needed. This is the normal path
   for AIO T7s, whose firmware trusts the fleet key.
3. Only if both are refused, show the code sheet.

`setThrowOnUnauthorised(true)` is already set on `AdbLink`, so an untrusted key fails straight
away instead of waiting on a prompt the other device may never show.

**Order matters, and so does one line of reflection.** libadb builds its TLS context once and
keeps it in a private static field, so a second connection would silently reuse the *first*
key — and we try two keys per device. `AdbLink.open()` clears that field first
(`resetTlsContext`). On top of that, `connectKnown` tries the fleet key first on a plain port
(adb checks the key itself there) and this phone's own key first on a TLS port (only a key the
device already trusts gets through).

### The fleet key

AIO firmware trusts one adb key, built into the image. The app **fetches** it after sign-in
(`MdmApi.fleetKey()` → `GET /api/v1/app/adb-key`) and keeps it in encrypted storage. It is never
in the APK.

- `GET /api/v1/app/me` returns `adb_key_version`. Re-fetch when it changes.
- **Delete the key on sign-out.** `Store.fleetKeyPem = null`.
- A `404` means no key is uploaded on the server. Carry on without it: pairing codes still work.

An admin uploads the key under Settings → Enrollment on the dashboard. It is uploaded on both
live and stage.

---

## 4. The screens (Material 3)

The agreed design is **option A** in the demo:
http://100.113.189.96:8082/static/enroll-app-android-demos.html

Open it — it shows every screen below, and ▶ Play runs a pairing and enrolment end to end.
This section is the spec the screens in `ui/` were written against; read it when changing one.

Bottom navigation: **Devices · Today · Account**.

### Sign-in

- **Account picker** when `Store.accounts()` is not empty: a circle per saved account, plus
  "Add account". An account with a saved password signs in on one tap.
- **Form** otherwise: email and password, with a "Save my password" switch. No server field —
  live is the default (`DEFAULT_SERVER` in `MdmApi.kt`).
- On success: save the session, remember the account, fetch the profile and the fleet key.

### Devices (the main screen)

One list, in this order:

1. **Pairing requests**, if any: devices with their pairing dialog open. Tapping one opens the
   code sheet.
2. **Connected devices**: those with a live `AdbLink`, with a tick when enrolled.
3. **On this Wi-Fi**: everything else `Discovery` found, each with a **Pair** button.

Each row shows the name, IP and serial, plus a label of what the MDM knows (section 5).
**Only the Pair button pairs** — tapping the row itself opens the device, it must not start
connecting.

### Code sheet

A Material 3 bottom sheet: six digit boxes, the device's name, IP and serial, and a Pair button.
Close it by itself if that device's pairing screen goes away (it was cancelled on the device);
say so briefly. The desktop app does this in `autoPairPopup`.

### Device page

Header, then grouped rows:

- **Enrollment**: status, "Used as" (a segmented control: dongle · pos · kds · kiosk), "Goes to"
  (the restaurant), "Enrolled by". A big **Enrol** button.
- **On the MDM**, once enrolled: Online, first check-in, battery, agent version, and a button
  that opens the device on the dashboard.
- **Before it can be enrolled**, when `Probe.blockedReason` is set: a checklist of the accounts
  on the device, any other device admin, and extra users. Re-read every few seconds so each item
  ticks off as it is fixed, and enable Enrol when it is clean.

### Enrolling

The eight `STEPS` from `Enroller.kt`, with a tick for each done, a spinner on the one running and
a progress bar. `enrollDevice(...)` reports each step through its `progress` callback.

### Today

What this phone enrolled today, from `Store.log()`: time, device, serial, class, restaurant, and
whether it is online now (`MdmApi.statuses`). Copy Serials and Export CSV.

### Account

Name and picture, the restaurant picker, and Sign Out.

---

## 5. Labels on devices

`MdmApi.classify()` says what the MDM knows about each serial. Use exactly the desktop app's
wording (`classChipHTML` in `ui/app.js`):

| `kind` | Label | On long-press |
|---|---|---|
| `fleet` | ✓ Enrolled · t7 | Already enrolled in the MDM as t7 |
| `production` | AIO · new | Serial is in production "T7 batch BU", not enrolled yet |
| `family` | Known model · pos | Same model as 3 enrolled devices (SUNMI D3 Pro), used as pos |
| `lookalike` | Serial like ours | Serial follows our pattern but matches no production |
| `other` | Not ours | The MDM knows nothing about this device |

**One exception:** a `fleet` device that has not checked in for over an hour is not managed right
now — it may have been reflashed with firmware that has no MDM client. Show **Offline 3 d** in
amber instead of "Enrolled". Get `last_seen_at` from `MdmApi.statuses` for fleet serials.

---

## 6. Things that will bite you

- **Keep rows steady.** Android's mDNS adds and drops services constantly. Key each row by the
  device (its mDNS name without any ` (2)` suffix), not by its address, and update rows in place.
  A list that rebuilds on every scan is the single worst thing about this kind of app — the
  desktop app had exactly this bug (`syncRows` in `ui/app.js` is the fix).
- **Ignore `10.0.2.x`.** Some Androids advertise their internal virtual network. Nothing can
  reach it. `Discovery` already drops these.
- **Ignore this phone itself.** `Discovery` already filters its own addresses.
- **One `AdbLink` per device**, closed when the device goes away or on sign-out. They hold a
  socket.
- **The device may sleep** mid-enrolment and the connection dies. Say so plainly rather than
  showing a stack trace.
- **An older server** returns no `online` field. `Status.online` is then `null`: show nothing
  rather than a wrong "Offline". `Enroller` already handles this.
- **Multicast lock.** `Discovery` takes one; release it when the screen is not visible, or it
  drains the battery.

---

## 7. Testing

- **The Pixel 6** on `arch-btw` is the phone to install on. SSH to that host is refused by the
  tailnet policy, so run `adb kill-server; adb -a -P 5037 nodaemon server` there and drive it from
  here with `export ADB_SERVER_SOCKET=tcp:100.126.94.48:5037` — install, logcat and screencap all
  work over that.
- **Logging.** `AioDiscovery` for what is on the Wi-Fi, `AioPair` for connect and auto-connect
  decisions, `AioAdb` for every shell command with its size and timing.
- **An AIO T7** is at `10.32.2.75:5555` — the no-code connect path. The fleet key **is**
  uploaded now, so this is testable: sign in, and the T7 should connect with no code at all.
- **Against the real MDM**: live is `https://mdm.dev.aioapp.com` and all the endpoints are
  deployed. Sign in with a dashboard account whose role may operate devices.
- **Enrolling for real changes the MDM.** The device shows up in the dashboard and counts against
  the enrollment profile. Use a bench device, not something in a restaurant.

---

## 8. What is still open

- **It has not run on a phone.** The build is green and the screens are written, but no screen
  has been seen on a device: no sign-in, no pairing, no enrolment has been exercised. That is
  the next thing to do, and the Pixel 6 on `arch-btw` is where.
- **No remote.** The repo has commits now but no remote; create one (`ali-aio/…`) and push.
- **One adb stream in six stalls for 20 s.** A stream carries all its output and then never reports
  EOF, so `AdbLink.readAll` has a watchdog that closes it at 20 s — the data is fine, the time is
  not. `Probe.read` sidesteps it by doing everything in **one** shell call (604 ms, where eleven
  separate calls took around a minute and blew past the probe timeout). `Enroller` still makes
  many separate calls and will feel slow until it does the same. The cause is somewhere in
  libadb's stream handling and is not understood yet.
- **Discovery keeps junk entries.** Some devices resolve to `null:5555` (two emulators did) and are
  still counted in "found", though they are dropped from the list.
- **Not 16 KB page-size compatible.** On the Pixel 6 the debug build raises Android's
  "App Compatibility" dialog: `libandroidx.graphics.path.so`, `libspake2.so` (libadb's pairing
  code) and `libconscrypt_jni.so` are not 16 KB aligned. It is only a warning for a debuggable
  build, but a release build will fail to load those libraries on a 16 KB device. The fix is
  newer builds of those three libraries, not anything in this repo — nothing here can realign
  them at packaging time.
- **No CI.** Copy `.github/workflows/build.yml` from the desktop app as a starting point; the
  GitHub billing lock on `ali-aio` has to be cleared before any workflow can run.
