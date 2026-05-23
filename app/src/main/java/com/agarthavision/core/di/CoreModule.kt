package com.agarthavision.core.di

import com.agarthavision.core.location.DefaultLocationProvider
import com.agarthavision.core.location.LocationProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindLocationProvider(
        locationProvider: DefaultLocationProvider
    ): LocationProvider
}
