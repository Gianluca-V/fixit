package com.vespelabs.fixit.core.data.di

import com.vespelabs.fixit.core.data.BuildConfig
import com.vespelabs.fixit.core.data.cloudinary.CloudinaryConfig
import com.vespelabs.fixit.core.data.cloudinary.CloudinaryUploader
import com.vespelabs.fixit.core.data.cloudinary.PhotoUploader
import com.vespelabs.fixit.core.data.photon.PhotonAddressSearch
import com.vespelabs.fixit.core.domain.repository.AddressSearch
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * HTTP stack of the data layer: one shared [OkHttpClient], the Cloudinary uploader (design 13.7, D21, D22) and the
 * Photon address search (icon-map-address D2).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    abstract fun photoUploader(impl: CloudinaryUploader): PhotoUploader

    /** Going back to the simulated catalog (no network) means binding FakeAddressSearch here instead. */
    @Binds
    abstract fun addressSearch(impl: PhotonAddressSearch): AddressSearch

    companion object {
        /** Single client: OkHttp shares its connection pool and threads across calls (Phase 4 reuses it). */
        @Provides
        @Singleton
        fun okHttpClient(): OkHttpClient = OkHttpClient()

        /** Values come from the git-ignored local.properties; empty ones fail the first upload with Unknown (D22). */
        @Provides
        fun cloudinaryConfig(): CloudinaryConfig = CloudinaryConfig(
            cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME,
            uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET,
        )
    }
}
