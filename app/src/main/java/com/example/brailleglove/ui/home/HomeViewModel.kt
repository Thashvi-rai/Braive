package com.example.brailleglove.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.example.brailleglove.ble.BleManager
import com.example.brailleglove.data.repository.BrailleRepository

class HomeViewModel(
    val bleManager: BleManager,
    repository: BrailleRepository
) : ViewModel() {

    // Expose BLE state as LiveData so Fragment can observe easily
    val connectionState = bleManager.connectionState.asLiveData()
    val isBatteryLow = bleManager.isBatteryLow.asLiveData()

    // Level data to show progress on home screen
    val allLevels = repository.allLevels

    fun startScan() = bleManager.startScan()
    fun disconnect() = bleManager.disconnect()

    override fun onCleared() {
        super.onCleared()
        // Do NOT call bleManager.destroy() here — it's app-scoped, not fragment-scoped
    }
}