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
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object RemoteNetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()
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
