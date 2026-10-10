package ar.edu.unaj.fixit.core.data.di

import ar.edu.unaj.fixit.core.data.fake.FakeAuthRepository
import ar.edu.unaj.fixit.core.data.fake.FakeLocation
import ar.edu.unaj.fixit.core.data.fake.FakeRepairRequestRepository
import ar.edu.unaj.fixit.core.data.fake.FakeUserRepository
import ar.edu.unaj.fixit.core.domain.repository.AddressResolver
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.repository.LocationProvider
import ar.edu.unaj.fixit.core.domain.repository.RepairRequestRepository
import ar.edu.unaj.fixit.core.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** THE Fake <-> Firebase switch (D11): swapping implementations means editing these bindings only. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun auth(impl: FakeAuthRepository): AuthRepository

    @Binds
    abstract fun users(impl: FakeUserRepository): UserRepository

    @Binds
    abstract fun requests(impl: FakeRepairRequestRepository): RepairRequestRepository

    @Binds // Phase 3: FusedLocationProvider
    abstract fun location(impl: FakeLocation): LocationProvider

    @Binds // Phase 3: GeocoderAddressResolver
    abstract fun address(impl: FakeLocation): AddressResolver
}
