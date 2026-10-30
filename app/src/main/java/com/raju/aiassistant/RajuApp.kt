package com.raju.aiassistant

import android.app.Application
import com.raju.aiassistant.di.AppContainer

class RajuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.consentNotifier.createChannel()
        com.raju.aiassistant.notifications.BusinessNotifier(this).createChannel()
        // Morning briefing and weekly report; they check the setting themselves each time.
        runCatching { com.raju.aiassistant.notifications.BusinessScheduler.schedule(this) }
        runCatching { container.cleanSharedFiles() }
    }

    @Suppress("DEPRECATION") // the level constants are still what Android delivers
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // BACKGROUND and above: the app has been in the background for a while.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) runCatching { container.trimForBackground() }
    }
}
