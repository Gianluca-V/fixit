package ar.edu.unaj.fixit.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.unaj.fixit.core.ui.R
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme
import coil3.compose.AsyncImage

/** Swipeable large photos with a "1 / 3" indicator. */
@Composable
fun PhotoPager(photoUrls: List<String>, modifier: Modifier = Modifier) {
    if (photoUrls.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { photoUrls.size })
    Box(modifier = modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            AsyncImage(
                model = photoUrls[page],
                contentDescription = stringResource(R.string.photo_content_description, page + 1, photoUrls.size),
                error = painterResource(R.drawable.photo_error),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
            )
        }
        if (photoUrls.size > 1) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${photoUrls.size}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoPagerPreview() {
    FixItTheme { PhotoPager(photoUrls = SamplePhotos) }
}
