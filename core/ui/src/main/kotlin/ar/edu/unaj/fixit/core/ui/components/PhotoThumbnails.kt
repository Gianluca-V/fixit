package ar.edu.unaj.fixit.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.unaj.fixit.core.ui.R
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme
import coil3.compose.AsyncImage

/** Row of small photos. When [onRemove] is set, each photo shows a button to remove it. */
@Composable
fun PhotoThumbnails(
    photoUrls: List<String>,
    modifier: Modifier = Modifier,
    onRemove: ((index: Int) -> Unit)? = null,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(photoUrls) { index, url ->
            Box(Modifier.size(96.dp)) {
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.photo_content_description, index + 1, photoUrls.size),
                    error = painterResource(R.drawable.photo_error),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                if (onRemove != null) {
                    IconButton(onClick = { onRemove(index) }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.photo_remove, index + 1))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoThumbnailsPreview() {
    FixItTheme { PhotoThumbnails(photoUrls = SamplePhotos, onRemove = {}) }
}
