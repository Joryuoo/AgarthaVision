package com.agarthavision.data.sync

import com.agarthavision.data.supabase.SyncSampleUseCase
import com.agarthavision.domain.sync.RecordingSyncScheduler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

// Robolectric for android.util.Log, which the failure path writes to.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackgroundSamplePushTest {

    private val syncSampleUseCase: SyncSampleUseCase = mock()
    private val syncScheduler = RecordingSyncScheduler()

    @Test
    fun `a push that throws is contained and still asks for a pass`() = runTest {
        // Nothing waits on this push any more, so nothing would catch a throw from it: in a
        // process-lifetime scope that is an app crash, long after the sheet has closed.
        whenever(syncSampleUseCase.invoke(any())).thenThrow(IllegalStateException("row read failed"))
        val failures = mutableListOf<Throwable>()
        // Shaped like the production scope: a supervisor root, where an escaped throw lands on
        // the handler instead of failing a parent.
        val scope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, t -> failures += t },
        )

        BackgroundSamplePush(syncSampleUseCase, syncScheduler, scope).push("sample-1")
        advanceUntilIdle()

        assertTrue("The throw must not escape the push.", failures.isEmpty())
        assertEquals(1, syncScheduler.requests)
    }
}
