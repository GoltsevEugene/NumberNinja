package number.ninja

import android.app.Application
import number.ninja.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class NumberNinjaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@NumberNinjaApp)
            modules(appModules)
        }
    }
}
