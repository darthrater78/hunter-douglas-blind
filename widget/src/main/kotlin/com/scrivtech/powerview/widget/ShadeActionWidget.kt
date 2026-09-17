package com.scrivtech.powerview.widget

/**
 * TODO(build order step 9): the Glance `GlanceAppWidget` + `GlanceAppWidgetReceiver`
 * pair for the 1x1 and 2x2/4x2 widgets described in spec §3.2.
 *
 * Deferred rather than stubbed with unverified Glance API calls: this
 * container has no Android SDK, so widget/receiver code here couldn't be
 * compiled or checked before being committed. [ActionRunner] and
 * [CommandWorker] — the pieces the widget click handler needs to call — are
 * already built and are meant to be driven from in-app buttons first (build
 * order step 8), so the widget itself is the only remaining piece once
 * that's proven out.
 *
 * What it needs to do, per spec §3.2-§3.4:
 * - `RunActionCallback : ActionCallback` — sets this widget instance's Glance
 *   state to [ActionResult.Pending], calls `update()`, then enqueues a
 *   `OneTimeWorkRequest` for [CommandWorker] with the action id from
 *   `actionParametersOf`. Must return quickly — Glance callbacks run on a
 *   short leash and BLE takes seconds.
 * - A `GlanceStateDefinition` for per-widget-instance state
 *   ([ActionResult]), so `Pending`/`Failed` render per widget, not globally.
 * - Two widget sizes/layouts (1x1 button, 2x2/4x2 grid of up to six actions)
 *   with `resizeMode="horizontal|vertical"` and `updatePeriodMillis="0"` in
 *   the widget info XML (never poll — push updates from [ActionRunner]).
 * - Debounce: ignore a tap on an action already `Pending`.
 */
public object ShadeActionWidgetPlaceholder
