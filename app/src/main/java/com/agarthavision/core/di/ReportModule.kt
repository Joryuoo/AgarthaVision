package com.agarthavision.core.di

import com.agarthavision.BuildConfig
import com.agarthavision.domain.model.ReportVerificationConfig
import com.agarthavision.domain.usecase.records.ReportVerificationUrlBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Provides configuration for clinical report generation and QR verification links.
 *
 * Kept separate from [DatabaseModule] because report verification config is unrelated to
 * Room persistence.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReportModule {
    @Provides
    fun provideReportVerificationConfig(): ReportVerificationConfig =
        ReportVerificationConfig(
            baseUrl = BuildConfig.REPORT_VERIFICATION_BASE_URL.ifBlank {
                ReportVerificationUrlBuilder.DEFAULT_BASE_URL
            },
        )
}
