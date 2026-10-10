package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.Place
import ar.edu.unaj.fixit.core.domain.repository.AddressResolver
import ar.edu.unaj.fixit.core.domain.repository.LocationProvider
import ar.edu.unaj.fixit.core.domain.result.AppResult
import javax.inject.Inject

class GetCurrentPlaceUseCase @Inject constructor(
    private val locationProvider: LocationProvider,
    private val addressResolver: AddressResolver,
) {
    /** Coordinates plus a readable address; when only the address fails, it is left empty for the user to fill in. */
    suspend operator fun invoke(): AppResult<Place> {
        val point = when (val located = locationProvider.current()) {
            is AppResult.Error -> return located
            is AppResult.Success -> located.data
        }
        val address = when (val resolved = addressResolver.resolve(point)) {
            is AppResult.Error -> ""
            is AppResult.Success -> resolved.data
        }
        return AppResult.Success(Place(point, address))
    }
}
