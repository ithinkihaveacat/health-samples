import re

with open('ExerciseSampleCompose/health-services-client/src/main/java/androidx/health/services/client/impl/ServiceBackedExerciseClient.kt', 'r') as f:
    text = f.read()

imports = """import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.ExerciseUpdate
"""
text = text.replace('import com.google.common.util.concurrent.SettableFuture', imports)

text = text.replace('import java.util.concurrent.Executor\n', '')
text = text.replace('import com.google.common.util.concurrent.FutureCallback\n', '')
text = text.replace('import com.google.common.util.concurrent.Futures\n', '')
text = text.replace('import com.google.common.util.concurrent.ListenableFuture\n', '')


def repl1(m):
    return f"override suspend fun {m.group(1)}({m.group(2)}) {{{{\n        {m.group(3)}{m.group(4)}.await()\n    }}}}\n"

text = re.sub(r'override fun (\w+)Async\((.*?)\):\s*ListenableFuture<Void>\s*=\s*(executeWithVersionCheck|execute)\s*(\{.*?\}|.*?)\n', repl1, text, flags=re.DOTALL)

def repl2(m):
    return f"override suspend fun {m.group(1)}() {{{{\n        {m.group(2)}{m.group(3)}.await()\n    }}}}\n"

text = re.sub(r'override fun (\w+)Async\(\):\s*ListenableFuture<Void>\s*=\s*(executeWithVersionCheck|execute)\s*(\{.*?\}|.*?)\n', repl2, text, flags=re.DOTALL)

def repl3(m):
    return f"override suspend fun {m.group(1)}({m.group(2)}) {{{{\n        {m.group(3)}(\n{m.group(4)}\n        ).await()\n    }}}}"

text = re.sub(r'override fun (\w+)Async\((.*?)\):\s*ListenableFuture<Void>\s*\{\s*return (executeWithVersionCheck|execute)\s*\((.*?)\)\s*\}', repl3, text, flags=re.DOTALL)


def repl4(m):
    return f"override suspend fun getCurrentExerciseInfo(): ExerciseInfo {{{{\n        return execute {m.group(1)}.await()\n    }}}}"
text = re.sub(r'override fun getCurrentExerciseInfoAsync\(\):\s*ListenableFuture<ExerciseInfo>\s*\{\s*return execute (\{.*?\})\s*\}', repl4, text, flags=re.DOTALL)


def repl5(m):
    return f"override suspend fun getCapabilities(): ExerciseCapabilities {{{{\n        val response = execute {m.group(1)}.await()\n        return response!!.exerciseCapabilities\n    }}}}"
text = re.sub(r'override fun getCapabilitiesAsync\(\):\s*ListenableFuture<ExerciseCapabilities>\s*=\s*Futures\.transform\(\s*execute\s*(\{.*?\}),\s*(\{.*?\}),\s*ContextCompat\.getMainExecutor\(context\),\s*\)', repl5, text, flags=re.DOTALL)


def repl6(m):
    return f"override suspend fun flush() {{{{\n        val request = FlushRequest(packageName)\n        execute {m.group(1)}.await()\n    }}}}"
text = re.sub(r'override fun flushAsync\(\):\s*ListenableFuture<Void>\s*\{\s*val request = FlushRequest\(packageName\)\s*return execute (\{.*?\})\s*\}', repl6, text, flags=re.DOTALL)


flow_code = '''
    override fun exerciseUpdates(): Flow<ExerciseUpdate> = callbackFlow {
        val callback = object : ExerciseUpdateCallback {
            override fun onExerciseUpdateReceived(update: ExerciseUpdate) { trySend(update) }
            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
            override fun onRegistered() {}
            override fun onRegistrationFailed(throwable: Throwable) { close(throwable) }
            override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
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
            try { unregisterFuture.get() } catch(e: Exception) {}
        }
    }

'''

text = re.sub(r'    override fun setUpdateCallback\(callback: ExerciseUpdateCallback\) \{.*?(?=    override fun addGoalToActiveExerciseAsync)', flow_code, text, flags=re.DOTALL)

# Handle the remaining async suffixes manually or safely
text = text.replace('override fun addGoalToActiveExerciseAsync(', 'override suspend fun addGoalToActiveExercise(')
text = text.replace('override fun removeGoalFromActiveExerciseAsync(', 'override suspend fun removeGoalFromActiveExercise(')
text = text.replace('override fun overrideAutoPauseAndResumeForActiveExerciseAsync(', 'override suspend fun overrideAutoPauseAndResumeForActiveExercise(')
text = text.replace('override fun overrideBatchingModesForActiveExerciseAsync(', 'override suspend fun overrideBatchingModesForActiveExercise(')
text = text.replace('override fun updateExerciseTypeConfigAsync(', 'override suspend fun updateExerciseTypeConfig(')
text = text.replace('override fun addDebouncedGoalToActiveExerciseAsync(', 'override suspend fun addDebouncedGoalToActiveExercise(')
text = text.replace('override fun removeDebouncedGoalFromActiveExerciseAsync(', 'override suspend fun removeDebouncedGoalFromActiveExercise(')


with open('ExerciseSampleCompose/health-services-client/src/main/java/androidx/health/services/client/impl/ServiceBackedExerciseClient.kt', 'w') as f:
    f.write(text)
