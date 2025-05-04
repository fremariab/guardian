// src/main/java/com/example/safemonitor/AlertManager.kt
package com.example.safemonitor

import com.google.android.gms.maps.model.LatLng

object AlertManager {
    private val listeners = mutableListOf<AlertListener>()

    // block bodies instead of single-expression
    fun register(listener: AlertListener) {
        listeners += listener
    }

    fun unregister(listener: AlertListener) {
        listeners -= listener
    }

    fun onAudio(scream: Float, anger: Float) {
        dispatch(Event.Audio(scream, anger))
    }

    fun onFace(label: String, scores: FloatArray) {
        dispatch(Event.Face(label, scores))
    }

    fun onGeoDeviation(location: LatLng) {
        dispatch(Event.Geo(location))
    }

    private fun dispatch(event: Event) {
        listeners.forEach { it.onEvent(event) }
    }

    interface AlertListener {
        fun onEvent(event: Event)
    }

    sealed class Event {
        data class Audio(val scream: Float, val anger: Float): Event()
        data class Face(val label: String, val scores: FloatArray): Event()
        data class Geo(val location: LatLng): Event()
    }
}
