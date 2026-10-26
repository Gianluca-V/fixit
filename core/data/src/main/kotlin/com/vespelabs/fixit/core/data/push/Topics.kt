package com.vespelabs.fixit.core.data.push

import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.User

/** Topic names shared with the Worker (design 14b.2). */
internal const val USER_TOPIC_PREFIX = "user_"
internal const val CATEGORY_TOPIC_PREFIX = "category_"

fun userTopic(uid: String): String = "$USER_TOPIC_PREFIX$uid"

fun categoryTopic(category: ServiceCategory): String = "$CATEGORY_TOPIC_PREFIX${category.name}"

fun isUserTopic(topic: String): Boolean = topic.startsWith(USER_TOPIC_PREFIX)

/** `{user_{uid}}` plus `category_{C}` of a professional; empty without a session. */
fun topicsFor(user: User?): Set<String> =
    if (user == null) emptySet() else topicsFor(user.id, user.professional?.categories.orEmpty())

/** Same rule from the parts that matter (the session key of TopicSync). Ordered: the user topic first. */
internal fun topicsFor(uid: String, categories: Set<ServiceCategory>): Set<String> =
    linkedSetOf(userTopic(uid)) + categories.sortedBy { it.ordinal }.map(::categoryTopic)

data class TopicPlan(val subscribe: Set<String>, val unsubscribe: Set<String>)

/**
 * History-free plan (design 14b.4, revised 2026-10-26): subscribe everything desired (idempotent in FCM) and
 * unsubscribe every category topic not desired (all eight are known) and every registered user topic not desired.
 */
fun topicPlan(desired: Set<String>, registeredUserTopics: Set<String>): TopicPlan {
    val allCategories = ServiceCategory.entries.map(::categoryTopic)
    return TopicPlan(
        subscribe = desired,
        unsubscribe = (allCategories + registeredUserTopics.sorted()).filterNot { it in desired }.toSet(),
    )
}
