package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaneschepke.wireguardautotunnel.data.DataStoreManager
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.routing.BypassAppsInitializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Snow Forest: ViewModel для экрана "Без VPN".
 * Читает из DataStore bypass_packages и получает ID первого туннеля.
 * Не привязан к конкретному туннелю — глобальная настройка.
 */
class NoVpnViewModel(
    private val tunnelRepository: TunnelRepository,
    private val dataStoreManager: DataStoreManager,
) : ViewModel() {

    private val _firstTunnelId = MutableStateFlow<Int?>(null)
    val firstTunnelId: StateFlow<Int?> = _firstTunnelId.asStateFlow()

    private val _bypassCount = MutableStateFlow(0)
    val bypassCount: StateFlow<Int> = _bypassCount.asStateFlow()

    init {
        viewModelScope.launch {
            tunnelRepository.flow.collect { tunnels ->
                _firstTunnelId.value = tunnels.firstOrNull()?.id
            }
        }
        viewModelScope.launch {
            val pkgs = dataStoreManager.getFromStore(BypassAppsInitializer.bypassPackages)
            _bypassCount.value = pkgs?.size ?: 0
        }
    }
}
