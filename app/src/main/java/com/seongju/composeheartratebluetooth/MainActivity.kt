package com.seongju.composeheartratebluetooth

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_SCAN_START
import com.seongju.composeheartratebluetooth.common.Constants.ACTION_SCAN_STOP
import com.seongju.composeheartratebluetooth.common.service.ServiceHelper
import com.seongju.composeheartratebluetooth.common.service.ServiceManager
import com.seongju.composeheartratebluetooth.ui.theme.ComposeHeartRateBluetoothTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var serviceManager: ServiceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposeHeartRateBluetoothTheme {

                // Permission observer
                val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
                val permissionState: MultiplePermissionsState = rememberMultiplePermissionsState(
                    permissions = listOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
                DisposableEffect(
                    key1 = lifecycleOwner,
                    effect = {
                        val observer = LifecycleEventObserver {_, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                permissionState.launchMultiplePermissionRequest()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                )

                ServiceHelper.triggerHeartRateService(
                    context = application,
                    action = ACTION_SCAN_START
                )

                lifecycleScope.launch {
                    delay(10000)
                    ServiceHelper.triggerHeartRateService(
                        context = application,
                        action = ACTION_SCAN_STOP
                    )
                }

                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Greeting("Android")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    ComposeHeartRateBluetoothTheme {
        Greeting("Android")
    }
}