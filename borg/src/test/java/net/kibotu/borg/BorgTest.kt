package net.kibotu.borg

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
class BorgTest {

    @Test
    fun `test simple assimilation order`() = runTest {
        // Given
        val assimilationOrder = mutableListOf<String>()

        class Drone1 : BorgDrone<Unit, Unit> {
            override suspend fun assimilate(context: Unit, borg: Borg<Unit>) {
                assimilationOrder.add("Drone1")
            }
        }

        class Drone2 : BorgDrone<Unit, Unit> {
            override fun requiredDrones(): List<Class<out BorgDrone<*, Unit>>> =
                listOf(Drone1::class.java)

            override suspend fun assimilate(context: Unit, borg: Borg<Unit>) {
                assimilationOrder.add("Drone2")
            }
        }

        // When
        Borg(setOf(Drone2(), Drone1())).assimilate(Unit)

        // Then
        assertEquals(listOf("Drone1", "Drone2"), assimilationOrder)
    }

    @Test
    fun `test circular dependency detection`() = runTest {
        // Given
        abstract class BaseDrone : BorgDrone<Unit, Unit> {
            override suspend fun assimilate(context: Unit, borg: Borg<Unit>) = Unit
        }

        lateinit var drone2: KClass<out BaseDrone>

        class Drone1 : BaseDrone() {
            override fun requiredDrones(): List<Class<out BorgDrone<*, Unit>>> = listOf(drone2.java)
        }

        class Drone2 : BaseDrone() {
            override fun requiredDrones(): List<Class<out BorgDrone<*, Unit>>> =
                listOf(Drone1::class.java)
        }

        drone2 = Drone2::class

        try {
            // When
            Borg(setOf(Drone1(), Drone2())).assimilate(Unit)
            fail("Expected CircularDependencyException")
        } catch (e: BorgException.CircularDependencyException) {
            // Then
            assertTrue(e.cycle.contains(Drone1::class.java))
            assertTrue(e.cycle.contains(Drone2::class.java))
        }
    }

    @Test
    fun `test parallel assimilation of independent drones`() = runTest {
        // Given
        val counter = AtomicInteger(0)

        class IndependentDrone1 : BorgDrone<Unit, Unit> {
            override suspend fun assimilate(context: Unit, borg: Borg<Unit>) {
                delay(100) // Simulate work
                counter.incrementAndGet()
            }
        }

        class IndependentDrone2 : BorgDrone<Unit, Unit> {
            override suspend fun assimilate(context: Unit, borg: Borg<Unit>) {
                delay(100) // Simulate work
                counter.incrementAndGet()
            }
        }

        // When
        val startTime = System.nanoTime()
        Borg(setOf(IndependentDrone1(), IndependentDrone2())).assimilate(Unit)
        val duration = (System.nanoTime() - startTime) / 1_000_000 // Convert to milliseconds

        // Then
        assertEquals(2, counter.get())
        assertTrue(
            "Assimilation should take ~100ms if parallel, ~200ms if sequential",
            duration < 150
        )
    }

    @Test
    fun `test complex dependency graph with parallel assimilation`() = runTest {
        val assimilationOrder = mutableListOf<String>()
        val assimilationTimes = mutableMapOf<String, Long>()

        abstract class TimedDrone : BorgDrone<Unit, Unit> {
            override suspend fun assimilate(context: Unit, borg: Borg<Unit>) {
                val startTime = System.nanoTime()
                delay(50) // Simulate some work
                assimilationOrder.add(this::class.simpleName ?: "unknown")
                assimilationTimes[this::class.simpleName ?: "unknown"] =
                    System.nanoTime() - startTime
            }
        }

        class Drone1 : TimedDrone()
        class Drone2 : TimedDrone() {
            override fun requiredDrones(): List<Class<out BorgDrone<*, Unit>>> =
                listOf(Drone1::class.java)
        }

        class Drone3 : TimedDrone() {
            override fun requiredDrones(): List<Class<out BorgDrone<*, Unit>>> =
                listOf(Drone1::class.java)
        }

        class Drone4 : TimedDrone() {
            override fun requiredDrones(): List<Class<out BorgDrone<*, Unit>>> =
                listOf(Drone2::class.java, Drone3::class.java)
        }

        // When
        Borg(setOf(Drone4(), Drone3(), Drone2(), Drone1())).assimilate(Unit)

        // Then
        // Drone1 should be first
        assertEquals("Drone1", assimilationOrder.first())

        // Drone2 and Drone3 should be in parallel (after Drone1)
        assertTrue(assimilationOrder.subList(1, 3).containsAll(listOf("Drone2", "Drone3")))

        // Drone4 should be last
        assertEquals("Drone4", assimilationOrder.last())
    }

