package com.seongju.composeheartratebluetooth.common.service

import android.app.Service
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_SCAN_START
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_SCAN_STOP
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

    private fun startScanHeartRateDevice() {
        heartRateClient.startDeviceScan()
            .catch { e -> e.printStackTrace() }
            .onEach {
                when(it) {
                    is HeartRateResult.Error -> {
                        Log.e(tag, it.message)
                    }
                    is HeartRateResult.HeartRateDevice -> {
                        Log.d(tag, it.scanResult.device.alias.toString())
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_SCAN_START -> {
                startScanHeartRateDevice()
            }
            ACTION_SCAN_STOP -> {
                stopScanHeartRateDevice()
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