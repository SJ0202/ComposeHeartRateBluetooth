package com.seongju.composeheartratebluetooth.presentation.heartrate

import androidx.lifecycle.ViewModel
import com.seongju.composeheartratebluetooth.common.service.ServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HeartRateViewModel @Inject constructor(
    val serviceManager: ServiceManager
): ViewModel() {

    private val tag = "HeartRateViewModel"

}