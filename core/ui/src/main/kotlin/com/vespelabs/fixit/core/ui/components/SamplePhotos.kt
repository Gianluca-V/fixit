package com.vespelabs.fixit.core.ui.components

/**
 * Phase 1 sample photos (drawables of this module). The `android.resource` URI uses the
 * application id, so it resolves once the module is merged into the app.
 */
val SamplePhotos: List<String> = (1..3).map {
    "android.resource://com.vespelabs.fixit/drawable/sample_repair_$it"
}
