# Borg 🤖

[![](https://jitpack.io/v/kibotu/Borg.svg)](https://jitpack.io/#kibotu/Borg)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.x-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A powerful, coroutine-based dependency initialization orchestrator for Android applications. Borg ensures your components are initialized in the correct order, with maximum parallelization and bulletproof thread safety.

> Resistance is futile - your components will be assimilated in perfect order.

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
class DatabaseDrone(
    private val configDrone: ConfigDrone
) : BorgDrone<AppDatabase, Context> {
    override fun requiredDrones() = listOf(configDrone::class.java)
    
    override suspend fun assimilate(context: Context, borg: Borg<Context>): AppDatabase {
        val config = borg.requireAssimilated(configDrone::class.java)
        return Room.databaseBuilder(context, AppDatabase::class.java, config.dbName)
            .build()
    }
}

// 3. Repository combining multiple dependencies
class RepositoryDrone(
    private val dbDrone: DatabaseDrone,
    private val apiDrone: ApiDrone
) : BorgDrone<Repository, Context> {
    override fun requiredDrones() = listOf(
        dbDrone::class.java,
        apiDrone::class.java
    )
    
    override suspend fun assimilate(context: Context, borg: Borg<Context>) = Repository(
        database = borg.requireAssimilated(dbDrone::class.java),
        api = borg.requireAssimilated(apiDrone::class.java)
    )
}
```

### 2. Initialize the Collective

```kotlin
class App : Application() {
    private val configDrone = ConfigDrone()
    private val databaseDrone = DatabaseDrone(configDrone)
    private val apiDrone = ApiDrone(configDrone)
    private val repositoryDrone = RepositoryDrone(databaseDrone, apiDrone)
    
    override fun onCreate() {
        super.onCreate()
        
        lifecycleScope.launch {
            try {
                // Create and initialize the collective
                val borg = Borg(setOf(
                    configDrone,
                    databaseDrone,
                    apiDrone,
                    repositoryDrone
                ))
                
                // Assimilate all components
                borg.assimilate(applicationContext)
                
                // Store initialized components
                appContainer.repository = repositoryDrone.assimilate(applicationContext, borg)
                
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
    DatabaseDrone(),      // No dependencies - Parallel
    ApiDrone(configDrone) // Waits for Config only
)

// Visualization of parallel execution:
// Time →
// Analytics   ▓▓▓▓▓▓▓
// Config      ▓▓▓▓
// Database    ▓▓▓▓▓▓▓▓
// Api              ▓▓▓▓ (starts after Config)
```

### Handling Optional Dependencies

Use `getAssimilated()` for optional dependencies:

```kotlin
class AnalyticsDrone(
    private val userDrone: UserDrone? = null
) : BorgDrone<Analytics, Context> {
    override fun requiredDrones() = userDrone?.let {
        listOf(it::class.java)
    } ?: emptyList()
    
    override suspend fun assimilate(context: Context, borg: Borg<Context>): Analytics {
        val analytics = FirebaseAnalytics.getInstance(context)
        
        // Optional user identification
        userDrone?.let { drone ->
            borg.getAssimilated(drone::class.java)?.let { user ->
                analytics.setUserId(user.id)
            }
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
class ApiDrone(
    private val configDrone: ConfigDrone
) : BorgDrone<ApiClient, Context> {
    override fun requiredDrones() = listOf(configDrone::class.java)
    
    override suspend fun assimilate(context: Context, borg: Borg<Context>): ApiClient {
        try {
            val config = borg.requireAssimilated(configDrone::class.java)
            
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
class RepositoryDrone(
    /** Required for database access */
    private val databaseDrone: DatabaseDrone,
    /** Required for API communication */
    private val apiDrone: ApiDrone,
    /** Optional: For caching responses */
    private val cacheDrone: CacheDrone? = null
) : BorgDrone<Repository, Context> {
    override fun requiredDrones() = buildList {
        add(databaseDrone::class.java)
        add(apiDrone::class.java)
        cacheDrone?.let { add(it::class.java) }
    }
    
    override suspend fun assimilate(context: Context, borg: Borg<Context>): Repository {
        val db = borg.requireAssimilated(databaseDrone::class.java)
        val api = borg.requireAssimilated(apiDrone::class.java)
        val cache = cacheDrone?.let { borg.getAssimilated(it::class.java) }
        
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
| Dependency Resolution | ✅ Automatic, type-safe | ❌ Manual ordering |
| Parallel Initialization | ✅ Automatic | ❌ Sequential only |
| Coroutine Support | ✅ Native | ❌ Blocking only |
| Error Handling | ✅ Structured | ❌ Basic |
| Thread Safety | ✅ Comprehensive | ✅ Basic |
| Testing Support | ✅ Constructor injection | ❌ ContentProvider mocking |
| Configuration | ✅ Runtime | ❌ Manifest only |

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