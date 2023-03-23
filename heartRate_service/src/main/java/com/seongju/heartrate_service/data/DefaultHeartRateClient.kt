package com.seongju.heartrate_service.data

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.*
import android.util.Log
import com.seongju.heartrate_service.common.BluetoothStatus
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

            // 스캔중이 아닐 때 스캔을 실행이 될 수도 있으니 한번 더 체크
            if (!scanState) {
                scanState = true
                bluetoothLeScanner.startScan(scanFilter, scanSettings, scanCallback)
                Log.d(tag, "HeartRate 디바이스 스캔을 시작하였습니다.")
            } else {
                launch { send(HeartRateResult.Error(message = "이미 동일한 설정으로 시작했으므로 스캔을 시작하지 못합니다.")) }
            }

            // 종료될 시 Callback 을 초기화 하기 위해 필요함
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

    @SuppressLint("MissingPermission")
    override fun connectDevice(bluetoothDevice: BluetoothDevice, reconnectTime: Long): Flow<HeartRateResult> {
        return callbackFlow {
            val bluetoothPermissionCheck = hasBluetoothPermission(context = context)
            if (!bluetoothPermissionCheck) {
                launch { send(HeartRateResult.Error(message = "블루투스 권한이 없습니다.")) }
                return@callbackFlow
            }

            val connectDelayRunnable = Runnable {
                launch { send(HeartRateResult.Error(message = "연결시간($reconnectTime)이 초과되었습니다.")) }
                handler.sendEmptyMessage(HEART_RATE_CONNECTING)
            }

            connectCallback = object : BluetoothGattCallback() {
                // Build.VERSION_CODES.TIRAMISU 미만일 경우
                override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                    super.onCharacteristicChanged(gatt, characteristic)
                    if (characteristic != null) {
                        onHeartRateChanged?.invoke(characteristic)
                    }
                }
                // Build.VERSION_CODES.TIRAMISU 이상일 경우
                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                    super.onCharacteristicChanged(gatt, characteristic, value)
                    onHeartRateChanged?.invoke(characteristic)
                }

                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    super.onConnectionStateChange(gatt, status, newState)

                    when(newState) {
                        BluetoothProfile.STATE_CONNECTING -> {
                            launch { send(HeartRateResult.HeartRateState(bluetoothStatus = BluetoothStatus.STATE_CONNECTING)) }
                        }
                        BluetoothProfile.STATE_CONNECTED -> {
                            when(status) {
                                BluetoothGatt.GATT_SUCCESS -> {
                                    try {
                                        discoverService()
                                        return
                                    } catch (error: Exception) {
                                        Log.e(tag, "디바이스의 서비스를 찾는 중에 오류가 발생하였습니다.")
                                        launch { send(HeartRateResult.HeartRateState(bluetoothStatus = BluetoothStatus.STATE_CONNECT_FAIL)) }
                                        handler.sendEmptyMessage(HEART_RATE_CONNECTING)
                                    }
                                }
                                else -> {
                                    Log.e(tag, "GATT 서버 연결에 실패하였습니다.")
                                    launch { send(HeartRateResult.HeartRateState(bluetoothStatus = BluetoothStatus.STATE_CONNECT_FAIL)) }
                                    handler.sendEmptyMessage(HEART_RATE_CONNECTING)
                                }
                            }
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            when(status) {
                                BluetoothGatt.GATT_CONNECTION_CONGESTED -> {
                                    Log.e(tag, "연결이 불안정하여 연결이 해제되었습니다. ${reconnectTime / 1000}초간 재연결을 시도합니다.")
                                    launch { send(HeartRateResult.HeartRateState(bluetoothStatus = BluetoothStatus.STATE_RECONNECTING)) }
                                    handler.postDelayed({
                                        Log.e(tag, "연결이 불안정하여 연결이 해제되었습니다.")
                                        launch { send(HeartRateResult.HeartRateState(bluetoothStatus = BluetoothStatus.STATE_CONNECT_FAIL)) }
                                        handler.sendEmptyMessage(HEART_RATE_RECONNECTING) }, reconnectTime)
                                }
                                BluetoothGatt.GATT_FAILURE -> {
                                    Log.e(tag, "연결에 실패하였습니다. ${reconnectTime / 1000}초간 재연결을 시도합니다.")
                                    launch { send(HeartRateResult.HeartRateState(bluetoothStatus = BluetoothStatus.STATE_RECONNECTING)) }
                                    handler.postDelayed({
                                        Log.e(tag, "연결에 실패하였습니다.")
                                        launch { send(HeartRateResult.HeartRateState(bluetoothStatus = BluetoothStatus.STATE_CONNECT_FAIL)) }
                                        handler.sendEmptyMessage(HEART_RATE_RECONNECTING) }, reconnectTime)
                                }
                                else -> {
                                    //통신불량으로 해제된 것인지, 사용자가 해제한 것인지 구분하기 위함
                                    if (disconnectCall) {
                                        disconnectCall = false
                                        launch { send(HeartRateResult.HeartRateState(bluetoothStatus = BluetoothStatus.STATE_DISCONNECTED)) }
                                        bluetoothGatt.close()
                                    } else {
                                        Log.e(tag, "알 수 없는 이유로 연결이 해제되었습니다. ${reconnectTime / 1000}초간 재연결을 시도합니다.")
                                        launch { send(HeartRateResult.HeartRateState(bluetoothStatus = BluetoothStatus.STATE_RECONNECTING)) }
                                        handler.postDelayed({
                                            Log.e(tag, "알 수 없는 이유로 연결이 해제되었습니다.")
                                            launch { send(HeartRateResult.HeartRateState(bluetoothStatus = BluetoothStatus.STATE_CONNECT_FAIL)) }
                                            handler.sendEmptyMessage(HEART_RATE_RECONNECTING) }, reconnectTime)
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    super.onServicesDiscovered(gatt, status)

                    when(status) {
                        // service 발견 후 characteristic 을 발견하지 못하였을 경우 연결 실패로 간주함
                        BluetoothGatt.GATT_SUCCESS -> {
                            val characteristicSettingState: Boolean = characteristicSetting()
                            if (characteristicSettingState) {
                                launch { send(HeartRateResult.HeartRateState(bluetoothStatus = BluetoothStatus.STATE_CONNECTED)) }
                                handler.removeCallbacks(connectDelayRunnable)
                            } else {
                                Log.e(tag, "서비스를 찾다가 오류가 발생하였습니다.")
                                launch { send(HeartRateResult.HeartRateState(bluetoothStatus = BluetoothStatus.STATE_CONNECT_FAIL)) }
                                handler.sendEmptyMessage(HEART_RATE_CONNECTING)
                            }
                        }
                        else -> {
                            Log.e(tag, "서비스를 찾을 수 없어 연결을 취소합니다.")
                            launch { send(HeartRateResult.HeartRateState(bluetoothStatus = BluetoothStatus.STATE_CONNECT_FAIL)) }
                            handler.sendEmptyMessage(HEART_RATE_CONNECTING)
                        }
                    }
                }
            }

            launch { send(HeartRateResult.HeartRateState(bluetoothStatus = BluetoothStatus.STATE_CONNECTING)) }
            bluetoothDevice.createBond()
            bluetoothGatt = bluetoothDevice.connectGatt(context, true, connectCallback, BluetoothDevice.TRANSPORT_LE)

            // 10초동안 연결 안 될 경우 연결 실패로 간주
            handler.postDelayed(connectDelayRunnable, reconnectTime)

            awaitClose {
                bluetoothGatt.disconnect()
                bluetoothGatt.close()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnectDevice(): Flow<HeartRateResult> {
        return flow {
            val bluetoothPermissionCheck = hasBluetoothPermission(context = context)
            if (!bluetoothPermissionCheck) {
                emit(HeartRateResult.Error(message = "블루투스 권한이 없습니다."))
                return@flow
            }

            if (::bluetoothGatt.isInitialized) {
                disconnectCall = true
                bluetoothGatt.disconnect()
                emit(HeartRateResult.Success)
            } else {
                emit(HeartRateResult.Error(message = "블루투스 연결해제에 실패하였습니다."))
            }
        }.flowOn(Dispatchers.IO)
    }

    @SuppressLint("MissingPermission")
    override fun startParser(): Flow<HeartRateResult> {
        return callbackFlow {
            val bluetoothPermissionCheck = hasBluetoothPermission(context = context)
            if (!bluetoothPermissionCheck) {
                launch { send(HeartRateResult.Error(message = "블루투스 권한이 없습니다.")) }
                return@callbackFlow
            }

            setOnHeartRateChangedListener { characteristic ->
                when(characteristic.uuid) {
                    serviceRxUUID -> {
                        val flag = characteristic.properties
                        val format = when(flag and 0x01) {
                            0x01 -> {
                                BluetoothGattCharacteristic.FORMAT_SINT16
                            }
                            else -> {
                                BluetoothGattCharacteristic.FORMAT_UINT8
                            }
                        }
                        val heartRate = characteristic.getIntValue(format, 1)
                        launch { send(HeartRateResult.HeartRate(heartRate = heartRate)) }
                    }
                    else -> {
                        val data = characteristic.value
                        if (data?.isNotEmpty() == true) {
                            val hexString = data.joinToString(separator = "") {
                                String.format("%02X", it)
                            }
                            Log.d(tag, hexString)
                        }
                    }
                }
            }

            bluetoothGatt.setCharacteristicNotification(readCharacteristic, true)
            val descriptor: BluetoothGattDescriptor = readCharacteristic!!.getDescriptor(serviceDescriptor)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    bluetoothGatt.writeDescriptor(descriptor)
                }
            } catch ( e: Exception) {
                launch { send(HeartRateResult.Error(message = "HeartRate 측정 시작에 실패하였습니다.")) }
            }
            awaitClose {

            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopParser(): Flow<HeartRateResult> {
        return flow<HeartRateResult> {
            val bluetoothPermissionCheck = hasBluetoothPermission(context = context)
            if (!bluetoothPermissionCheck) {
                emit(HeartRateResult.Error(message = "블루투스 권한이 없습니다."))
                return@flow
            }

            bluetoothGatt.setCharacteristicNotification(readCharacteristic, false)
            val descriptor: BluetoothGattDescriptor = readCharacteristic!!.getDescriptor(serviceDescriptor)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt.writeDescriptor(descriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
                } else {
                    descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    bluetoothGatt.writeDescriptor(descriptor)
                }
                emit(HeartRateResult.Success)
            } catch ( e: Exception) {
                emit(HeartRateResult.Error(message = "HeartRate 측정 중지에 실패하였습니다."))
            }
        }.flowOn(Dispatchers.IO)
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