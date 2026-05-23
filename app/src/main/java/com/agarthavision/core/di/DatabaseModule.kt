package com.agarthavision.core.di

// TODO: Uncomment when AgarthaDatabase and DAOs are defined in data/local/
//
// import android.content.Context
// import androidx.room.Room
// import com.agarthavision.data.local.AgarthaDatabase
// import dagger.Module
// import dagger.Provides
// import dagger.hilt.InstallIn
// import dagger.hilt.android.qualifiers.ApplicationContext
// import dagger.hilt.components.SingletonComponent
// import javax.inject.Singleton
//
// @Module
// @InstallIn(SingletonComponent::class)
// object DatabaseModule {
//     @Provides @Singleton
//     fun provideDatabase(@ApplicationContext ctx: Context): AgarthaDatabase =
//         Room.databaseBuilder(ctx, AgarthaDatabase::class.java, "agarthavision.db")
//             .build()
//
//     @Provides fun provideSampleDao(db: AgarthaDatabase) = db.sampleDao()
//     @Provides fun provideDetectionDao(db: AgarthaDatabase) = db.detectionDao()
// }
