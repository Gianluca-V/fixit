package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.FeedItem
import ar.edu.unaj.fixit.core.domain.model.ProfessionalInfo
import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.model.Reputation
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.repository.RepairRequestRepository
import ar.edu.unaj.fixit.core.domain.repository.UserRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.rules.Matching
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ObserveFeedUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val requestRepository: RepairRequestRepository,
) {
    /**
     * Profile -> (categories, base, radius) -> candidates near the base -> exact [Matching] filter,
     * newest first. The profile is projected and de-duplicated BEFORE flatMapLatest, so a change of
     * name or reputation does not restart the candidate observation. An Error from either
     * observation is emitted as the feed's Error.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<AppResult<List<FeedItem>>> {
        val userId = authRepository.currentUserId() ?: return flowOf(AppResult.Error(DomainError.Unauthorized))
        return userRepository.observe(userId)
            .map { it.asWorkZone() }
            .distinctUntilChanged()
            .flatMapLatest { workZone ->
                when (workZone) {
                    is AppResult.Error -> flowOf(workZone)
                    is AppResult.Success -> feedFor(workZone.data)
                }
            }
    }

    private fun feedFor(zone: ProfessionalInfo): Flow<AppResult<List<FeedItem>>> =
        requestRepository.observeOpenNear(zone.base.point, zone.radiusKm).map { candidates ->
            when (candidates) {
                is AppResult.Error -> candidates
                is AppResult.Success -> AppResult.Success(candidates.data.toFeedItems(zone))
            }
        }

    private fun List<RepairRequest>.toFeedItems(zone: ProfessionalInfo): List<FeedItem> =
        mapNotNull { request -> Matching.distanceIfMatches(zone, request)?.let { FeedItem(request, it) } }
            .sortedByDescending { it.request.createdAt }

    /** The part of the profile that drives the feed; a client has none, so it is not authorized. */
    private fun AppResult<User>.asWorkZone(): AppResult<ProfessionalInfo> =
        when (this) {
            is AppResult.Error -> this
            is AppResult.Success ->
                data.professional
                    ?.copy(reputation = Reputation())
                    ?.let { AppResult.Success(it) }
                    ?: AppResult.Error(DomainError.Unauthorized)
        }
}
