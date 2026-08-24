package dev.local.autotube.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session

class AutoTubeSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return HomeScreen(carContext)
    }
}
