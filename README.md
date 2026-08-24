# AutoTube — personal Android Auto video app (scaffold)

Personal-use only. Not for distribution. Declares the `NAVIGATION` car-app category
specifically to get a raw `Surface` (instead of a fixed template UI) so a hidden
`WebView`'s frames can be drawn onto the car screen — see comments in
`bridge/WebViewSurfaceBridge.kt` and `car/PlaybackScreen.kt` for exactly how and why.

## What's here
- `car/AutoTubeCarAppService.kt`, `car/AutoTubeSession.kt` — entry point Android Auto binds to
- `car/HomeScreen.kt` — favorites + continue-watching chooser (`ListTemplate`)
- `car/BrowserScreen.kt` — free-form URL entry via `SearchTemplate` (real on-screen keyboard)
- `car/PlaybackScreen.kt` — the actual `NavigationTemplate` + `Surface` + WebView bridge
- `car/FavoritesEditScreen.kt` — stub; add/delete flows are a TODO
- `car/DrivingStateGate.kt` — safety gate; **currently a conservative placeholder**
  (blanks the surface whenever the screen isn't foregrounded). Read the comments —
  wiring real speed/parking-brake sensor data via `CarHardwareManager` is the next
  priority before you'd trust this in the car unattended.
- `bridge/WebViewSurfaceBridge.kt` — the render loop (WebView → Bitmap → Surface) and
  touch replay (car taps → synthetic `MotionEvent`s on the hidden WebView)
- `data/` — Room DB: `SavedItem` (channels/playlists/sites, shared table) + `WatchHistory`

## This has NOT been compiled or run yet
I don't have an Android SDK or emulator in this environment, so none of this has been
built or tested — treat it as a structurally-correct starting point, not working code.
Expect real compile errors on first sync (API surface details like exact `Action`/
`SearchTemplate` builder methods can drift between Car App Library versions).

## Next steps (hand this to Claude Code in Android Studio)
1. Open this folder in Android Studio — let it generate the Gradle wrapper on first sync.
2. Fix compile errors against whatever `androidx.car.app:app` version actually resolves
   (currently pinned to 1.4.0 in `app/build.gradle.kts` — bump if needed).
3. Set up the Desktop Head Unit (DHU) or connect your Nothing phone via USB with
   Android Auto's "Unknown sources" developer setting enabled, and run the app.
4. Verify the render loop actually shows YouTube in `PlaybackScreen` before building
   out favorites-editing or the driving-state sensor wiring — that's the highest-risk,
   highest-value piece to validate first.
5. Flesh out `FavoritesEditScreen` (add/delete UI) and wire `DrivingStateGate` to real
   `CarHardwareManager` sensor data once the render loop is confirmed working.
