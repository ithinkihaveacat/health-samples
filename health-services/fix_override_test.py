import re

with open('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/ServiceBackedExerciseClientTest.kt', 'r') as f:
    text = f.read()

# Fix the overrideBatchingModes test hanging
# The problem is that Turbine's .test { ... } block awaits completion unless we call cancelAndIgnoreRemainingEvents()
# But wait, overrideBatchingModesForActiveExercise_notImplementedError doesn't use Turbine. It uses runTest.
text = text.replace('        try {\n            val actionJob = launch { client.overrideBatchingModesForActiveExercise(batchingMode) }\n            runCurrent()\n            shadowOf(getMainLooper()).idle()\n            actionJob.join()\n            throw AssertionError("Expected NotImplementedError")\n        } catch (e: NotImplementedError) {\n            // expected\n        }',
'''        try {
            client.overrideBatchingModesForActiveExercise(batchingMode)
            shadowOf(getMainLooper()).idle()
            throw AssertionError("Expected NotImplementedError")
        } catch (e: NotImplementedError) {
            // expected
        }''')

with open('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/ServiceBackedExerciseClientTest.kt', 'w') as f:
    f.write(text)
