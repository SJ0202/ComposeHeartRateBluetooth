package com.seongju.composeheartratebluetooth.presentation.heartrate.components

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seongju.heartrate_service.common.BluetoothStatus

@Composable
fun BluetoothList(
    modifier: Modifier = Modifier,
    bluetoothList: MutableMap<BluetoothDevice, BluetoothStatus>,
    onClick: (BluetoothDevice) -> Unit
){
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(5.dp))
            .border(
                width = 1.dp,
                color = Color.Black,
                shape = RoundedCornerShape(5.dp)
            )
            .padding(
                horizontal = 15.dp,
                vertical = 10.dp
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            bluetoothList.forEach{
                var bluetoothStateText = ""
                var buttonEnable = true
                when(it.value) {
                    BluetoothStatus.STATE_CONNECTING -> {
                        bluetoothStateText = "연결중"
                        buttonEnable = false
                    }
                    BluetoothStatus.STATE_CONNECTED -> {
                        bluetoothStateText = "연결됨"
                        buttonEnable = true
                    }
                    BluetoothStatus.STATE_DISCONNECTED -> {
                        bluetoothStateText = "연결안됨"
                        buttonEnable = true
                    }
                    BluetoothStatus.STATE_CONNECT_FAIL -> {
                        bluetoothStateText = "연결실패"
                        buttonEnable = false
                    }
                    BluetoothStatus.STATE_RECONNECTING -> {
                        bluetoothStateText = "재연결중"
                        buttonEnable = false
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(buttonEnable) {
                            onClick(it.key)
                        }
                        .padding(horizontal = 10.dp)
                        .wrapContentHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = it.key.alias.toString(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = bluetoothStateText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}