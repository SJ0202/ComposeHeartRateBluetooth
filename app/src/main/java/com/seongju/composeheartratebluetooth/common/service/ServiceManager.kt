package com.seongju.composeheartratebluetooth.common.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.mutableStateOf

class ServiceManager(
    private val context: Context
) {

    private val tag: String = "ServiceManager"
    val heartRateServiceBound = mutableStateOf(false)

    lateinit var heartRateService: HeartRateService

    private lateinit var heartRateServiceConnection: ServiceConnection

    init {
        initServiceConnection()
    }

    fun startHeartRateService() {
        if (!::heartRateService.isInitialized) {
            val intent = Intent(context, HeartRateService::class.java)
            context.bindService(intent, heartRateServiceConnection, Context.BIND_AUTO_CREATE)
        } else {
            Log.e(tag, "HeartRateService 를 Bind 할 수 없습니다.")
        }
    }

    fun stopHeartRateService() {
        if (::heartRateService.isInitialized) {
            context.unbindService(heartRateServiceConnection)
        } else {
            Log.e(tag, "HeartRateService 를 unBind 할 수 없습니다.")
        }
    }

    private fun initServiceConnection() {
        heartRateServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val getHeartRateService = service as HeartRateService.LocalBinder
                heartRateService = getHeartRateService.getHeartRateService()
                heartRateServiceBound.value = true
                Log.d(tag, "HeartRateService 가 정상적으로 실행에 성공하였습니다.")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.e(tag, "HeartRateService 가 비정상적으로 종료되었습니다.")
            }
        }
    }
}