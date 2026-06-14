package com.mipuble.data.di

import com.mipuble.data.remote.DriveAuthProvider
import com.mipuble.data.remote.DriveRemoteLibrarySource
import com.mipuble.data.remote.PlayServicesDriveAuthProvider
import com.mipuble.data.remote.RemoteLibrarySource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object RemoteNetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        // No overall call timeout: large book downloads/uploads can run long.
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteSourceModule {

    @Binds
    @Singleton
    abstract fun bindDriveAuthProvider(impl: PlayServicesDriveAuthProvider): DriveAuthProvider

    /**
     * Bind the real Google Drive source.
     */
    @Binds
    abstract fun bindRemoteLibrarySource(impl: DriveRemoteLibrarySource): RemoteLibrarySource
}
