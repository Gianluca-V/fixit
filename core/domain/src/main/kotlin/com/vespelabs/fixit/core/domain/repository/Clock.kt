package com.vespelabs.fixit.core.domain.repository

fun interface Clock {
    fun now(): Long
}
