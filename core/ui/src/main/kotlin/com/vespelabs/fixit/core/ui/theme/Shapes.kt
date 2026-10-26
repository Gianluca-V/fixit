package com.vespelabs.fixit.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 defaults are 4/8/12/16/28. These are one step rounder at every size: a repair app is
 * about people who come to your house, so the geometry should not feel like a banking form. Cards
 * and buttons land on [Shapes.medium] (14dp), sheets and dialogs on [Shapes.extraLarge] (28dp).
 */
val FixItShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
