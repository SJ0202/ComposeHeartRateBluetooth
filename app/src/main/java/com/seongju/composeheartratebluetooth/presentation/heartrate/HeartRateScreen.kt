package com.seongju.composeheartratebluetooth.presentation.heartrate

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_CONNECT
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_DISCONNECT
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_PARSER_START
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_PARSER_STOP
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_SCAN_START
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_SCAN_STOP
import com.seongju.composeheartratebluetooth.common.service.ServiceHelper
import com.seongju.composeheartratebluetooth.presentation.heartrate.components.BluetoothList

@Composable
fun HeartRateScreen(
    heartRateViewModel: HeartRateViewModel = hiltViewModel()
) {
    val localContext: Context = LocalContext.current
    val bluetoothScanList = heartRateViewModel.serviceManager.heartRateService.heartRateScanList
    val bluetoothConnectDevice = heartRateViewModel.serviceManager.heartRateService.heartRateConnectDevice
    val currentHeartRate = heartRateViewModel.serviceManager.heartRateService.heartRate

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                modifier = Modifier
                    .weight(1f),
                onClick = {
                    ServiceHelper.triggerHeartRateService(
                        context = localContext,
                        action = ACTION_SCAN_START
                    )
                }
            ) {
                Text(
                    text = "스캔 시작"
                )
            }
            Button(
                modifier = Modifier
                    .weight(1f),
                onClick = {
                    ServiceHelper.triggerHeartRateService(
                        context = localContext,
                        action = ACTION_SCAN_STOP
                    )
                }
            ) {
                Text(
                    text = "스캔 종료"
                )
            }

        }

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                modifier = Modifier
                    .weight(1f),
                onClick = {
                    ServiceHelper.triggerHeartRateService(
                        context = localContext,
                        action = ACTION_PARSER_START
                    )
                }
            ) {
                Text(
                    text = "심박수 시작"
                )
            }
            Button(
                modifier = Modifier
                    .weight(1f),
                onClick = {
                    ServiceHelper.triggerHeartRateService(
                        context = localContext,
                        action = ACTION_PARSER_STOP
                    )
                }
            ) {
                Text(
                    text = "심박수 종료"
                )
            }
        }

        Text(text = "스캔 목록")
        BluetoothList(
            bluetoothList = bluetoothScanList,
            onClick = {
                ServiceHelper.connectHeartRateService(
                    context = localContext,
                    action = ACTION_CONNECT,
                    bluetoothDevice = it
                )
            }
        )
        Spacer(
            modifier = Modifier
                .height(20.dp)
        )
        Text(text = "연결 목록")
        BluetoothList(
            bluetoothList = bluetoothConnectDevice,
            onClick = {
                ServiceHelper.connectHeartRateService(
                    context = localContext,
                    action = ACTION_DISCONNECT,
                    bluetoothDevice = it
                )
            }
        )
        Spacer(
            modifier = Modifier
                .height(20.dp)
        )
        Text(text = "현재 심박수")
        Text(
            text = currentHeartRate.value.toString(),
            fontSize = 28.sp
        )
    }
}