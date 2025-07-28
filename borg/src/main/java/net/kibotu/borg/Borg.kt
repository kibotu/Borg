package net.kibotu.borg

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTimedValue

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
 * - Generic context type for flexible initialization
 * - Access to cached drone values during assimilation
 *
 * Example usage:
 * ```
 * // Dagger component example
 * class DaggerDrone : BorgDrone<DaggerComponent, Context> {
 *     override suspend fun assimilate(context: Context) =
 *         DaggerAppComponent.factory().create(context)
 * }
 *
 * class RepositoryDrone(private val daggerDrone: DaggerDrone) : BorgDrone<Repository, Context> {
 *     override fun requiredDrones() = listOf(daggerDrone::class.java)
 *
 *     override suspend fun assimilate(context: Context, borg: Borg<Context>): Repository {
 *         val daggerComponent = borg.getAssimilated(daggerDrone::class.java)
 *         return daggerComponent.repository()
 *     }
 * }
 * ```
 *
 * @throws BorgException.DroneNotFoundException When a required drone is missing from the collective
 * @throws BorgException.CircularDependencyException When a circular dependency is detected
 * @throws BorgException.AssimilationException When a drone fails to initialize
 */
class Borg<C>(drones: Set<BorgDrone<*, C>>, private val enableLogging: Boolean = false) {
    private val droneMap: Map<Class<out BorgDrone<*, C>>, BorgDrone<*, C>> = drones
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

    private val collective = ConcurrentHashMap<Class<out BorgDrone<*, C>>, Any?>()
    private val assimilating =
        Collections.synchronizedSet(mutableSetOf<Class<out BorgDrone<*, C>>>())
    private val assimilated =
        Collections.synchronizedSet(mutableSetOf<Class<out BorgDrone<*, C>>>())
    private val assimilationMutex = Mutex()
    private val unitMutex = Mutex()

    // Define an enum for assimilation state
    enum class AssimilationState {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETE
    }

    val assimilationState: SharedFlow<AssimilationState>
        field = MutableStateFlow(AssimilationState.NOT_STARTED)

    fun log(message: String) {
        if (enableLogging) {
            Log.v("Borg", message)
        }
    }

    /**
     * Gets a previously assimilated drone value.
     *
     * @param droneClass The class of the drone whose value you want to retrieve
     * @return The assimilated value, or null if not yet assimilated
     * @throws ClassCastException if the cached value is not of type T
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getAssimilated(droneClass: Class<out BorgDrone<T, C>>): T? = collective[droneClass] as? T

    /**
     * Gets a previously assimilated drone value, throwing if not found.
     *
     * @param droneClass The class of the drone whose value you want to retrieve
     * @return The assimilated value
     * @throws BorgException.DroneNotAssimilatedException if the drone has not been assimilated yet
     * @throws ClassCastException if the cached value is not of type T
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> requireAssimilated(droneClass: Class<out BorgDrone<T, C>>): T =
        collective[droneClass] as? T ?: throw BorgException.DroneNotAssimilatedException(
            droneClass
        )

    /**
     * Orchestrates the initialization of all components in the collective while maximizing parallelism.
     *
     * @param context The context object needed for initialization of all drones
     */
    suspend fun assimilate(context: C) = coroutineScope {
        assimilationState.value = AssimilationState.IN_PROGRESS
        // Get sorted units for parallel assimilation where possible
        val units = getAssimilationUnits()

        // Assimilate units in order, with parallel assimilation within each unit
        units.forEach { unit ->
            // All drones in a unit can be assimilated in parallel
            val deferreds = unit.map { droneClass ->
                async {
                    val dt = measureTimedValue {
                        assimilateDrone(droneClass, context)
                    }
                    log("${droneClass.simpleName}: assimilated after ${dt.duration}")
                    dt.value
                }
            }
            deferreds.awaitAll()
        }
        assimilationState.value = AssimilationState.COMPLETE
        log("All drones have been assimilated.")
    }

    /**
     * Initializes a single component while respecting its dependencies.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun assimilateDrone(clazz: Class<out BorgDrone<*, C>>, context: C): Any? {
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
                    assimilateDrone(dependency, context)
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
                val result = (drone as BorgDrone<Any?, C>).assimilate(context, this)
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
    private suspend fun getAssimilationUnits(): List<List<Class<out BorgDrone<*, C>>>> =
        unitMutex.withLock {
            val units = mutableListOf<List<Class<out BorgDrone<*, C>>>>()
            val stack = mutableListOf<Class<out BorgDrone<*, C>>>()

            // Reset tracking sets under mutex lock
            assimilating.clear()
            assimilated.clear()

            // Process each unassimilated drone in dependency order
            val sortedDrones = droneMap.keys.sortedWith(
                compareBy(
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
        drone: Class<out BorgDrone<*, C>>,
        stack: MutableList<Class<out BorgDrone<*, C>>>,
        units: MutableList<List<Class<out BorgDrone<*, C>>>>
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
            val unit = mutableListOf<Class<out BorgDrone<*, C>>>()
            while (stack.isNotEmpty() && !hasUnassimilatedDependents(stack.last())) {
                unit.add(stack.removeAt(stack.lastIndex))
            }
            if (unit.isNotEmpty()) {
                units.add(unit.reversed()) // Reverse to maintain dependency order
            }
        }
    }

    private fun isRootDrone(drone: Class<out BorgDrone<*, C>>): Boolean {
        return droneMap.values.none { current ->
            drone in current.requiredDrones() && current::class.java !in assimilated
        }
    }

    private fun hasUnassimilatedDependents(drone: Class<out BorgDrone<*, C>>): Boolean {
        return droneMap.values.any { current ->
            drone in current.requiredDrones() && current::class.java !in assimilated
        }
    }
}