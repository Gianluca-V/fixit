package ar.edu.unaj.fixit.core.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.unaj.fixit.core.domain.model.Contact
import ar.edu.unaj.fixit.core.domain.model.Reputation
import ar.edu.unaj.fixit.core.ui.R
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme

/**
 * Name, phone and (for a professional) reputation, with "Call" (ACTION_DIAL) and "WhatsApp"
 * (ACTION_VIEW). No `<queries>` in the manifest: we just try and catch ActivityNotFoundException.
 */
@Composable
fun ContactCard(contact: Contact, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var noAppAvailable by remember { mutableStateOf(false) }
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.contact_title), style = MaterialTheme.typography.labelLarge)
            Text(contact.name, style = MaterialTheme.typography.titleMedium)
            Text(contact.phone, style = MaterialTheme.typography.bodyMedium)
            contact.reputation?.let { ReputationText(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                    noAppAvailable = !context.tryStart(dial)
                }) { Text(stringResource(R.string.contact_call)) }
                OutlinedButton(onClick = {
                    val view = Intent(Intent.ACTION_VIEW, Uri.parse(contact.whatsAppUrl))
                    noAppAvailable = !context.tryStart(view)
                }) { Text(stringResource(R.string.contact_whatsapp)) }
            }
            if (noAppAvailable) {
                Text(
                    stringResource(R.string.contact_no_app),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Returns false when no app can handle the intent. */
private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}

@Preview(showBackground = true)
@Composable
private fun ContactCardPreview() {
    FixItTheme {
        ContactCard(
            Contact(
                name = "Carlos Gómez",
                phone = "11 5555-0101",
                whatsAppUrl = "https://wa.me/5491155550101",
                reputation = Reputation(ratingSum = 9, reviewCount = 2),
            ),
        )
    }
}
