package com.vespelabs.fixit.core.data.di

import com.vespelabs.fixit.core.domain.repository.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    /** The only place that reads the system clock; data and domain code receive a [Clock]. */
    @Provides
    fun clock(): Clock = Clock { System.currentTimeMillis() }

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
