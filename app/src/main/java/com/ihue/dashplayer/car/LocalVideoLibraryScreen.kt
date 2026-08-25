package com.ihue.dashplayer.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ihue.dashplayer.R
import com.ihue.dashplayer.data.LocalVideo
import com.ihue.dashplayer.data.LocalVideoLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Car-side browser for the folder the user selected in the phone-side app. */
class LocalVideoLibraryScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private var videos: List<LocalVideo> = emptyList()
    private var isLoading = true
    private var hasFolder = false

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        lifecycleScope.launch {
            hasFolder = LocalVideoLibrary.selectedTreeUri(carContext) != null
            videos = withContext(Dispatchers.IO) { LocalVideoLibrary.listVideos(carContext) }
            isLoading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        when {
            isLoading -> list.setNoItemsMessage("Loading phone videos…")
            !hasFolder -> list.setNoItemsMessage("Choose a video folder in the Dash Player phone app first.")
            videos.isEmpty() -> list.setNoItemsMessage("No supported video files found in the selected folder.")
            else -> videos.forEach { video ->
                list.addItem(
                    Row.Builder()
                        .setTitle(video.title)
                        .addText(video.mimeType ?: "Video")
                        .setImage(carIcon(carContext, R.drawable.ic_movie))
                        .setOnClickListener {
                            screenManager.push(LocalVideoPlaybackScreen(carContext, video))
                        }
                        .build()
                )
            }
        }
        return ListTemplate.Builder()
            .setTitle("Phone videos")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }
}
