import re

with open('ExerciseSampleCompose/health-services-client/src/main/java/androidx/health/services/client/data/ExerciseConfig.kt', 'r') as f:
    text = f.read()

# Give default arguments to the first four to make it a pure data class with full defaults where possible, just like Builder allowed.
# Actually, the Builder defaulted `dataTypes` to `emptySet()`, `isAutoPauseAndResumeEnabled` to `false`, and `isGpsEnabled` to `false`.
text = text.replace(
    'val dataTypes: Set<DataType<*, *>>,\n',
    'val dataTypes: Set<DataType<*, *>> = emptySet(),\n'
)
text = text.replace(
    'val isAutoPauseAndResumeEnabled: Boolean,\n',
    'val isAutoPauseAndResumeEnabled: Boolean = false,\n'
)
text = text.replace(
    'val isGpsEnabled: Boolean,\n',
    'val isGpsEnabled: Boolean = false,\n'
)

with open('ExerciseSampleCompose/health-services-client/src/main/java/androidx/health/services/client/data/ExerciseConfig.kt', 'w') as f:
    f.write(text)

