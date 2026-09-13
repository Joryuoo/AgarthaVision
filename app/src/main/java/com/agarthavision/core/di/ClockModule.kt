package com.agarthavision.core.di

import android.os.SystemClock
import com.agarthavision.core.util.ElapsedClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    /**
     * Deliberately **unscoped**. C5 keeps `@Singleton` to a closed list
     * (`docs/constraints.md`), and this binding is a stateless lambda over a static —
     * scoping it would grow that list for nothing.
     */
    @Provides
    fun provideElapsedClock(): ElapsedClock = ElapsedClock { SystemClock.elapsedRealtime() }
}
