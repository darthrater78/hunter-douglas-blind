package com.scrivtech.powerview.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrivtech.powerview.data.Shade
import com.scrivtech.powerview.data.ShadeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Backs [DebugScanScreen] (build order step 2) and, later, the real shade
 * list. Deliberately thin — it does no BLE or persistence work itself, only
 * reshapes [ShadeRepository.shades] for display.
 */
public class ShadeListViewModel(private val repository: ShadeRepository) : ViewModel() {

    public val shades: StateFlow<List<Shade>> = repository.shades
        .map { byMac -> byMac.values.sortedBy { it.label } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    public class Factory(private val repository: ShadeRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ShadeListViewModel::class.java))
            return ShadeListViewModel(repository) as T
        }
    }
}
