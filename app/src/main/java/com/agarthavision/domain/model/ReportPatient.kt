package com.agarthavision.domain.model

/**
 * The patient-identifying fields printed on a session report PDF.
 *
 * Pure Kotlin per C2: assembled in [com.agarthavision.domain.usecase.records.GenerateSessionReportUseCase]
 * from [Patient] and the resolved [PsgcBarangay], so the renderer never has to reach back into a
 * repository to know who the report is about.
 */
data class ReportPatient(
    /** [Patient.displayName] — masking is a list-view concern and does not apply on a signed report. */
    val name: String,
    /** Null prints `patients_sex_unknown`; see [Patient.sex] for why it can be null. */
    val sex: Sex?,
    /** [Patient.ageYears] resolved at the same instant the report's `generatedAt` uses. */
    val ageYears: Int,
    /**
     * `"${barangay.name} · ${barangay.parentPath}"`, or the raw [Patient.psgcBarangayCode] when
     * the code no longer resolves — a PSGC vintage change can retire a code a patient row still
     * carries, and the raw code is a more honest fallback than a blank line.
     */
    val barangayLabel: String,
)
