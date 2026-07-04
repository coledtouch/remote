# Curb Remote

A lightweight, dark, modern Android app + home‑screen widget that controls your
**Vizio VQM65C‑10** TV and your **onn 4K Pro (Google TV)** streaming box — directly
over your Wi‑Fi, with no extra hub or bridge server.

- **Vizio TV** ← SmartCast local API: power, volume, mute, input switching.
- **onn 4K Pro** ← Android TV Remote v2 (the same protocol the Google TV app uses):
  D‑pad + OK/Back/Home, playback, and one‑tap app launches (Netflix, YouTube, …).
- **Home‑screen widget:** TV power / volume / mute + quick YouTube & Netflix.

Everything runs on the phone. The onn box being always‑on means the app can reach it
instantly; the app itself speaks both device protocols, so there is nothing else to run.

---

## 1. Get the APK

You do **not** need to be a developer. Pick whichever is easier for you.

### Option A — Build in the cloud (recommended, no tools to install)

1. Create a free account at <https://github.com> if you don't have one.
2. Make a new repository and upload this whole project folder to it
   (drag‑and‑drop works: *Add file → Upload files*).
3. GitHub automatically runs the included build (see the **Actions** tab).
   When it finishes (~3 min), open the run and download the artifact
   **`curb-remote-debug-apk`** — inside is `app-debug.apk`.
4. Copy that APK to your phone and install it (see **Install** below).

The build recipe lives in `.github/workflows/build.yml` and needs no configuration.

### Option B — Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (free).
2. *File → Open* this folder. Let it sync (it downloads Gradle + SDK automatically).
3. Plug in your phone (USB debugging on) and press **Run**, or
   *Build → Build Bundle(s) / APK(s) → Build APK(s)*.

---

## 2. Install on your phone

1. Move `app-debug.apk` to the phone (USB, Google Drive, email to yourself, etc.).
2. Tap it. Android will ask to allow installing from this source — allow it.
3. Open **Curb Remote**.

> It's a debug build signed with the standard Android debug key, so it installs
> without Play Store. It only talks to devices on your local network.

---

## 3. First‑time setup (pairing)

Make sure your **phone, TV, and onn box are on the same Wi‑Fi**. On first launch the
app opens the **Set up devices** screen.

### onn 4K Pro
1. Find its IP: on the box, **Settings → Network & Internet → (your Wi‑Fi) → IP address**.
2. Type that IP in the app and tap **Pair**.
3. A 6‑character code appears on the TV. Enter it in the app and tap **Confirm & pair**.

### Vizio VQM65C‑10
1. Find its IP: **Menu → Network → Manual Setup → IP address** (or in your router).
2. Type the IP (leave **Port** at `7345`; if pairing fails, try `9000`) and tap **Pair**.
3. A PIN appears on the TV. Enter it and tap **Confirm & pair**.

You can re‑open setup any time from the gear icon, and re‑pair either device.

---

## 4. Add the home‑screen widget

Long‑press your phone's home screen → **Widgets** → find **Curb Remote** → drag it out.
It gives you TV **Power / Vol− / Vol+ / Mute** and one‑tap **YouTube / Netflix** on the onn.

---

## 5. How the buttons are routed

| Control | Goes to | Why |
|---|---|---|
| Power, Volume, Mute, Input | **Vizio TV** | The TV owns the panel, speakers, and inputs |
| D‑pad, OK, Back, Home, Menu | **onn 4K Pro** | Navigation of the Google TV interface |
| Play/Pause, Rewind, Fast‑forward, Prev/Next | **onn 4K Pro** | Media transport keys |
| App shortcuts (Netflix, YouTube, …) | **onn 4K Pro** | Launched via Android TV app links |

---

## 6. Troubleshooting

- **"Couldn't reach the device"** — confirm the IP and that the phone is on the same
  Wi‑Fi (not a guest network). Give the devices static/reserved IPs in your router so
  they don't change.
- **Vizio pairing fails** — switch the port between **7345** and **9000**. Make sure the
  TV is on. Some Vizios ask you to enable mobile control under
  *Menu → System → Mobile Devices / SmartCast*.
- **onn pairing fails** — the box must have the pre‑installed *Android TV Remote Service*
  (it does by default). If a stale pairing exists, remove *Curb Remote* under
  *Settings → Remotes & Accessories* on the onn, then pair again.
- **App shortcut doesn't open** — a few apps use different deep links; the standard ones
  are included. Everything else keeps working.
- **Widget taps feel slow the first time** — the app opens a secure connection to the
  onn box on the first command, then stays fast.

---

## 7. How it works (for the curious)

- **Vizio SmartCast**: HTTPS REST on `https://<tv>:7345` (self‑signed cert). Pairing
  returns an `AUTH_TOKEN`; commands are `PUT /key_command/` with `{CODESET, CODE}` pairs.
- **Android TV Remote v2**: two TLS services — pairing on **6467**, commands on **6466** —
  exchanging length‑delimited protobuf messages. The app generates a self‑signed client
  certificate once (`app/src/main/proto/*.proto` define the messages); pairing proves both
  sides share the on‑screen code via a SHA‑256 of both certificates' public keys + the code.

Built with Kotlin, Jetpack Compose (Material 3), Glance (widget), protobuf‑lite, and
BouncyCastle. Min Android 8.0.

---

## Project layout

```
app/src/main/
  proto/                     Android TV Remote v2 message definitions
  java/com/curbscript/tvremote/
    onn/                     Cert + pairing + persistent remote session (Google TV)
    vizio/                   SmartCast REST client (TV)
    control/Controller.kt    Routes each button to the right device
    data/                    Saved config + app‑shortcut definitions
    ui/                      Dark Compose remote + setup/pairing screens
    widget/                  Glance home‑screen widget
.github/workflows/build.yml  Cloud APK build
```

## Companion TV app (Curb TV) — Phase 3

`tvapp/` is a separate, minimal Android TV app. Install it on the onn 4K Pro to watch
IPTV channels on the TV, controlled from your phone (Guide → "Watch on TV").

- CI builds it as **tvapp-debug.apk** alongside the phone APK (same Actions artifact).
- Sideload it onto the onn:
  - **Easiest:** on the onn, install the **Downloader** app (by AFTVnews) from Google Play,
    then open the artifact link / a hosted copy of `tvapp-debug.apk` and let it install
    (enable "install unknown apps" for Downloader when prompted), or
  - **adb:** `adb connect <onn-ip>:5555` then `adb install tvapp-debug.apk`.
- Open **Curb TV** once on the onn; it shows `http://<onn-ip>:8099` and waits.
- On the phone: open the **TV Guide**, flip **Watch on TV**, tap a channel — the phone
  launches Curb TV on the onn and streams the channel there.
