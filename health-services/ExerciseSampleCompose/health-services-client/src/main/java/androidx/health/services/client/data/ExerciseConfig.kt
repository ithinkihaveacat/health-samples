/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.health.services.client.data

import android.os.Bundle
import androidx.annotation.FloatRange
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.proto.DataProto

/**
 * Defines configuration for an exercise tracked using Health Services.
 *
 * @property exerciseType [ExerciseType] user is performing for this exercise
 * @property dataTypes [DataType] which will be tracked for this exercise
 * @property isAutoPauseAndResumeEnabled whether auto-pause/resume is enabled for this exercise
 * @property isGpsEnabled whether GPS is enabled for this exercise. Must be set to `true` when
 *   [DataType.LOCATION] is present in [dataTypes].
 * @property exerciseGoals [ExerciseGoal]s for this exercise. [DataType]s in [ExerciseGoal]s must
 *   also be tracked (i.e. contained in [dataTypes]) in some form. For example, an [ExerciseGoal]
 *   for [DataType.STEPS_TOTAL] requires that [dataTypes] contains either or both of
 *   [DataType.STEPS_TOTAL] / [DataType.STEPS].
 * @property exerciseParams [Bundle] bundle for specifying exercise presets, the values of an
 *   on-going exercise which can be used to pre-populate a new exercise.
 * @property swimmingPoolLengthMeters length (in meters) of the swimming pool, or 0 if not relevant
 *   to this exercise
 * @property exerciseTypeConfig [ExerciseTypeConfig] containing attributes which may be modified
 *   after the exercise has started
 * @property batchingModeOverrides [BatchingMode] overrides for this exercise
 * @property exerciseEventTypes [ExerciseEventType]s which should be tracked for this exercise
 * @property debouncedGoals [DebouncedGoal]s for this exercise. [DataType]s in [DebouncedGoal]s must
 *   also be tracked.
 * @constructor Creates a new ExerciseConfig for an exercise tracked using Health Services
 */
@Suppress("ParcelCreator")
data class ExerciseConfig(

    val exerciseType: ExerciseType,
    val dataTypes: Set<DataType<*, *>> = emptySet(),
    val isAutoPauseAndResumeEnabled: Boolean = false,
    val isGpsEnabled: Boolean = false,
    val exerciseGoals: List<ExerciseGoal<*>> = listOf(),
    val exerciseParams: Bundle = Bundle(),
    @FloatRange(from = 0.0) val swimmingPoolLengthMeters: Float = SWIMMING_POOL_LENGTH_UNSPECIFIED,
    val exerciseTypeConfig: ExerciseTypeConfig? = null,
    val batchingModeOverrides: Set<BatchingMode> = emptySet(),
    val exerciseEventTypes: Set<ExerciseEventType<*>> = emptySet(),
    val debouncedGoals: List<DebouncedGoal<*>> = emptyList(),
) {

    internal constructor(
        proto: DataProto.ExerciseConfig
    ) : this(
        ExerciseType.fromProto(proto.exerciseType),
        proto.dataTypesList.map { DataType.deltaFromProto(it) }.toMutableSet() +
            proto.aggregateDataTypesList.map { DataType.aggregateFromProto(it) },
        proto.isAutoPauseAndResumeEnabled,
        proto.isGpsUsageEnabled,
        proto.exerciseGoalsList.map { ExerciseGoal.fromProto(it) },
        BundlesUtil.fromProto(proto.exerciseParams),
        if (proto.hasSwimmingPoolLengthMeters()) {
            proto.swimmingPoolLengthMeters
        } else {
            SWIMMING_POOL_LENGTH_UNSPECIFIED
        },
        if (proto.hasExerciseTypeConfig()) {
            ExerciseTypeConfig.fromProto(proto.exerciseTypeConfig)
        } else null,
        proto.batchingModeOverridesList.map { BatchingMode(it) }.toSet(),
        proto.exerciseEventTypesList.map { ExerciseEventType.fromProto(it) }.toSet(),
        proto.debouncedGoalsList.map { DebouncedGoal.fromProto(it) },
    )

    init {
        require(!dataTypes.contains(DataType.LOCATION) || isGpsEnabled) {
            "If LOCATION data is being requested, setGpsEnabled(true) must be configured in the " +
                "ExerciseConfig. "
        }

        if (exerciseType == ExerciseType.SWIMMING_POOL) {
            require(swimmingPoolLengthMeters != 0.0f) {
                "If exercise type is SWIMMING_POOL, " +
                    "then swimming pool length must also be specified"
            }
        }
    }

    

    override fun toString(): String =
        "ExerciseConfig(" +
            "exerciseType=$exerciseType, " +
            "dataTypes=$dataTypes, " +
            "isAutoPauseAndResumeEnabled=$isAutoPauseAndResumeEnabled, " +
            "isGpsEnabled=$isGpsEnabled, " +
            "exerciseGoals=$exerciseGoals, " +
            "swimmingPoolLengthMeters=$swimmingPoolLengthMeters, " +
            "exerciseTypeConfig=$exerciseTypeConfig, " +
            "debouncedGoals=$debouncedGoals)"

    internal fun toProto(): DataProto.ExerciseConfig {
        val builder =
            DataProto.ExerciseConfig.newBuilder()
                .setExerciseType(exerciseType.toProto())
                .addAllDataTypes(dataTypes.filter { !it.isAggregate }.map { it.proto })
                .addAllAggregateDataTypes(dataTypes.filter { it.isAggregate }.map { it.proto })
                .setIsAutoPauseAndResumeEnabled(isAutoPauseAndResumeEnabled)
                .setIsGpsUsageEnabled(isGpsEnabled)
                .addAllExerciseGoals(exerciseGoals.map { it.proto })
                .addAllDebouncedGoals(debouncedGoals.map { it.proto })
                .setExerciseParams(BundlesUtil.toProto(exerciseParams))
                .setSwimmingPoolLengthMeters(swimmingPoolLengthMeters)
                .addAllBatchingModeOverrides(batchingModeOverrides.map { it.toProto() })
                .addAllExerciseEventTypes(exerciseEventTypes.map { it.toProto() })
        if (exerciseTypeConfig != null) {
            builder.exerciseTypeConfig = exerciseTypeConfig.toProto()
        }
        return builder.build()
    }

    companion object {
        

        public const val SWIMMING_POOL_LENGTH_UNSPECIFIED = 0.0f
    }
}
