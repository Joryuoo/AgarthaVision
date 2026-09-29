package com.agarthavision.domain.model

data class Ratio(val numerator: Int, val denominator: Int) {
    val value: Double?
        get() = if (denominator == 0) null else numerator.toDouble() / denominator
}

data class AgreementBreakdown(
    val confirmed: Int,
    val wrongClass: Int,
    val boxIncorrect: Int,
    val falsePositive: Int,
) {
    val total: Int
        get() = confirmed + wrongClass + boxIncorrect + falsePositive
    val corrected: Int
        get() = total - confirmed
}

data class KpiMetric<T>(
    val current: T,
    val previous: T,
    val sparkline: List<Double?>,
)

data class ModelRuling(
    val verifiedAt: Long,
    val verdict: DetectionVerdict,
)

data class SessionOutcome(
    val sessionId: String,
    val patientId: String,
    val startedAt: Long,
    val examined: Boolean,
    val positive: Boolean,
)

data class SampleTime(
    val sampleId: String,
    val status: String,
    val capturedAt: Long,
    val verifiedAt: Long,
)

data class HomeKpis(
    val sessions: KpiMetric<Int>,
    val patientsInSessions: Int,
    val positiveRate: KpiMetric<Ratio>,
    val toReview: KpiMetric<Int>,
    val verifiedInPeriod: Int,
    val aiAgreement: KpiMetric<Ratio>,
    val aiBreakdown: AgreementBreakdown,
)

data class SessionFinding(
    val sessionId: String,
    val rawSpecies: String,
    val townCode: String?,
)

data class SpeciesFinding(
    val name: String,
    val count: Int,
    val ratio: Float,
    val formattedPercentage: String,
)

data class FindingsResult(
    val species: List<SpeciesFinding>,
    val positiveSmearsCount: Int,
)
