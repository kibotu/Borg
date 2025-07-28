package net.kibotu.borg

/**
 * A hierarchy of exceptions that can occur during component initialization.
 * 
 * Why sealed class?
 * - Provides exhaustive when expressions for error handling
 * - Ensures all possible failures are documented
 * - Prevents creation of unexpected error types
 * - Enables smart casting in error handlers
 * 
 * Design principles:
 * 1. Clear Error Categories: Each subclass represents a distinct failure mode
 * 2. Rich Context: Each exception carries relevant data for debugging
 * 3. Helpful Messages: Error descriptions guide users to solutions
 * 4. Clean Stack Traces: Original causes are preserved when wrapping errors
 * 
 * Example usage:
 * ```kotlin
 * try {
 *     borg.assimilate(context)
 * } catch (e: BorgException) {
 *     when (e) {
 *         is CircularDependencyException -> {
 *             // Handle circular dependency detected
 *             Log.e("Borg", "Circular dependency in: ${e.cycle}")
 *         }
 *         is DroneNotFoundException -> {
 *             // Handle missing dependency
 *             Log.e("Borg", "Missing drone: ${e.requiredDrone}")
 *         }
 *         is AssimilationException -> {
 *             // Handle initialization failure
 *             Log.e("Borg", "Failed to initialize: ${e.drone}", e.cause)
 *         }
 *         is DroneNotAssimilatedException -> {
 *             // Handle attempt to access uninitialized drone
 *             Log.e("Borg", "Drone not ready: ${e.drone}")
 *         }
 *     }
 * }
 * ```
 */
sealed class BorgException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /**
     * Thrown when components form a dependency cycle.
     * 
     * Why track the full cycle?
     * - Helps developers visualize the dependency problem
     * - Makes it clear which components need restructuring
     * - Provides context for architectural decisions
     * 
     * Example cycles:
     * - DatabaseDrone -> RepositoryDrone -> ServiceDrone -> DatabaseDrone
     * - ConfigDrone -> NetworkDrone -> ApiDrone -> ConfigDrone
     * 
     * Common fixes:
     * 1. Extract shared dependencies into a separate drone
     * 2. Use dependency injection to break cycles
     * 3. Refactor component responsibilities
     * 4. Use lazy initialization where appropriate
     */
    class CircularDependencyException(
        val cycle: List<Class<*>>
    ) : BorgException(
        "Circular dependency detected: ${cycle.joinToString(" -> ")}"
    )

    /**
     * Thrown when a component fails to initialize.
     * 
     * Why wrap the original exception?
     * - Preserves the original error context
     * - Adds initialization-specific context
     * - Maintains exception hierarchy
     * - Enables detailed error reporting
     * 
     * Common causes:
     * 1. Configuration errors
     *    - Missing API keys
     *    - Invalid URLs
     *    - Malformed config files
     * 
     * 2. Resource access failures
     *    - Database connection errors
     *    - File system permissions
     *    - Memory constraints
     * 
     * 3. Network connectivity issues
     *    - DNS resolution failures
     *    - Timeout errors
     *    - SSL/TLS problems
     * 
     * 4. Invalid component state
     *    - Null dependencies
     *    - Invalid initialization order
     *    - Resource contention
     */
    class AssimilationException(
        val drone: Class<*>,
        cause: Throwable
    ) : BorgException(
        "Failed to assimilate drone $drone",
        cause
    ) {
        override val cause: Throwable = cause
    }

    /**
     * Thrown when a required component is missing from the collective.
     * 
     * Why check dependencies upfront?
     * - Fails fast before any initialization starts
     * - Prevents partial system initialization
     * - Makes dependency errors obvious
     * - Helps maintain clean architecture
     * 
     * Common scenarios:
     * 1. Missing dependency registration
     *    - Forgot to add drone to collective
     *    - Typo in dependency class reference
     * 
     * 2. Incorrect dependency declaration
     *    - Wrong class type in requiredDrones()
     *    - Dependency on abstract/interface type
     * 
     * 3. Module configuration errors
     *    - Missing DI module
     *    - Incomplete feature setup
     * 
     * Best practices:
     * 1. Use dependency injection frameworks
     * 2. Maintain a central registry of drones
     * 3. Document dependencies clearly
     * 4. Write integration tests
     */
    class DroneNotFoundException(
        val drone: Class<*>,
        val requiredDrone: Class<*>
    ) : BorgException(
        "Drone $drone requires $requiredDrone which is not in the collective"
    )

    /**
     * Thrown when attempting to access a drone's result before it has been initialized.
     * 
     * Common scenarios:
     * 1. Accessing drone outside of assimilation process
     * 2. Race condition in parallel initialization
     * 3. Missing dependency declaration
     * 4. Incorrect initialization order
     * 
     * Best practices:
     * 1. Always declare dependencies in requiredDrones()
     * 2. Use requireAssimilated() only when certain of initialization
     * 3. Handle potential DroneNotAssimilatedException in async code
     * 4. Consider lazy initialization for optional dependencies
     */
    class DroneNotAssimilatedException(
        val drone: Class<*>
    ) : BorgException(
        "Drone $drone has not been assimilated yet"
    )
} 