package ar.edu.unaj.fixit.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.unaj.fixit.core.domain.model.RequestStatus
import ar.edu.unaj.fixit.core.ui.text.label
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme

/** Small colored label with the status of a repair request. */
@Composable
fun StatusBadge(status: RequestStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        RequestStatus.OPEN -> colors.secondaryContainer to colors.onSecondaryContainer
        RequestStatus.ACCEPTED -> colors.primaryContainer to colors.onPrimaryContainer
        RequestStatus.COMPLETED -> colors.tertiaryContainer to colors.onTertiaryContainer
        RequestStatus.CANCELLED -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(modifier = modifier, color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
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
