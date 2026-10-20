package com.entretv.app.di

import android.content.Context
import androidx.room.Room
import com.entretv.app.data.local.AppDatabase
import com.entretv.app.data.local.CatalogDao
import com.entretv.app.data.local.ChannelDao
import com.entretv.app.data.local.DownloadDao
import com.entretv.app.data.local.EpgDao
import com.entretv.app.data.local.FavoriteDao
import com.entretv.app.data.local.PlaylistDao
import com.entretv.app.data.local.ProgressDao
import com.entretv.app.data.local.VodDao
import com.entretv.app.data.remote.XtreamApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "entre_http")
        // Sin interceptor de registro: las URLs de Xtream llevan usuario y contraseña.
        return OkHttpClient.Builder()
            .cache(Cache(cacheDir, 32L * 1024 * 1024))
            .retryOnConnectionFailure(true)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideXtreamApi(client: OkHttpClient, json: Json): XtreamApi =
        Retrofit.Builder()
            .baseUrl("https://placeholder.invalid/")
            // Sin caché: si el servidor corta una respuesta, OkHttp podría guardarla incompleta.
            .client(
                client.newBuilder()
                    .cache(null)
                    .addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder().header("Cache-Control", "no-cache").build())
                    }
                    .build()
            )
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
            .create(XtreamApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "entre_tv.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6
            )
            .build()

    @Provides fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()
    @Provides fun provideChannelDao(db: AppDatabase): ChannelDao = db.channelDao()
    @Provides fun provideVodDao(db: AppDatabase): VodDao = db.vodDao()
    @Provides fun provideProgressDao(db: AppDatabase): ProgressDao = db.progressDao()
    @Provides fun provideEpgDao(db: AppDatabase): EpgDao = db.epgDao()
    @Provides fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideCatalogDao(db: AppDatabase): CatalogDao = db.catalogDao()
    @Provides fun provideDownloadDao(db: AppDatabase): DownloadDao = db.downloadDao()
}
