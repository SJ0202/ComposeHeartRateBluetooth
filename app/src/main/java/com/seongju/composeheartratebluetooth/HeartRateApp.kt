package com.seongju.composeheartratebluetooth

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HeartRateApp:Application() {

    override fun onCreate() {
        super.onCreate()
    }

}