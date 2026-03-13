import re

with open('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/ServiceBackedExerciseClientTest.kt', 'r') as f:
    text = f.read()

methods = ['prepareExercise', 'startExercise', 'pauseExercise', 'resumeExercise', 'endExercise', 'flush', 'markLap', 'getCurrentExerciseInfo', 'addGoalToActiveExercise', 'removeGoalFromActiveExercise', 'overrideAutoPauseAndResumeForActiveExercise', 'overrideBatchingModesForActiveExercise', 'getCapabilities', 'updateExerciseTypeConfig', 'addDebouncedGoalToActiveExercise', 'removeDebouncedGoalFromActiveExercise']

for m in methods:
    # Match client.method(...) followed by shadowOf(getMainLooper()).idle()
    # It might have no args or some args.
    pattern = r'(\s*)client\.' + m + r'\((.*?)\)\n(\s*)shadowOf\(getMainLooper\(\)\)\.idle\(\)'
    replacement = r'\1val actionJob = launch { client.' + m + r'(\2) }\n\3shadowOf(getMainLooper()).idle()\n\3actionJob.join()'
    text = re.sub(pattern, replacement, text)
    
    # Also for methods with no args like pauseExercise()
    # Wait, the above pattern captures empty parens too! `pauseExercise()` matches `client.pauseExercise()`.
    
with open('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/ServiceBackedExerciseClientTest.kt', 'w') as f:
    f.write(text)
