package com.vespelabs.fixit

import android.app.Application
import com.vespelabs.fixit.core.data.push.TopicSync
import com.vespelabs.fixit.push.Notifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FixItApplication : Application() {
    @Inject lateinit var topicSync: TopicSync

    override fun onCreate() {
        super.onCreate() // Hilt injects the fields here
        // Phase 4 (design 14b.4, 14b.5): the notice channel, and the FCM topics kept in line with the session.
        Notifications.createChannel(this)
        topicSync.start()
    }
}
