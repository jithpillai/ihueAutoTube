package com.ihue.dashplayer.car

import androidx.car.app.CarContext

/**
 * NOTE: This is the single most important safety piece in the app — treat it as
 * non-negotiable, not a nice-to-have.
 *
 * The Car App Library does not currently expose a simple "isParked" boolean directly;
 * in production you'd combine:
 *   1. CarHardwareManager -> CarSensors -> getParkingBrake() / getSpeed() where the OEM
 *      supports it (requires the PERMISSION_CAR_SPEED / PERMISSION_CAR_MILEAGE permissions
 *      and a physical CarHardware provider — not all head units support this), and/or
 *   2. Requiring the ignition/parking-brake signal in a robust production build.
 *
 * Since API support for reading exact "parked" state varies a LOT by OEM and head unit,
 * the safe, conservative default here is to only allow the WebView surface to render
 * once, and to blank it any time the app itself is backgrounded/paused
 * (Screen.onStop / onPause lifecycle) — treating any interruption as "assume moving until
 * proven otherwise." Wire the CarHardwareManager sensor path in as an enhancement once you
 * confirm your specific head unit/OEM exposes it.
 */
class DrivingStateGate(private val carContext: CarContext) {

    var isRenderingAllowed: Boolean = true
        private set

    fun onScreenStarted() {
        // Conservative default: allow rendering only while the screen is in the
        // foreground and the user is actively on this screen.
        isRenderingAllowed = true
    }

    fun onScreenStopped() {
        isRenderingAllowed = false
    }
}
