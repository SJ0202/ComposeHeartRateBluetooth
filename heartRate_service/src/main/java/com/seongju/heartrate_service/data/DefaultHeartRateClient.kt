package com.seongju.heartrate_service.data

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.ParcelUuid
import android.util.Log
import com.seongju.heartrate_service.common.Constants.CLIENT_CHARACTERISTIC_CONFIG
import com.seongju.heartrate_service.common.Constants.HEART_RATE_CONNECTING
import com.seongju.heartrate_service.common.Constants.HEART_RATE_CONTROL_POINT
import com.seongju.heartrate_service.common.Constants.HEART_RATE_MEASUREMENT
import com.seongju.heartrate_service.common.Constants.HEART_RATE_RECONNECTING
import com.seongju.heartrate_service.common.Constants.HEART_RATE_SERVICE_UUID
import com.seongju.heartrate_service.domain.HeartRateClient
import com.seongju.heartrate_service.util.HeartRateResult
import com.seongju.heartrate_service.util.hasBluetoothPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class DefaultHeartRateClient(
    private val context: Context,
    private val bluetoothLeScanner: BluetoothLeScanner
): HeartRateClient {

    private val tag: String = "DefaultHeartRateClient"
    private var scanState: Boolean = false
    private var disconnectCall: Boolean = false

    private val serviceUUID: UUID = UUID.fromString(HEART_RATE_SERVICE_UUID)
    private val serviceTxUUID: UUID = UUID.fromString(HEART_RATE_CONTROL_POINT)
    private val serviceRxUUID: UUID = UUID.fromString(HEART_RATE_MEASUREMENT)
    private val serviceDescriptor: UUID = UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG)

    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var readCharacteristic: BluetoothGattCharacteristic? = null
    private var descriptorCharacteristic: BluetoothGattCharacteristic? = null

    private lateinit var handler: Handler
    private lateinit var scanCallback: ScanCallback
    private lateinit var connectCallback: BluetoothGattCallback
    private lateinit var bluetoothGatt: BluetoothGatt

    private var onHeartRateChanged: ((BluetoothGattCharacteristic) -> Unit)? = null

    init {
        initHandler()
    }

    @SuppressLint("MissingPermission")
    override fun startDeviceScan(): Flow<HeartRateResult> {
        return callbackFlow {
            val bluetoothPermissionCheck = hasBluetoothPermission(context = context)
            if (!bluetoothPermissionCheck) {
                launch { send(HeartRateResult.Error(message = "블루투스 권한이 없습니다.")) }
                return@callbackFlow
            }

            // Bluetooth Scan Filter
            val scanFilter: MutableList<ScanFilter> = ArrayList()
            val heartRateFilter: ScanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUUID))
                .build()
            scanFilter.add(heartRateFilter)

            // Bluetooth Scan Setting
            val scanSettings: ScanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build()

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    super.onScanResult(callbackType, result)

                    if (result != null) {
                        launch { send(HeartRateResult.HeartRateDevice(scanResult = result)) }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    super.onScanFailed(errorCode)
                    when (errorCode) {
                        SCAN_FAILED_ALREADY_STARTED -> {
                            launch { send(HeartRateResult.Error(message = "이미 동일한 설정으로 시작했으므로 스캔을 시작하지 못합니다.")) }
                        }
                        SCAN_FAILED_FEATURE_UNSUPPORTED -> {
                            launch { send(HeartRateResult.Error(message = "BLE 기능이 지원되지 않기 때문에 스캔을 시작할 수 없습니다.")) }
                        }
                        else -> {
                            Log.e(tag, "errorCode: ${errorCode}, 알 수 없는 이유로 스캔을 시작할 수 없습니다.")
                            launch { send(HeartRateResult.Error(message = "알 수 없는 이유로 스캔을 시작할 수 없습니다.")) }
                        }
                    }
                }
            }

            if (!scanState) {
                scanState = true
                bluetoothLeScanner.startScan(scanFilter, scanSettings, scanCallback)
                Log.d(tag, "HeartRate 디바이스 스캔을 시작하였습니다.")
            } else {
                launch { send(HeartRateResult.Error(message = "이미 동일한 설정으로 시작했으므로 스캔을 시작하지 못합니다.")) }
            }

            awaitClose {
                bluetoothLeScanner.stopScan(scanCallback)
                scanState = false
                Log.d(tag, "HeartRate 디바이스 스캔을 중지하였습니다.")
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopDeviceScan(): Flow<HeartRateResult> {
        return flow {
            val bluetoothPermissionCheck = hasBluetoothPermission(context = context)
            if (!bluetoothPermissionCheck) {
                emit(HeartRateResult.Error(message = "블루투스 권한이 없습니다."))
                return@flow
            }

            if (scanState) {
                bluetoothLeScanner.stopScan(scanCallback)
                scanState = false
                emit(HeartRateResult.Success)
            } else {
                emit(HeartRateResult.Error(message = "HeartRate 디바이스 스캔을 중지에 실패 하였습니다."))
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun connectDevice(bluetoothDevice: BluetoothDevice, reconnectTime: Long): Flow<HeartRateResult> {
        TODO("Not yet implemented")
    }

    override fun disconnectDevice(): Flow<HeartRateResult> {
        TODO("Not yet implemented")
    }

    override fun startParser(): Flow<HeartRateResult> {
        TODO("Not yet implemented")
    }

    override fun stopParser(): Flow<HeartRateResult> {
        TODO("Not yet implemented")
    }

    @SuppressLint("MissingPermission")
    private fun discoverService() {
        val connectMessage: Boolean = handler.hasMessages(HEART_RATE_CONNECTING)
        if (connectMessage) {
            handler.removeMessages(HEART_RATE_CONNECTING)
        }

        val bluetoothPermissionCheck = hasBluetoothPermission(context = context)
        if (!bluetoothPermissionCheck) {
            throw SecurityException("No bluetooth permission")
        }

        bluetoothGatt.discoverServices()
    }

    @SuppressLint("MissingPermission")
    private fun connectFail() {
        val connectMessage: Boolean = handler.hasMessages(HEART_RATE_CONNECTING)
        val reconnectMessage: Boolean = handler.hasMessages(HEART_RATE_RECONNECTING)
        if (connectMessage) {
            handler.removeMessages(HEART_RATE_CONNECTING)
        }
        if (reconnectMessage) {
            handler.removeMessages(HEART_RATE_RECONNECTING)
        }

        val bluetoothPermissionCheck = hasBluetoothPermission(context = context)
        if (!bluetoothPermissionCheck) {
            throw SecurityException("No bluetooth permission")
        }

        bluetoothGatt.disconnect()
        bluetoothGatt.close()
    }

    /**
     * Init handler
     *
     * Bluetooth Scan, Connecting, Reconnecting event
     */
    private fun initHandler() {
        val handlerCallback: Handler.Callback = Handler.Callback { msg: Message ->
            when(msg.what) {
                HEART_RATE_CONNECTING -> {
                    connectFail()
                }
                HEART_RATE_RECONNECTING -> {
                    connectFail()
                }
            }
            false
        }
        handler = Handler(Looper.getMainLooper(), handlerCallback)
    }

    /**
     * Characteristic setting
     *
     * @return
     */
    private fun characteristicSetting(): Boolean {
        val uuidSetting = serviceUUIDSetting()
        val getService = bluetoothGatt.getService(serviceUUID)
        if (uuidSetting && getService != null) {
            val serviceCharacteristic = getService.characteristics
            // 연결된 Bluetooth Device 의 특성 개수만큼 특성(ex. HeartRate, Step UUID)를 확인
            for (i in 0 until serviceCharacteristic.size) {
                Log.d(tag, "characteristic 종류: ${serviceCharacteristic[i].uuid}")
            }
            writeCharacteristic = getService.getCharacteristic(serviceTxUUID)
            readCharacteristic = getService.getCharacteristic(serviceRxUUID)
            descriptorCharacteristic = getService.getCharacteristic(serviceDescriptor)
        } else {
            return false
        }
        return true
    }

    /**
     * Service u u i d setting
     *
     * @return heart rate uuid discover state
     */
    private fun serviceUUIDSetting(): Boolean {
        var result = false
        val bluetoothService = bluetoothGatt.services
        bluetoothService.reverse()
        val bluetoothServices = bluetoothService.iterator()
        // 현재 연결된 BLE Device에서 Service UUID를 확인하여, BLE Service의 serviceUUID, serviceTxUUID, serviceRxUUID, serviceDescriptor 설정
        while (bluetoothServices.hasNext()) {
            // ignoreCase -> 대소문자 무시
            val discoveryService =
                (bluetoothServices.next() as BluetoothGattService).uuid.toString()
                    .equals(HEART_RATE_SERVICE_UUID, ignoreCase = true)
            if (discoveryService) {
                result = true
                break
            }
        }
        return result
    }

    /**
     * Set on heart rate changed listener
     *
     * @param listener : HeartRate Characteristic
     * @receiver
     */
    private fun setOnHeartRateChangedListener(listener: (BluetoothGattCharacteristic) -> Unit) {
        onHeartRateChanged = listener
    }
}