package com.vespelabs.fixit.core.data.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firebase entry points of the data layer (design 13). No FirebaseStorage: photos go to Cloudinary (13.7).
 * The default app is initialised by the google-services plugin from `app/google-services.json`.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun firebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun firebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    /** Phase 4: FCM topic subscriptions (design 14b.4). */
    @Provides
    @Singleton
    fun firebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}
