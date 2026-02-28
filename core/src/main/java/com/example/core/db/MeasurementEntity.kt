package com.example.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mesure horodatée.
 * - timestampMs: epoch millis
 * - lat/lon: position
 * - temp/hum: valeurs capteur (nullable si non dispo)
 */
@Entity(
    tableName = "measurements",
    indices = [
        Index(value = ["timestampMs"], unique = false)
    ]
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val temperatureC: Double?,
    val humidityPct: Double?,
    val raw: String
)