    @Test
    fun `test thread safety with concurrent modifications`() = runTest {
        // Track assimilation count
        val assimilationCount = ConcurrentHashMap<String, Int>()
        val droneCount = 5
        val concurrentRuns = 3

        // Create unique drone classes
        abstract class BaseDrone(private val id: Int) : BorgDrone<Unit, Unit> {
            override suspend fun assimilate(context: Unit, borg: Borg<Unit>) {
                delay(10) // Simulate work
                assimilationCount.compute("Drone$id") { _, count -> (count ?: 0) + 1 }
            }
        }

        // Create drones with unique class types
        val drones = (1..droneCount).map { id ->
            when (id) {
                1 -> object : BaseDrone(1) {}
                2 -> object : BaseDrone(2) {}
                3 -> object : BaseDrone(3) {}
                4 -> object : BaseDrone(4) {}
                else -> object : BaseDrone(5) {}
            }
        }.toSet()

        // Create a single Borg instance to test thread safety of assimilation
        val borg = Borg(drones)

        // Run multiple assimilations in parallel
        coroutineScope {
            val jobs = List(concurrentRuns) {
                async {
                    borg.assimilate(Unit)
                }
            }
            jobs.awaitAll()
        }

        // Verify each drone was assimilated exactly once despite multiple concurrent calls
        assimilationCount.forEach { (droneId, count) ->
            assertEquals(
                "Drone $droneId should have been assimilated exactly once, but was assimilated $count times",
                1,
                count
            )
        }

        // Verify all drones were assimilated
        assertEquals(
            "All drones should have been assimilated",
            droneCount,
            assimilationCount.size
        )

        // Verify each expected drone was assimilated
        (1..droneCount).forEach { id ->
            assertTrue(
                "Drone$id should have been assimilated",
                assimilationCount.containsKey("Drone$id")
            )
        }
    }

    @Test
    fun `test error propagation`() = runTest {
        class ResistingDrone : BorgDrone<Unit, Unit> {
            override suspend fun assimilate(context: Unit, borg: Borg<Unit>) {
                throw RuntimeException("Resistance is futile, but I try anyway")
            }
        }

        try {
            Borg(setOf(ResistingDrone())).assimilate(Unit)
            fail("Expected AssimilationException")
        } catch (e: BorgException.AssimilationException) {
            assertEquals("Resistance is futile, but I try anyway", e.cause?.message)
            assertEquals(ResistingDrone::class.java, e.drone)
        }
    }

    @Test
    fun `test missing drone detection`() = runTest {
        class Drone1 : BorgDrone<Unit, Unit> {
            override suspend fun assimilate(context: Unit, borg: Borg<Unit>) = Unit
        }

        class Drone2 : BorgDrone<Unit, Unit> {
            override fun requiredDrones(): List<Class<out BorgDrone<*, Unit>>> =
                listOf(Drone1::class.java)

            override suspend fun assimilate(context: Unit, borg: Borg<Unit>) = Unit
        }

        try {
            Borg(setOf(Drone2())).assimilate(Unit) // Drone1 is missing
            fail("Expected DroneNotFoundException")
        } catch (e: BorgException.DroneNotFoundException) {
            assertEquals(Drone2::class.java, e.drone)
            assertEquals(Drone1::class.java, e.requiredDrone)
        }
    }

    @Test
    fun `test result caching`() = runTest {
        val counter = AtomicInteger(0)

        class CachedDrone : BorgDrone<Int, Unit> {
            override suspend fun assimilate(context: Unit, borg: Borg<Unit>): Int {
                delay(10) // Simulate work
                return counter.incrementAndGet()
            }
        }

        class DependentDrone : BorgDrone<Int, Unit> {
            override fun requiredDrones(): List<Class<out BorgDrone<*, Unit>>> =
                listOf(CachedDrone::class.java)

            override suspend fun assimilate(context: Unit, borg: Borg<Unit>): Int {
                delay(10) // Simulate work
                return counter.get() * 2
            }
        }

        val borg = Borg(setOf(CachedDrone(), DependentDrone()))
        borg.assimilate(Unit)

        assertEquals(1, counter.get()) // Should only be assimilated once
    }

    @Test
    fun `test optional result handling`() = runTest {
        // Given
        class OptionalResultDrone : BorgDrone<String?, Unit> {
            override suspend fun assimilate(context: Unit, borg: Borg<Unit>): String? {
                return null // Simulating an optional result
            }
        }

        class DependentDrone : BorgDrone<String, Unit> {
            override fun requiredDrones(): List<Class<out BorgDrone<*, Unit>>> =
                listOf(OptionalResultDrone::class.java)

            override suspend fun assimilate(context: Unit, borg: Borg<Unit>): String {
                // Get optional result safely
                val optionalValue = borg.getAssimilated(OptionalResultDrone::class.java)
                return optionalValue ?: "default value"
            }
        }

        // When
        val borg = Borg(setOf(OptionalResultDrone(), DependentDrone()))
        borg.assimilate(Unit)

        // Then
        // Verify optional result is properly cached
        assertNull(borg.getAssimilated(OptionalResultDrone::class.java))
        
        // Verify dependent drone can handle null result
        assertEquals("default value", borg.requireAssimilated(DependentDrone::class.java))
    }
} 