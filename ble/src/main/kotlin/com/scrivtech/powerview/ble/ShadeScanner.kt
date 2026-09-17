package com.scrivtech.powerview.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.scrivtech.powerview.protocol.AdvertisementParser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/**
 * Scans for PowerView Gen 3 advertisements. See `docs/PROTOCOL.md` §1.2 and
 * the spec's Android app architecture §2.2.
 *
 * Position reads need **no connection and no encryption key** — this is the
 * entire read path. Hold one long-lived scan rather than stopping/starting:
 * Android silently throttles apps that start/stop scans repeatedly (an
 * undocumented ~5 scans per 30 seconds), and restarting a scan to "refresh"
 * gains nothing since matching advertisements arrive continuously anyway.
 */
public class ShadeScanner(private val context: Context) {

    /**
     * Starts a scan and emits every matching advertisement as it arrives.
     * The flow stays open (and the scan running) until the collector cancels
     * it — callers own the lifecycle (e.g. stop collecting when the app goes
     * to background, per the spec's scan-mode guidance).
     *
     * Emits nothing and completes if `BLUETOOTH_SCAN` isn't granted; check
     * [hasScanPermission] first and drive the runtime permission request
     * from the UI layer, which this module deliberately doesn't own.
     */
    public fun scan(scanMode: Int = ScanSettings.SCAN_MODE_LOW_LATENCY): Flow<RawAdvertisement> = callbackFlow {
        if (!hasScanPermission(context)) {
            close(SecurityException("BLUETOOTH_SCAN not granted"))
            return@callbackFlow
        }

        val adapter = ContextCompat.getSystemService(context, android.bluetooth.BluetoothManager::class.java)
            ?.adapter
        if (adapter == null || !adapter.isEnabled) {
            close(IllegalStateException("Bluetooth adapter unavailable or disabled"))
            return@callbackFlow
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("BluetoothLeScanner unavailable"))
            return@callbackFlow
        }

        // Empty mask = match any payload for this manufacturer ID. See
        // docs/PROTOCOL.md §1.1 — any advertiser with this company ID is a
        // PowerView Gen 3 device.
        val filter = ScanFilter.Builder()
            .setManufacturerData(AdvertisementParser.MANUFACTURER_ID, ByteArray(0), ByteArray(0))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val payload = result.scanRecord
                    ?.getManufacturerSpecificData(AdvertisementParser.MANUFACTURER_ID)
                    ?: return
                val state = AdvertisementParser.parseAndroidPayload(payload) ?: return
                trySend(
                    RawAdvertisement(
                        macAddress = result.device.address,
                        rssi = result.rssi,
                        shadeState = state,
                        rawPayloadHex = payload.joinToString(" ") { String.format(Locale.ROOT, "%02X", it) },
                        timestampMillis = System.currentTimeMillis(),
                    ),
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed, error code $errorCode"))
            }
        }

        scanner.startScan(listOf(filter), settings, callback)

        awaitClose {
            if (hasScanPermission(context)) {
                scanner.stopScan(callback)
            }
        }
    }

    public companion object {
        public fun hasScanPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
    }
}
