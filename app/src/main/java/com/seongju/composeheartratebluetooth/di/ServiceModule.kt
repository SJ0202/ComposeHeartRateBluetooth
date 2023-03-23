package com.seongju.composeheartratebluetooth.di

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import com.seongju.heartrate_service.data.DefaultHeartRateClient
import com.seongju.heartrate_service.domain.HeartRateClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.scopes.ServiceScoped

@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {

    @Provides
    @ServiceScoped
    fun provideHeartRateClient(application: Application): HeartRateClient {
        return DefaultHeartRateClient(
            context = application,
            bluetoothLeScanner = ((application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter).bluetoothLeScanner
        )
    }

}