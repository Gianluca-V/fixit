package com.vespelabs.fixit.core.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.ui.R
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** Street level: enough to tell one block from the next. */
private const val PICKER_ZOOM = 16.0

/**
 * MapLibre map with the OpenFreeMap style (OpenStreetMap data, icon-map-address D1). It opens at [initialCenter],
 * reports every camera stop to [onCameraIdle] and animates to [cameraTarget] when it changes, then calls
 * [onCameraMoved]. Rotation and tilt are off: a picker only needs pan and zoom. The style shows the OpenStreetMap
 * attribution. No JVM test: the map is checked by hand (tasks 8.2).
 */
@Composable
fun MapLibreMap(
    initialCenter: GeoPoint,
    cameraTarget: GeoPoint?,
    onCameraIdle: (GeoPoint) -> Unit,
    onCameraMoved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        // MapLibre does not draw in Android Studio previews.
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    val context = LocalContext.current
    val styleUrl = stringResource(R.string.map_style_url)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { libreMap ->
                libreMap.uiSettings.isRotateGesturesEnabled = false
                libreMap.uiSettings.isTiltGesturesEnabled = false
                libreMap.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(initialCenter.latitude, initialCenter.longitude))
                    .zoom(PICKER_ZOOM)
                    .build()
                libreMap.setStyle(Style.Builder().fromUri(styleUrl))
                libreMap.addOnCameraIdleListener {
                    libreMap.cameraPosition.target?.let { currentOnCameraIdle(GeoPoint(it.latitude, it.longitude)) }
                }
                map = libreMap
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(map, cameraTarget) {
        val libreMap = map ?: return@LaunchedEffect
        val target = cameraTarget ?: return@LaunchedEffect
        libreMap.animateCamera(CameraUpdateFactory.newLatLng(LatLng(target.latitude, target.longitude)))
        onCameraMoved()
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
