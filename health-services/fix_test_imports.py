import re

with open('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/ServiceBackedExerciseClientTest.kt', 'r') as f:
    text = f.read()

# Fix GolfShotSwingType import
text = text.replace(
    'import androidx.health.services.client.data.GolfShotSwingType\n',
    'import androidx.health.services.client.data.GolfShotEvent.GolfShotSwingType\nimport android.os.Looper.getMainLooper\n'
)

# Fix client.packageName access
text = text.replace(
    'assertThat(fakeService.setListenerPackageNames).contains(client.packageName)',
    'assertThat(fakeService.setListenerPackageNames).contains(ApplicationProvider.getApplicationContext<Application>().packageName)'
)

with open('ExerciseSampleCompose/health-services-client/src/test/java/androidx/health/services/client/impl/ServiceBackedExerciseClientTest.kt', 'w') as f:
    f.write(text)
