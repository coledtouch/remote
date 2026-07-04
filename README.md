# Curb Remote

A dark, modern Android app + home‑screen widget that runs your living room and
bedroom from your phone — TVs, streaming boxes, smart lights, and live TV — all
over Wi‑Fi, with no hub or bridge server.

**Living Room:** Vizio VQM65C‑10 (SmartCast) + onn 4K Pro (Google TV)
**Bedroom:** Samsung S32DM702UN monitor (Tizen) + onn Bluetooth soundbar (via the monitor)
**Lights:** EcoSmart / Hubspace bulbs, grouped per room with scene presets
**Live TV:** an IPTV TV Guide (Xtream Codes or M3U) you can watch on the phone, cast, or push to the TV

---

## 1. Get the app

You don't need to be a developer. This repo builds itself in the cloud.

1. Open the **Actions** tab of this repo on GitHub.
2. Open the newest green run and download the artifact — inside are two files:
   - **`app-debug.apk`** — the phone app (install this).
   - **`tvapp-debug.apk`** — the companion "Curb TV" player for your streaming box
     (you normally don't need this by hand; the phone can install it for you — see §7).
3. Copy `app-debug.apk` to your phone, tap it, allow install from this source, open **Curb Remote**.

It's a debug build (standard Android debug key), local‑network only.

---

## 2. First‑time setup (pairing)

Put your phone and all devices on the **same Wi‑Fi**, then open **Set up devices**
(the gear icon). Tap **Auto‑detect devices on Wi‑Fi** to fill in IPs, or enter them
by hand. Find any device's IP in its own network settings.

- **onn 4K Pro** — enter its IP, tap **Pair**, then type the 6‑character code shown on the TV.
- **Vizio TV** — enter its IP (port **7345**; older firmware uses **9000**), tap **Pair**, type the PIN.
- **Samsung monitor** — enter its IP, tap **Pair**, then tap **Allow** on the monitor.
- **Hubspace (lights)** — sign in with your Hubspace email + password. (Two‑factor isn't
  supported yet; if your account has it on, turn it off, sign in here, turn it back on.)
- **IPTV** — pick **Xtream** (server + username + password) or **M3U** (playlist URL),
  optionally add an EPG/XMLTV URL, then **Save & load guide**.

Everything is stored only on the phone.

---

## 3. Rooms

Use the **Living Room / Bedroom** switch at the top. Each room shows the right
remote (Vizio+onn vs Samsung), the right apps, and that room's lights.

| Control | Living Room | Bedroom |
|---|---|---|
| Power / Volume / Mute | Vizio TV | Samsung monitor (soundbar via monitor) |
| D‑pad / OK / Back / Home / Menu | onn 4K Pro | Samsung |
| Playback | onn 4K Pro | Samsung |
| Apps | onn (TiviMate, YouTube, Netflix, Spotify) | Samsung (YouTube, Netflix, Spotify) |
| Keyboard | pops up automatically when the TV shows a text field | same |

Switch the navigation between **D‑pad** and **Trackpad** any time.

---

## 4. Lights + Scenes

The **Lights** panel sits at the top of the remote once you're signed into Hubspace.

- **Pick this room's bulbs:** tap the sliders icon on the Lights header and check the
  bulbs that belong to this room. Only those show here — no whole‑house list. Saved per room.
- **Per‑bulb control:** toggle on/off and drag the brightness slider.
- **Scenes** (one tap, applied to the room's bulbs):
  - **Movie** — warm, dim (12%)
  - **Daytime** — cool, full brightness
  - **Night** — warm, very dim (nightlight)
  - **Chill** — warm, 35% — and launches **Spotify** on that room's TV for the mood

Color temperature is best‑effort: tunable bulbs go warm/cool, dimmable‑only bulbs
just change brightness.

---

## 5. TV Guide (live TV)

Tap the **Guide** (LiveTv) icon. You get your channel list with now/next from the EPG,
logos, and search. Pick a channel and choose how to watch:

- **On the phone** — built‑in player with **Picture‑in‑Picture**.
- **Cast** — to any Chromecast/Cast device.
- **On the TV** — launches the companion **Curb TV** app on your onn and plays it there,
  so the phone and the big screen stay in sync.

---

## 6. Home‑screen widget

Long‑press the home screen → **Widgets** → **Curb Remote**. Resizable on the Android
grid, transparent background, dark theme: TV **Power / Vol / Mute** plus one‑tap app
shortcuts using your installed apps' real icons.

---

## 7. Install "Curb TV" on your streaming box (one tap, no computer)

To use **Watch on TV**, the small companion player has to be on your onn box. The phone
can sideload it for you over the network:

1. On the TV: **Settings → System → About → tap Build 7×**, then **Developer options →**
   turn on **USB debugging** (and "Wireless/ADB debugging" if shown).
2. In the phone app: **Setup → Install Curb TV on your box** → the TV's IP is pre‑filled →
   **Install Curb TV**.
3. **Accept** the "Allow debugging?" prompt on the TV the first time.

The phone generates its own ADB key and installs the app over ADB (port 5555). If your
box can't do network ADB, sideload `tvapp-debug.apk` manually instead.

---

## 8. Privacy & security

- The app's stored tokens/passwords are **excluded from Android backup** (`allowBackup=false`),
  so they don't leave the phone via `adb backup` or cloud backup.
- The companion Curb TV player only accepts playback commands carrying a **shared app token**,
  so a random device on your Wi‑Fi can't hijack your screen.
- All device traffic stays on your local network (except the Hubspace and IPTV provider logins).

---

## 9. How it works (for the curious)

- **Vizio SmartCast** — HTTPS REST on `:7345` (self‑signed); `PUT /key_command/` with `{CODESET, CODE}`.
- **Android TV Remote v2** — TLS pairing on `:6467`, commands on `:6466`, length‑delimited
  protobuf; the app makes a client cert once and proves the on‑screen code via SHA‑256.
- **Samsung Tizen** — `wss://<ip>:8002` remote; token granted by the on‑screen Allow prompt.
- **Hubspace/Afero** — Keycloak OAuth (PKCE); device state via `api2.afero.net`.
- **IPTV** — Xtream `player_api.php` / M3U playlists; XMLTV EPG; Media3 + Cast; a tiny
  HTTP server in the companion TV app receives channels from the phone.

Built with Kotlin, Jetpack Compose (Material 3), Glance (widget), Media3/ExoPlayer, the
Cast SDK, protobuf‑lite, and BouncyCastle. Min Android 8.0.

---

## Project layout

```
app/src/main/java/com/curbscript/tvremote/
  onn/ vizio/ samsung/ hubspace/    device clients
  iptv/ player/ cast/ tvsync/       TV guide, player, casting, phone→TV sync
  adb/                              one-tap Curb TV installer (network ADB)
  discovery/                        Wi‑Fi auto-detect (SSDP + mDNS)
  control/Controller.kt             routes every action to the right device
  data/  ui/  widget/               config, Compose screens, home-screen widget
tvapp/                              companion "Curb TV" player for Android TV
.github/workflows/build.yml         cloud build (both APKs)
```
