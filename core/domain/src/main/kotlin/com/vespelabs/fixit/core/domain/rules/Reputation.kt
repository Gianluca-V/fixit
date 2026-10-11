package com.vespelabs.fixit.core.domain.rules

import com.vespelabs.fixit.core.domain.model.Reputation
import java.math.BigDecimal
import java.math.RoundingMode

/** Average rating with one decimal (HALF_UP), or null when there are no reviews. */
fun Reputation.average(): BigDecimal? =
    if (reviewCount == 0) null
    else BigDecimal(ratingSum).divide(BigDecimal(reviewCount), 1, RoundingMode.HALF_UP)
