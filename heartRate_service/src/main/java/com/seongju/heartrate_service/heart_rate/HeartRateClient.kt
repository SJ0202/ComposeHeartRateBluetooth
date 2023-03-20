package com.seongju.heartrate_service.heart_rate

import com.seongju.heartrate_service.util.HeartRateResult
import kotlinx.coroutines.flow.Flow

interface HeartRateClient {

    fun startDeviceScan(): Flow<HeartRateResult>
    fun stopDeviceScan(): Flow<HeartRateResult>

    fun connectDevice(): Flow<HeartRateResult>
    fun disconnectDevice(): Flow<HeartRateResult>

    fun startParser(): Flow<HeartRateResult>
    fun stopParser(): Flow<HeartRateResult>

}