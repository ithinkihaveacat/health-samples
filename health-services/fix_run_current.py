import re

with open('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/ServiceBackedExerciseClientTest.kt', 'r') as f:
    text = f.read()

# I want to add runCurrent() before every assertThat statement.
# This ensures that any pending events emitted into the flow channel have been processed by the collecting coroutine.
# Actually, the simplest is to replace every `assertThat(` with `kotlinx.coroutines.test.runCurrent()\n        assertThat(`
text = re.sub(r'(\s+)assertThat\(', r'\1kotlinx.coroutines.test.runCurrent()\1assertThat(', text)

# Also need to runCurrent() before verify or other assertions if there are any.
# I will just write this back.
with open('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/ServiceBackedExerciseClientTest.kt', 'w') as f:
    f.write(text)
