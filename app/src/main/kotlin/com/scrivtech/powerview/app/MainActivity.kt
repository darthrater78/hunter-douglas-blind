package com.scrivtech.powerview.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.scrivtech.powerview.ui.DebugScanScreen
import com.scrivtech.powerview.ui.ShadeListViewModel

/**
 * Build order step 2: hosts [DebugScanScreen] so decoded advertisements can
 * be confirmed against real hardware before any writes are attempted.
 * Everything past permission handling and view-model wiring belongs in the
 * `:ui`/`:widget` modules, not here.
 */
public class MainActivity : ComponentActivity() {

    private val viewModel: ShadeListViewModel by viewModels {
        ShadeListViewModel.Factory((application as PowerViewApplication).shadeRepository)
    }

    private val requestBlePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // The scan started in onCreate typically fails because it races this
        // very prompt — retry once we have an answer (ShadeRepository.scanState
        // reflects a denial as ScanState.Failed for the UI to react to).
        if (grants[Manifest.permission.BLUETOOTH_SCAN] == true) {
            (application as PowerViewApplication).shadeRepository.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ShadeRepository no longer starts scanning from its constructor, so
        // this is the one place that does. Unconditional, because below API 31
        // the BLE permissions are install-time and no prompt is shown at all —
        // gating this on the prompt would mean never scanning on those devices.
        (application as PowerViewApplication).shadeRepository.startScan()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBlePermissions.launch(
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
            )
        }

        setContent {
            MaterialTheme {
                Surface {
                    DebugScanScreen(viewModel = viewModel)
                }
            }
        }
    }
}
