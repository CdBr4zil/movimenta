package com.example.projeto_movimenta

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface InventoryEffect {
    data object Return : InventoryEffect
    data class SelectSector(val id: Long) : InventoryEffect
    data class ClearAdjustment(val lotId: Long) : InventoryEffect
}

internal data class InventoryOutcome(val message: String? = null, val effect: InventoryEffect? = null)

internal class InventoryController(val repository: InventoryRepository) : ViewModel() {
    var inventory by mutableStateOf<Inventory?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var loadFailed by mutableStateOf(false)
        private set
    var outcome by mutableStateOf<InventoryOutcome?>(null)
        private set

    init {
        reload()
    }

    fun reload() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                refresh()
            } finally {
                busy = false
            }
        }
    }

    fun write(action: InventoryRepository.() -> InventoryOutcome) {
        if (busy || outcome != null) return
        busy = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repository.action() }
                refresh()
                outcome = result
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                outcome = InventoryOutcome("Não foi possível salvar a alteração. Tente novamente.")
            } finally {
                busy = false
            }
        }
    }

    fun consume(result: InventoryOutcome) {
        if (outcome === result) outcome = null
    }

    private suspend fun refresh() {
        try {
            inventory = withContext(Dispatchers.IO) { repository.snapshot() }
            loadFailed = false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            loadFailed = true
        }
    }

    override fun onCleared() {
        repository.close()
    }
}
