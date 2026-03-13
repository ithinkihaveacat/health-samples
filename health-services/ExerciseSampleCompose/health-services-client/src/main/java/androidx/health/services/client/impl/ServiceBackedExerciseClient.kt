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

package androidx.health.services.client.impl

import android.content.Context
import androidx.annotation.GuardedBy
import androidx.annotation.RestrictTo
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.BatchingMode
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DebouncedGoal
import androidx.health.services.client.data.ExerciseCapabilities
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseGoal
import androidx.health.services.client.data.ExerciseInfo
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseTypeConfig
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.ExerciseEvent
import androidx.health.services.client.ExerciseUpdateMessage
import androidx.health.services.client.data.WarmUpConfig
import androidx.health.services.client.impl.IpcConstants.EXERCISE_API_BIND_ACTION
import androidx.health.services.client.impl.IpcConstants.SERVICE_PACKAGE_NAME
import androidx.health.services.client.impl.internal.ExerciseInfoCallback
import androidx.health.services.client.impl.internal.HsConnectionManager
import androidx.health.services.client.impl.internal.StatusCallback
import androidx.health.services.client.impl.ipc.Client
import androidx.health.services.client.impl.ipc.ClientConfiguration
import androidx.health.services.client.impl.ipc.internal.ConnectionManager
import androidx.health.services.client.impl.request.AutoPauseAndResumeConfigRequest
import androidx.health.services.client.impl.request.BatchingModeConfigRequest
import androidx.health.services.client.impl.request.CapabilitiesRequest
import androidx.health.services.client.impl.request.DebouncedGoalRequest
import androidx.health.services.client.impl.request.ExerciseGoalRequest
import androidx.health.services.client.impl.request.FlushRequest
import androidx.health.services.client.impl.request.PrepareExerciseRequest
import androidx.health.services.client.impl.request.StartExerciseRequest
import androidx.health.services.client.impl.request.UpdateExerciseTypeConfigRequest
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow


