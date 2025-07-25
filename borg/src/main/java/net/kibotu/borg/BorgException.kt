package net.kibotu.borg

sealed class BorgException(message: String) : Exception(message) {
    class CircularDependencyException(
        val cycle: List<Class<out BorgDrone<*>>>
    ) : BorgException(
        "Resistance detected: Circular dependency in drones: ${cycle.joinToString(" -> ") { it.simpleName }}"
    )

    class AssimilationException(
        val drone: Class<out BorgDrone<*>>,
        cause: Throwable
    ) : BorgException(
        "Resistance encountered: Failed to assimilate ${drone.simpleName}: ${cause.message}"
    ) {
        override val cause: Throwable = cause
    }

    class DroneNotFoundException(
        val drone: Class<out BorgDrone<*>>,
        val requiredDrone: Class<out BorgDrone<*>>
    ) : BorgException(
        "Resistance detected: Required drone ${requiredDrone.simpleName} not found for ${drone.simpleName}"
    )
} 