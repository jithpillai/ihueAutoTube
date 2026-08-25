# Dash Player — Progress Log

Formerly **AutoTube**. Personal Android Auto app that shows YouTube/web content on the car screen via a
hidden WebView rendered onto a `NavigationTemplate`'s raw `Surface`. This file is a
handoff snapshot — read it first in any new session before touching the code.

**Start with the current-state section below.** Older round-by-round sections are
chronological history only; some describe issues that are now resolved.

## Current state — Dash Player 4.0.1 (versionCode 32) — 2026-08-25

UI/UX-only release — no playback/render/data-layer behavior changed from 3.0.0 above.
All functionality carries forward unchanged; this section covers the visual pass only.

### What changed

- **Brand palette + car host theming**: new `values/colors.xml`/`styles.xml`, accent
  `#FF3B1A` (from `app_logo`'s play-button mark). Wired via `androidx.car.app.theme`
  meta-data (`carColorPrimary`/`carColorSecondary` etc.) — the first time this app has
  themed any host-drawn car chrome; previously unset entirely.
- **Car screen icons**: 15 new vector drawables (`ic_home`, `ic_star`, `ic_folder`,
  `ic_zoom`, `ic_menu`, `ic_play_circle`, `ic_delete`, `ic_movie`, `ic_replay10`,
  `ic_input`, `ic_stop`, `ic_history`, `ic_public`, `ic_check_circle`, `ic_add`),
  attached to every row/action across `HomeScreen`, `PlaybackMenuScreen`,
  `DisplayScaleScreen`, `FavoritesEditScreen`, `FavoriteDetailScreen`,
  `LocalVideoLibraryScreen`, `LocalVideoControlsScreen`, `BrowserScreen`,
  `SearchInPageScreen`, and `PlaybackScreen`'s previously bare-text "Menu" action.
  Shared helper: `car/CarIcons.kt`'s `carIcon(carContext, resId)`.
- **One-time car splash**: `HomeScreen` shows a ~2.5s `MessageTemplate` (big centered
  Dash Player logo, title, "Powered by ihue" text) on the session's first launch only,
  then flips to the normal favorites list — same `Screen` instance throughout (a
  `showSplash` flag + `lifecycleScope.launch { delay(2500); ... invalidate() }`), so
  there's no new back-stack entry and no navigation-timing risk. Reused for
  `popToRoot()` returns to Home, which don't re-show it.
- **Phone-side redesign** (`MainActivity`/`AboutActivity`/`FavoritesActivity`): new
  dark theme (`Theme.DashPlayer.Phone`, applied only to these three activities — not
  `CarAppActivity`), card-based sections via shared helpers in `UiStyle.kt`
  (`sectionCard`/`primaryButton`/`secondaryButton`), still plain framework widgets, no
  new Gradle dependency. `MainActivity`'s "Powered by ihue" moved from a small bottom
  footer to a medium (~40dp), clearly-visible row directly under the logo/title
  (`PoweredByView.kt` gained a `heroSize` param). `FavoritesActivity` rows now show a
  type icon (channel/playlist vs. site) and an icon-based remove button.
- ~~**Adaptive launcher icon**~~ — attempted, broke the icon (see "Fixes after first
  look" below), reverted.

### Fixes after first look (still 4.0.0, versionCode bumped 28 → 29)

User feedback after seeing the first 4.0.0 build:

1. **Launcher icon was cropped.** Root cause: the adaptive-icon foreground
   (`ic_launcher_foreground.xml`) referenced `app_logo` — which lives in
   `drawable-nodpi/` — inside an `<inset><bitmap android:gravity="center">`. `nodpi`
   means the 512x512 PNG is used at 1:1 pixel scale with no density-bucket scaling,
   and `gravity="center"` (instead of the default `fill`) draws the bitmap at that
   native size instead of scaling it to the inset bounds — so on any real device it
   rendered far larger than the icon canvas and got clipped. **Fix**: removed the
   adaptive icon entirely (deleted `mipmap-anydpi-v26/ic_launcher*.xml` and the two
   `ic_launcher_background`/`ic_launcher_foreground` drawables) — back to the original
   legacy per-density `mipmap-*/ic_launcher*.png`, per explicit user preference ("use
   the original app icon itself").
2. **ihue_logo's black wordmark/tap-icon was invisible on the new dark theme.**
   Generated `drawable-nodpi/ihue_logo_white.png` — same file with only the near-black
   pixels (threshold-based, RGB < 60) repainted white, colorful splash drops
   untouched — via a one-off Python/PIL script (not checked in). All `R.drawable
   .ihue_logo` references (`PoweredByView.kt`, `HomeScreen.kt`'s row) now point at
   `ihue_logo_white`. The original `ihue_logo.png` is kept, unused, in case a future
   light-background context wants it.
3. **No phone-side splash screen existed** — the "hero" MainActivity redesign wasn't
   an actual splash, just the top of the landing activity. Added `SplashActivity.kt`:
   logo + "Dash Player" + hero "Powered by ihue", shown for 1.6s then launches
   `MainActivity` and finishes itself. Now the `LAUNCHER` activity in the manifest
   (`MainActivity` lost `LAUNCHER`/`exported`, only reachable from `SplashActivity`
   now).

4. **"Powered by ihue" row rendered off to the side instead of centered** (seen on the
   phone splash). Root cause: `PoweredByView.kt`'s row `LinearLayout` never set its own
   `layoutParams`, so when added to a vertical parent it got the parent's default
   `MATCH_PARENT`-width child params — and the row's own `gravity` was
   `CENTER_VERTICAL` only, so its content (text + logo) sat left-aligned within that
   full-width row. The app-name `TextView` above it looked fine only because `TextView
   .gravity` centers its own text regardless of the view's width — a `LinearLayout`
   row needs `CENTER_HORIZONTAL` explicitly. **Fix**: `gravity = Gravity.CENTER`
   (both axes) on that row — fixes it everywhere the shared helper is used (splash,
   MainActivity hero, AboutActivity footer), not just the splash where it was noticed.

5. **Home screen row order**: the three fixed entry points (Browse full YouTube / Open
   another site / Play videos from phone) were being pushed below the favorites list
   instead of anchoring the top. `HomeScreen.onGetTemplate()` now adds those three
   rows first, favorites after, history last — matches the code comment's original
   intent ("kept above...") which the actual row-add order didn't.

### Verification status

- `./gradlew assembleRelease` and `bundleRelease` both succeed — compile/resource-merge
  verified only. **Not yet tested on a real car or DHU** — the host theme's actual
  visual effect, dark-theme contrast, and both splash screens' timing/look still need
  a real pass before calling this confirmed.
- Signed AAB built this round: `app-release.aab`, versionCode 32 / versionName 4.0.1,
  ready for Play Console Internal Testing upload. **Convention going forward: bump
  `versionCode` on every rebuild, even a versionName-only or no-code-change rebuild**
  — Play Console rejects re-uploading a versionCode it's already seen.

## Current state — Dash Player 3.0.0 (versionCode 27) — 2026-08-25 (superseded above)

### Rebrand and release identity

- Product name, Android labels, package/namespace, source packages, database names,
  and car service are now **Dash Player** / `com.ihue.dashplayer`.
- The current configured release is **3.0.0 (27)**. Treat older AutoTube version
  numbers below as historical only.

### Confirmed working

- **YouTube/web playback**: the compact desktop layout, touch scrolling, back/search
  actions, favorites, history, and fixed VirtualDisplay scale behavior remain the
  proven baseline from real-car tests.
- **Phone videos**: the user chooses a folder in the phone app, then opens
  **Play videos from phone** in Android Auto. Native ExoPlayer renders both video
  and audio to the car Surface successfully.
- The prior local-video ANR/crash at roughly 15–25 seconds is fixed. The unstable
  foreground MediaSession-service path was removed; local playback now remains in
  the app process.
- Local playback includes play/pause, Stop playback, a `+10` action, Back 10
  seconds, and Go to position input (`1:30:00`, `90:00`, or `3` for three minutes).

### Deliberately parked: local-video seeking

- On the tested phone/document-provider combination, the file plays normally but
  **every seek restarts from 0:00**. This affects `+10`, Back 10 seconds, and Go
  to position alike; it is not a control/UI issue.
- The playback duration may likewise appear as `0:00`. A metadata fallback is used
  for the text where available, but it cannot make the source seekable.
- Two attempts were rejected by real-car testing: retaining an explicit MIME type
  did not restore seeking, and replacing the renderer with platform `MediaPlayer`
  produced a black screen with no audio. The working ExoPlayer renderer was restored.
- Do **not** stack more speculative seek fixes onto the working renderer. Options
  for a future dedicated effort are: remove/disable the misleading seek controls,
  or copy a selected video to app-private storage before playback (reliable seeking,
  but a potentially large storage/time cost for movies).

## Fullscreen support test — v1.0.13 / versionCode 13 — unverified

The YouTube fullscreen button previously showed "Full screen not available" because
`WebChromeClient` did not implement `onShowCustomView`; Android WebView therefore
reported fullscreen unsupported. This build adds a `FrameLayout` root inside the
existing VirtualDisplay `Presentation`. On a fullscreen request, Chromium's custom
video View replaces the WebView inside that same root, so ImageReader should keep
capturing it normally. `onHideCustomView` restores the WebView, and the app's Back
arrow exits fullscreen before using in-page browser history.

Expected scope: it fills AutoTube's raw playback Surface only; Android Auto system
UI outside that Surface cannot be hidden or taken over. Needs a real-car AAB test.
If it renders black, fails to enter, or breaks input, revert this isolated custom-view
handling while retaining all current layout/scroll improvements.

## Current confirmed state — v1.0.10 / versionCode 12 — 2026-08-24

**Confirmed by real-car testing: the active playback bugs are fixed.** Video and
audio work; YouTube holds its desktop two-column layout; the page stays compact
without touch/scroll-triggered zoom glitches; and native touch scrolling works
smoothly. This is the current handoff state.

### Stable display-size design

- The hidden WebView renders into a `VirtualDisplay` at a configurable multiple
  of the car Surface dimensions, then its complete frame is scaled down to the
  physical car Surface. The default **Compact (67%)** option uses `1.5x`, giving
  YouTube a genuinely desktop-width viewport rather than trying to fool it with
  density or JavaScript alone.
- This VirtualDisplay multiplier is now the **only** page-size controller.
  `setInitialScale`, overview mode, and non-default text zoom were removed,
  because WebView applies those asynchronously after layout and they produced
  the earlier multi-step zoom/re-render glitch.
- `Menu → Display size` persists one explicit setting: Normal (100%), Balanced
  (80%), Compact (67%, default), or Extra compact (57%). A size changes only
  when the user selects it and the playback Surface is recreated; it must not
  change during regular page interaction.
- The desktop Chrome user-agent and `useWideViewPort` remain. The old injected
  touch-capability spoof was removed because the real enlarged viewport is what
  proved reliable.

### Confirmed input and playback UI

- `SurfaceCallback.onScroll` now maps directly to `WebView.scrollBy`, and the
  optional host fling maps to `WebView.flingScroll`. This replaced the rejected
  synthetic DOWN/MOVE/UP gesture replay. **Touch scrolling is confirmed good.**
- The temporary translucent in-surface up/down scroll buttons were completely
  removed after native touch scrolling was confirmed.
- Playback action strip, left to right: **Back arrow**, **Search icon**, and
  **Menu**. Back is immediate and browser-style: it goes back within WebView
  history first, then leaves playback only when there is no page history.
- Menu contains Home, Manage favorites, Display size, and Save to favorites.
  Search is now direct rather than a menu row, but still opens the host
  `SearchTemplate` so typing works with Android Auto's keyboard.
- Favorite URLs are tracked from WebView navigation start and committed history,
  rather than `onPageFinished`. This avoids YouTube's late Home-page completion
  overwriting the URL of a video/page being saved. Favorites created by older
  builds that already contain `youtube.com` Home must be deleted and saved again;
  their original target URL was not stored and cannot be reconstructed.

### Notes for future work

- Do not reintroduce WebView initial-scale/overview/text-zoom changes without a
  real-car test; the single VirtualDisplay scale is intentional.
- `openFresh()` still intentionally reuses the shared WebView while the session
  model remains conservative. Revisit only if a future feature specifically
  needs an explicit "new/close session" action.
- Real parked/speed sensor integration in `DrivingStateGate` and complete
  Google-login persistence remain future enhancements, not current playback
  blockers.

## Real-car test results, round 1 (v1.0.0 / versionCode 2) — 2026-08-24

Tested the video/search/WebView-kill/cookie build in the actual car. Results:

1. **Video: still audio-only, no picture.** `LAYER_TYPE_SOFTWARE` did not fix it.
2. **Search: button + host keyboard now appear correctly** (that part of the
   architecture fix worked) — but submitting a search did not visibly navigate
   anything. Root cause, per user's own correct hypothesis: caused by #3 below.
3. **WebView-kill was firing far too eagerly.** Opening the Search screen (which
   merely covers `PlaybackScreen`, not a real exit) was killing the WebView —
   `Screen.onDestroy` fired on real hardware in cases that weren't a genuine
   "removed from back stack for good," contrary to the assumed Fragment-like
   back-stack model. This explains #2 above: by the time the search query was
   submitted, `bridge` was likely already a destroyed WebView, so `loadUrl()`
   silently no-op'd.
4. **Google sign-in now reachable** (since keyboard/search works) but doesn't
   persist — same root cause as #3: the WebView holding the session gets killed
   too readily.

**Redesign in response (this round, not yet tested):** stopped tying WebView
teardown to `Screen` lifecycle events entirely — per user's explicit direction,
kill/reuse now follows navigation *intent*, not lifecycle timing:
- New `bridge/PlaybackSession.kt`: a singleton holding the one shared
  `WebViewSurfaceBridge`/`WebView` for the whole app, independent of any single
  `PlaybackScreen` instance.
- `PlaybackScreen`'s constructor is now private; entry points go through two
  factories: `openOrResume()` (reuses the existing session if one's open — used
  only by Home's "Browse full YouTube") and `openFresh()` (always kills any
  existing session first — used by every deliberate destination pick: favorites,
  history/"Continue watching", typed URL, search submission, saved sites).
  `PlaybackScreen.onDestroy` no longer touches the WebView at all.
- New explicit **"Close" action** in `PlaybackScreen`'s action strip (4th action
  alongside Back/Search/Save — unverified this still fits the template's action
  limit) — the only other way to kill the shared WebView, via
  `PlaybackSession.close()` + `screenManager.popToRoot()`.
- `WebViewSurfaceBridge.currentUrl`/`currentTitle` are now the single source of
  truth (moved off `PlaybackScreen`'s per-instance fields), since the bridge/
  WebView now outlives any individual screen instance.

## Video capture rewrite (this round, not yet tested) — VirtualDisplay + ImageReader

`LAYER_TYPE_SOFTWARE` didn't fix video because it never addressed the real cause:
Chromium's `<video>` hardware decode goes through its own internal SurfaceTexture,
independent of the outer WebView's Android-level layer type — `View.draw(canvas)`
into a software `Surface.lockCanvas()` canvas was never going to see it, regardless
of layer type.

**New approach in `WebViewSurfaceBridge.kt`**: the WebView is now hosted as the
content view of an `android.app.Presentation` shown on a private, in-process
`VirtualDisplay` (`DisplayManager.createVirtualDisplay`, flags
`VIRTUAL_DISPLAY_FLAG_PRESENTATION | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY` — no
special permission needed for a private, non-secure virtual display). This gives
the WebView a **real attached window**, so it renders exactly as it would on a
physical screen — hardware video decode included — while never being visible
anywhere physical. The VirtualDisplay's output feeds an `ImageReader`; each
`onImageAvailable` callback copies the frame's buffer into a reused `Bitmap` and
blits it onto the car's actual `Surface` (same destination-side code as before,
`Surface.lockCanvas()` + `Canvas.drawBitmap`). The old polling render-loop timer
(`frameIntervalMs`/`renderLoop`) is gone — frames now blit exactly when the
ImageReader produces one, driven by the VirtualDisplay's own compositing rate.

**Real risks, unverified on real hardware:**
- Whether a private `VirtualDisplay` genuinely needs no special permission in
  practice on this specific phone + Android Auto projection setup (public API
  says no, but this combination hasn't been tested).
- Whether `Presentation` actually renders hardware video correctly on a virtual
  (non-physical) display — the theory is sound (it's a real attached window) but
  unconfirmed.
- Whether reparenting the single long-lived WebView between Presentations across
  attach/detach cycles works cleanly (`(webView.parent as? ViewGroup)?.removeView`
  before each new `Presentation.setContentView`).
- CPU cost of the per-frame `ImageReader` → `Bitmap` copy at whatever fps the
  VirtualDisplay produces (no artificial fps cap anymore, unlike the old
  12fps-capped timer) — could be smoother, or could be worse if uncapped.

**Also changed as part of this rewrite:** removed `webView.setLayerType
(LAYER_TYPE_SOFTWARE)` — that was specifically a workaround for the old
software-canvas capture path, and is actively counterproductive now (we want
real hardware compositing so ImageReader can capture it).

## Real-car test results, round 2 (v1.0.2) — 2026-08-24

**Video now works — both Shorts and regular videos, picture and audio.** The
VirtualDisplay + Presentation + ImageReader rewrite is confirmed fixed. This was
the top-priority blocker; it's done.

Remaining issues found, with screenshots (`resources/screenshots/`):

1. **The reload bug, not actually a "kill" bug.** Search would show a glimpse of
   the typed query, then revert; navigating around felt like everything kept
   "refreshing." Root cause found by inspection, not logs: `PlaybackScreen`'s
   `forceLoad` flag was being honored on *every* `onSurfaceAvailable` call, but
   `onSurfaceAvailable` fires again any time the raw Surface is torn down and
   recreated — which happens whenever another screen (e.g. the Search flow)
   merely covers `PlaybackScreen`. So a session opened via `openFresh`
   (`forceLoad = true`) would silently reload its original URL over top of
   whatever the user had since navigated to, including a just-submitted search.
   **Fixed**: `forceLoad` is now one-shot (`pendingForceLoad`, consumed and
   cleared after the first real load) — see `PlaybackScreen.kt`. Unverified on
   real hardware.
2. **Per explicit user direction, `openFresh`'s actual WebView-killing is
   temporarily suppressed** — it now behaves like `openOrResume` (reuse instead
   of kill). Reasoning: with the reload bug likely compounding the perceived
   "killing" behavior, the plan is to validate the reuse path in isolation first.
   **`PlaybackSession.close()` should be restored inside `openFresh` once the
   reload-bug fix is confirmed stable** — search this file for "TEMPORARILY" in
   `PlaybackScreen.kt`. Also removed the standalone "Close" action from the UI
   (see UI section below) since there's currently no code path that needs it —
   nothing calls `PlaybackSession.close()` at all right now except the (currently
   bypassed) `openFresh` body.
3. **Scroll didn't work at all.** Root cause: `dispatchScroll`'s touch origin was
   hardcoded to `(0, 0)` — every scroll gesture was replayed as a drag confined to
   the WebView's top-left corner pixel, regardless of where the actual gesture
   was. **Fixed**: `PlaybackScreen` now tracks the live surface width/height and
   passes the surface center as the origin instead. Unverified on real hardware.
4. **UI: YouTube renders in mobile-app layout on the wide car screen** —
   screenshots show the mobile bottom tab bar (Home/Shorts/You) and a video
   letterboxed into a small centered box with large black bars either side,
   instead of using the available width. Root cause: WebView's default
   user-agent identifies as a phone, so YouTube (like most sites) serves its
   mobile-optimized responsive layout regardless of the actual Surface pixel
   dimensions. **Fixed (unverified)**: `WebViewSurfaceBridge` now sets a desktop
   Chrome user-agent string, plus `useWideViewPort`/`loadWithOverviewMode`/
   `textZoom=100` and disabled pinch-zoom controls (touch replay has no pinch
   gesture anyway) as a belt-and-suspenders measure. Should make YouTube serve
   its widescreen grid/player layout, which actually fits a landscape display —
   addresses both "everything looks zoomed in" and "can't see more than one
   video without scrolling."
5. **Too many action-strip buttons, and Back always exited to the landing page
   instead of behaving like browser back.** Redesigned per explicit user
   request:
   - **Back** is now a custom action (not the `Action.BACK` constant): if the
     WebView has in-page history (`WebView.canGoBack()`), it does
     `WebView.goBack()` first — browser-style — and only pops the Car App screen
     once there's no more in-page history left.
   - **Home** (new): `screenManager.popToRoot()` — jump straight to the landing
     page. Does not kill the session, consistent with the resumable-session
     model (matches "Home" being navigation-only, not a stop button).
   - **Search** and **Save**: unchanged.
   - **Close was removed** to cut the action strip back down to 4 total buttons
     (adding "Home" while keeping "Close" would have made 5). This also means
     there is currently **no way to fully stop/kill the shared session from the
     UI** — acceptable for now since `openFresh`'s kill is also suppressed (see
     #2), but worth reconsidering once the reload-bug fix is validated and
     killing gets re-enabled: may want Close back, or to fold "stop" into some
     other action.

## Round 3 changes (v1.0.4 / versionCode 6) — 2026-08-24, unverified

User feedback after round 2: video confirmed still working; the "mobile layout"
fix (desktop UA) partially worked (screenshots showed a desktop-style header) but
YouTube's own client-side JS was switching back to mobile layout mid-session —
user's own diagnosis, confirmed by screenshots: "initially renders as web view,
but then automatically switches to mobile." Also asked to consolidate the
Back/Home/Search/Save action-strip buttons into a single menu.

1. **Stronger desktop-layout forcing.** Two changes in `WebViewSurfaceBridge.kt`:
   - The `VirtualDisplay` now uses a hardcoded baseline density
     (`DisplayMetrics.DENSITY_DEFAULT` = 160) instead of `container.dpi`. Theory:
     `container.dpi` (tuned for the car host's own map/template rendering) was
     making the WebView's CSS dp-width come out narrow enough to still trip
     YouTube's responsive mobile breakpoint even with a desktop UA. Baseline
     density (1dp = 1px) maximizes CSS dp-width for the same physical pixel
     count, pushing it solidly into desktop-breakpoint territory regardless of
     what the host reports.
   - `onPageStarted` now spoofs `navigator.maxTouchPoints = 0` via injected JS,
     to stop YouTube's client-side JS from re-detecting touch capability and
     switching back to mobile mid-session (the standard "force desktop site"
     trick). Not airtight — `onPageStarted` fires early but isn't guaranteed
     before every inline `<head>` script — but low-risk to try alongside the
     density fix.
2. **Action strip consolidated into a single "Menu" button.** New
   `PlaybackMenuScreen.kt` (a `ListTemplate` list) — Back/Home/Search/Save are
   now rows in a menu opened from one action-strip button, instead of 4 separate
   buttons. Each row pops the menu screen (revealing `PlaybackScreen`) before
   invoking its action.

## Emulator-based iteration attempted, hit friction, reverted to AAB workflow

Tried switching to a faster local loop: there's a persistent Android Automotive
OS emulator already running (`emulator-5556`, connected via `adb`), which runs
the same app code (PlaybackScreen/WebViewSurfaceBridge) as real Android Auto —
legitimate for iterating on rendering/layout/menu bugs, though not for
Android-Auto-specific quirks like the Play-install gate. Hit two snags before
the user asked to abandon this for now and go back to AAB builds:

1. **Debug builds (`assembleDebug`) are missing the entire
   `androidx.car.app.activity` package from their dex** — confirmed via
   `dexdump` across all 4 `classes*.dex` files in the built APK: zero classes
   from that package, despite the manifest correctly declaring
   `CarAppActivity` and ~579 other `androidx.car.app.*` classes being present.
   Result: `ClassNotFoundException` instantiating `CarAppActivity`, reproducible
   across a clean rebuild. This is **specific to local debug-APK assembly** —
   release builds (`bundleRelease`/`assembleRelease`) have worked correctly on
   the real car all session, so this is a local AGP/dexing quirk for the debug
   variant on this machine, not a code bug. **If local debug-APK testing is
   wanted again later, use `assembleRelease` instead of `assembleDebug`** (a
   signed release APK installs fine via `adb install` — confirmed working) —
   don't spend time debugging the debug-variant dexing issue unless it matters.
2. **The AAOS emulator's own launcher grid resolves app tiles via the
   `LAUNCHER` category** (unlike real Android Auto) — since only `MainActivity`
   carries `LAUNCHER`, tapping the "AutoTube" tile opened the phone-side landing
   page, not the car player. This was already a known, previously-documented
   requirement (see "How we got here" point 7 below) — temporarily adding
   `LAUNCHER` to `CarAppActivity` created a separate "AutoTube Player" tile as
   expected, but tapping *that* tile silently did nothing (no crash, no focus
   change) even after the dexing issue was fixed — not root-caused before the
   workflow switch. Direct `am start -n .../CarAppActivity` did launch the
   activity process (confirmed via `dumpsys activity activities`) but it never
   held foreground focus (`"top resumed state loss timeout"` in logcat) and
   fell back to the launcher — also not root-caused.

**The `LAUNCHER` category addition to `CarAppActivity` was reverted** before
building this round's AAB — it must never ship in a release build (confirmed
still absent in the current manifest).

## Real-car test results, round 3 (v1.0.4) — 2026-08-24 — SESSION HANDOFF

**Read this section first if picking up the resolution/scroll bugs.**

Tested v1.0.4 (Menu consolidation + density-override/touch-spoof desktop-layout
attempt) on the real car:

1. **Menu button works well** — Back/Home/Search/Save consolidated into one
   "Menu" list screen, confirmed good.
2. **Resolution/mobile-layout bug is NOT fixed.** Still reverts to the
   phone-style mobile layout despite: (a) desktop user-agent, (b) forcing
   `VirtualDisplay` density to baseline 160 instead of `container.dpi`, (c)
   spoofing `navigator.maxTouchPoints = 0` via injected JS on `onPageStarted`.
   **All three attempts so far have failed to hold.** This means either the
   density theory is wrong (dp-width still isn't the deciding factor), the
   touch-spoof isn't taking effect early enough (YouTube's own detection script
   may run before `onPageStarted` fires, or may use a different signal than
   `maxTouchPoints` — e.g. `ontouchstart in window`, `navigator.userAgentData`,
   or a server-side redirect keyed on UA rather than client JS at all), or
   there's some other mechanism entirely not yet identified. **Needs fresh
   investigation, ideally with actual page inspection** (e.g. temporarily log
   `document.documentElement.clientWidth`/`navigator.userAgent`/
   `navigator.maxTouchPoints` from the live page via `evaluateJavascript` to see
   what YouTube's JS is actually observing) rather than another guess-and-ship
   cycle.
3. **Scroll still doesn't work.** The center-origin fix (round 2) didn't
   resolve it. Possible causes not yet investigated: the single
   DOWN→MOVE→UP synthetic gesture may not clear Chromium's touch-slop threshold
   to be recognized as a scroll at all (may need multiple intermediate MOVE
   steps to read as a real drag); `SurfaceCallback.onScroll`'s
   `distanceX`/`distanceY` sign/unit convention relative to what a car's rotary
   controller or touchpad actually sends is unverified — worth adding temporary
   `AutoTubeDebug` logging of the actual values received to check they're
   sane before touching the dispatch logic further.

**Suggested approach for the next session**: before trying another blind fix
for either bug, add targeted diagnostic logging/JS probes and get one real-car
test cycle purely for *data*, not a fix attempt — both of these bugs have now
survived two fix attempts each, suggesting the current mental model of the root
cause is wrong somewhere.

## Reminder: only a Play-installed build is testable on the real car

**A sideloaded/debug APK will never show up in real Android Auto's app list**, no
matter how it's installed (`adb install`, "Unknown sources" enabled, etc) — see
point 5 under "How we got here" below for the full logcat evidence
(`"Package is not installed by play"` → denied). This isn't a signing or
permissions issue, it's a hard platform check specific to real Android Auto (not
reproduced on the Android Automotive OS emulator/DHU). **The only build worth
producing for real-car testing is a signed release AAB uploaded to Play Console
Internal Testing** — that's what actually gets "installed by com.android.vending"
and clears the check. Don't bother building/sharing a debug APK for car testing;
it's only useful for phone-side (`MainActivity`) sanity checks or emulator/DHU work.

## In progress — fixes attempted this session, need real-car verification (2026-08-24)

Per user priority ("first thing first": video, then search input — the two things
without which the app doesn't work at all), both were addressed with code changes.
**Neither has been tested on a real car yet** — build and verify next session/drive:

1. **Video bug (#2)**: `WebViewSurfaceBridge.kt` init now calls
   `webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)`. Rationale: the render
   loop's canvas comes from `Surface.lockCanvas()` (software), and hardware video
   compositing (`SurfaceTexture`) never shows up in a software `draw()` capture.
   Forcing the software layer should make Chromium fall back to a compositing path
   `draw()` can actually see, at some perf/battery cost. **Unverified** — if picture
   still doesn't appear, next try the `PixelCopy` approach from "Ideas for the video
   bug" below (bigger change: requires the WebView to be attached to a real, if
   off-screen, window).
2. **In-page search / keyboard input (#1)**: root-caused, not just patched around.
   `PlaybackScreen`'s WebView is a raw `Surface` — a bitmap feed to the host with no
   attached Android window — so there is no window for a system IME to anchor to,
   regardless of touch-dispatch accuracy. The old `AutoTubeDebug` touch-coordinate
   investigation was solving the wrong layer for this specific case (it may still
   matter for scroll/regular taps, just not for text entry). Fix: added
   `SearchInPageScreen.kt` (a small reusable `SearchTemplate` wrapper, same
   host-keyboard/voice-input pattern already proven in `BrowserScreen`) and a new
   "Search" action in `PlaybackScreen`'s action strip
   (`PlaybackScreen.kt:openSearch/buildSearchUrl`). Submitting a query there
   navigates the **existing** WebView in place via `bridge.loadUrl(...)`
   (deliberately not pushing a new `PlaybackScreen`, to avoid adding another
   instance to bug #4 below) — YouTube URLs go to
   `youtube.com/results?search_query=...`, anything else falls back to
   `UrlUtils.normalizeUrl`. This changes the UX from tapping YouTube's own in-page
   search icon to tapping an app-level "Search" button — confirmed with the user as
   the right tradeoff given the architecture. **Unverified on-device.**
3. **Orphaned-WebView bug (#4)**: `WebViewSurfaceBridge.kt` gained a `destroy()`
   method (stop render loop, `stopLoading()`, `onPause()`, `loadUrl("about:blank")`,
   `CookieManager.flush()`, `WebView.destroy()`). `PlaybackScreen.kt` now overrides
   `onDestroy` (not just `onStop`) to call it — `onDestroy` only fires when the
   screen is actually removed from the back stack (e.g. Back), unlike `onStop`,
   which also fires when merely covered by a pushed screen (e.g. opening the new
   Search screen) and must NOT kill the WebView in that case. This should stop the
   exact behavior reported: hit Back, video audio kept playing in the background
   with no way to reach it again. Doesn't change the "push a new instance every
   time" navigation pattern itself, just makes sure old instances actually die
   instead of leaking. **Unverified on-device.**
4. **Login/session persistence (#3), partial**: `WebViewSurfaceBridge.kt` init now
   explicitly enables `CookieManager.setAcceptCookie` /
   `setAcceptThirdPartyCookies`, and `destroy()` calls `CookieManager.flush()` before
   tearing down, so whatever session cookies exist are guaranteed to hit disk before
   a screen/WebView goes away. **This is not a login feature** — no sign-in UI was
   built. And there's a real open question for when that UI does get built: Google
   has blocked OAuth/sign-in inside embedded WebViews since ~2021 ("This browser or
   app may not be secure") as an anti-phishing policy — a generic `WebView` may
   simply refuse to complete a Google login regardless of keyboard/touch support.
   If so, account login would need a different mechanism entirely (e.g. Custom
   Tabs / `AccountManager` + token handoff into the WebView's cookies), which is a
   materially bigger feature than "fix the keyboard." Needs verifying against the
   current YouTube/Google login flow before investing in it.

## Current state (superseded — see "SESSION HANDOFF" above for the live summary)

*(This section describes state from earlier in the 2026-08-24 session, before the
video/search/WebView-reuse/menu fixes below. Kept for historical context on how
each bug was originally found. For what's actually true right now, read the
"Real-car test results, round 3 ... SESSION HANDOFF" section near the top of
this file instead.)*

**The app is confirmed working on real Android Auto** (phone-projected, actual car,
not just emulators): it appears in the car's app list, opens, and navigates between
screens. Getting to this point took most of this session — see "How we got here"
below for the non-obvious platform issues that were blocking it.

Original four bugs found during first real-car testing (since resolved or
superseded — see round 1-3 sections above for what actually happened):

1. On-screen keyboard / voice input didn't appear — resolved via the
   `SearchInPageScreen` architecture (host `SearchTemplate`, not raw-Surface
   typing). Superseded by the Menu-button consolidation in round 3.
2. Video was audio-only — resolved via the VirtualDisplay/ImageReader rewrite
   (round 1-2). **Confirmed working on real hardware.**
3. No account login flow / session persistence unverified — cookie persistence
   improved (round 1), but no actual login UI was ever built, and real login
   testing hasn't happened. Still open, low priority relative to the round-3
   handoff bugs.
4. Orphaned background WebViews / can't return to a playing video — resolved via
   `PlaybackSession` (shared WebView, intent-based kill/reuse) in round 2,
   though `openFresh`'s actual kill is currently suppressed pending validation
   (see round 2 section) — re-enabling it is a loose end worth revisiting once
   the round-3 bugs are fixed.

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

- **Package name**: `com.ihue.dashplayer`
- **Repo**: https://github.com/jithpillai/ihueDashPlayer.git (`main`)
- **Privacy policy** (live): https://jithpillai.github.io/ihue-legal/dashplayer/
  — source in the separate `ihue-legal` repo (`/Users/prijith/proto/ihue-legal`,
  `dashplayer/index.html`), which also has a hueai app's policy as a style reference.
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
2. **Fix orphaned-WebView / can't-get-back-to-playing-video bug (#4 above)** —
   independent of the other bugs, and currently the most disruptive on real drives
   (stuck audio with no way to reach the screen playing it). Give `PlaybackScreen`
   real teardown (pause + destroy the WebView when popped for good) and reconsider
   push-a-new-instance-every-time in favor of reusing/returning to a single live
   playback instance.
3. **Fix raw-Surface touch/keyboard input** on `PlaybackScreen` — investigate with
   the existing `AutoTubeDebug` logging on a real car (not emulator this time).
4. **Add account login (#3 above)**, once #3's prerequisite (keyboard input, i.e.
   item 3 here) is fixed — then verify session/cookie persistence actually survives
   an app force-stop + relaunch before considering it done.
5. Once video, keyboard, and orphaned-WebView bugs are fixed, remove the temporary
   `AutoTubeDebug` logging from `PlaybackScreen.kt` and `WebViewSurfaceBridge.kt`.
6. Wire `DrivingStateGate` to real `CarHardwareManager` sensor data (currently a
   conservative "blank whenever not foregrounded" placeholder only).
7. Consider whether `androidx.car.app:app-automotive` is worth re-adding as a
   **separate build variant/flavor** if Android Automotive OS support is ever
   wanted alongside phone-projected Android Auto (they can't coexist in one Play
   Console app entry, per point 6 above).