/** [ExerciseClient] implementation that is backed by Health Services. */
@RestrictTo(RestrictTo.Scope.LIBRARY)
internal class ServiceBackedExerciseClient(
    private val context: Context,
    connectionManager: ConnectionManager = HsConnectionManager.getInstance(context),
) :
    ExerciseClient,
    Client<IExerciseApiService>(
        CLIENT_CONFIGURATION,
        connectionManager,
        { binder -> IExerciseApiService.Stub.asInterface(binder) },
        { service -> service.apiVersion },
    ) {

    private val requestedDataTypesLock = Any()
    @GuardedBy("requestedDataTypesLock")
    private val requestedDataTypes: MutableSet<DataType<*, *>> = mutableSetOf()
    private val packageName = context.packageName

    override suspend fun prepareExercise(configuration: WarmUpConfig) {
        execute { service, resultFuture ->
            service.prepareExercise(
                PrepareExerciseRequest(packageName, configuration),
                object : StatusCallback(resultFuture) {
                    override fun onSuccess() {
                        synchronized(requestedDataTypesLock) {
                            requestedDataTypes.clear()
                            requestedDataTypes.addAll(configuration.dataTypes)
                        }
                        super.onSuccess()
                    }
                },
            )
        }.await()
    }

    override suspend fun startExercise(configuration: ExerciseConfig) {
        execute { service, resultFuture ->
            service.startExercise(
                StartExerciseRequest(packageName, configuration),
                object : StatusCallback(resultFuture) {
                    override fun onSuccess() {
                        synchronized(requestedDataTypesLock) {
                            requestedDataTypes.clear()
                            requestedDataTypes.addAll(configuration.dataTypes)
                        }
                        super.onSuccess()
                    }
                },
            )
        }.await()
    }

    override suspend fun pauseExercise() {
        execute { service, resultFuture ->
            service.pauseExercise(packageName, StatusCallback(resultFuture))
        }.await()
    }

    override suspend fun resumeExercise() {
        execute { service, resultFuture ->
            service.resumeExercise(packageName, StatusCallback(resultFuture))
        }.await()
    }

    override suspend fun endExercise() {
        execute { service, resultFuture ->
            service.endExercise(packageName, StatusCallback(resultFuture))
        }.await()
    }

    override suspend fun flush() {
        val request = FlushRequest(packageName)
        execute { service, resultFuture ->
            service.flushExercise(request, StatusCallback(resultFuture))
        }.await()
    }

    override suspend fun markLap() {
        execute { service, resultFuture ->
            service.markLap(packageName, StatusCallback(resultFuture))
        }.await()
    }

    override suspend fun getCurrentExerciseInfo(): ExerciseInfo {
        return execute { service, resultFuture ->
            service.getCurrentExerciseInfo(packageName, ExerciseInfoCallback(resultFuture))
        }.await()
    }

    override fun exerciseUpdates(): Flow<ExerciseUpdateMessage> = callbackFlow {
        val callback = object : ExerciseUpdateCallback {
            override fun onExerciseUpdateReceived(update: ExerciseUpdate) { trySend(ExerciseUpdateMessage.Update(update)) }
            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) { trySend(ExerciseUpdateMessage.LapSummary(lapSummary)) }
            override fun onRegistered() {}
            override fun onRegistrationFailed(throwable: Throwable) { close(throwable) }
            override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) { trySend(ExerciseUpdateMessage.AvailabilityChanged(dataType, availability)) }
            override fun onExerciseEventReceived(event: ExerciseEvent) { trySend(ExerciseUpdateMessage.Event(event)) }
        }
        val listenerStub = ExerciseUpdateListenerStub.ExerciseUpdateListenerCache.INSTANCE.create(
            callback,
            Runnable::run,
            requestedDataTypesProvider = {
                synchronized(requestedDataTypesLock) { requestedDataTypes.toSet() }
            },
        )
        val registerFuture = registerListener(listenerStub.listenerKey) { service, result: SettableFuture<Void?> ->
            service.setUpdateListener(packageName, listenerStub, StatusCallback(result))
        }
        try {
            registerFuture.await()
        } catch (t: Throwable) {
            close(t)
            return@callbackFlow
        }
        awaitClose {
            val unregisterFuture = unregisterListener(listenerStub.listenerKey) { service, resultFuture ->
                service.clearUpdateListener(packageName, listenerStub, StatusCallback(resultFuture))
            }
            
        }
    }

    override suspend fun addGoalToActiveExercise(
        exerciseGoal: ExerciseGoal<*>
    ) {
        execute { service, resultFuture ->
            service.addGoalToActiveExercise(
                ExerciseGoalRequest(packageName, exerciseGoal),
                StatusCallback(resultFuture),
            )
        }.await()
    }

    override suspend fun removeGoalFromActiveExercise(
        exerciseGoal: ExerciseGoal<*>
    ) {
        execute { service, resultFuture ->
            service.removeGoalFromActiveExercise(
                ExerciseGoalRequest(packageName, exerciseGoal),
                StatusCallback(resultFuture),
            )
        }.await()
    }

    override suspend fun overrideAutoPauseAndResumeForActiveExercise(
        enabled: Boolean
    ) {
        execute { service, resultFuture ->
            service.overrideAutoPauseAndResumeForActiveExercise(
                AutoPauseAndResumeConfigRequest(packageName, enabled),
                StatusCallback(resultFuture),
            )
        }.await()
    }

    override suspend fun overrideBatchingModesForActiveExercise(
        batchingModes: Set<BatchingMode>
    ) {
        executeWithVersionCheck(
            { service, resultFuture ->
                service.overrideBatchingModesForActiveExercise(
                    BatchingModeConfigRequest(packageName, batchingModes),
                    StatusCallback(resultFuture),
                )
            },
            4,
        ).await()
    }

    override suspend fun getCapabilities(): ExerciseCapabilities {
        val response = execute { service -> service.getCapabilities(CapabilitiesRequest(packageName)) }.await()
        return response.exerciseCapabilities
    }

    override suspend fun updateExerciseTypeConfig(
        config: ExerciseTypeConfig
    ) {
        executeWithVersionCheck(
            { service, resultFuture ->
                service.updateExerciseTypeConfigForActiveExercise(
                    UpdateExerciseTypeConfigRequest(packageName, config),
                    StatusCallback(resultFuture),
                )
            },
            3,
        ).await()
    }

    override suspend fun addDebouncedGoalToActiveExercise(
        debouncedGoal: DebouncedGoal<*>
    ) {
        executeWithVersionCheck(
            { service, resultFuture ->
                service.addDebouncedGoalToActiveExercise(
                    DebouncedGoalRequest(packageName, debouncedGoal),
                    StatusCallback(resultFuture),
                )
            },
            7,
        ).await()
    }

    override suspend fun removeDebouncedGoalFromActiveExercise(
        debouncedGoal: DebouncedGoal<*>
    ) {
        executeWithVersionCheck(
            { service, resultFuture ->
                service.removeDebouncedGoalFromActiveExercise(
                    DebouncedGoalRequest(packageName, debouncedGoal),
                    StatusCallback(resultFuture),
                )
            },
            7,
        ).await()
    }

    internal companion object {
        internal const val CLIENT = "HealthServicesExerciseClient"
        internal val CLIENT_CONFIGURATION =
            ClientConfiguration(CLIENT, SERVICE_PACKAGE_NAME, EXERCISE_API_BIND_ACTION)
    }
}
