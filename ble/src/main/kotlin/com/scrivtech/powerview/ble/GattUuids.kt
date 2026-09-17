package com.scrivtech.powerview.ble

import java.util.UUID

/**
 * BLE identifiers for PowerView Gen 3 shades. See `docs/PROTOCOL.md` §1.1
 * and §1.3.
 */
public object GattUuids {

    /** Custom write service. */
    public val SERVICE_SHADE: UUID = UUID.fromString("0000FDC1-0000-1000-8000-00805F9B34FB")

    /** Position/tilt command write characteristic, on [SERVICE_SHADE]. */
    public val CHARACTERISTIC_COMMAND: UUID = UUID.fromString("CAFE1001-C0FF-EE01-8000-A110CA7AB1E0")

    /**
     * Second characteristic on [SERVICE_SHADE] with an unconfirmed purpose
     * (the openHAB binding names it `UUID_CHARACTERISTIC_TBD`). Enumerate
     * its properties/descriptors during hardware bring-up — see
     * `docs/PROTOCOL.md` §5.
     */
    public val CHARACTERISTIC_UNKNOWN: UUID = UUID.fromString("CAFE1002-C0FF-EE01-8000-A110CA7AB1E0")

    /** Standard GATT Device Information Service (vendor/model/hw/fw/serial). */
    public val SERVICE_DEVICE_INFORMATION: UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")

    /** Standard GATT Battery Service. */
    public val SERVICE_BATTERY: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")

    /** Battery level characteristic on [SERVICE_BATTERY]. Coarse bucket: 10/50/100 ~ low/medium/high. */
    public val CHARACTERISTIC_BATTERY_LEVEL: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

    /** Client Characteristic Configuration Descriptor — write to enable notifications, if supported. */
    public val DESCRIPTOR_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}
