import os
import glob

test_files = glob.glob('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/**/*Test.kt', recursive=True)

for file in test_files:
    with open(file, 'r') as f:
        text = f.read()
    
    # Replace literal "androidx.health.services.client.test" with context.packageName
    text = text.replace('"androidx.health.services.client.test"', 'context.packageName')
    
    with open(file, 'w') as f:
        f.write(text)
