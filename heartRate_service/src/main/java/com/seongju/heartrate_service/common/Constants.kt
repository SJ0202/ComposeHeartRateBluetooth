package com.seongju.heartrate_service.common

object Constants {

    // HeartRate Device UUID
    const val HEART_RATE_SERVICE_UUID = "0000180D-0000-1000-8000-00805f9b34fb"
    // HeartRate Tx
    const val HEART_RATE_CONTROL_POINT = "00002A39-0000-1000-8000-00805f9b34fb"
    // HeartRate Rx
    const val HEART_RATE_MEASUREMENT = "00002A37-0000-1000-8000-00805f9b34fb"
    // BluetoothGattDescriptor
    const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"

    const val HEART_RATE_CONNECTING = 101
    const val HEART_RATE_RECONNECTING = 102

}