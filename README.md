# Borg 🤖

[![](https://jitpack.io/v/kibotu/Borg.svg)](https://jitpack.io/#kibotu/Borg)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.x-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202-blue)](LICENSE)

> Resistance is futile - your components will be assimilated in perfect order.

🚀 **About**
Borg is a high-performance Android initialization orchestrator that brings order to your app's startup chaos. It automatically manages complex dependency graphs, parallelizes independent initializations, and provides bulletproof thread safety - all with the elegance of Kotlin coroutines.

`#android` `#kotlin` `#coroutines` `#dependency-management` `#initialization` `#performance` `#async` `#thread-safe` `#type-safe` `#parallel-execution` `#dependency-injection` `#startup-optimization` `#android-library` `#kotlin-first` `#testing-support`

A powerful, coroutine-based dependency initialization orchestrator for Android applications. Borg ensures your components are initialized in the correct order, with maximum parallelization and bulletproof thread safety.

## Table of Contents 📑
- [Why Borg?](#why-borg-)
- [Key Features](#key-features-)
- [Installation](#installation-)
- [Quick Start](#quick-start-)
- [Advanced Usage](#advanced-usage-)
- [Best Practices](#best-practices-)
- [Error Handling](#error-handling-)
- [Testing](#testing-)
- [Comparison with Alternatives](#comparison-with-alternatives-)
- [Contributing](#contributing-)
- [License](#license-)

## Why Borg? 🤔

Modern Android apps face complex initialization challenges:

### Common Problems
- ❌ Race conditions in component initialization
- ❌ Unclear dependency ordering
- ❌ Blocking main thread during setup
- ❌ Hard to test initialization logic
- ❌ Difficult error recovery
- ❌ Poor performance from sequential initialization

### Borg's Solutions
- ✅ Thread-safe, deterministic initialization
- ✅ Automatic dependency resolution
- ✅ Non-blocking coroutine-based setup
- ✅ Easy to test with constructor injection
- ✅ Structured error handling
- ✅ Maximum parallel initialization

## Key Features 🌟

- **Type-Safe Dependencies**: Compile-time verification of dependency graph
- **Parallel Initialization**: Automatic parallelization of independent components
- **Coroutine Support**: Native suspend function support for async operations
- **Thread Safety**: Bulletproof concurrency handling with deadlock prevention
- **Error Handling**: Rich exception hierarchy with detailed context
- **Testing Support**: Easy mocking and testing through constructor injection
- **Performance**: Optimal initialization order with parallel execution
- **Flexibility**: Generic context type for any initialization needs

## Installation 📦

1. Add JitPack repository:
```groovy
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

2. Add the dependency:
```groovy
// build.gradle.kts
dependencies {
    implementation("com.github.kibotu:Borg:latest-version")
}
```

## Quick Start 🚀

### 1. Define Your Components

Create a drone for each component that needs initialization:

```kotlin
// 1. Simple configuration
class ConfigDrone : BorgDrone<AppConfig, Context> {
    override suspend fun assimilate(context: Context, borg: Borg<Context>) =
        AppConfig.load(context.assets.open("config.json"))
}

// 2. Database with config dependency
class DatabaseDrone : BorgDrone<AppDatabase, Context> {
    override fun requiredDrones() = listOf(ConfigDrone::class.java)
    
    override suspend fun assimilate(context: Context, borg: Borg<Context>): AppDatabase {
        val config = borg.requireAssimilated(ConfigDrone::class.java)
        return Room.databaseBuilder(context, AppDatabase::class.java, config.dbName)
            .build()
    }
}

// 3. Repository combining multiple dependencies
class RepositoryDrone : BorgDrone<Repository, Context> {
    override fun requiredDrones() = listOf(
        DatabaseDrone::class.java,
        ApiDrone::class.java
    )
    
    override suspend fun assimilate(context: Context, borg: Borg<Context>) = Repository(
        database = borg.requireAssimilated(DatabaseDrone::class.java),
        api = borg.requireAssimilated(ApiDrone::class.java)
    )
}
```

### 2. Initialize the Collective

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        
        lifecycleScope.launch {
            try {
                // Create and initialize the collective
                val borg = Borg(setOf(
                    ConfigDrone(),
                    DatabaseDrone(),
                    ApiDrone(),
                    RepositoryDrone()
                ))
                
                // Assimilate all components
                borg.assimilate(applicationContext)
                
                // Store initialized components
                appContainer.repository = borg.requireAssimilated(RepositoryDrone::class.java)
                
            } catch (e: BorgException) {
                handleInitializationError(e)
            }
        }
    }
}
```

## Advanced Usage 🔧

### Parallel Initialization

Borg automatically parallelizes initialization of independent components:

```kotlin
val drones = setOf(
    AnalyticsDrone(),     // No dependencies - Parallel
    ConfigDrone(),        // No dependencies - Parallel
    DatabaseDrone(),      // Depends on Config - Waits for Config
    ApiDrone()           // Depends on Config - Waits for Config
)

// Visualization of parallel execution:
// Time →
// Analytics   ▓▓▓▓▓▓▓
// Config      ▓▓▓▓
// Database         ▓▓▓▓ (starts after Config)
// Api              ▓▓▓▓ (starts after Config)
```

### Handling Optional Dependencies

Use `getAssimilated()` for optional dependencies:

```kotlin
class AnalyticsDrone : BorgDrone<Analytics, Context> {
    override fun requiredDrones() = listOf(UserDrone::class.java)
    
    override suspend fun assimilate(context: Context, borg: Borg<Context>): Analytics {
        val analytics = FirebaseAnalytics.getInstance(context)
        
        // Optional user identification
        borg.getAssimilated(UserDrone::class.java)?.let { user ->
            analytics.setUserId(user.id)
        }
        
        return analytics
    }
}
```

### Fallback Handling

Implement graceful degradation:

```kotlin
class RemoteConfigDrone : BorgDrone<Config, Context> {
    override suspend fun assimilate(context: Context, borg: Borg<Context>): Config {
        return try {
            // Try remote config first
            FirebaseRemoteConfig.getInstance()
                .fetchAndActivate()
                .await()
                .let { RemoteConfig() }
        } catch (e: Exception) {
            // Fall back to local config
            LocalConfig.fromAssets(context)
        }
    }
}
```

## Best Practices 💡

### 1. Keep Drones Focused

```kotlin
// ❌ Bad: Too many responsibilities
class MonolithicDrone : BorgDrone<AppServices, Context> {
    override suspend fun assimilate(context: Context, borg: Borg<Context>): AppServices {
        val db = Room.databaseBuilder(/*...*/).build()
        val api = Retrofit.Builder().build()
        val analytics = FirebaseAnalytics.getInstance(context)
        return AppServices(db, api, analytics)
    }
}

// ✅ Good: Single responsibility
class DatabaseDrone : BorgDrone<AppDatabase, Context> {
    override suspend fun assimilate(context: Context, borg: Borg<Context>) =
        Room.databaseBuilder(context, AppDatabase::class.java, "app.db").build()
}
```

### 2. Handle Errors Gracefully

```kotlin
class ApiDrone : BorgDrone<ApiClient, Context> {
    override fun requiredDrones() = listOf(ConfigDrone::class.java)
    
    override suspend fun assimilate(context: Context, borg: Borg<Context>): ApiClient {
        try {
            val config = borg.requireAssimilated(ConfigDrone::class.java)
            
            // Validate configuration
            require(config.apiUrl.isNotBlank()) { "API URL is required" }
            
            return ApiClient(config.apiUrl)
                .also { client ->
                    // Verify connectivity
                    client.ping()
                        .await()
                        .also { response ->
                            require(response.isSuccessful) {
                                "API not reachable: ${response.code()}"
                            }
                        }
                }
                
        } catch (e: Exception) {
            throw BorgException.AssimilationException(
                drone = this::class.java,
                cause = e
            )
        }
    }
}
```

### 3. Document Dependencies

```kotlin
class RepositoryDrone : BorgDrone<Repository, Context> {
    override fun requiredDrones() = listOf(
        DatabaseDrone::class.java,
        ApiDrone::class.java,
        CacheDrone::class.java
    )
    
    override suspend fun assimilate(context: Context, borg: Borg<Context>): Repository {
        val db = borg.requireAssimilated(DatabaseDrone::class.java)
        val api = borg.requireAssimilated(ApiDrone::class.java)
        val cache = borg.getAssimilated(CacheDrone::class.java)
        
        return Repository(db, api, cache)
    }
}
```

## Error Handling 🚨

Borg provides structured error handling:

```kotlin
try {
    borg.assimilate(context)
} catch (e: BorgException) {
    when (e) {
        is BorgException.CircularDependencyException -> {
            // Circular dependency detected
            Log.e("Borg", "Dependency cycle: ${e.cycle.joinToString(" -> ")}")
        }
        is BorgException.DroneNotFoundException -> {
            // Missing required drone
            Log.e("Borg", "${e.drone} requires ${e.requiredDrone}")
        }
        is BorgException.AssimilationException -> {
            // Initialization failed
            Log.e("Borg", "Failed to initialize ${e.drone}", e.cause)
        }
        is BorgException.DroneNotAssimilatedException -> {
            // Accessed uninitialized drone
            Log.e("Borg", "Drone not ready: ${e.drone}")
        }
    }
}
```

## Testing 🧪

Borg is designed for testability:

```kotlin
class RepositoryTest {
    @Test
    fun `test repository initialization`() = runTest {
        // Given
        val mockDb = mockk<AppDatabase>()
        val mockApi = mockk<ApiClient>()
        
        val dbDrone = object : BorgDrone<AppDatabase, Context> {
            override suspend fun assimilate(context: Context, borg: Borg<Context>) = mockDb
        }
        
        val apiDrone = object : BorgDrone<ApiClient, Context> {
            override suspend fun assimilate(context: Context, borg: Borg<Context>) = mockApi
        }
        
        val repositoryDrone = RepositoryDrone(dbDrone, apiDrone)
        
        // When
        val borg = Borg(setOf(dbDrone, apiDrone, repositoryDrone))
        borg.assimilate(mockk())
        
        // Then
        val repository = repositoryDrone.assimilate(mockk(), borg)
        assertNotNull(repository)
    }
}
```

## Comparison with Alternatives 🔄

### vs androidx.startup

| Feature | Borg | androidx.startup |
|---------|------|-----------------|
| Dependency Resolution | ✅ Automatic, type-safe with compile-time validation | ✅ Automatic via dependencies() method |
| Parallel Initialization | ✅ Automatic parallel execution of independent components | ❌ Sequential execution only |
| Coroutine Support | ✅ Native suspend function support | ❌ Blocking calls only |
| Error Handling | ✅ Structured exception hierarchy with dependency context | ❌ Basic exceptions without dependency context |
| Thread Safety | ✅ Full thread safety with deadlock prevention | ✅ Basic thread safety |
| Initialization Caching | ✅ Thread-safe result caching | ✅ Component-level caching |
| Circular Dependency Detection | ✅ Compile-time detection with clear error messages | ✅ Runtime detection |
| Testing Support | ✅ Easy to mock with direct instantiation | ❌ Requires ContentProvider mocking |
| Lazy Initialization | ✅ On-demand initialization with dependency tracking | ✅ Manual lazy initialization |
| Configuration | ✅ Runtime configuration with constructor params | ❌ Manifest metadata only |
| Auto-initialization | ❌ Manual Application.onCreate() call | ✅ Automatic via ContentProvider |
| Library Size | ❌ Larger due to coroutine support | ✅ Very small footprint |

### When to Use What?

**Choose Borg when you need:**
- Type-safe dependency management with compile-time validation
- Maximum performance through parallel initialization
- Async operations with coroutine support
- Rich error context for debugging dependency issues
- Runtime configuration flexibility
- Direct component testing without Android dependencies
- Complex dependency graphs with clear visualization

**Choose androidx.startup when:**
- You want automatic initialization without Application class changes
- Your initialization chain is relatively simple
- You prefer configuration through AndroidManifest.xml
- Minimal library footprint is critical
- You're strictly following Android component lifecycle
- You don't need async initialization support

### vs Dagger/Hilt

| Feature | Borg | Dagger/Hilt |
|---------|------|-------------|
| Focus | Initialization order | Dependency injection |
| Learning Curve | 📊 Medium | 📈 Steep |
| Compile-time Safety | ✅ Yes | ✅ Yes |
| Initialization Control | ✅ Explicit | ❌ Implicit |
| Parallel Init | ✅ Automatic | ❌ Manual |
| Android Integration | ✅ Native context | ✅ Full framework |

## Contributing 🤝

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License 📄

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments 🙏

- Inspired by Star Trek's Borg Collective
- Built with Kotlin Coroutines
- Made with ❤️ by [kibotu](https://github.com/kibotu) 