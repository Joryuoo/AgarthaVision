package com.agarthavision.domain.usecase.home

import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.ModelRuling
import com.agarthavision.domain.model.PeriodWindows
import com.agarthavision.domain.model.SampleTime
import com.agarthavision.domain.model.SessionOutcome
import com.agarthavision.domain.model.TimeWindow
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ObserveHomeKpisUseCaseTest {

    private val sessionRepository: SessionRepository = mock()
    private val sampleRepository: SampleRepository = mock()
    private val detectionRepository: DetectionRepository = mock()

    private val useCase = ObserveHomeKpisUseCase(
        sessionRepository = sessionRepository,
        sampleRepository = sampleRepository,
        detectionRepository = detectionRepository,
    )

    private fun sampleWindows(): PeriodWindows {
        val current = TimeWindow(7_000L, 8_000L)
        val previous = TimeWindow(6_000L, 7_000L)
        val buckets = (0 until 7).map { i ->
            TimeWindow(7_000L + i * 100L, 7_000L + (i + 1) * 100L)
        }
        return PeriodWindows(current, previous, buckets)
    }

    @Test
    fun `distinct patients counted correctly for sessions in current window`() = runTest {
        val windows = sampleWindows()
        val sessions = listOf(
            SessionOutcome(
                sessionId = "s1",
                patientId = "p1",
                startedAt = 7_100L,
                examined = true,
                positive = true,
            ),
            SessionOutcome(
                sessionId = "s2",
                patientId = "p1",
                startedAt = 7_200L,
                examined = true,
                positive = false,
            ),
            SessionOutcome(
                sessionId = "s3",
                patientId = "p2",
                startedAt = 7_300L,
                examined = true,
                positive = true,
            ),
            SessionOutcome(
                sessionId = "s-prev",
                patientId = "p3",
                startedAt = 6_500L,
                examined = true,
                positive = false,
            ),
        )

        whenever(sessionRepository.observeSessionOutcomesBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(sessions))
        whenever(sampleRepository.observeSampleTimesBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(emptyList()))
        whenever(detectionRepository.observeModelRulingsBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(emptyList()))

        val kpis = useCase("user-1", windows).first()

        assertEquals(3, kpis.sessions.current)
        assertEquals(1, kpis.sessions.previous)
        assertEquals(2, kpis.patientsInSessions)
        assertEquals(3, kpis.positiveRate.current.denominator)
        assertEquals(2, kpis.positiveRate.current.numerator)
        assertEquals(2.0 / 3.0, kpis.positiveRate.current.value!!, 0.001)
    }

    @Test
    fun `zero denominator gives null ratio value`() = runTest {
        val windows = sampleWindows()

        whenever(sessionRepository.observeSessionOutcomesBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(emptyList()))
        whenever(sampleRepository.observeSampleTimesBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(emptyList()))
        whenever(detectionRepository.observeModelRulingsBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(emptyList()))

        val kpis = useCase("user-1", windows).first()

        assertEquals(0, kpis.positiveRate.current.denominator)
        assertNull(kpis.positiveRate.current.value)

        assertEquals(0, kpis.aiAgreement.current.denominator)
        assertNull(kpis.aiAgreement.current.value)
    }

    @Test
    fun `breakdown sums and AI agreement calculated from model rulings`() = runTest {
        val windows = sampleWindows()
        val rulings = listOf(
            ModelRuling(verifiedAt = 7_100L, verdict = DetectionVerdict.CONFIRMED),
            ModelRuling(verifiedAt = 7_200L, verdict = DetectionVerdict.CONFIRMED),
            ModelRuling(verifiedAt = 7_300L, verdict = DetectionVerdict.WRONG_CLASS),
            ModelRuling(verifiedAt = 7_400L, verdict = DetectionVerdict.BOX_INCORRECT),
            ModelRuling(verifiedAt = 7_500L, verdict = DetectionVerdict.FALSE_POSITIVE),
            // in previous window
            ModelRuling(verifiedAt = 6_500L, verdict = DetectionVerdict.CONFIRMED),
        )

        whenever(sessionRepository.observeSessionOutcomesBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(emptyList()))
        whenever(sampleRepository.observeSampleTimesBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(emptyList()))
        whenever(detectionRepository.observeModelRulingsBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(rulings))

        val kpis = useCase("user-1", windows).first()

        val breakdown = kpis.aiBreakdown
        assertEquals(2, breakdown.confirmed)
        assertEquals(1, breakdown.wrongClass)
        assertEquals(1, breakdown.boxIncorrect)
        assertEquals(1, breakdown.falsePositive)
        assertEquals(5, breakdown.total)
        assertEquals(3, breakdown.corrected)

        assertEquals(2, kpis.aiAgreement.current.numerator)
        assertEquals(5, kpis.aiAgreement.current.denominator)
        assertEquals(0.4, kpis.aiAgreement.current.value!!, 0.001)

        assertEquals(1, kpis.aiAgreement.previous.numerator)
        assertEquals(1, kpis.aiAgreement.previous.denominator)
        assertEquals(1.0, kpis.aiAgreement.previous.value!!, 0.001)
    }

    @Test
    fun `toReview counts flagged by capturedAt and verifiedInPeriod counts non-flagged by verifiedAt`() = runTest {
        val windows = sampleWindows()
        val samples = listOf(
            // Flagged captured in current window -> toReview.current
            SampleTime(sampleId = "s1", status = "flagged", capturedAt = 7_100L, verifiedAt = 0L),
            // Flagged captured in previous window -> toReview.previous
            SampleTime(sampleId = "s2", status = "flagged", capturedAt = 6_500L, verifiedAt = 0L),
            // Verified in current window (even if captured earlier) -> verifiedInPeriod
            SampleTime(sampleId = "s3", status = "verified", capturedAt = 5_000L, verifiedAt = 7_200L),
            // Verified in current window
            SampleTime(sampleId = "s4", status = "verified", capturedAt = 7_300L, verifiedAt = 7_300L),
        )

        whenever(sessionRepository.observeSessionOutcomesBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(emptyList()))
        whenever(sampleRepository.observeSampleTimesBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(samples))
        whenever(detectionRepository.observeModelRulingsBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(emptyList()))

        val kpis = useCase("user-1", windows).first()

        assertEquals(1, kpis.toReview.current)
        assertEquals(1, kpis.toReview.previous)
        assertEquals(2, kpis.verifiedInPeriod)
    }

    @Test
    fun `bucketing populates 7 points for each sparkline`() = runTest {
        val windows = sampleWindows()
        // Put one session in bucket 0 and one in bucket 2
        val sessions = listOf(
            SessionOutcome(
                sessionId = "s0",
                patientId = "p1",
                startedAt = 7_050L,
                examined = true,
                positive = true,
            ),
            SessionOutcome(
                sessionId = "s2",
                patientId = "p2",
                startedAt = 7_250L,
                examined = true,
                positive = false,
            ),
        )

        whenever(sessionRepository.observeSessionOutcomesBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(sessions))
        whenever(sampleRepository.observeSampleTimesBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(emptyList()))
        whenever(detectionRepository.observeModelRulingsBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(emptyList()))

        val kpis = useCase("user-1", windows).first()

        assertEquals(7, kpis.sessions.sparkline.size)
        assertEquals(1.0, kpis.sessions.sparkline[0]!!, 0.001)
        assertEquals(0.0, kpis.sessions.sparkline[1]!!, 0.001)
        assertEquals(1.0, kpis.sessions.sparkline[2]!!, 0.001)

        assertEquals(7, kpis.positiveRate.sparkline.size)
        assertEquals(1.0, kpis.positiveRate.sparkline[0]!!, 0.001)
        assertNull(kpis.positiveRate.sparkline[1])
        assertEquals(0.0, kpis.positiveRate.sparkline[2]!!, 0.001)
    }
}
