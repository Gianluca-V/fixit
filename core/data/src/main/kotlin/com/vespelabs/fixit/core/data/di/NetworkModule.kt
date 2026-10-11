package com.vespelabs.fixit.core.data.di

import com.vespelabs.fixit.core.data.BuildConfig
import com.vespelabs.fixit.core.data.cloudinary.CloudinaryConfig
import com.vespelabs.fixit.core.data.cloudinary.CloudinaryUploader
import com.vespelabs.fixit.core.data.cloudinary.PhotoUploader
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** HTTP stack of the data layer: one shared [OkHttpClient] and the Cloudinary uploader (design 13.7, D21, D22). */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    abstract fun photoUploader(impl: CloudinaryUploader): PhotoUploader

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
