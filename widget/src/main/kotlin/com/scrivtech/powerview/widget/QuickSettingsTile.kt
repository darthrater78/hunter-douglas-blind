package com.scrivtech.powerview.widget

/**
 * TODO(build order step 11): a `TileService` wrapping one designated
 * [com.scrivtech.powerview.data.ShadeAction], reachable from the lock screen
 * (spec §3.5). Small once [ActionRunner] exists — `onClick()` enqueues the
 * same [CommandWorker] work as a widget tap and updates `qsTile.state`
 * (`Tile.STATE_ACTIVE` while pending) instead of Glance widget state.
 */
public object QuickSettingsTilePlaceholder
