package com.scrivtech.powerview.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.scrivtech.powerview.ui.ActionsViewModel
import com.scrivtech.powerview.ui.PowerViewApp
import com.scrivtech.powerview.ui.PowerViewTheme
import com.scrivtech.powerview.ui.SettingsViewModel
import com.scrivtech.powerview.ui.ShadeListViewModel

/**
 * Hosts [PowerViewApp]. Everything past permission handling and view-model
 * wiring belongs in the `:ui`/`:widget` modules, not here.
 *
 * This used to host the step-2 debug scan screen directly. That screen is now
 * one route inside the app rather than the whole of it — still reachable,
 * because it is what confirmed the advertisement offsets against hardware and
 * `docs/PROTOCOL.md` §8 still has open questions for it to answer.
 */
public class MainActivity : ComponentActivity() {

    private val viewModel: ShadeListViewModel by viewModels {
        val app = application as PowerViewApplication
        ShadeListViewModel.Factory(
            app.shadeRepository,
            app.batteryReader,
            app.shadeStore,
            app.actionRunner,
        )
    }

    private val actionsViewModel: ActionsViewModel by viewModels {
        val app = application as PowerViewApplication
        ActionsViewModel.Factory(app.actionStore, app.actionRunner)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory((application as PowerViewApplication).settingsStore)
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

        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            // For the weekly low-battery sweep (build order step 10). Asked for
            // here rather than in context because the sweep runs in the
            // background on a weekly period — there is no later moment the user
            // is present for. BatteryNotifier stays silent if this is declined,
            // and the readings still land in the app either way.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (wanted.isNotEmpty()) {
            requestBlePermissions.launch(wanted.toTypedArray())
        }

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()

            PowerViewTheme(mode = themeMode) {
                Surface {
                    PowerViewApp(
                        viewModel = viewModel,
                        actionsViewModel = actionsViewModel,
                        themeMode = themeMode,
                        onThemeModeChange = settingsViewModel::setThemeMode,
                    )
                }
            }
        }
    }
}
