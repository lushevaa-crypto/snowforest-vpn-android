package com.zaneschepke.tunnel.backend.features

import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

internal class ActiveConfigMonitor(
    private val tunnelId: Int,
    private val interval: Duration,
    private val host: Host,
) {
    interface Host {
        suspend fun getActiveConfig(): ActiveConfig?

        fun updateActiveConfig(config: ActiveConfig?)
    }

    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            val config = host.getActiveConfig()
            if (config == null) {
                Timber.w("ActiveConfigMonitor[$tunnelId]: no handle/config, stopping")
                return@launch
            }
            host.updateActiveConfig(config)
            delay(interval)
        }
    }
}
