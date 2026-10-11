package com.vespelabs.fixit.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.ui.text.label
import com.vespelabs.fixit.core.ui.theme.FixItTheme

/**
 * One chip per [ServiceCategory]. It only reports taps: the ViewModel decides whether the
 * selection is single (new request) or multiple (professional profile).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChips(
    selected: Set<ServiceCategory>,
    onCategoryClick: (ServiceCategory) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ServiceCategory.entries.forEach { category ->
            FilterChip(
                selected = category in selected,
                onClick = { onCategoryClick(category) },
                label = { Text(category.label()) },
                enabled = enabled,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoryChipsPreview() {
    FixItTheme {
        CategoryChips(
            selected = setOf(ServiceCategory.ELECTRICITY, ServiceCategory.GAS),
            onCategoryClick = {},
        )
    }
}
