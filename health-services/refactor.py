import re

with open('ExerciseSampleCompose/health-services-client/src/main/java/androidx/health/services/client/impl/ServiceBackedExerciseClient.kt', 'r') as f:
    text = f.read()

text = text.replace('import com.google.common.util.concurrent.SettableFuture',
'''import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.ExerciseUpdateCallback''')

# Basic signature and await replacements
text = re.sub(r'override fun (\w+)Async\((.*?)\): ListenableFuture<Void>', r'override suspend fun \1(\2)', text)
text = re.sub(r'override fun (\w+)Async\(\): ListenableFuture<Void>', r'override suspend fun \1()', text)
text = re.sub(r'override fun getCurrentExerciseInfoAsync\(\): ListenableFuture<ExerciseInfo>', r'override suspend fun getCurrentExerciseInfo(): ExerciseInfo', text)
text = re.sub(r'override fun getCapabilitiesAsync\(\): ListenableFuture<ExerciseCapabilities>', r'override suspend fun getCapabilities(): ExerciseCapabilities', text)

# For methods returning execute { ... }, replace with execute { ... }.await()
# We can do this by finding execute { and matching until the closing brace, but it's easier to just append .await() after the block ends.
# A simpler way is to replace `execute {` with `execute {` and then find the corresponding `}` and append `.await()`.
# Since `ServiceBackedExerciseClient` methods are structured simply, we can replace:
text = text.replace('        }\n    }\n\n    override fun', '        }.await()\n    }\n\n    override suspend fun')
text = text.replace('        }\n    }\n\n    internal companion object', '        }.await()\n    }\n\n    internal companion object')
text = text.replace('        )\n    }\n\n    override fun', '        ).await()\n    }\n\n    override suspend fun')
text = text.replace('        )\n    }\n\n    internal companion object', '        ).await()\n    }\n\n    internal companion object')

text = text.replace('        return execute { service, resultFuture ->\n            service.getCurrentExerciseInfo(packageName, ExerciseInfoCallback(resultFuture))\n        }',
                    '        return execute { service, resultFuture ->\n            service.getCurrentExerciseInfo(packageName, ExerciseInfoCallback(resultFuture))\n        }.await()')

text = text.replace('        Futures.transform(\n            execute { service -> service.getCapabilities(CapabilitiesRequest(packageName)) },\n            { response -> response!!.exerciseCapabilities },\n            ContextCompat.getMainExecutor(context),\n        )',
                    '        val response = execute { service -> service.getCapabilities(CapabilitiesRequest(packageName)) }.await()\n        return response.exerciseCapabilities')

# Also for flushAsync
text = text.replace('        return execute { service, resultFuture ->\n            service.flushExercise(request, StatusCallback(resultFuture))\n        }',
                    '        execute { service, resultFuture ->\n            service.flushExercise(request, StatusCallback(resultFuture))\n        }.await()')

# Flow callback replacement
old_callback_start = '    override fun setUpdateCallback(callback: ExerciseUpdateCallback) {'
old_callback_end = '        )\n    }'
# Actually we can just regex out the setUpdateCallback methods and clearUpdateCallbackAsync
text = re.sub(r'    override suspend fun setUpdateCallback\(callback: ExerciseUpdateCallback\) \{.*?(?=    override suspend fun addGoalToActiveExercise)', 
r'''    override fun exerciseUpdates(): Flow<ExerciseUpdate> = callbackFlow {
        val callback = object : ExerciseUpdateCallback {
            override fun onExerciseUpdateReceived(update: ExerciseUpdate) { trySend(update) }
            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
            override fun onRegistered() {}
            override fun onRegistrationFailed(throwable: Throwable) { close(throwable) }
            override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
        }
        val listenerStub =
            ExerciseUpdateListenerStub.ExerciseUpdateListenerCache.INSTANCE.create(
                callback,
                Runnable::run,
                requestedDataTypesProvider = {
                    synchronized(requestedDataTypesLock) { requestedDataTypes.toSet() }
                },
            )
        val future =
            registerListener(listenerStub.listenerKey) { service, result: SettableFuture<Void?> ->
                service.setUpdateListener(packageName, listenerStub, StatusCallback(result))
            }
        try {
            future.await()
        } catch (t: Throwable) {
            close(t)
            return@callbackFlow
        }
        awaitClose {
            val unregisterFuture = unregisterListener(listenerStub.listenerKey) { service, resultFuture ->
                service.clearUpdateListener(packageName, listenerStub, StatusCallback(resultFuture))
            }
            try { unregisterFuture.get() } catch(e: Exception) {}
        }
    }

''', text, flags=re.DOTALL)

with open('ExerciseSampleCompose/health-services-client/src/main/java/androidx/health/services/client/impl/ServiceBackedExerciseClient.kt', 'w') as f:
    f.write(text)

