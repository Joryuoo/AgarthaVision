package com.agarthavision.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The account check must not renew the session a password change is replacing (14zcqntjph9):
 * whichever holds [AuthSessionLock] first finishes before the other starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionLockTest {

    @Test
    fun `an account check waits for a password change already under way`() = runTest {
        val lock = AuthSessionLock()
        val events = mutableListOf<String>()
        val passwordSent = CompletableDeferred<Unit>()

        launch {
            lock.withLock {
                events += "change started"
                passwordSent.await()
                events += "change finished"
            }
        }
        runCurrent()
        launch { lock.withLock { events += "check" } }
        runCurrent()
        passwordSent.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("change started", "change finished", "check"), events)
    }
}
