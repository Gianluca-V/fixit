package com.vespelabs.fixit.core.data.di

import com.vespelabs.fixit.core.data.device.FusedLocationProvider
import com.vespelabs.fixit.core.data.device.GeocoderAddressResolver
import com.vespelabs.fixit.core.data.firebase.FirebaseAuthRepository
import com.vespelabs.fixit.core.data.firebase.FirebaseRepairRequestRepository
import com.vespelabs.fixit.core.data.firebase.FirebaseUserRepository
import com.vespelabs.fixit.core.data.prefs.SharedPreferencesThemePreferences
import com.vespelabs.fixit.core.data.push.AndroidNoticeCanceller
import com.vespelabs.fixit.core.data.push.FirebaseIdTokenProvider
import com.vespelabs.fixit.core.data.push.FirebaseTopicMessaging
import com.vespelabs.fixit.core.data.push.HttpWorkerClient
import com.vespelabs.fixit.core.data.push.IdTokenProvider
import com.vespelabs.fixit.core.data.push.NoticeCanceller
import com.vespelabs.fixit.core.data.push.SharedPrefsTopicStore
import com.vespelabs.fixit.core.data.push.TopicMessaging
import com.vespelabs.fixit.core.data.push.TopicStore
import com.vespelabs.fixit.core.data.push.WorkerClient
import com.vespelabs.fixit.core.data.push.WorkerConfig
import com.vespelabs.fixit.core.data.push.withPush
import com.vespelabs.fixit.core.domain.repository.AddressResolver
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.repository.LocationProvider
import com.vespelabs.fixit.core.domain.repository.RepairRequestRepository
import com.vespelabs.fixit.core.domain.repository.ThemePreferences
import com.vespelabs.fixit.core.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/**
 * THE Fake <-> Firebase switch (D11): swapping implementations means editing these bindings only. Phase 2 binds the
 * Firebase repositories; going back to the Fakes means binding FakeAuthRepository, FakeUserRepository and
 * FakeRepairRequestRepository here again. Phase 3 binds the real location (Fused provider + Geocoder); going back to
 * the fixed simulated place means binding FakeLocation to both LocationProvider and AddressResolver.
 *
 * The repository bindings are @Singleton so every consumer shares one instance: the auth repository keeps the
 * in-progress operation flag that the session reads (design 13.5), which must be the same object everywhere.
 *
 * Phase 4 (design 14b.3): the request repository is the Firebase one wrapped by the push decorator through withPush;
 * with an empty PUSH_WORKER_URL it is the same undecorated instance. Rolling back push means going back to the
 * Phase 2 `@Binds` of FirebaseRepairRequestRepository here.
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
    abstract fun location(impl: FusedLocationProvider): LocationProvider

    @Binds
    abstract fun address(impl: GeocoderAddressResolver): AddressResolver

    /** Also @Singleton: the app shell and the profile screens must observe the same instance. */
    @Binds
    @Singleton
    abstract fun themePreferences(impl: SharedPreferencesThemePreferences): ThemePreferences

    // Phase 4: push notifications (design 14b).
    @Binds
    abstract fun workerClient(impl: HttpWorkerClient): WorkerClient

    @Binds
    abstract fun idTokenProvider(impl: FirebaseIdTokenProvider): IdTokenProvider

    @Binds
    abstract fun topicMessaging(impl: FirebaseTopicMessaging): TopicMessaging

    @Binds
    abstract fun topicStore(impl: SharedPrefsTopicStore): TopicStore

    @Binds
    abstract fun noticeCanceller(impl: AndroidNoticeCanceller): NoticeCanceller

    companion object {
        @Provides
        @Singleton
        fun requests(
            real: FirebaseRepairRequestRepository,
            config: WorkerConfig,
            worker: WorkerClient,
            tokens: IdTokenProvider,
            @ApplicationScope scope: CoroutineScope,
        ): RepairRequestRepository = withPush(real, config.baseUrl, worker, tokens, scope)
    }
}
