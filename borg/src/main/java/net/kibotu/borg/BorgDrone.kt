package net.kibotu.borg

/**
 * A drone that can be assimilated into the Borg collective.
 * Each drone may require other drones to be assimilated first.
 */
interface BorgDrone<T> {
    /**
     * List of other drones that need to be assimilated before this drone
     */
    fun requiredDrones(): List<Class<out BorgDrone<*>>> = emptyList()

    /**
     * Assimilates this drone into the collective
     * @return The result of the assimilation that can be used by dependent drones
     */
    suspend fun assimilate(): T
} 