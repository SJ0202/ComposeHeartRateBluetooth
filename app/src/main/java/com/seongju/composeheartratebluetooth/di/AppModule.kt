package com.seongju.composeheartratebluetooth.di

import android.app.Application
import com.seongju.composeheartratebluetooth.common.service.ServiceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideServiceManager(application: Application): ServiceManager {
        return ServiceManager(context = application).also {
            it.startHeartRateService()
        }
    }

}