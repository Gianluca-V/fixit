package com.vespelabs.fixit.core.data.di

import com.vespelabs.fixit.core.domain.repository.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    /** The only place that reads the system clock; data and domain code receive a [Clock]. */
    @Provides
    fun clock(): Clock = Clock { System.currentTimeMillis() }

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * Phase 4 (design 14b.3): a scope, not an injected dispatcher (D13 holds: Dispatchers.Default is implicit).
     * SupervisorJob: one failed child never cancels the others.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob())
}
