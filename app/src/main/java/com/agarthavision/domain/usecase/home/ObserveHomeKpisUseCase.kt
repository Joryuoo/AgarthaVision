package com.agarthavision.domain.usecase.home

import com.agarthavision.domain.model.AgreementBreakdown
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.HomeKpis
import com.agarthavision.domain.model.KpiMetric
import com.agarthavision.domain.model.ModelRuling
import com.agarthavision.domain.model.PeriodWindows
import com.agarthavision.domain.model.Ratio
import com.agarthavision.domain.model.SampleTime
import com.agarthavision.domain.model.SessionOutcome
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveHomeKpisUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
) {
    operator fun invoke(userId: String, windows: PeriodWindows): Flow<HomeKpis> {
        val queryStart = minOf(windows.previous.startMillis, windows.buckets.first().startMillis)
        val queryEnd = windows.current.endMillis

        return combine(
            sessionRepository.observeSessionOutcomesBetween(userId, queryStart, queryEnd),
            sampleRepository.observeSampleTimesBetween(userId, queryStart, queryEnd),
            detectionRepository.observeModelRulingsBetween(userId, queryStart, queryEnd),
        ) { sessions, samples, rulings ->
            val (sessionsMetric, patientsInSessions) = computeSessionsKpi(sessions, windows)
            val positiveRateMetric = computePositiveRateKpi(sessions, windows)
            val (toReviewMetric, verifiedInPeriod) = computeToReviewKpi(samples, windows)
            val (aiAgreementMetric, aiBreakdown) = computeAiAgreementKpi(rulings, windows)

            HomeKpis(
                sessions = sessionsMetric,
                patientsInSessions = patientsInSessions,
                positiveRate = positiveRateMetric,
                toReview = toReviewMetric,
                verifiedInPeriod = verifiedInPeriod,
                aiAgreement = aiAgreementMetric,
                aiBreakdown = aiBreakdown,
            )
        }
    }

    private fun computeSessionsKpi(
        sessions: List<SessionOutcome>,
        windows: PeriodWindows,
    ): Pair<KpiMetric<Int>, Int> {
        val curSessions = sessions.filter {
            it.startedAt >= windows.current.startMillis && it.startedAt < windows.current.endMillis
        }
        val prevSessions = sessions.filter {
            it.startedAt >= windows.previous.startMillis && it.startedAt < windows.previous.endMillis
        }
        val sessionsSparkline = windows.buckets.map { b ->
            sessions.count { it.startedAt >= b.startMillis && it.startedAt < b.endMillis }.toDouble()
        }
        val metric = KpiMetric(
            current = curSessions.size,
            previous = prevSessions.size,
            sparkline = sessionsSparkline,
        )
        val distinctPatients = curSessions.map { it.patientId }.distinct().size
        return metric to distinctPatients
    }

    private fun computePositiveRateKpi(
        sessions: List<SessionOutcome>,
        windows: PeriodWindows,
    ): KpiMetric<Ratio> {
        val curSessions = sessions.filter {
            it.startedAt >= windows.current.startMillis && it.startedAt < windows.current.endMillis
        }
        val prevSessions = sessions.filter {
            it.startedAt >= windows.previous.startMillis && it.startedAt < windows.previous.endMillis
        }
        val curExamined = curSessions.count { it.examined }
        val curPositive = curSessions.count { it.positive }
        val prevExamined = prevSessions.count { it.examined }
        val prevPositive = prevSessions.count { it.positive }
        val positiveRateSparkline = windows.buckets.map { b ->
            val inBucket = sessions.filter { it.startedAt >= b.startMillis && it.startedAt < b.endMillis }
            Ratio(inBucket.count { it.positive }, inBucket.count { it.examined }).value
        }
        return KpiMetric(
            current = Ratio(curPositive, curExamined),
            previous = Ratio(prevPositive, prevExamined),
            sparkline = positiveRateSparkline,
        )
    }

    private fun computeToReviewKpi(
        samples: List<SampleTime>,
        windows: PeriodWindows,
    ): Pair<KpiMetric<Int>, Int> {
        val curFlagged = samples.count {
            it.status == "flagged" &&
                it.capturedAt >= windows.current.startMillis &&
                it.capturedAt < windows.current.endMillis
        }
        val prevFlagged = samples.count {
            it.status == "flagged" &&
                it.capturedAt >= windows.previous.startMillis &&
                it.capturedAt < windows.previous.endMillis
        }
        val toReviewSparkline = windows.buckets.map { b ->
            samples.count {
                it.status == "flagged" && it.capturedAt >= b.startMillis && it.capturedAt < b.endMillis
            }.toDouble()
        }
        val metric = KpiMetric(
            current = curFlagged,
            previous = prevFlagged,
            sparkline = toReviewSparkline,
        )
        val verifiedInPeriod = samples.count {
            it.status != "flagged" &&
                it.verifiedAt >= windows.current.startMillis &&
                it.verifiedAt < windows.current.endMillis
        }
        return metric to verifiedInPeriod
    }

    private fun computeAiAgreementKpi(
        rulings: List<ModelRuling>,
        windows: PeriodWindows,
    ): Pair<KpiMetric<Ratio>, AgreementBreakdown> {
        val curRulings = rulings.filter {
            it.verifiedAt >= windows.current.startMillis && it.verifiedAt < windows.current.endMillis
        }
        val prevRulings = rulings.filter {
            it.verifiedAt >= windows.previous.startMillis && it.verifiedAt < windows.previous.endMillis
        }
        val curConfirmed = curRulings.count { it.verdict == DetectionVerdict.CONFIRMED }
        val prevConfirmed = prevRulings.count { it.verdict == DetectionVerdict.CONFIRMED }
        val aiAgreementSparkline = windows.buckets.map { b ->
            val inBucket = rulings.filter { it.verifiedAt >= b.startMillis && it.verifiedAt < b.endMillis }
            Ratio(inBucket.count { it.verdict == DetectionVerdict.CONFIRMED }, inBucket.size).value
        }
        val metric = KpiMetric(
            current = Ratio(curConfirmed, curRulings.size),
            previous = Ratio(prevConfirmed, prevRulings.size),
            sparkline = aiAgreementSparkline,
        )
        val aiBreakdown = AgreementBreakdown(
            confirmed = curConfirmed,
            wrongClass = curRulings.count { it.verdict == DetectionVerdict.WRONG_CLASS },
            boxIncorrect = curRulings.count { it.verdict == DetectionVerdict.BOX_INCORRECT },
            falsePositive = curRulings.count { it.verdict == DetectionVerdict.FALSE_POSITIVE },
        )
        return metric to aiBreakdown
    }
}
