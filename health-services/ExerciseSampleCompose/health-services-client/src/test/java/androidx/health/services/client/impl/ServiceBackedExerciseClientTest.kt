/*
 * Copyright 2022 The Android Open Source Project
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

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Looper.getMainLooper
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.BatchingMode
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataType.Companion.GOLF_SHOT_COUNT
import androidx.health.services.client.data.DataType.Companion.HEART_RATE_BPM
import androidx.health.services.client.data.DataType.Companion.HEART_RATE_BPM_STATS
import androidx.health.services.client.data.DataTypeAvailability.Companion.ACQUIRING
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseEvent
import androidx.health.services.client.data.ExerciseEventType
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.ExerciseUpdateMessage
import androidx.health.services.client.data.GolfExerciseTypeConfig
import androidx.health.services.client.data.GolfShotEvent
import androidx.health.services.client.data.GolfShotEvent.GolfShotSwingType
import androidx.health.services.client.data.WarmUpConfig
import androidx.health.services.client.impl.event.ExerciseUpdateListenerEvent
import androidx.health.services.client.impl.internal.IExerciseInfoCallback
import androidx.health.services.client.impl.internal.IStatusCallback
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
import androidx.health.services.client.impl.response.AvailabilityResponse
import androidx.health.services.client.impl.response.ExerciseCapabilitiesResponse
import androidx.health.services.client.impl.response.ExerciseEventResponse
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [org.robolectric.annotation.Config.TARGET_SDK])
class ServiceBackedExerciseClientTest {

    private lateinit var client: ServiceBackedExerciseClient
    private lateinit var fakeService: FakeServiceStub
    private val callback = FakeExerciseUpdateCallback()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        client =
            ServiceBackedExerciseClient(context, ConnectionManager(context, context.mainLooper))
        fakeService = FakeServiceStub()

        val packageName = ServiceBackedExerciseClient.CLIENT_CONFIGURATION.servicePackageName
        val action = ServiceBackedExerciseClient.CLIENT_CONFIGURATION.bindAction
        shadowOf(context)
            .setComponentNameAndServiceForBindServiceForIntent(
                Intent().setPackage(packageName).setAction(action),
                ComponentName(packageName, ServiceBackedExerciseClient.CLIENT),
                fakeService,
            )
    }

    @After
    fun tearDown() {
    }

    @Test
    fun setUpdateCallback_registeredCallbackShouldBeInvoked() = runTest {
        val job = backgroundScope.launch { client.exerciseUpdates().collect {
            when (it) {
                is ExerciseUpdateMessage.Update -> callback.onExerciseUpdateReceived(it.update)
                is ExerciseUpdateMessage.LapSummary -> callback.onLapSummaryReceived(it.lapSummary)
                is ExerciseUpdateMessage.AvailabilityChanged -> callback.onAvailabilityChanged(it.dataType, it.availability)
                is ExerciseUpdateMessage.Event -> callback.onExerciseEventReceived(it.event)
            }
        } }; callback.onRegistered()
        shadowOf(getMainLooper()).idle()

        runCurrent()

        runCurrent()

        assertThat(callback.onRegisteredCalls).isEqualTo(1)
        runCurrent()
        runCurrent()
        assertThat(callback.onRegistrationFailedCalls).isEqualTo(0)
    }

    
    @Test
    fun dataTypeInAvailabilityCallbackShouldMatchRequested_justSampleType_startExercise() = runTest {
        val exerciseConfig =
            ExerciseConfig(
                ExerciseType.WALKING,
                setOf(HEART_RATE_BPM),
                isAutoPauseAndResumeEnabled = false,
                isGpsEnabled = false,
            )
        val availabilityEvent =
            ExerciseUpdateListenerEvent.createAvailabilityUpdateEvent(
                AvailabilityResponse(HEART_RATE_BPM, ACQUIRING)
            )
        val job = backgroundScope.launch { client.exerciseUpdates().collect {
            when (it) {
                is ExerciseUpdateMessage.Update -> callback.onExerciseUpdateReceived(it.update)
                is ExerciseUpdateMessage.LapSummary -> callback.onLapSummaryReceived(it.lapSummary)
                is ExerciseUpdateMessage.AvailabilityChanged -> callback.onAvailabilityChanged(it.dataType, it.availability)
                is ExerciseUpdateMessage.Event -> callback.onExerciseEventReceived(it.event)
            }
        } }; callback.onRegistered()
        val actionJob1 = launch { client.startExercise(exerciseConfig) }
        
        while (actionJob1.isActive) {
            shadowOf(getMainLooper()).idle()
        runCurrent()
            runCurrent()
        }


        fakeService.listener!!.onExerciseUpdateListenerEvent(availabilityEvent)
        shadowOf(getMainLooper()).idle()

        runCurrent()

        runCurrent()

        assertThat(callback.availabilities).containsEntry(HEART_RATE_BPM, ACQUIRING)
    }

    @Test
    fun dataTypeInAvailabilityCallbackShouldMatchRequested_justStatsType_startExercise() = runTest {
        val exerciseConfig =
            ExerciseConfig(
                ExerciseType.WALKING,
                setOf(HEART_RATE_BPM_STATS),
                isAutoPauseAndResumeEnabled = false,
                isGpsEnabled = false,
            )
        val availabilityEvent =
            ExerciseUpdateListenerEvent.createAvailabilityUpdateEvent(
                // Currently the proto form of HEART_RATE_BPM and HEART_RATE_BPM_STATS is identical.
                // The
                // APK doesn't know about _STATS, so pass the sample type to mimic that behavior.
                AvailabilityResponse(HEART_RATE_BPM, ACQUIRING)
            )
        val job = backgroundScope.launch { client.exerciseUpdates().collect {
            when (it) {
                is ExerciseUpdateMessage.Update -> callback.onExerciseUpdateReceived(it.update)
                is ExerciseUpdateMessage.LapSummary -> callback.onLapSummaryReceived(it.lapSummary)
                is ExerciseUpdateMessage.AvailabilityChanged -> callback.onAvailabilityChanged(it.dataType, it.availability)
                is ExerciseUpdateMessage.Event -> callback.onExerciseEventReceived(it.event)
            }
        } }; callback.onRegistered()
        val actionJob2 = launch { client.startExercise(exerciseConfig) }
        
        while (actionJob2.isActive) {
            shadowOf(getMainLooper()).idle()
        runCurrent()
            runCurrent()
        }


        fakeService.listener!!.onExerciseUpdateListenerEvent(availabilityEvent)
        shadowOf(getMainLooper()).idle()

        runCurrent()

        runCurrent()

        assertThat(callback.availabilities).containsEntry(HEART_RATE_BPM_STATS, ACQUIRING)
    }

    @Test
    fun dataTypeInAvailabilityCallbackShouldMatchRequested_statsAndSample_startExercise() = runTest {
        val exerciseConfig =
            ExerciseConfig(
                ExerciseType.WALKING,
                setOf(HEART_RATE_BPM, HEART_RATE_BPM_STATS),
                isAutoPauseAndResumeEnabled = false,
                isGpsEnabled = false,
            )
        val availabilityEvent =
            ExerciseUpdateListenerEvent.createAvailabilityUpdateEvent(
                // Currently the proto form of HEART_RATE_BPM and HEART_RATE_BPM_STATS is identical.
                // The
                // APK doesn't know about _STATS, so pass the sample type to mimic that behavior.
                AvailabilityResponse(HEART_RATE_BPM, ACQUIRING)
            )
        val job = backgroundScope.launch { client.exerciseUpdates().collect {
            when (it) {
                is ExerciseUpdateMessage.Update -> callback.onExerciseUpdateReceived(it.update)
                is ExerciseUpdateMessage.LapSummary -> callback.onLapSummaryReceived(it.lapSummary)
                is ExerciseUpdateMessage.AvailabilityChanged -> callback.onAvailabilityChanged(it.dataType, it.availability)
                is ExerciseUpdateMessage.Event -> callback.onExerciseEventReceived(it.event)
            }
        } }; callback.onRegistered()
        val actionJob3 = launch { client.startExercise(exerciseConfig) }
        
        while (actionJob3.isActive) {
            shadowOf(getMainLooper()).idle()
        runCurrent()
            runCurrent()
        }


        fakeService.listener!!.onExerciseUpdateListenerEvent(availabilityEvent)
        shadowOf(getMainLooper()).idle()
        runCurrent()

        // When both the sample type and stat type are requested, both should be notified
        runCurrent()
        runCurrent()
        assertThat(callback.availabilities).containsEntry(HEART_RATE_BPM, ACQUIRING)
        runCurrent()
        runCurrent()
        assertThat(callback.availabilities).containsEntry(HEART_RATE_BPM_STATS, ACQUIRING)
    }

    @Test
    fun withExerciseTypeConfig_statsAndSample_startExercise() = runTest {
        val exerciseConfig =
            ExerciseConfig(
                ExerciseType.GOLF,
                setOf(HEART_RATE_BPM, HEART_RATE_BPM_STATS),
                isAutoPauseAndResumeEnabled = false,
                isGpsEnabled = false,
                exerciseTypeConfig =
                    GolfExerciseTypeConfig(
                        GolfExerciseTypeConfig.GolfShotTrackingPlaceInfo
                            .GOLF_SHOT_TRACKING_PLACE_INFO_FAIRWAY
                    ),
            )
        val availabilityEvent =
            ExerciseUpdateListenerEvent.createAvailabilityUpdateEvent(
                // Currently the proto form of HEART_RATE_BPM and HEART_RATE_BPM_STATS is identical.
                // The
                // APK doesn't know about _STATS, so pass the sample type to mimic that behavior.
                AvailabilityResponse(HEART_RATE_BPM, ACQUIRING)
            )
        val job = backgroundScope.launch { client.exerciseUpdates().collect {
            when (it) {
                is ExerciseUpdateMessage.Update -> callback.onExerciseUpdateReceived(it.update)
                is ExerciseUpdateMessage.LapSummary -> callback.onLapSummaryReceived(it.lapSummary)
                is ExerciseUpdateMessage.AvailabilityChanged -> callback.onAvailabilityChanged(it.dataType, it.availability)
                is ExerciseUpdateMessage.Event -> callback.onExerciseEventReceived(it.event)
            }
        } }; callback.onRegistered()
        val actionJob4 = launch { client.startExercise(exerciseConfig) }
        
        while (actionJob4.isActive) {
            shadowOf(getMainLooper()).idle()
        runCurrent()
            runCurrent()
        }


        fakeService.listener!!.onExerciseUpdateListenerEvent(availabilityEvent)
        shadowOf(getMainLooper()).idle()
        runCurrent()

        // When both the sample type and stat type are requested, both should be notified
        runCurrent()
        runCurrent()
        assertThat(callback.availabilities).containsEntry(HEART_RATE_BPM, ACQUIRING)
        runCurrent()
        runCurrent()
        assertThat(callback.availabilities).containsEntry(HEART_RATE_BPM_STATS, ACQUIRING)
    }

    @Test
    fun withExerciseEventConfig_startExercise_receiveCorrectExerciseEventCallback() = runTest {
        val exerciseConfig =
            ExerciseConfig(
                ExerciseType.GOLF,
                setOf(GOLF_SHOT_COUNT),
                isAutoPauseAndResumeEnabled = false,
                isGpsEnabled = false,
                exerciseTypeConfig =
                    GolfExerciseTypeConfig(
                        GolfExerciseTypeConfig.GolfShotTrackingPlaceInfo
                            .GOLF_SHOT_TRACKING_PLACE_INFO_PUTTING_GREEN
                    ),
                exerciseEventTypes = setOf(ExerciseEventType.GOLF_SHOT_EVENT),
            )
        val golfShotEvent =
            ExerciseUpdateListenerEvent.createExerciseEventUpdateEvent(
                ExerciseEventResponse(GolfShotEvent(Duration.ofMinutes(1), GolfShotSwingType.PUTT))
            )

        val job = backgroundScope.launch { client.exerciseUpdates().collect {
            when (it) {
                is ExerciseUpdateMessage.Update -> callback.onExerciseUpdateReceived(it.update)
                is ExerciseUpdateMessage.LapSummary -> callback.onLapSummaryReceived(it.lapSummary)
                is ExerciseUpdateMessage.AvailabilityChanged -> callback.onAvailabilityChanged(it.dataType, it.availability)
                is ExerciseUpdateMessage.Event -> callback.onExerciseEventReceived(it.event)
            }
        } }; callback.onRegistered()
        val actionJob5 = launch { client.startExercise(exerciseConfig) }
        
        while (actionJob5.isActive) {
            shadowOf(getMainLooper()).idle()
        runCurrent()
            runCurrent()
        }

        fakeService.listener!!.onExerciseUpdateListenerEvent(golfShotEvent)
        shadowOf(getMainLooper()).idle()

        runCurrent()

        runCurrent()

        assertThat(callback.exerciseEvents)
            .contains(GolfShotEvent(Duration.ofMinutes(1), GolfShotSwingType.PUTT))
    }

    @Test
    fun dataTypeInAvailabilityCallbackShouldMatchRequested_justSampleType_prepare() = runTest {
        val warmUpConfig = WarmUpConfig(ExerciseType.WALKING, setOf(HEART_RATE_BPM))
        val availabilityEvent =
            ExerciseUpdateListenerEvent.createAvailabilityUpdateEvent(
                AvailabilityResponse(HEART_RATE_BPM, ACQUIRING)
            )
        val job = backgroundScope.launch { client.exerciseUpdates().collect {
            when (it) {
                is ExerciseUpdateMessage.Update -> callback.onExerciseUpdateReceived(it.update)
                is ExerciseUpdateMessage.LapSummary -> callback.onLapSummaryReceived(it.lapSummary)
                is ExerciseUpdateMessage.AvailabilityChanged -> callback.onAvailabilityChanged(it.dataType, it.availability)
                is ExerciseUpdateMessage.Event -> callback.onExerciseEventReceived(it.event)
            }
        } }; callback.onRegistered()
        val actionJob6 = launch { client.prepareExercise(warmUpConfig) }
        
        while (actionJob6.isActive) {
            shadowOf(getMainLooper()).idle()
        runCurrent()
            runCurrent()
        }


        fakeService.listener!!.onExerciseUpdateListenerEvent(availabilityEvent)
        shadowOf(getMainLooper()).idle()

        runCurrent()

        runCurrent()

        assertThat(callback.availabilities).containsEntry(HEART_RATE_BPM, ACQUIRING)
    }

    @Test
    fun updateExerciseTypeConfigForActiveExercise() = runTest {
        val exerciseConfig = ExerciseConfig.builder(ExerciseType.GOLF).build()
        val exerciseTypeConfig =
            GolfExerciseTypeConfig(
                GolfExerciseTypeConfig.GolfShotTrackingPlaceInfo
                    .GOLF_SHOT_TRACKING_PLACE_INFO_FAIRWAY
            )
        val job = backgroundScope.launch { client.exerciseUpdates().collect {
            when (it) {
                is ExerciseUpdateMessage.Update -> callback.onExerciseUpdateReceived(it.update)
                is ExerciseUpdateMessage.LapSummary -> callback.onLapSummaryReceived(it.lapSummary)
                is ExerciseUpdateMessage.AvailabilityChanged -> callback.onAvailabilityChanged(it.dataType, it.availability)
                is ExerciseUpdateMessage.Event -> callback.onExerciseEventReceived(it.event)
            }
        } }; callback.onRegistered()
        val actionJob7 = launch { client.startExercise(exerciseConfig) }
        
        while (actionJob7.isActive) {
            shadowOf(getMainLooper()).idle()
        runCurrent()
            runCurrent()
        }


        val actionJob8 = launch { client.updateExerciseTypeConfig(exerciseTypeConfig) }
        
        while (actionJob8.isActive) {
            shadowOf(getMainLooper()).idle()
        runCurrent()
            runCurrent()
        }


        runCurrent()


        runCurrent()


        assertThat(fakeService.exerciseConfig?.exerciseTypeConfig).isEqualTo(exerciseTypeConfig)
    }

    @Test
    fun overrideBatchingModesForActiveExercise_notImplementedError() = runTest {
        val batchingMode = HashSet<BatchingMode>()
        val job = backgroundScope.launch { client.exerciseUpdates().collect {
            when (it) {
                is ExerciseUpdateMessage.Update -> callback.onExerciseUpdateReceived(it.update)
                is ExerciseUpdateMessage.LapSummary -> callback.onLapSummaryReceived(it.lapSummary)
                is ExerciseUpdateMessage.AvailabilityChanged -> callback.onAvailabilityChanged(it.dataType, it.availability)
                is ExerciseUpdateMessage.Event -> callback.onExerciseEventReceived(it.event)
            }
        } }; callback.onRegistered()

        
        var actionJob9: kotlinx.coroutines.Job? = null
        try {
            actionJob9 = launch { client.overrideBatchingModesForActiveExercise(batchingMode) }
            
        while (actionJob9?.isActive == true) {
            shadowOf(getMainLooper()).idle()
        runCurrent()
            runCurrent()
        }

            throw AssertionError("Expected NotImplementedError")
        } catch (e: NotImplementedError) {
            // expected
            actionJob9?.cancel()
        }

    }

    class FakeExerciseUpdateCallback : ExerciseUpdateCallback {
        val availabilities = mutableMapOf<DataType<*, *>, Availability>()
        val registrationFailureThrowables = mutableListOf<Throwable>()
        var onRegisteredCalls = 0
        var onRegistrationFailedCalls = 0
        var exerciseEvents = mutableSetOf<ExerciseEvent>()

        override fun onRegistered() {
            onRegisteredCalls++
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            onRegistrationFailedCalls++
            registrationFailureThrowables.add(throwable)
        }

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {}

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
            availabilities[dataType] = availability
        }

        override fun onExerciseEventReceived(event: ExerciseEvent) {
            when (event) {
                is GolfShotEvent -> {
                    exerciseEvents.add(event)
                }
            }
        }
    }

    class FakeServiceStub : IExerciseApiService.Stub() {

        var listener: IExerciseUpdateListener? = null
        var statusCallbackAction: (IStatusCallback?) -> Unit = { it!!.onSuccess() }
        var exerciseConfig: ExerciseConfig? = null
        val setListenerPackageNames = mutableListOf<String>()
        val clearListenerPackageNames = mutableListOf<String>()

        override fun getApiVersion(): Int = 12

        override fun prepareExercise(
            prepareExerciseRequest: PrepareExerciseRequest?,
            statusCallback: IStatusCallback?,
        ) {
            statusCallbackAction.invoke(statusCallback)
        }

        override fun startExercise(
            startExerciseRequest: StartExerciseRequest?,
            statusCallback: IStatusCallback?,
        ) {
            exerciseConfig = startExerciseRequest?.exerciseConfig
            statusCallbackAction.invoke(statusCallback)
        }

        override fun pauseExercise(packageName: String?, statusCallback: IStatusCallback?) {
            throw NotImplementedError()
        }

        override fun resumeExercise(packageName: String?, statusCallback: IStatusCallback?) {
            throw NotImplementedError()
        }

        override fun endExercise(packageName: String?, statusCallback: IStatusCallback?) {
            throw NotImplementedError()
        }

        override fun markLap(packageName: String?, statusCallback: IStatusCallback?) {
            throw NotImplementedError()
        }

        override fun getCurrentExerciseInfo(
            packageName: String?,
            exerciseInfoCallback: IExerciseInfoCallback?,
        ) {
            throw NotImplementedError()
        }

        override fun setUpdateListener(
            packageName: String,
            listener: IExerciseUpdateListener?,
            statusCallback: IStatusCallback?,
        ) {
            this.listener = listener
            setListenerPackageNames += packageName
            statusCallbackAction.invoke(statusCallback)
        }

        override fun clearUpdateListener(
            packageName: String,
            listener: IExerciseUpdateListener?,
            statusCallback: IStatusCallback?,
        ) {
            clearListenerPackageNames += packageName
            if (this.listener == listener) {
                this.listener = null
            }
            this.statusCallbackAction.invoke(statusCallback)
        }

        override fun addGoalToActiveExercise(
            request: ExerciseGoalRequest?,
            statusCallback: IStatusCallback?,
        ) {
            throw NotImplementedError()
        }

        override fun removeGoalFromActiveExercise(
            request: ExerciseGoalRequest?,
            statusCallback: IStatusCallback?,
        ) {
            throw NotImplementedError()
        }

        override fun addDebouncedGoalToActiveExercise(
            request: DebouncedGoalRequest?,
            statusCallback: IStatusCallback?,
        ) {
            throw NotImplementedError()
        }

        override fun removeDebouncedGoalFromActiveExercise(
            request: DebouncedGoalRequest?,
            statusCallback: IStatusCallback?,
        ) {
            throw NotImplementedError()
        }

        override fun overrideAutoPauseAndResumeForActiveExercise(
            request: AutoPauseAndResumeConfigRequest?,
            statusCallback: IStatusCallback?,
        ) {
            throw NotImplementedError()
        }

        override fun overrideBatchingModesForActiveExercise(
            request: BatchingModeConfigRequest?,
            statusCallback: IStatusCallback?,
        ) {
            throw NotImplementedError()
        }

        override fun getCapabilities(request: CapabilitiesRequest?): ExerciseCapabilitiesResponse {
            throw NotImplementedError()
        }

        override fun flushExercise(request: FlushRequest?, statusCallback: IStatusCallback?) {
            throw NotImplementedError()
        }

        override fun updateExerciseTypeConfigForActiveExercise(
            updateExerciseTypeConfigRequest: UpdateExerciseTypeConfigRequest,
            statuscallback: IStatusCallback,
        ) {
            val newExerciseTypeConfig = updateExerciseTypeConfigRequest.exerciseTypeConfig
            val newExerciseConfig =
                ExerciseConfig.builder(exerciseConfig!!.exerciseType)
                    .setExerciseTypeConfig(newExerciseTypeConfig)
                    .build()
            this.exerciseConfig = newExerciseConfig
            this.statusCallbackAction.invoke(statuscallback)
        }

    }
}
