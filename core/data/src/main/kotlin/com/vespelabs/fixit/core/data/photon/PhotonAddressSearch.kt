package com.vespelabs.fixit.core.data.photon

import com.vespelabs.fixit.core.data.di.IoDispatcher
import com.vespelabs.fixit.core.data.firebase.firebaseCall
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.repository.AddressSearch
import com.vespelabs.fixit.core.domain.result.AppResult
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.coroutines.executeAsync

/** Upper bound for one search; past it the suggestions are useless anyway. */
const val SEARCH_TIMEOUT_MS = 10_000L

/** Photon's fair-use policy asks for an identifiable client. */
private const val USER_AGENT = "FixIt/1.0 (UNAJ TP2; Android)"

/**
 * Address autocomplete with the public Photon instance by komoot (design D2): no key, no card. `firebaseCall` maps
 * the failures like every other remote call: IOException or the timeout -> Network, a non-2xx code or a body that
 * is not the expected JSON -> Unknown (logged); a cancelled search (a newer text) is propagated, not mapped.
 * No JVM test: the request, the parsing and the formatting are pure functions with their own tests.
 */
class PhotonAddressSearch @Inject constructor(
    private val client: OkHttpClient,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) : AddressSearch {
    override suspend fun search(query: String, near: GeoPoint?): AppResult<List<Place>> = firebaseCall(SEARCH_TIMEOUT_MS) {
        client.newCall(buildPhotonRequest(query, near, USER_AGENT)).executeAsync().use { response ->
            // executeAsync() resumes on the headers; the body is read with blocking I/O (same as CloudinaryUploader).
            val body = withContext(dispatcher) { response.body.string() }
            parsePhotonResponse(response.code, body).mapNotNull { it.toPlace() }
        }
    }
}
