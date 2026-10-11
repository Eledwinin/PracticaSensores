package com.example.practicasensores.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.practicasensores.data.models.SensorReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SensorController(context: Context) {
    // Obtenemos el servicio de hardware del sistema
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun getSensorData(sensorType: SensorType): Flow<SensorReading> = callbackFlow {
        val type = when (sensorType) {
            SensorType.ACCELEROMETER -> Sensor.TYPE_ACCELEROMETER
            SensorType.GYROSCOPE -> Sensor.TYPE_GYROSCOPE
            SensorType.LIGHT -> Sensor.TYPE_LIGHT
            SensorType.GRAVITY -> Sensor.TYPE_GRAVITY
        }

        val sensor = sensorManager.getDefaultSensor(type)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    // Usamos .clone() para evitar que Android sobrescriba el array en memoria
                    trySend(SensorReading(it.values.clone(), it.timestamp))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            }
        }

        if (sensor != null) {
            // SENSOR_DELAY_GAME espara el minijuego y conteo de saltos
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            close(Exception("El sensor no está disponible en este dispositivo"))
        }

        // awaitClose se ejecuta automáticamente al cancelar la corrutina
        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}