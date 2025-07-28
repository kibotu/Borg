package net.kibotu.borg

/**
 * Contract for components that need ordered, dependency-aware initialization.
 * 
 * Why an interface?
 * - Keeps initialization logic separate from component business logic
 * - Makes dependencies explicit through the type system
 * - Allows mocking in tests
 * 
 * Example implementation:
 * ```
 * class NetworkClientDrone : BorgDrone<NetworkClient> {
 *     override suspend fun assimilate() = NetworkClient(
 *         timeout = 30.seconds,
 *         retries = 3
 *     )
 * }
 * 
 * class ApiDrone(
 *     private val configDrone: ConfigDrone,
 *     private val networkDrone: NetworkClientDrone
 * ) : BorgDrone<ApiClient> {
 *     override fun requiredDrones() = listOf(
 *         configDrone::class.java,
 *         networkDrone::class.java
 *     )
 *     
 *     override suspend fun assimilate() = ApiClient(
 *         baseUrl = configDrone.assimilate().apiUrl,
 *         client = networkDrone.assimilate()
 *     )
 * }
 * ```
 */
interface BorgDrone<T> {
    /**
     * Declares initialization prerequisites.
     * 
     * Why a list?
     * - Natural representation of multiple dependencies
     * - Order doesn't matter (Borg handles sequencing)
     * - Empty by default for leaf components
     */
    fun requiredDrones(): List<Class<out BorgDrone<*>>> = emptyList()

    /**
     * Creates and configures a component instance.
     * 
     * Contract:
     * - Must be idempotent
     * - Should complete initialization fully or fail fast
     * - May access results of required drones
     * - Should return immutable or thread-safe result
     */
    suspend fun assimilate(): T
} 