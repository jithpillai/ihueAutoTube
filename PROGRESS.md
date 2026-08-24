# AutoTube — Progress Log

Personal Android Auto app that shows YouTube/web content on the car screen via a
hidden WebView rendered onto a `NavigationTemplate`'s raw `Surface`. This file is a
handoff snapshot — read it first in any new session before touching the code.

## Current state (as of 2026-08-24)

**The app is confirmed working on real Android Auto** (phone-projected, actual car,
not just emulators): it appears in the car's app list, opens, and navigates between
screens. Getting to this point took most of this session — see "How we got here"
below for the non-obvious platform issues that were blocking it.

**Two known bugs remain, found during real-car testing:**

1. **On-screen keyboard / voice input doesn't appear when tapping the YouTube search
   box.** Touch replay into the hidden WebView (`WebViewSurfaceBridge.dispatchClick`)
   was never fully verified end-to-end — see "Known issue: raw-Surface touch" below.
2. **Video playback is audio-only — no picture.** Almost certainly because
   `WebView.draw(canvas)` (used by the render loop to blit WebView content onto the
   car's `Surface`) does not capture hardware-accelerated `<video>` playback — the
   video frame is composited by the GPU/MediaCodec directly, bypassing the normal
   View software-drawing path `draw()` uses. This is a well-known Android WebView
   limitation, not a bug in this app's logic. **This is the priority thing to
   investigate next** — it may need a fundamentally different capture approach for
   video specifically (see "Ideas for the video bug" below).

## Architecture

- `car/AutoTubeCarAppService.kt` — the `CarAppService` Android Auto binds to.
- `car/AutoTubeSession.kt` — creates the initial `Screen` (`HomeScreen`).
- `car/HomeScreen.kt` — favorites + continue-watching list (`ListTemplate`).
- `car/BrowserScreen.kt` — URL/search entry (`SearchTemplate`).
- `car/PlaybackScreen.kt` — the actual `NavigationTemplate` + raw `Surface` +
  WebView bridge. This is where the WebView-to-car-screen trick happens.
- `car/FavoritesEditScreen.kt` / `AddFavoriteScreen.kt` / `FavoriteDetailScreen.kt` —
  add/delete favorites flow (`SearchTemplate` + `ListTemplate`).
- `car/UrlUtils.kt` — shared URL normalization/type-guessing helper.
- `car/DrivingStateGate.kt` — safety gate; **still a conservative placeholder**
  (blanks the surface whenever the screen isn't foregrounded). Wiring real
  speed/parking-brake sensor data via `CarHardwareManager` is still a TODO.
- `bridge/WebViewSurfaceBridge.kt` — the render loop (WebView → Bitmap → Surface,
  ~12fps) and touch/scroll replay (car taps → synthetic `MotionEvent`s on the
  hidden WebView). Also tracks live URL/title via `WebViewClient`/`WebChromeClient`
  callbacks, and can read back `<video>` playback position via `evaluateJavascript`.
- `MainActivity.kt` — phone-side landing page (logo, how-to-use, feature list, live
  favorites count, About button). Deliberately **not** the car's entry point.
- `AboutActivity.kt` — dev credit / contact info, not exported (reached only via
  MainActivity's button).
- `data/` — Room DB: `SavedItem` (channels/playlists/sites, one shared table) +
  `WatchHistory` (videoId, title, last position, watched-at).

## How we got here — platform issues found & fixed this session

These were all real, non-obvious platform requirements, not code logic bugs. Worth
understanding before changing manifest/dependencies again:

1. **No launcher icon at all** (empty `mipmap`/`drawable`, no `android:icon`) — car
   launchers need an icon to render a tile. Fixed: real icon added (from
   `resources/autotube.png` / `autotube_trans.png`), density-specific PNG mipmaps.

2. **Missing `androidx.car.app.activity.CarAppActivity` entry point.** The app had a
   custom `MainActivity` as the only `LAUNCHER` activity, but androidx Car App
   Library requires `CarAppActivity` (a class the library ships, not one you write)
   to be declared as `LAUNCHER` — it's what hosts the `CarAppService` and is what
   any host (DHU, Android Auto, Automotive OS) actually invokes. Without it there
   was no real entry point into the car UI on *any* host. This explains basically
   every earlier symptom: DHU dying right after protocol negotiation, Android
   Automotive OS's app grid opening the wrong activity, etc.

3. **Missing `androidx.car.app.ACCESS_SURFACE` permission.** Crashed
   `PlaybackScreen` (`SecurityException`) the instant it tried
   `AppManager.setSurfaceCallback()`. `NAVIGATION_TEMPLATES` permission alone is not
   enough for raw `Surface` access.

4. **`automotive_app_desc.xml` had `<uses name="navigation"/>` — should be
   `<uses name="template"/>`.** This is the one that blocked *real* Android Auto
   specifically (not caught by AAOS/DHU testing). Confirmed via `adb logcat` on the
   phone while gearhead rebuilt its app list:
   `CAR.VALIDATOR: Package DENIED; Uses for TEMPLATE not defined [dev.local.autotube]`.
   `template` is the correct, generic declaration for any Car App Library app,
   regardless of category — category-specific behavior comes from the
   `CarAppService`'s intent-filter category (`NAVIGATION` here), not this file.

5. **Real Android Auto requires the app to be installed via Google Play** — even
   with "Unknown sources" enabled in Android Auto's developer settings. Confirmed
   via the same logcat investigation:
   `"Package is not installed by play [dev.local.autotube]"` → a category-based
   allowlist check → `"Package DENIED; failed all other checks"`. `NAVIGATION`
   category apps aren't in whatever category list is exempt from this check (unlike,
   presumably, POI/PARKING/CHARGING/IOT — not verified). "Unknown sources" bypasses
   *other* restrictions (unsigned APKs, sideloading itself) but not this specific
   Play-installer check for nav apps. **This is why the app is now going through
   Play Console Internal Testing** rather than staying sideloaded — Internal
   Testing is a device-level "installed by com.android.vending" check, not a
   review-tier check, so it satisfies this gate without needing a public listing.

6. **Play Console rejected the AAB**: *"The app cannot declare
   'android.hardware.type.automotive' device feature and
   'com.google.android.gms.car.application' metadata at the same time."* Root
   cause: the `androidx.car.app:app-automotive` dependency's own manifest declares
   `<uses-feature android.hardware.type.automotive>`, which got merged in regardless
   of what our own manifest said. Fixed by dropping that dependency — it's the
   Automotive-OS-specific artifact (native `CarHardwareManager`/`ResultManager`
   integration), not needed for phone + Android Auto projection. Only
   `androidx.car.app:app` is needed.

7. **Confusing phone-side UX**: giving `CarAppActivity` a `LAUNCHER` icon (needed
   for Android Automotive OS's own car launcher, which — unlike real Android Auto —
   *does* resolve tiles via `LAUNCHER` category) meant tapping the app icon on a
   real phone home screen triggered `CarAppActivity`'s built-in "connect to Android
   Auto" gate, which reads like a broken "needs update" message pointing to Play
   Store. Fixed: only `MainActivity` holds `LAUNCHER` now. Real Android Auto
   discovers the app via the `CarAppService` intent-filter, not via `LAUNCHER`, so
   this doesn't cost anything for the real target — it only means Android Automotive
   OS emulator testing needs `LAUNCHER` added back to `CarAppActivity` *temporarily*
   if you go back to testing there.

## Known issue: raw-`Surface` touch (bug #1 above)

On the Android Automotive OS emulator, tapping inside the `PlaybackScreen`'s WebView
area produced **zero** log output from `PlaybackScreen.onClick()` (confirmed via
temporary `Log.d` calls still present in `PlaybackScreen.kt` /
`WebViewSurfaceBridge.kt` — search `AutoTubeDebug` tag) — the host never even
invoked the callback. Meanwhile normal templated UI (list row taps, actions) worked
fine via the same input method. A `dumpsys window windows` dump showed the embedded
surface hosting the car template had `scaleFactor=0.0`, which looked like an
emulator-specific coordinate-mapping bug in `ControlledRemoteCarTaskView`, not our
code. That theory is now called into question by real-car testing showing keyboard
input also failing there — so this needs fresh investigation on the actual
`PlaybackScreen.surfaceCallback.onClick()` path with the debug logging still in
place, on a real car this time.

## Ideas for the video bug (priority — bug #2 above)

`WebView.draw(canvas)` is a software/bitmap capture path; hardware video decode
surfaces are known to not show up in it (common complaint across Android WebView
screenshot/recording use cases generally, not specific to this app). Possible
directions, none tried yet:
- Force WebView into `LAYER_TYPE_SOFTWARE` (`webView.setLayerType(...)`) — may force
  video onto the software path too, at a performance/battery cost.
- Investigate whether `WebSettings` has a flag to disable hardware video decode
  path for embedded `<video>` (forcing software decode).
- Consider whether `PixelCopy` (instead of `View.draw`) can capture what `draw()`
  can't — `PixelCopy` reads from the actual rendered `Surface`/`SurfaceView`, which
  may include hardware-composited content that `draw()` misses. Would need the
  WebView to actually be attached to a real window/surface first, which conflicts
  with the current "hidden, never-attached WebView" design — may need rethinking
  the bridge's whole approach for video specifically (e.g. an attached but
  off-screen-positioned WebView instead of a truly detached one).

## Features built this session (all verified via direct DB checks on-device)

- **Favorites add/delete**: `AddFavoriteScreen` (single-step `SearchTemplate` — a
  two-step URL-then-title flow was tried first and abandoned: swapping in a second
  `SearchTemplate` with a different `SearchCallback` via `invalidate()` doesn't
  rebind cleanly on-device, it keeps dispatching to the first template's callback).
  `FavoriteDetailScreen` for open/delete. `FavoritesEditScreen` reloads on every
  `onStart`, not just once, so returning to it reflects changes. `HomeScreen` does
  the same.
- **Watch history**: `PlaybackScreen` tracks live URL (via `onPageFinished`, since
  the user may navigate within the WebView away from the constructor's initial
  URL) and title (via `onReceivedTitle`), extracts a YouTube video ID by regex, and
  every 15s (+ on screen stop) saves position via
  `WebViewSurfaceBridge.currentPositionSeconds()` (reads
  `document.querySelector('video').currentTime` via `evaluateJavascript`).
  Confirmed working end-to-end including real position tracking.
- **"Save to favorites" action** in `PlaybackScreen`'s action strip, using the live
  URL/title.
- **Phone-side `MainActivity` redesign** + new `AboutActivity` (dev credit:
  developed by ihue.in, ihue.india@gmail.com).
- **New app icon** everywhere (from `resources/autotube.png` /
  `resources/autotube_trans.png`).

## Distribution / Play Console

- **Package name**: `dev.local.autotube`
- **Repo**: https://github.com/jithpillai/ihueAutoTube.git (`main`)
- **Privacy policy** (live): https://jithpillai.github.io/ihue-legal/autotube/
  — source in the separate `ihue-legal` repo (`/Users/prijith/proto/ihue-legal`,
  `autotube/index.html`), which also has a hueai app's policy as a style reference.
- **Release signing**: keystore at `app/keystore/autotube-release.jks`, credentials
  in `app/keystore/keystore.properties` (both gitignored — **never commit these**).
  **Back up the keystore file somewhere safe outside this machine** — if it's lost
  before enrolling in Play App Signing, or if Play App Signing wasn't used, future
  updates to the app become impossible under this package name. Enrolling in Play
  App Signing (offered/required during first Play Console upload) mitigates this,
  since Google then holds the real signing key and can help recover a lost upload
  key.
- **Distribution track**: Internal Testing (required specifically because real
  Android Auto's `NAVIGATION` category needs a Play-installed app — see point 5
  above; Internal Testing is the fastest way to satisfy that without a public
  listing).
- **Store listing assets**: screenshots at `requirements/screenshots/`
  (`phone_main.png`, `phone_about.png`).
- Manifest currently has **no** `<uses-feature android.hardware.type.automotive>`
  and **no** `androidx.car.app:app-automotive` dependency — keep it that way for
  Play Console to accept uploads (see point 6 above). If a genuine Android
  Automotive OS-native build is ever wanted, that needs to be a **separate**
  app/package, not a variant of this one.

## Suggested next steps

1. **Fix the video-playback bug** — highest priority, core feature is broken
   without it. Start with the `PixelCopy` idea above.
2. **Fix raw-Surface touch/keyboard input** on `PlaybackScreen` — investigate with
   the existing `AutoTubeDebug` logging on a real car (not emulator this time).
3. Once both are fixed, remove the temporary `AutoTubeDebug` logging from
   `PlaybackScreen.kt` and `WebViewSurfaceBridge.kt`.
4. Wire `DrivingStateGate` to real `CarHardwareManager` sensor data (currently a
   conservative "blank whenever not foregrounded" placeholder only).
5. Consider whether `androidx.car.app:app-automotive` is worth re-adding as a
   **separate build variant/flavor** if Android Automotive OS support is ever
   wanted alongside phone-projected Android Auto (they can't coexist in one Play
   Console app entry, per point 6 above).
