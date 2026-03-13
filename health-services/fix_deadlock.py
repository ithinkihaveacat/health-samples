import re

with open('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/ServiceBackedExerciseClientTest.kt', 'r') as f:
    text = f.read()

# I want to insert `kotlinx.coroutines.test.runCurrent()` before `shadowOf(getMainLooper()).idle()` 
# whenever it's immediately preceded by `val actionJob... = launch { ... }`

# The current structure is:
# val actionJob1 = launch { client.startExercise(exerciseConfig) }
# shadowOf(getMainLooper()).idle()
# actionJob1.join()

pattern = r'(val (actionJob\d+) = launch \{ client\..*? \})\n(\s*)shadowOf\(getMainLooper\(\)\)\.idle\(\)'
replacement = r'\1\n\3kotlinx.coroutines.test.runCurrent()\n\3shadowOf(getMainLooper()).idle()'

text = re.sub(pattern, replacement, text)

with open('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/ServiceBackedExerciseClientTest.kt', 'w') as f:
    f.write(text)
