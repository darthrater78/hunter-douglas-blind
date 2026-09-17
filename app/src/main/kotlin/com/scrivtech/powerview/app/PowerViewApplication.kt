package com.scrivtech.powerview.app

import android.app.Application
import androidx.work.Configuration
import com.scrivtech.powerview.ble.ShadeScanner
import com.scrivtech.powerview.data.ActionStore
import com.scrivtech.powerview.data.KeystreamStore
import com.scrivtech.powerview.data.ShadeRepository
import com.scrivtech.powerview.data.ShadeStore
import com.scrivtech.powerview.widget.ActionRunner
import com.scrivtech.powerview.widget.CommandWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Wires up the singletons every screen/worker/widget shares. No DI framework
 * yet — this is the "starting framework" stage (build order step 1-7 have
 * concrete implementations; widgets/notifications are still TODO per their
 * own files). Swap for Hilt/Koin if/when the object graph outgrows manual
 * wiring — nothing here depends on it staying manual.
 */
public class PowerViewApplication : Application(), Configuration.Provider {

    // SupervisorJob: one failed collector (e.g. a scan permission exception)
    // must not cancel the others sharing this scope.
    public val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob())

    public val shadeStore: ShadeStore by lazy { ShadeStore(this) }
    public val keystreamStore: KeystreamStore by lazy { KeystreamStore(this) }
    public val actionStore: ActionStore by lazy { ActionStore(this) }
    public val shadeScanner: ShadeScanner by lazy { ShadeScanner(this) }
    public val actionRunner: ActionRunner by lazy { ActionRunner(this, shadeStore, keystreamStore) }

    public val shadeRepository: ShadeRepository by lazy {
        ShadeRepository(shadeScanner, shadeStore, applicationScope)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(CommandWorker.Factory(actionStore) { actionRunner })
            .build()
}
