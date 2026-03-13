import re

with open('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/ServiceBackedExerciseClientTest.kt', 'r') as f:
    text = f.read()

# Replace actionJob.join() and the preceding idle/runCurrent with a loop
pattern = r'runCurrent\(\)\n\s*shadowOf\(getMainLooper\(\)\)\.idle\(\)\n\s*(actionJob\d+)\.join\(\)'
replacement = r'''
        while (\1.isActive) {
            shadowOf(getMainLooper()).idle()
            runCurrent()
        }
'''
text = re.sub(pattern, replacement, text)

with open('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/ServiceBackedExerciseClientTest.kt', 'w') as f:
    f.write(text)
