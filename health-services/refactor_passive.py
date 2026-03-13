import re

with open('ExerciseSampleCompose/health-services-client/src/main/java/androidx/health/services/client/data/PassiveListenerConfig.kt', 'r') as f:
    text = f.read()

# Make it a data class
text = text.replace('public class PassiveListenerConfig(', 'public data class PassiveListenerConfig(\n')

# Convert the constructor parameters to have default arguments
# Since they are not null, we can provide empty sets/false
text = text.replace(
    'public val dataTypes: Set<DataType<out Any, out DataPoint<out Any>>>,\n',
    'public val dataTypes: Set<DataType<out Any, out DataPoint<out Any>>> = emptySet(),\n'
)
text = text.replace(
    '@get:JvmName("shouldUserActivityInfoBeRequested")\n    public val shouldUserActivityInfoBeRequested: Boolean,\n',
    '@get:JvmName("shouldUserActivityInfoBeRequested")\n    public val shouldUserActivityInfoBeRequested: Boolean = false,\n'
)
text = text.replace(
    'public val dailyGoals: Set<PassiveGoal>,\n',
    'public val dailyGoals: Set<PassiveGoal> = emptySet(),\n'
)
text = text.replace(
    'public val healthEventTypes: Set<HealthEvent.Type>,\n',
    'public val healthEventTypes: Set<HealthEvent.Type> = emptySet(),\n'
)

# Remove the Builder class
builder_pattern = r'/\*\* Builder for \[PassiveListenerConfig\] instances\. \*/\s*public class Builder \{.*?(?=\s*internal val proto: DataProto\.PassiveListenerConfig)'
text = re.sub(builder_pattern, '', text, flags=re.DOTALL)

# Remove the builder method in Companion object
builder_method_pattern = r'@JvmStatic public fun builder\(\): Builder = Builder\(\)'
text = re.sub(builder_method_pattern, '', text, flags=re.DOTALL)

with open('ExerciseSampleCompose/health-services-client/src/main/java/androidx/health/services/client/data/PassiveListenerConfig.kt', 'w') as f:
    f.write(text)

with open('ExerciseSampleCompose/health-services-client/src/main/java/androidx/health/services/client/data/WarmUpConfig.kt', 'r') as f:
    text = f.read()

text = text.replace('public class WarmUpConfig(', 'public data class WarmUpConfig(')

with open('ExerciseSampleCompose/health-services-client/src/main/java/androidx/health/services/client/data/WarmUpConfig.kt', 'w') as f:
    f.write(text)
