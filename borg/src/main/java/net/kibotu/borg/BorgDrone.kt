package net.kibotu.borg

/**
 * Contract for components that need ordered, dependency-aware initialization.
 * 
 * Why an interface?
 * - Keeps initialization logic separate from component business logic
 * - Makes dependencies explicit through the type system
 * - Allows mocking in tests
 * - Generic context parameter allows flexibility in initialization
 * 
 * Example implementations:
 * ```kotlin
 * // 1. Simple configuration drone
 * class ConfigDrone : BorgDrone<AppConfig, Context> {
 *     override suspend fun assimilate(context: Context, borg: Borg<Context>): AppConfig {
 *         return AppConfig.load(context.assets.open("config.json"))
 *     }
 * }
 * 
 * // 2. Database initialization with config dependency
 * class DatabaseDrone(private val configDrone: ConfigDrone) : BorgDrone<Database, Context> {
 *     override fun requiredDrones() = listOf(configDrone::class.java)
 *     
 *     override suspend fun assimilate(context: Context, borg: Borg<Context>): Database {
 *         val config = borg.requireAssimilated(configDrone::class.java)
 *         return Room.databaseBuilder(context, Database::class.java, config.dbName)
 *             .build()
 *     }
 * }
 * 
 * // 3. Repository with multiple dependencies
 * class RepositoryDrone(
 *     private val databaseDrone: DatabaseDrone,
 *     private val apiDrone: ApiDrone
 * ) : BorgDrone<Repository, Context> {
 *     override fun requiredDrones() = listOf(
 *         databaseDrone::class.java,
 *         apiDrone::class.java
 *     )
 *     
 *     override suspend fun assimilate(context: Context, borg: Borg<Context>): Repository {
 *         val db = borg.requireAssimilated(databaseDrone::class.java)
 *         val api = borg.requireAssimilated(apiDrone::class.java)
 *         return Repository(db, api)
 *     }
 * }
 * 
 * // 4. Analytics with fallback handling
 * class AnalyticsDrone : BorgDrone<Analytics, Context> {
 *     override suspend fun assimilate(context: Context, borg: Borg<Context>): Analytics {
 *         return try {
 *             FirebaseAnalytics.getInstance(context)
 *         } catch (e: Exception) {
 *             NoOpAnalytics() // Fallback implementation
 *         }
 *     }
 * }
 * ```
 * 
 * Best practices:
 * 1. Keep drones focused and single-purpose
 * 2. Make dependencies explicit in constructor
 * 3. Handle errors gracefully with fallbacks
 * 4. Use meaningful names that reflect purpose
 * 5. Document any special initialization requirements
 * 6. Consider thread safety in returned objects
 * 7. Cache expensive operations appropriately
 * 8. Write unit tests for initialization logic
 * 
 * @param T The type of component this drone initializes
 * @param C The context type needed for initialization
 */
interface BorgDrone<T, C> {
    /**
     * Declares initialization prerequisites.
     * 
     * Why a list?
     * - Natural representation of multiple dependencies
     * - Order doesn't matter (Borg handles sequencing)
     * - Empty by default for leaf components
     * 
     * Best practices:
     * 1. List ALL required dependencies
     * 2. Use constructor injection for dependencies
     * 3. Keep dependency count low (ideally ≤ 3)
     * 4. Document why each dependency is needed
     * 5. Consider breaking up drones with many dependencies
     * 
     * Example:
     * ```kotlin
     * override fun requiredDrones() = listOf(
     *     configDrone::class.java,  // For API configuration
     *     authDrone::class.java,    // For authentication tokens
     *     logDrone::class.java      // For request logging
     * )
     * ```
     * 
     * @return List of drone classes this drone depends on
     */
    fun requiredDrones(): List<Class<out BorgDrone<*, C>>> = emptyList()

    /**
     * Creates and configures a component instance.
     * 
     * Contract:
     * - Must be idempotent (same result for multiple calls)
     * - Should complete initialization fully or fail fast
     * - May access results of required drones via [borg]
     * - Should return immutable or thread-safe result
     * 
     * Best practices:
     * 1. Validate inputs and dependencies early
     * 2. Handle errors gracefully with fallbacks
     * 3. Log important initialization steps
     * 4. Clean up resources on failure
     * 5. Keep initialization logic simple
     * 6. Document any assumptions or requirements
     * 
     * Example error handling:
     * ```kotlin
     * override suspend fun assimilate(context: C, borg: Borg<C>): T {
     *     try {
     *         // 1. Get dependencies
     *         val config = borg.requireAssimilated(configDrone::class.java)
     *         
     *         // 2. Validate configuration
     *         require(config.isValid) { "Invalid configuration" }
     *         
     *         // 3. Initialize component
     *         return MyComponent(config)
     *             .also { it.initialize() }
     *             
     *     } catch (e: Exception) {
     *         // 4. Clean up on failure
     *         cleanup()
     *         throw BorgException.AssimilationException(
     *             drone = this::class.java,
     *             cause = e
     *         )
     *     }
     * }
     * ```
     * 
     * @param context The context object needed for initialization
     * @param borg The Borg instance, providing access to assimilated drone values
     * @return The initialized component instance
     * @throws BorgException if initialization fails
     */
    suspend fun assimilate(context: C, borg: Borg<C>): T
} 