package androidx.health.services.client

import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.ExerciseEvent

public sealed class ExerciseUpdateMessage {
    public data class Update(val update: ExerciseUpdate) : ExerciseUpdateMessage()
    public data class LapSummary(val lapSummary: ExerciseLapSummary) : ExerciseUpdateMessage()
    public data class AvailabilityChanged(val dataType: DataType<*, *>, val availability: Availability) : ExerciseUpdateMessage()
    public data class Event(val event: ExerciseEvent) : ExerciseUpdateMessage()
}
