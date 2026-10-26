package com.vespelabs.fixit.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.ui.text.label
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.core.ui.theme.LocalFixItColors

/**
 * Small coloured label with the status of a repair request. The leading dot is what makes the
 * badge scannable in a list: a shape you can recognise in peripheral vision, before reading.
 *
 * Colour carries meaning, so the mapping is fixed: amber = waiting for someone, teal = taken,
 * green = done, brick = cancelled.
 */
@Composable
fun StatusBadge(status: RequestStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalFixItColors.current
    val (container, content) = when (status) {
        RequestStatus.OPEN -> colors.secondaryContainer to colors.onSecondaryContainer
        RequestStatus.ACCEPTED -> colors.primaryContainer to colors.onPrimaryContainer
        RequestStatus.COMPLETED -> extra.successContainer to extra.onSuccessContainer
        RequestStatus.CANCELLED -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(modifier = modifier, color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color = content, shape = CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(text = status.label(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusBadgePreview() {
    FixItTheme {
        androidx.compose.foundation.layout.Column {
            RequestStatus.entries.forEach { StatusBadge(it, Modifier.padding(4.dp)) }
        }
    }
}
