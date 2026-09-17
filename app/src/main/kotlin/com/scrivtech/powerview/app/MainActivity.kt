package com.scrivtech.powerview.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.scrivtech.powerview.ble.ShadeScanner
import com.scrivtech.powerview.data.BatterySweepWorker
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
        val app = application as PowerViewApplication
        SettingsViewModel.Factory(app.settingsStore) { BatterySweepWorker.sweepNow(app) }
    }

    private val requestBlePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // The scan started in onCreate typically fails because it races this
        // very prompt — retry once we have an answer (ShadeRepository.scanState
        // reflects a denial as ScanState.Failed for the UI to react to).
        if (grants[ShadeScanner.scanPermission()] == true) {
            (application as PowerViewApplication).shadeRepository.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ShadeRepository no longer starts scanning from its constructor, so
        // this is the one place that does. Unconditional, so that a device
        // where the permission is already granted scans without waiting for
        // the prompt callback, which is not called when nothing is asked.
        (application as PowerViewApplication).shadeRepository.startScan()

        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                // Below API 31 the Bluetooth permissions are install-time, but a
                // scan also needs location, and that is a runtime permission.
                // Without it the scan runs and returns nothing.
                add(Manifest.permission.ACCESS_FINE_LOCATION)
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
            val tileActionId by settingsViewModel.tileActionId.collectAsState()
            val sweepInterval by settingsViewModel.sweepInterval.collectAsState()
            val notificationsEnabled by settingsViewModel.notificationsEnabled.collectAsState()

            PowerViewTheme(mode = themeMode) {
                Surface {
                    PowerViewApp(
                        viewModel = viewModel,
                        actionsViewModel = actionsViewModel,
                        themeMode = themeMode,
                        onThemeModeChange = settingsViewModel::setThemeMode,
                        tileActionId = tileActionId,
                        onTileActionChange = settingsViewModel::setTileActionId,
                        sweepInterval = sweepInterval,
                        onSweepIntervalChange = settingsViewModel::setSweepInterval,
                        onSweepNow = settingsViewModel::sweepNow,
                        notificationsEnabled = notificationsEnabled,
                        onNotificationsEnabledChange = settingsViewModel::setNotificationsEnabled,
                        onOpenSystemNotificationSettings = { openSystemNotificationSettings() },
                    )
                }
            }
        }
    }

    /** Opens this app's page in the system notification settings (sound, vibration, importance). */
    private fun openSystemNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(intent)
    }
}
