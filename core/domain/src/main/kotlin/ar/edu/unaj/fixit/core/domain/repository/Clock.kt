package ar.edu.unaj.fixit.core.domain.repository

fun interface Clock {
    fun now(): Long
}
