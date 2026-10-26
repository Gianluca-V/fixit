package com.vespelabs.fixit.core.data.di

import javax.inject.Qualifier

/**
 * Qualifier of the process-wide [kotlinx.coroutines.CoroutineScope] (design 14b.3). Only the push decorator and
 * TopicSync receive it: work launched there survives the ViewModel that triggered it.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
