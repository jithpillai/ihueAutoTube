package com.ihue.dashplayer.car

import androidx.annotation.DrawableRes
import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat

/** Shared helper for wrapping a drawable resource as a [CarIcon] for row/action images. */
fun carIcon(carContext: CarContext, @DrawableRes resId: Int): CarIcon =
    CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
