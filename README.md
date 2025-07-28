# Borg 🤖

[![](https://jitpack.io/v/kibotu/Borg.svg)](https://jitpack.io/#kibotu/Borg)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.x-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A lightweight, coroutine-based dependency initialization orchestrator for Android applications. Borg ensures your components are initialized in the correct order, with maximum parallelization and bulletproof thread safety.

> Resistance is futile - your components will be assimilated in perfect order.

## Why Borg? 🤔

Modern Android apps have complex initialization requirements:
- Services that depend on configuration
- Repositories that need database connections
- Analytics that require user session
- Network clients with specific setup needs

Borg solves these challenges by:
- ✅ Automatically resolving initialization order
- ✅ Parallelizing independent initializations
- ✅ Ensuring thread-safe, once-only execution
- ✅ Detecting circular dependencies early
- ✅ Providing clear error messages
- ✅ Supporting Kotlin coroutines natively

## Installation 📦

1. Add JitPack repository to your root build.gradle:
```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

2. Add the dependency:
```groovy
dependencies {
    implementation 'com.github.kibotu:Borg:latest-version'
}
```

## Usage 🛠️

### 1. Define Your Components

Create a drone for each component that needs initialization:

```kotlin
class NetworkClientDrone : BorgDrone<NetworkClient> {
    override suspend fun assimilate() = NetworkClient(
        timeout = 30.seconds,
        retries = 3
    )
}

class ApiDrone(
    private val configDrone: ConfigDrone,
    private val networkDrone: NetworkClientDrone
) : BorgDrone<ApiClient> {
    override fun requiredDrones() = listOf(
        configDrone::class.java,
        networkDrone::class.java
    )
    
    override suspend fun assimilate() = ApiClient(
        baseUrl = configDrone.assimilate().apiUrl,
        client = networkDrone.assimilate()
    )
}
```

### 2. Initialize the Collective

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Create your drones
        val drones = setOf(
            NetworkClientDrone(),
            ConfigDrone(),
            ApiDrone(configDrone, networkDrone),
            DatabaseDrone(),
            RepositoryDrone(databaseDrone)
        )
        
        // Initialize everything in correct order
        lifecycleScope.launch {
            val borg = Borg(drones)
            borg.assimilate()
        }
    }
}
```

### 3. Handle Errors

Borg provides clear error types for common issues:

```kotlin
try {
    borg.assimilate()
} catch (e: BorgException) {
    when (e) {
        is BorgException.CircularDependencyException -> {
            // Handle circular dependency detected
            Log.e("App", "Circular dependency in: ${e.cycle}")
        }
        is BorgException.DroneNotFoundException -> {
            // Handle missing dependency
            Log.e("App", "Missing drone: ${e.requiredDrone}")
        }
        is BorgException.AssimilationException -> {
            // Handle initialization failure
            Log.e("App", "Failed to initialize: ${e.drone}", e.cause)
        }
    }
}
```

## Features 🌟

### Parallel Initialization

Borg automatically parallelizes initialization of independent components:

```kotlin
val drones = setOf(
    AnalyticsDrone(),  // No dependencies
    ConfigDrone(),     // No dependencies
    DatabaseDrone(),   // No dependencies
    ApiDrone(configDrone, networkDrone) // Waits for Config & Network
)
```

In this example, Analytics, Config, and Database initialize in parallel, while Api waits for its dependencies.

### Thread Safety

All initializations are thread-safe and cached:
- Components initialize exactly once
- Results are cached for reuse
- Concurrent access is handled safely
- Deadlocks are prevented

### Dependency Validation

Borg validates the dependency graph before starting:
- Detects missing dependencies
- Identifies circular dependencies
- Ensures complete initialization
- Provides helpful error messages

## Best Practices 💡

1. **Keep Drones Focused**
   - One responsibility per drone
   - Clear, explicit dependencies
   - Immutable results

2. **Handle Failures Gracefully**
   - Provide fallback values
   - Clean up on failure
   - Log detailed errors

3. **Optimize Performance**
   - Minimize dependencies
   - Use parallel initialization
   - Cache expensive operations

## Contributing 🤝

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

## License 📄

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments 🙏

- Inspired by Star Trek's Borg Collective
- Built with Kotlin Coroutines
- Made with ❤️ by [kibotu](https://github.com/kibotu) 