package com.agarthavision.domain.usecase.settings

import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.repository.ThemePreferenceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SetThemeModeUseCaseTest {

    private val themePreferenceRepository: ThemePreferenceRepository = mock()
    private val useCase = SetThemeModeUseCase(themePreferenceRepository)

    @Test
    fun `invoke persists the requested mode and returns success`() = runTest {
        val result = useCase(ThemeMode.DARK)

        verify(themePreferenceRepository).setThemeMode(ThemeMode.DARK)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `invoke surfaces repository failure as Result failure`() = runTest {
        val error = IllegalStateException("disk full")
        whenever(themePreferenceRepository.setThemeMode(ThemeMode.LIGHT)).thenThrow(error)

        val result = useCase(ThemeMode.LIGHT)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === error)
    }
}
