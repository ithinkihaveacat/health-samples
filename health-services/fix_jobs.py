import re

with open('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/ServiceBackedExerciseClientTest.kt', 'r') as f:
    text = f.read()

count = 0
def repl(m):
    global count
    count += 1
    return f"val actionJob{count} = launch"

text = re.sub(r'val actionJob = launch', repl, text)

# Now fix the joins
count = 0
def repl2(m):
    global count
    count += 1
    return f"actionJob{count}.join()"

text = re.sub(r'actionJob\.join\(\)', repl2, text)

with open('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/ServiceBackedExerciseClientTest.kt', 'w') as f:
    f.write(text)

