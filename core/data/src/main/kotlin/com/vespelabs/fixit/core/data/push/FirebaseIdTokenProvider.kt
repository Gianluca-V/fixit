package com.vespelabs.fixit.core.data.push

import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/** The cached Firebase ID token (refreshed by the SDK when it expires). Checked by hand in 4.13.1. */
class FirebaseIdTokenProvider @Inject constructor(
    private val auth: FirebaseAuth,
) : IdTokenProvider {
    override suspend fun idToken(): String? = auth.currentUser?.getIdToken(false)?.await()?.token
}
