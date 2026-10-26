package com.vespelabs.fixit.core.data.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The registered `user_*` topics in the `topic_sync` SharedPreferences (design 14b.4). Sets are copied with toSet()
 * on read and write (SharedPreferences must not see the returned instance mutated). commit() is synchronous on
 * purpose: TopicSync never runs on the main thread. No JVM test: checked by hand in 4.13.3.
 */
class SharedPrefsTopicStore @Inject constructor(
    @ApplicationContext context: Context,
) : TopicStore {
    private val prefs = context.getSharedPreferences("topic_sync", Context.MODE_PRIVATE)

    override fun userTopics(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    override fun addUserTopic(topic: String) = write(userTopics() + topic)

    override fun removeUserTopic(topic: String) = write(userTopics() - topic)

    private fun write(topics: Set<String>) {
        prefs.edit().putStringSet(KEY, topics.toSet()).commit()
    }

    private companion object {
        const val KEY = "user_topics"
    }
}
