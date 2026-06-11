package com.mipuble.data.di

import com.mipuble.data.remote.DriveAuthProvider
import com.mipuble.data.remote.FakeRemoteLibrarySource
import com.mipuble.data.remote.RemoteLibrarySource
import com.mipuble.data.remote.UnconfiguredDriveAuthProvider
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

    @Provides
    @Singleton
    fun provideDriveAuthProvider(): DriveAuthProvider = UnconfiguredDriveAuthProvider()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteSourceModule {

    /**
     * Bind the offline demo source by default so the app runs without
     * credentials. Swap to [com.mipuble.data.remote.DriveRemoteLibrarySource]
     * once a real [DriveAuthProvider] is wired.
     */
    @Binds
    abstract fun bindRemoteLibrarySource(impl: FakeRemoteLibrarySource): RemoteLibrarySource
}
