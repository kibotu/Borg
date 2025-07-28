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
 */
sealed class BorgException(message: String) : Exception(message) {
    /**
     * Thrown when components form a dependency cycle.
     * 
     * Why track the full cycle?
     * - Helps developers visualize the dependency problem
     * - Makes it clear which components need restructuring
     * - Provides context for architectural decisions
     * 
     * Example cycle: DatabaseDrone -> RepositoryDrone -> ServiceDrone -> DatabaseDrone
     */
    class CircularDependencyException(
        val cycle: List<Class<out BorgDrone<*>>>
    ) : BorgException(
        "Resistance detected: Circular dependency in drones: ${cycle.joinToString(" -> ") { it.simpleName }}"
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
     * - Configuration errors
     * - Resource access failures
     * - Network connectivity issues
     * - Invalid component state
     */
    class AssimilationException(
        val drone: Class<out BorgDrone<*>>,
        cause: Throwable
    ) : BorgException(
        "Resistance encountered: Failed to assimilate ${drone.simpleName}: ${cause.message}"
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
     * - Missing dependency registration
     * - Incorrect dependency declaration
     * - Module configuration errors
     */
    class DroneNotFoundException(
        val drone: Class<out BorgDrone<*>>,
        val requiredDrone: Class<out BorgDrone<*>>
    ) : BorgException(
        "Resistance detected: Required drone ${requiredDrone.simpleName} not found for ${drone.simpleName}"
    )
} 