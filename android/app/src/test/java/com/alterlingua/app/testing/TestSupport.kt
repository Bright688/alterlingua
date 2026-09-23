package com.alterlingua.app.testing

import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.storage.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** In-memory stand-in for the saved settings, so tests do not need a phone or a file. */
class FakeUserSettingsRepository(initial: UserSettings = UserSettings()) : UserSettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = state

    /** The latest saved value. */
    val current: UserSettings get() = state.value

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        state.update(transform)
    }
}

/** Lets ViewModels that use viewModelScope run in plain JVM tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(UnconfinedTestDispatcher())
    override fun finished(description: Description) = Dispatchers.resetMain()
}
