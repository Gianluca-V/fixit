package com.vespelabs.fixit.core.data.di

import com.vespelabs.fixit.core.data.device.FusedLocationProvider
import com.vespelabs.fixit.core.data.device.GeocoderAddressResolver
import com.vespelabs.fixit.core.data.firebase.FirebaseAuthRepository
import com.vespelabs.fixit.core.data.firebase.FirebaseRepairRequestRepository
import com.vespelabs.fixit.core.data.firebase.FirebaseUserRepository
import com.vespelabs.fixit.core.data.prefs.SharedPreferencesThemePreferences
import com.vespelabs.fixit.core.domain.repository.AddressResolver
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.repository.LocationProvider
import com.vespelabs.fixit.core.domain.repository.RepairRequestRepository
import com.vespelabs.fixit.core.domain.repository.ThemePreferences
import com.vespelabs.fixit.core.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * THE Fake <-> Firebase switch (D11): swapping implementations means editing these bindings only. Phase 2 binds the
 * Firebase repositories; going back to the Fakes means binding FakeAuthRepository, FakeUserRepository and
 * FakeRepairRequestRepository here again. Phase 3 binds the real location (Fused provider + Geocoder); going back to
 * the fixed simulated place means binding FakeLocation to both LocationProvider and AddressResolver.
 *
 * The repository bindings are @Singleton so every consumer shares one instance: the auth repository keeps the
 * in-progress operation flag that the session reads (design 13.5), which must be the same object everywhere.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun auth(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun users(impl: FirebaseUserRepository): UserRepository

    @Binds
    @Singleton
    abstract fun requests(impl: FirebaseRepairRequestRepository): RepairRequestRepository

    @Binds
    abstract fun location(impl: FusedLocationProvider): LocationProvider

    @Binds
    abstract fun address(impl: GeocoderAddressResolver): AddressResolver

    /** Also @Singleton: the app shell and the profile screens must observe the same instance. */
    @Binds
    @Singleton
    abstract fun themePreferences(impl: SharedPreferencesThemePreferences): ThemePreferences
}
