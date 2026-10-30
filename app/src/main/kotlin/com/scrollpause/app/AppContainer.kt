package com.scrollpause.app

import android.content.Context
import com.scrollpause.app.data.db.AppDatabase
import com.scrollpause.app.data.prefs.PreferencesRepository
import com.scrollpause.app.data.repository.ReelRepository

/**
 * Manual dependency injection container.
 *
 * Why not Hilt?
 * This app has one module, one Activity, and one Service. Manual DI through
 * a singleton container is clear, zero-overhead, and straightforward to test
 * by supplying alternative instances. Hilt can be added later if the project
 * grows into multiple modules.
 *
 * Created once in [ScrollPauseApplication] and accessed via
 * [ScrollPauseApplication.container].
 */
class AppContainer(context: Context) {

    val database: AppDatabase = AppDatabase.getInstance(context)

    val reelRepository: ReelRepository = ReelRepository(
        dailyStatDao = database.dailyStatDao(),
        sessionEntryDao = database.sessionEntryDao(),
    )

    val preferencesRepository: PreferencesRepository = PreferencesRepository(context)
}
