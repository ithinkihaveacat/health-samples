package androidx.health.services.client.impl

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.BatchingMode
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataType.Companion.GOLF_SHOT_COUNT
import androidx.health.services.client.data.DataType.Companion.HEART_RATE_BPM
import androidx.health.services.client.data.DataType.Companion.HEART_RATE_BPM_STATS
import androidx.health.services.client.data.DebouncedGoal
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseEventType
import androidx.health.services.client.data.ExerciseGoal
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseTypeConfig
import androidx.health.services.client.data.GolfExerciseTypeConfig
import androidx.health.services.client.data.GolfShotEvent
import androidx.health.services.client.data.GolfShotEvent.GolfShotSwingType
import android.os.Looper.getMainLooper
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
import app.cash.turbine.test
import androidx.health.services.client.ExerciseUpdateMessage
import androidx.health.services.client.data.DataTypeAvailability.Companion.ACQUIRING

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [org.robolectric.annotation.Config.TARGET_SDK])
class ServiceBackedExerciseClientTest {

    private lateinit var client: ServiceBackedExerciseClient
    private lateinit var fakeService: FakeServiceStub

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
        fakeService.listener = null
    }

    @Test
    fun setUpdateCallback_registeredCallbackShouldBeInvoked() = runTest {
        client.exerciseUpdates().test {
            shadowOf(getMainLooper()).idle()
            assertThat(fakeService.setListenerPackageNames).contains(ApplicationProvider.getApplicationContext<Application>().packageName)
            cancelAndIgnoreRemainingEvents()
        }
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
            
        client.exerciseUpdates().test {
            val actionJob = launch { client.startExercise(exerciseConfig) }
            runCurrent()
            shadowOf(getMainLooper()).idle()
            actionJob.join()

            fakeService.listener!!.onExerciseUpdateListenerEvent(availabilityEvent)
            shadowOf(getMainLooper()).idle()

            val item = awaitItem() as ExerciseUpdateMessage.AvailabilityChanged
            assertThat(item.dataType).isEqualTo(HEART_RATE_BPM)
            assertThat(item.availability).isEqualTo(ACQUIRING)
            
            cancelAndIgnoreRemainingEvents()
        }
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
                AvailabilityResponse(HEART_RATE_BPM, ACQUIRING)
            )
        
        client.exerciseUpdates().test {
            val actionJob = launch { client.startExercise(exerciseConfig) }
            runCurrent()
            shadowOf(getMainLooper()).idle()
            actionJob.join()

            fakeService.listener!!.onExerciseUpdateListenerEvent(availabilityEvent)
            shadowOf(getMainLooper()).idle()

            val item = awaitItem() as ExerciseUpdateMessage.AvailabilityChanged
            assertThat(item.dataType).isEqualTo(HEART_RATE_BPM_STATS)
            assertThat(item.availability).isEqualTo(ACQUIRING)
            
            cancelAndIgnoreRemainingEvents()
        }
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
                AvailabilityResponse(HEART_RATE_BPM, ACQUIRING)
            )
            
        client.exerciseUpdates().test {
            val actionJob = launch { client.startExercise(exerciseConfig) }
            runCurrent()
            shadowOf(getMainLooper()).idle()
            actionJob.join()

            fakeService.listener!!.onExerciseUpdateListenerEvent(availabilityEvent)
            shadowOf(getMainLooper()).idle()

            val item1 = awaitItem() as ExerciseUpdateMessage.AvailabilityChanged
            val item2 = awaitItem() as ExerciseUpdateMessage.AvailabilityChanged
            
            val dataTypes = setOf(item1.dataType, item2.dataType)
            assertThat(dataTypes).containsExactly(HEART_RATE_BPM, HEART_RATE_BPM_STATS)
            assertThat(item1.availability).isEqualTo(ACQUIRING)
            assertThat(item2.availability).isEqualTo(ACQUIRING)
            
            cancelAndIgnoreRemainingEvents()
        }
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
                AvailabilityResponse(HEART_RATE_BPM, ACQUIRING)
            )
            
        client.exerciseUpdates().test {
            val actionJob = launch { client.startExercise(exerciseConfig) }
            runCurrent()
            shadowOf(getMainLooper()).idle()
            actionJob.join()

            fakeService.listener!!.onExerciseUpdateListenerEvent(availabilityEvent)
            shadowOf(getMainLooper()).idle()

            val item1 = awaitItem() as ExerciseUpdateMessage.AvailabilityChanged
            val item2 = awaitItem() as ExerciseUpdateMessage.AvailabilityChanged
            
            val dataTypes = setOf(item1.dataType, item2.dataType)
            assertThat(dataTypes).containsExactly(HEART_RATE_BPM, HEART_RATE_BPM_STATS)
            
            cancelAndIgnoreRemainingEvents()
        }
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

        client.exerciseUpdates().test {
            val actionJob = launch { client.startExercise(exerciseConfig) }
            runCurrent()
            shadowOf(getMainLooper()).idle()
            actionJob.join()

            fakeService.listener!!.onExerciseUpdateListenerEvent(golfShotEvent)
            shadowOf(getMainLooper()).idle()

            val item = awaitItem() as ExerciseUpdateMessage.Event
            assertThat(item.event).isEqualTo(GolfShotEvent(Duration.ofMinutes(1), GolfShotSwingType.PUTT))
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dataTypeInAvailabilityCallbackShouldMatchRequested_justSampleType_prepare() = runTest {
        val warmUpConfig = WarmUpConfig(ExerciseType.WALKING, setOf(HEART_RATE_BPM))
        val availabilityEvent =
            ExerciseUpdateListenerEvent.createAvailabilityUpdateEvent(
                AvailabilityResponse(HEART_RATE_BPM, ACQUIRING)
            )
            
        client.exerciseUpdates().test {
            val actionJob = launch { client.prepareExercise(warmUpConfig) }
            runCurrent()
            shadowOf(getMainLooper()).idle()
            actionJob.join()

            fakeService.listener!!.onExerciseUpdateListenerEvent(availabilityEvent)
            shadowOf(getMainLooper()).idle()

            val item = awaitItem() as ExerciseUpdateMessage.AvailabilityChanged
            assertThat(item.dataType).isEqualTo(HEART_RATE_BPM)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateExerciseTypeConfigForActiveExercise() = runTest {
        val exerciseConfig =
            ExerciseConfig(
                ExerciseType.GOLF,
                setOf(HEART_RATE_BPM, HEART_RATE_BPM_STATS),
                isAutoPauseAndResumeEnabled = false,
                isGpsEnabled = false,
                exerciseTypeConfig =
                    GolfExerciseTypeConfig(
                        GolfExerciseTypeConfig.GolfShotTrackingPlaceInfo
                            .GOLF_SHOT_TRACKING_PLACE_INFO_PUTTING_GREEN
                    ),
            )
        val exerciseTypeConfig: ExerciseTypeConfig =
            GolfExerciseTypeConfig(
                GolfExerciseTypeConfig.GolfShotTrackingPlaceInfo
                    .GOLF_SHOT_TRACKING_PLACE_INFO_FAIRWAY
            )
            
        client.exerciseUpdates().test {
            val actionJob = launch { client.startExercise(exerciseConfig) }
            runCurrent()
            shadowOf(getMainLooper()).idle()
            actionJob.join()

            val actionJob2 = launch { client.updateExerciseTypeConfig(exerciseTypeConfig) }
            runCurrent()
            shadowOf(getMainLooper()).idle()
            actionJob2.join()

            assertThat(fakeService.exerciseConfig?.exerciseTypeConfig).isEqualTo(exerciseTypeConfig)
            cancelAndIgnoreRemainingEvents()
        }
    }
    class FakeServiceStub : IExerciseApiService.Stub() {
        var listener: IExerciseUpdateListener? = null
        var statusCallbackAction: (IStatusCallback?) -> Unit = { it?.onSuccess() }
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
            // We use the new data class constructor instead of builder in stage 2 if we changed it.
            // But let's wait until we actually change ExerciseConfig before fixing this FakeStub method.
            val newExerciseConfig =
                ExerciseConfig(exerciseType = exerciseConfig!!.exerciseType, exerciseTypeConfig = newExerciseTypeConfig)
            this.exerciseConfig = newExerciseConfig
            this.statusCallbackAction.invoke(statuscallback)
        }

    }
}