package com.vespelabs.fixit.core.data.di

import javax.inject.Qualifier

/**
 * Qualifier of the single global IO dispatcher binding (D13). Only blocking adapters inject it
 * (Phase 2 ImageCompressor, Phase 3 geocoder); repositories and ViewModels never receive a dispatcher.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher
