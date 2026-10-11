package com.example.practicasensores.data.models

data class SensorReading(
    val values: FloatArray,
    val timestamp: Long
) {
    // Sobrescribimos equals y hashCode porque usamos FloatArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SensorReading
        if (!values.contentEquals(other.values)) return false
        return timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = values.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}