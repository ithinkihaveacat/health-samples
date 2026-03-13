import os
import glob

test_files = glob.glob('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/**/*Test.kt', recursive=True)

for file in test_files:
    with open(file, 'r') as f:
        text = f.read()
    
    # Replace context.packageName with ApplicationProvider.getApplicationContext<Application>().packageName
    text = text.replace('context.packageName', 'ApplicationProvider.getApplicationContext<Application>().packageName')
    
    with open(file, 'w') as f:
        f.write(text)
