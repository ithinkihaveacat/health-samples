import re

with open('ExerciseSampleCompose/health-services-client/src/main/java/androidx/health/services/client/data/ExerciseConfig.kt', 'r') as f:
    text = f.read()

# Make it a data class
text = text.replace('class ExerciseConfig\n@JvmOverloads\nconstructor(', 'data class ExerciseConfig(\n')

# Remove the Builder class
# The Builder starts at `class Builder(` and ends before `override fun toString()`
builder_pattern = r'/\*\* Builder for \[ExerciseConfig\] instances\. \*/\s*class Builder\(.*?(?=\s*override fun toString\(\))'
text = re.sub(builder_pattern, '', text, flags=re.DOTALL)

# Remove the builder method in Companion object
builder_method_pattern = r'/\*\*\s*\* Returns a fresh new \[Builder\].*?@JvmStatic fun builder\(exerciseType: ExerciseType\): Builder = Builder\(exerciseType\)'
text = re.sub(builder_method_pattern, '', text, flags=re.DOTALL)

with open('ExerciseSampleCompose/health-services-client/src/main/java/androidx/health/services/client/data/ExerciseConfig.kt', 'w') as f:
    f.write(text)

