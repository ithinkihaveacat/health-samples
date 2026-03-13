/*
 * Copyright (C) 2022 The Android Open Source Project
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

import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.proto.DataProto

/**
 * Defines configuration for a passive monitoring listener request using Health Services.
 *
 * @property dataTypes set of [DataType]s which should be tracked. Requested data will be returned
 *   by [PassiveListenerCallback.onNewDataPointsReceived].
 * @property shouldUserActivityInfoBeRequested whether to request [UserActivityInfo] updates. Data
 *   will be returned by [PassiveListenerCallback.onUserActivityInfoReceived]. If set to true,
 *   calling app must have [android.Manifest.permission.ACTIVITY_RECOGNITION].
 * @property dailyGoals set of daily [PassiveGoal]s which should be tracked. Achieved goals will be
 *   returned by [PassiveListenerCallback.onGoalCompleted].
 * @property healthEventTypes set of [HealthEvent.Type] which should be tracked. Detected health
 *   events will be returned by [PassiveListenerCallback.onHealthEventReceived].
 * @constructor Creates a new [PassiveListenerConfig] which defines a request for passive monitoring
 *   using Health Services
 */
@Suppress("ParcelCreator")
public data class PassiveListenerConfig(

    public val dataTypes: Set<DataType<out Any, out DataPoint<out Any>>> = emptySet(),
    @get:JvmName("shouldUserActivityInfoBeRequested")
    public val shouldUserActivityInfoBeRequested: Boolean = false,
    public val dailyGoals: Set<PassiveGoal> = emptySet(),
    public val healthEventTypes: Set<HealthEvent.Type> = emptySet(),
) {

    internal constructor(
        proto: DataProto.PassiveListenerConfig
    ) : this(
        proto.dataTypesList.map { DataType.deltaFromProto(it) }.toSet(),
        proto.includeUserActivityState,
        proto.passiveGoalsList.map { PassiveGoal(it) }.toSet(),
        proto.healthEventTypesList.map { HealthEvent.Type.fromProto(it) }.toSet(),
    )

    internal fun isValidPassiveGoal(): Boolean {
        // Check if the registered goals are also tracked
        for (passiveGoal: PassiveGoal in dailyGoals) {
            if (!dataTypes.contains(passiveGoal.dataTypeCondition.dataType)) return false
        }
        return true
    }

    

    internal val proto: DataProto.PassiveListenerConfig =
        DataProto.PassiveListenerConfig.newBuilder()
            .addAllDataTypes(dataTypes.map { it.proto })
            .setIncludeUserActivityState(shouldUserActivityInfoBeRequested)
            .addAllPassiveGoals(dailyGoals.map { it.proto })
            .addAllHealthEventTypes(healthEventTypes.map { it.toProto() })
            .build()

    public companion object {
        
    }
}
