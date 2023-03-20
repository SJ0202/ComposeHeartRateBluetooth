package com.seongju.heartrate_service.common

enum class BluetoothStatus {
    STATE_CONNECTING,
    STATE_CONNECTED,
    STATE_DISCONNECTED,
    STATE_CONNECT_FAIL,
    STATE_CONNECTION_LOST,
    STATE_RECONNECTING
}