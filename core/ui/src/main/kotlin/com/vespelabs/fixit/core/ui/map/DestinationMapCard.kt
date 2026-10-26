package com.vespelabs.fixit.core.ui.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.ui.R
import com.vespelabs.fixit.core.ui.components.tryStart
import com.vespelabs.fixit.core.ui.theme.FixItTheme

private val PIN_SIZE = 40.dp

/**
 * Where a job is: a fixed map centred on [place] with the pin on it, its address and "Cómo llegar",
 * which hands the place to the phone's maps app (a `geo:` intent) for turn-by-turn directions.
 * The map takes no gestures: it sits inside a scrolling screen and only has to show the spot.
 */
@Composable
fun DestinationMapCard(place: Place, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var noAppAvailable by remember { mutableStateOf(false) }
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.map_destination_title), style = MaterialTheme.typography.labelLarge)
            Box(Modifier.fillMaxWidth().height(180.dp)) {
                MapLibreMap(
                    initialCenter = place.point,
                    cameraTarget = null,
                    onCameraIdle = {},
                    onCameraMoved = {},
                    modifier = Modifier.fillMaxSize(),
                    interactive = false,
                )
                // The tip of the pin marks the map centre, which is the place.
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = stringResource(R.string.map_pin_description),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center).offset(y = -PIN_SIZE / 2).size(PIN_SIZE),
                )
            }
            Text(place.address, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { noAppAvailable = !context.tryStart(Intent(Intent.ACTION_VIEW, place.geoUri())) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.map_directions)) }
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

/** `geo:0,0?q=lat,lng(label)`: maps apps drop a labelled pin on the exact point and offer directions to it. */
private fun Place.geoUri(): Uri {
    val coordinates = "${point.latitude},${point.longitude}"
    return Uri.parse("geo:0,0?q=" + Uri.encode("$coordinates($address)", ",()"))
}

@Preview(showBackground = true)
@Composable
private fun DestinationMapCardPreview() {
    FixItTheme {
        DestinationMapCard(Place(GeoPoint(-34.8065, -58.2788), "Av. San Martín 2000, Florencio Varela"))
    }
}
