package com.seongju.composeheartratebluetooth.common.service

import android.content.Context
import android.content.Intent

object ServiceHelper {

    fun triggerHeartRateService(context: Context, action: String) {
        Intent(context, HeartRateService::class.java).apply {
            this.action = action
            context.startService(this)
        }
    }

}