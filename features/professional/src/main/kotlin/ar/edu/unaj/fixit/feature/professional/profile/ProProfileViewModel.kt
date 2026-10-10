package ar.edu.unaj.fixit.feature.professional.profile

import androidx.lifecycle.ViewModel
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// TODO(task 1.9.7/1.9.8): TEMPORARY ViewModel that only offers "Cerrar sesión", so a developer can
// switch between seed users. Replace it by the TDD version (ProProfileViewModelTest first):
// UiState Loading/Success(user)/Error from UserRepository.observe, plus signOut().
@HiltViewModel
class ProProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    fun onSignOutClick() = authRepository.signOut()
}
