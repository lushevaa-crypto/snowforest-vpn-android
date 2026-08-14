package com.zaneschepke.wireguardautotunnel.viewmodel

import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.routing.BypassAppsInitializer
import com.zaneschepke.wireguardautotunnel.data.DataStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

/**
 * Snow Forest: ViewModel для экрана "Без VPN".
 * Читает из DataStore bypass_packages и получает ID первого туннеля.
 * Не привязан к конкретному туннелю — это глобальная настройка.
 */
class NoVpnViewModel(
    private val tunnelRepository: TunnelRepository,
    private val dataStoreManager: DataStoreManager,
) : androidx.lifecycle.ViewModel() {

    private val _firstTunnelId = MutableStateFlow<Int?>(null)
    val firstTunnelId: StateFlow<Int?> = _firstTunnelId.asStateFlow()

    private val _bypassCount = MutableStateFlow(0)
    val bypassCount: StateFlow<Int> = _bypassCount.asStateFlow()

    init {
        androidx.lifecycle.viewModelScope.launch {
            tunnelRepository.flow.collect { tunnels ->
                _firstTunnelId.value = tunnels.firstOrNull()?.id
            }
        }
        androidx.lifecycle.viewModelScope.launch {
            val pkgs = dataStoreManager.getFromStore(BypassAppsInitializer.bypassPackages)
            _bypassCount.value = pkgs?.size ?: 0
        }
    }
}
