package com.seongju.heartrate_service.util

import android.bluetooth.le.ScanResult

sealed interface HeartRateResult {

    object Success: HeartRateResult
    data class HeartRateDevice(val scanResult: ScanResult): HeartRateResult
    data class Error(val message: String): HeartRateResult

}