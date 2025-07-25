package net.kibotu.borg

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * The Borg coordinates the assimilation of components into the collective.
 * Resistance is futile - each component will be assimilated exactly once in the correct order.
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
     * Assimilates all drones into the collective in the correct order.
     * This method is thread-safe and can be called multiple times concurrently.
     * Each drone will be assimilated exactly once, with results cached for subsequent calls.
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