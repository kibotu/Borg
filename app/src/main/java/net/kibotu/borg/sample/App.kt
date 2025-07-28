package net.kibotu.borg.sample

import android.app.Application
import android.content.Context
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.kibotu.borg.Borg
import net.kibotu.borg.BorgDrone
import javax.inject.Singleton

class App : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        setupBorg()
    }

    private fun setupBorg() {
        val borg = Borg(
            drones = setOf(
                DaggerDrone(),
                RepositoryDrone()
            ),
            enableLogging = true
        )

        // Initialize all drones with context
        applicationScope.launch {
            borg.assimilate(applicationContext)
        }
    }
}

/**
 * Example Dagger setup
 */
@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {
    fun repository(): Repository

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance context: Context): AppComponent
    }
}

@Module
object AppModule {
    @Provides
    @Singleton
    fun provideRepository(context: Context): Repository {
        return Repository(context)
    }
}

class Repository(private val context: Context)

/**
 * Drones for Dagger integration
 */
class DaggerDrone : BorgDrone<AppComponent, Context> {
    override suspend fun assimilate(context: Context, borg: Borg<Context>): AppComponent =
        DaggerAppComponent.factory().create(context)
}

class RepositoryDrone : BorgDrone<Repository, Context> {
    override fun requiredDrones() = listOf(DaggerDrone::class.java)

    override suspend fun assimilate(context: Context, borg: Borg<Context>): Repository {
        // Get the Dagger component from the collective
        val daggerComponent = borg.requireAssimilated(DaggerDrone::class.java)
        // Use it to get our repository
        return daggerComponent.repository()
    }
}