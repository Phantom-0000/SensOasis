package es.upm.sensoasis

import android.app.Application
import es.upm.sensoasis.di.AppContainer

/**
 * Application entry point holding the shared [AppContainer].
 */
class SensOasisApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
