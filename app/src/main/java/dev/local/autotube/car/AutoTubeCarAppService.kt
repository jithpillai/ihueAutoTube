package dev.local.autotube.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class AutoTubeCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // Personal/sideloaded use — allow all hosts rather than pinning to a signature.
        // Fine for a private build; tighten this if you ever distribute the APK.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session = AutoTubeSession()
}
