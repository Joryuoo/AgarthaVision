package com.agarthavision.domain.model

enum class PatientNameOption {
    SHOW,
    CENSOR,
    EXCLUDE,
}

data class ReportConfig(
    val includeSampleImages: Boolean = false,
    val patientNameOption: PatientNameOption = PatientNameOption.SHOW,
)
