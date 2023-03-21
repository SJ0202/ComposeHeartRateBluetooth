package com.seongju.composeheartratebluetooth.common.service

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import com.seongju.composeheartratebluetooth.common.Constants.BLUETOOTH_DEVICE

object ServiceHelper {

    fun triggerHeartRateService(context: Context, action: String) {
        Intent(context, HeartRateService::class.java).apply {
            this.action = action
            context.startService(this)
        }
    }

    fun connectHeartRateService(context: Context, action: String, bluetoothDevice: BluetoothDevice) {
        Intent(context, HeartRateService::class.java).apply {
            this.action = action
            this.putExtra(BLUETOOTH_DEVICE, bluetoothDevice)
            context.startService(this)
        }
    }

}