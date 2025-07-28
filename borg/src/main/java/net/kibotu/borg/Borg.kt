package net.kibotu.borg

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * A thread-safe dependency injection and initialization orchestrator inspired by Star Trek's Borg collective.
 * 
 * Why Borg?
 * - Ensures deterministic initialization of complex, interdependent components (drones)
 * - Prevents common pitfalls like circular dependencies and race conditions
 * - Maximizes performance through parallel initialization where dependencies allow
 * - Provides fail-fast behavior by validating the dependency graph upfront
 * 
 * Key Features:
 * - Thread-safe initialization with results cached for subsequent access
 * - Automatic parallel initialization of independent components
 * - Topological sorting to respect dependency order
 * - Early detection of missing or circular dependencies
 * - Coroutine-based for non-blocking operation
 * 
 * Example usage:
 * ```
 * val drones = setOf(
 *     DatabaseDrone(),      // No dependencies
 *     RepositoryDrone(),    // Depends on DatabaseDrone
 *     ViewModelDrone()      // Depends on RepositoryDrone
 * )
 * val borg = Borg(drones)
 * runBlocking { borg.assimilate() }
 * ```
 * 
 * @throws BorgException.DroneNotFoundException When a required drone is missing from the collective
 * @throws BorgException.CircularDependencyException When a circular dependency is detected
 * @throws BorgException.AssimilationException When a drone fails to initialize
 */
class Borg(drones: Set<BorgDrone<*>>) {
    private val droneMap: Map<Class<out BorgDrone<*>>, BorgDrone<*>> = drones
        .associateBy { it::class.java }
        .also { map ->
            // Verify all dependencies are present in the collective
            drones.forEach { drone ->
                drone.requiredDrones().forEach { dependency ->
                    if (!map.containsKey(dependency)) {
                        throw BorgException.DroneNotFoundException(
                            drone = drone::class.java,
                            requiredDrone = dependency
                        )
                    }
                }
            }
        }

    private val collective = ConcurrentHashMap<Class<out BorgDrone<*>>, Any?>()
    private val assimilating = Collections.synchronizedSet(mutableSetOf<Class<out BorgDrone<*>>>())
    private val assimilated = Collections.synchronizedSet(mutableSetOf<Class<out BorgDrone<*>>>())
    private val assimilationMutex = Mutex()
    private val unitMutex = Mutex()

    /**
     * Orchestrates the initialization of all components in the collective while maximizing parallelism.
     * 
     * Why suspend?
     * - Allows non-blocking initialization of slow components (e.g. network, disk I/O)
     * - Enables parallel initialization without blocking threads
     * - Integrates naturally with coroutine-based Android/Kotlin applications
     * 
     * Why cache results?
     * - Prevents redundant initialization costs on subsequent calls
     * - Ensures consistent singleton-like behavior across the app
     * - Reduces memory usage by sharing initialized components
     * 
     * Implementation details:
     * 1. Groups independent components into "units" that can be initialized in parallel
     * 2. Processes units sequentially to respect dependencies
     * 3. Within each unit, components are initialized concurrently
     * 4. Results are cached thread-safely for future access
     */
    suspend fun assimilate() = coroutineScope {
        // Get sorted units for parallel assimilation where possible
        val units = getAssimilationUnits()
        
        // Assimilate units in order, with parallel assimilation within each unit
        units.forEach { unit ->
            // All drones in a unit can be assimilated in parallel
            val deferreds = unit.map { droneClass ->
                async {
                    assimilateDrone(droneClass)
                }
            }
            deferreds.awaitAll()
        }
    }

