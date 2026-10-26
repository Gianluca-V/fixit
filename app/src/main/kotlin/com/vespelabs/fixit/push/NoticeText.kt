package com.vespelabs.fixit.push

import androidx.annotation.StringRes
import com.vespelabs.fixit.R
import com.vespelabs.fixit.core.data.push.PushEventType

/** Title and body of a visible notice, as string resources of the app. */
data class NoticeText(@StringRes val title: Int, @StringRes val body: Int)

/** Only the event type decides the text: no category, distance, names or request data (design 14b.5). */
fun noticeTextFor(type: PushEventType): NoticeText = NoticeText(
    title = R.string.app_name,
    body = when (type) {
        PushEventType.REQUEST_ACCEPTED -> R.string.notice_request_accepted
        PushEventType.NEW_REQUEST -> R.string.notice_new_request
        PushEventType.REQUEST_CANCELLED -> R.string.notice_request_cancelled
    },
)
