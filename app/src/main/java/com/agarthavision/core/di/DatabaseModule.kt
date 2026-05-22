@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AgarthaDatabase =
        Room.databaseBuilder(ctx, AgarthaDatabase::class.java, "agarthavision.db")
            .build()

    @Provides fun provideSampleDao(db: AgarthaDatabase) = db.sampleDao()
    @Provides fun provideDetectionDao(db: AgarthaDatabase) = db.detectionDao()
    // ... one per DAO
}