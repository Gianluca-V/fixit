package com.vespelabs.fixit.core.data.push

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Clears the app's notices from the tray when the session ends (design 14b.4). Checked by hand in 4.13.3. */
class AndroidNoticeCanceller @Inject constructor(
    @ApplicationContext private val context: Context,
) : NoticeCanceller {
    override fun cancelAll() = NotificationManagerCompat.from(context).cancelAll()
}
