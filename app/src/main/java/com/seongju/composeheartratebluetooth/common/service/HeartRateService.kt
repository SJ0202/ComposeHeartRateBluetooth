package com.seongju.composeheartratebluetooth.common.service

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_CONNECT
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_DISCONNECT
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_PARSER_START
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_PARSER_STOP
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_SCAN_START
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_SCAN_STOP
import com.seongju.composeheartratebluetooth.common.Constants.BLUETOOTH_DEVICE
import com.seongju.heartrate_service.common.BluetoothStatus
import com.seongju.heartrate_service.domain.HeartRateClient
import com.seongju.heartrate_service.util.HeartRateResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class HeartRateService: Service() {

    private val tag: String = "HeartRateService"
    private val binder: IBinder = LocalBinder()
    private val serviceHeartRateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var heartRateClient: HeartRateClient

    var heartRateScanList = mutableStateMapOf<BluetoothDevice, BluetoothStatus>()
        private set
    var heartRateConnectDevice = mutableStateMapOf<BluetoothDevice, BluetoothStatus>()
        private set
    var heartRate = mutableStateOf(0)

    private fun startScanHeartRateDevice() {
        heartRateClient.startDeviceScan()
            .catch { e -> e.printStackTrace() }
            .onEach {
                when(it) {
                    is HeartRateResult.Error -> {
                        Log.e(tag, it.message)
                    }
                    is HeartRateResult.HeartRateDevice -> {
                        heartRateScanList[it.scanResult.device] = BluetoothStatus.STATE_DISCONNECTED
                    }
                    else -> Unit
                }
            }.launchIn(serviceHeartRateScope)
    }

    private fun stopScanHeartRateDevice() {
        heartRateClient.stopDeviceScan()
            .catch { e -> e.printStackTrace() }
            .onEach {
                when(it) {
                    is HeartRateResult.Error -> {
                        Log.e(tag, it.message)
                    }
                    HeartRateResult.Success -> {
                        Log.d(tag, "HeartRate device scan stop success")
                    }
                    else -> Unit
                }
            }.launchIn(serviceHeartRateScope)
    }

    private fun connectHeartRateDevice(bluetoothDevice: BluetoothDevice) {
        heartRateClient.connectDevice(bluetoothDevice = bluetoothDevice, reconnectTime = 5000)
            .catch { e -> e.printStackTrace() }
            .onEach {
                when(it) {
                    is HeartRateResult.Error -> {
                        Log.e(tag, it.message)
                    }
                    is HeartRateResult.HeartRateState -> {
                        if (it.bluetoothStatus == BluetoothStatus.STATE_DISCONNECTED || it.bluetoothStatus == BluetoothStatus.STATE_CONNECT_FAIL) {
                            heartRateConnectDevice.remove(bluetoothDevice)
                        } else {
                            heartRateConnectDevice[bluetoothDevice] = it.bluetoothStatus
                        }
                    }
                    else -> Unit
                }
            }.launchIn(serviceHeartRateScope)
    }

    private fun disconnectHeartRateDevice() {
        heartRateClient.disconnectDevice()
            .catch { e -> e.printStackTrace() }
            .onEach {
                when(it) {
                    is HeartRateResult.Error -> {
                        Log.e(tag, it.message)
                    }
                    HeartRateResult.Success -> {
                        Log.d(tag, "HeartRate device disconnect success")
                    }
                    else -> Unit
                }
            }.launchIn(serviceHeartRateScope)
    }

    private fun startParserHeartRateDevice() {
        heartRateClient.startParser()
            .catch { e -> e.printStackTrace() }
            .onEach {
                when(it) {
                    is HeartRateResult.Error -> {
                        Log.e(tag, it.message)
                    }
                    is HeartRateResult.HeartRate -> {
                        heartRate.value = it.heartRate
                    }
                    else -> Unit
                }
            }.launchIn(serviceHeartRateScope)
    }

    private fun stopParserHeartRateDevice() {
        heartRateClient.stopParser()
            .catch { e -> e.printStackTrace() }
            .onEach {
                when(it) {
                    is HeartRateResult.Error -> {
                        Log.e(tag, it.message)
                    }
                    HeartRateResult.Success -> {
                        Log.d(tag, "HeartRate device parser stop success")
                    }
                    else -> Unit
                }
            }.launchIn(serviceHeartRateScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_SCAN_START -> {
                startScanHeartRateDevice()
            }
            ACTION_SCAN_STOP -> {
                stopScanHeartRateDevice()
            }
            ACTION_CONNECT -> {
                val bluetoothDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BLUETOOTH_DEVICE, BluetoothDevice::class.java)
                } else {
                    intent.getParcelableExtra(BLUETOOTH_DEVICE)
                }
                if (bluetoothDevice != null)
                    connectHeartRateDevice(bluetoothDevice = bluetoothDevice)
            }
            ACTION_DISCONNECT -> {
                val bluetoothDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BLUETOOTH_DEVICE, BluetoothDevice::class.java)
                } else {
                    intent.getParcelableExtra(BLUETOOTH_DEVICE)
                }
                if (bluetoothDevice != null)
                    disconnectHeartRateDevice()
            }
            ACTION_PARSER_START -> {
                startParserHeartRateDevice()
            }
            ACTION_PARSER_STOP -> {
                stopParserHeartRateDevice()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceHeartRateScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    inner class LocalBinder(): Binder() {
        fun getHeartRateService(

        ): HeartRateService {
            return this@HeartRateService
        }
    }
}