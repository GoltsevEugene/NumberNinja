package number.ninja.di

import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import number.ninja.data.db.AppDatabase
import number.ninja.data.db.StatsRepository
import number.ninja.data.facts.FactsRepository
import number.ninja.data.settings.AppLanguageManager
import number.ninja.data.settings.SettingsRepository
import number.ninja.data.settings.settingsDataStore
import number.ninja.domain.ExampleGenerator
import number.ninja.ui.home.HomeViewModel
import number.ninja.ui.practice.FreePracticeViewModel
import number.ninja.ui.progress.ProgressViewModel
import number.ninja.ui.quiz.QuizResultsHolder
import number.ninja.ui.quiz.QuizViewModel
import number.ninja.ui.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val applicationScopeQualifier = named("applicationScope")

val dataModule = module {
    single(applicationScopeQualifier) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()
    }
    single { get<AppDatabase>().attemptDao() }
    single { StatsRepository(get()) }
    single {
        SettingsRepository(
            dataStore = androidContext().settingsDataStore,
            applicationScope = get(applicationScopeQualifier),
        )
    }
    single {
        AppLanguageManager(
            dataStore = androidContext().settingsDataStore,
            applicationScope = get(applicationScopeQualifier),
        )
    }
    single { FactsRepository(androidContext()) }
}

val domainModule = module {
    single { ExampleGenerator() }
}

val uiModule = module {
    viewModel { HomeViewModel(get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { ProgressViewModel(get()) }
    viewModel { FreePracticeViewModel(get(), get(), get(), get()) }
    // Holds a finished quiz's attempts across the Session -> QuizResults navigation hop, since
    // QuizViewModel is scoped to (and cleared with) the Route.Session back-stack entry. See
    // QuizResultsHolder's doc comment for why this isn't a @Serializable route argument instead.
    single { QuizResultsHolder() }
    viewModel { QuizViewModel(get(), get(), get(), get(), get()) }
}

val appModules = listOf(dataModule, domainModule, uiModule)