    /**
     * Initializes a single component while respecting its dependencies.
     * 
     * Why mutex-protected?
     * - Prevents duplicate initialization under high concurrency
     * - Ensures exactly-once semantics for component initialization
     * - Maintains consistency of the dependency graph
     * 
     * The triple-check pattern is used because:
     * 1. First check: Quick rejection without lock overhead
     * 2. Second check: Handle race condition before expensive work
     * 3. Final check: Ultimate guard against concurrent initialization
     * 
     * @throws BorgException.DroneNotFoundException When the requested drone is not in the collective
     * @throws BorgException.AssimilationException When initialization fails
     */
    private suspend fun assimilateDrone(clazz: Class<out BorgDrone<*>>): Any? {
        // Return cached result if already assimilated
        collective[clazz]?.let { return it }

        // Assimilate all required drones first
        val drone = droneMap[clazz] ?: throw BorgException.DroneNotFoundException(
            drone = clazz,
            requiredDrone = clazz
        )

        // Assimilate all dependencies first
        val dependencies = drone.requiredDrones()
        coroutineScope {
            val deferreds = dependencies.map { dependency ->
                async {
                    assimilateDrone(dependency)
                }
            }
            deferreds.awaitAll()
        }

        // Double-check if someone else assimilated it while we were waiting
        collective[clazz]?.let { return it }

        // Acquire the assimilation lock for this specific drone
        return assimilationMutex.withLock {
            // Triple-check under mutex
            collective[clazz]?.let { return it }

            try {
                val result = drone.assimilate()
                collective[clazz] = result
                result
            } catch (e: Exception) {
                throw BorgException.AssimilationException(
                    drone = clazz,
                    cause = e
                )
            }
        }
    }

    /**
     * Groups components into initialization units that can be processed in parallel.
     * 
     * Why topological sorting?
     * - Maximizes initialization parallelism while respecting dependencies
     * - Prevents deadlocks by detecting cycles early
     * - Ensures deterministic initialization order
     * 
     * The algorithm:
     * 1. Sorts drones by dependency count for optimal processing
     * 2. Groups independent drones into parallel units
     * 3. Maintains strict ordering between dependent units
     * 4. Detects and prevents circular dependencies
     * 
     * @throws BorgException.CircularDependencyException When a dependency cycle is detected
     */
    private suspend fun getAssimilationUnits(): List<List<Class<out BorgDrone<*>>>> = unitMutex.withLock {
        val units = mutableListOf<List<Class<out BorgDrone<*>>>>()
        val stack = mutableListOf<Class<out BorgDrone<*>>>()
        
        // Reset tracking sets under mutex lock
        assimilating.clear()
        assimilated.clear()

        // Process each unassimilated drone in dependency order
        val sortedDrones = droneMap.keys.sortedWith(compareBy(
            { clazz -> droneMap[clazz]?.requiredDrones()?.size ?: 0 },
            { it.name } // Secondary sort by name for stable ordering
        ))

        sortedDrones.forEach { drone ->
            if (drone !in assimilated) {
                processDrone(drone, stack, units)
            }
        }

        units.reversed()
    }

    /**
     * Processes a single drone and its dependencies to build the initialization graph.
     * 
     * Why recursive?
     * - Naturally handles nested dependencies of any depth
     * - Simplifies cycle detection through the call stack
     * - Makes the dependency traversal order explicit
     * 
     * State tracking:
     * - assimilating: Tracks drones being processed (for cycle detection)
     * - assimilated: Prevents reprocessing of completed drones
     * - stack: Maintains the current dependency chain
     */
    private fun processDrone(
        drone: Class<out BorgDrone<*>>,
        stack: MutableList<Class<out BorgDrone<*>>>,
        units: MutableList<List<Class<out BorgDrone<*>>>>
    ) {
        // Check for circular dependencies
        if (drone in assimilating) {
            val cycle = assimilating.toList()
            throw BorgException.CircularDependencyException(cycle)
        }

        // Skip if already fully assimilated
        if (drone in assimilated) {
            return
        }

        assimilating.add(drone)

        // Process all dependencies first
        val currentDrone = droneMap[drone] ?: return
        currentDrone.requiredDrones().forEach { dependency ->
            processDrone(dependency, stack, units)
        }

        assimilating.remove(drone)
        assimilated.add(drone)
        stack.add(drone)

        // If this is a root drone (no unassimilated dependents),
        // create a new unit
        if (isRootDrone(drone)) {
            val unit = mutableListOf<Class<out BorgDrone<*>>>()
            while (stack.isNotEmpty() && !hasUnassimilatedDependents(stack.last())) {
                unit.add(stack.removeAt(stack.lastIndex))
            }
            if (unit.isNotEmpty()) {
                units.add(unit.reversed()) // Reverse to maintain dependency order
            }
        }
    }

    private fun isRootDrone(drone: Class<out BorgDrone<*>>): Boolean {
        return droneMap.values.none { current ->
            drone in current.requiredDrones() && current::class.java !in assimilated
        }
    }

    private fun hasUnassimilatedDependents(drone: Class<out BorgDrone<*>>): Boolean {
        return droneMap.values.any { current ->
            drone in current.requiredDrones() && current::class.java !in assimilated
        }
    }
}