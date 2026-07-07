package com.agarthavision.domain.usecase.settings

import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.repository.ThemePreferenceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveThemeModeUseCaseTest {

    private val themePreferenceRepository: ThemePreferenceRepository = mock()

    @Test
    fun `invoke emits the repository's theme mode flow`() = runTest {
        whenever(themePreferenceRepository.themeMode).thenReturn(flowOf(ThemeMode.DARK))
        val useCase = ObserveThemeModeUseCase(themePreferenceRepository)

        val emitted = useCase().first()

        assertEquals(ThemeMode.DARK, emitted)
    }

    @Test
    fun `invoke defaults to LIGHT when repository emits LIGHT`() = runTest {
        whenever(themePreferenceRepository.themeMode).thenReturn(flowOf(ThemeMode.LIGHT))
        val useCase = ObserveThemeModeUseCase(themePreferenceRepository)

        val emitted = useCase().first()

        assertEquals(ThemeMode.LIGHT, emitted)
    }
}
