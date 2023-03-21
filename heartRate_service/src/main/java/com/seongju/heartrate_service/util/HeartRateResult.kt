package com.seongju.heartrate_service.util

import android.bluetooth.le.ScanResult
import com.seongju.heartrate_service.common.BluetoothStatus

sealed interface HeartRateResult {

    object Success: HeartRateResult
    data class HeartRateDevice(val scanResult: ScanResult): HeartRateResult
    data class HeartRateState(val bluetoothStatus: BluetoothStatus): HeartRateResult
    data class HeartRate(val heartRate: Int): HeartRateResult
    data class Error(val message: String): HeartRateResult

}