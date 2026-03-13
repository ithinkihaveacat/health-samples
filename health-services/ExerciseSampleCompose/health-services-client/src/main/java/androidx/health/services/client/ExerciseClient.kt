package androidx.health.services.client

import androidx.health.services.client.data.BatchingMode
import androidx.health.services.client.data.DebouncedGoal
import androidx.health.services.client.data.ExerciseCapabilities
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseGoal
import androidx.health.services.client.data.ExerciseInfo
import androidx.health.services.client.data.ExerciseTypeConfig
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.WarmUpConfig
import kotlinx.coroutines.flow.Flow

public interface ExerciseClient {
    public suspend fun prepareExercise(configuration: WarmUpConfig)
    public suspend fun startExercise(configuration: ExerciseConfig)
    public suspend fun pauseExercise()
    public suspend fun resumeExercise()
    public suspend fun endExercise()
    public suspend fun flush()
    public suspend fun markLap()
    public suspend fun getCurrentExerciseInfo(): ExerciseInfo
    public suspend fun updateExerciseTypeConfig(config: ExerciseTypeConfig)
    public suspend fun addGoalToActiveExercise(exerciseGoal: ExerciseGoal<*>)
    public suspend fun removeGoalFromActiveExercise(exerciseGoal: ExerciseGoal<*>)
    public suspend fun addDebouncedGoalToActiveExercise(debouncedGoal: DebouncedGoal<*>)
    public suspend fun removeDebouncedGoalFromActiveExercise(debouncedGoal: DebouncedGoal<*>)
    public suspend fun overrideAutoPauseAndResumeForActiveExercise(enabled: Boolean)
    public suspend fun overrideBatchingModesForActiveExercise(batchingModes: Set<BatchingMode>)
    public suspend fun getCapabilities(): ExerciseCapabilities
    public fun exerciseUpdates(): Flow<ExerciseUpdateMessage>
}