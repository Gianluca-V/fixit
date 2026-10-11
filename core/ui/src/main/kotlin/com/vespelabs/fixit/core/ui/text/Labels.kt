package com.vespelabs.fixit.core.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.ui.R

@Composable
fun ServiceCategory.label(): String = stringResource(
    when (this) {
        ServiceCategory.ELECTRICITY -> R.string.category_electricity
        ServiceCategory.PLUMBING -> R.string.category_plumbing
        ServiceCategory.GAS -> R.string.category_gas
        ServiceCategory.CARPENTRY -> R.string.category_carpentry
        ServiceCategory.PAINTING -> R.string.category_painting
        ServiceCategory.LOCKSMITH -> R.string.category_locksmith
        ServiceCategory.MASONRY -> R.string.category_masonry
        ServiceCategory.OTHER -> R.string.category_other
    },
)

@Composable
fun RequestStatus.label(): String = stringResource(
    when (this) {
        RequestStatus.OPEN -> R.string.status_open
        RequestStatus.ACCEPTED -> R.string.status_accepted
        RequestStatus.COMPLETED -> R.string.status_completed
        RequestStatus.CANCELLED -> R.string.status_cancelled
    },
)
