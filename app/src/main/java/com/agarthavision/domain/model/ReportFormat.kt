package com.agarthavision.domain.model

/**
 * The file format a session report is generated and exported as. A report is created in exactly
 * one format — the medtech's choice at export time — so a report carries either a CSV file or a
 * PDF file, never both.
 */
enum class ReportFormat { PDF, CSV }
