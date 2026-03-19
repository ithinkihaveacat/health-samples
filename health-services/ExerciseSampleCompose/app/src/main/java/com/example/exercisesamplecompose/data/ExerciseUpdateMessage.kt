package com.example.exercisesamplecompose.data

import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseEvent
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseUpdate

sealed class ExerciseUpdateMessage {
    data class Update(val update: ExerciseUpdate) : ExerciseUpdateMessage()
    data class LapSummary(val lapSummary: ExerciseLapSummary) : ExerciseUpdateMessage()
    data class AvailabilityChanged(val dataType: DataType<*, *>, val availability: Availability) : ExerciseUpdateMessage()
    data class Event(val event: ExerciseEvent) : ExerciseUpdateMessage()
}