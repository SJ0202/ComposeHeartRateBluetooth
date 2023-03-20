package com.seongju.heartrate_service.util

sealed interface HeartRateResult {

    object Success: HeartRateResult
    class Result<T>(data: T): HeartRateResult
    class Error<T>(message: String, data: T? = null): HeartRateResult

}