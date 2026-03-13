import re

with open('ExerciseSampleCompose/app/src/main/java/com/example/exercisesamplecompose/data/ExerciseClientManager.kt', 'r') as f:
    text = f.read()

# Remove old imports
imports_to_remove = [
    'import androidx.health.services.client.endExercise\n',
    'import androidx.health.services.client.getCapabilities\n',
    'import androidx.health.services.client.markLap\n',
    'import androidx.health.services.client.pauseExercise\n',
    'import androidx.health.services.client.prepareExercise\n',
    'import androidx.health.services.client.resumeExercise\n',
    'import androidx.health.services.client.startExercise\n',
    'import androidx.health.services.client.ExerciseUpdateCallback\n',
    'import kotlinx.coroutines.channels.awaitClose\n',
    'import kotlinx.coroutines.channels.trySendBlocking\n',
    'import kotlinx.coroutines.flow.callbackFlow\n'
]
for imp in imports_to_remove:
    text = text.replace(imp, '')

text = text.replace('import androidx.health.services.client.data.WarmUpConfig',
                    'import androidx.health.services.client.data.WarmUpConfig\nimport androidx.health.services.client.ExerciseUpdateMessage\nimport kotlinx.coroutines.flow.map\nimport kotlinx.coroutines.flow.filterNotNull')

old_flow_pattern = r'val exerciseUpdateFlow =\s*callbackFlow \{.*?\n        \}'

new_flow = '''val exerciseUpdateFlow = exerciseClient.exerciseUpdates().map { message ->
        when (message) {
            is ExerciseUpdateMessage.Update -> ExerciseMessage.ExerciseUpdateMessage(message.update)
            is ExerciseUpdateMessage.LapSummary -> ExerciseMessage.LapSummaryMessage(message.lapSummary)
            is ExerciseUpdateMessage.AvailabilityChanged -> {
                if (message.availability is LocationAvailability) {
                    ExerciseMessage.LocationAvailabilityMessage(message.availability)
                } else null
            }
            else -> null
        }
    }.filterNotNull()'''

text = re.sub(old_flow_pattern, new_flow, text, flags=re.DOTALL)

with open('ExerciseSampleCompose/app/src/main/java/com/example/exercisesamplecompose/data/ExerciseClientManager.kt', 'w') as f:
    f.write(text)
