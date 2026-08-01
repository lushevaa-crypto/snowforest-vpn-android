package com.zaneschepke.tunnel.backend.features

import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.enums.FamilyOverride
import com.zaneschepke.tunnel.event.TunnelEvent
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.model.BootstrapResolution
import com.zaneschepke.tunnel.model.DnsBootstrapResult
import com.zaneschepke.tunnel.state.ActiveTunnel
import com.zaneschepke.tunnel.util.PublicKey
import com.zaneschepke.tunnel.util.buildResolvedPeers
import com.zaneschepke.tunnel.util.findEndpointMismatches
import com.zaneschepke.tunnel.util.hasIpv6Peers
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import com.zaneschepke.wireguardautotunnel.parser.PeerSection
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class TunnelRecovery(
    private val context: Context,
    private val tunnelId: Int,
    private val mode: BackendMode,
    private val recovery: Tunnel.Feature.Recovery,
    private val failureThreshold: Duration,
    private val stabilizeWindow: Duration,
    private val host: Host,
) {

    @OptIn(ExperimentalAtomicApi::class)
    private var lastIpv4FallbackNetworkKey: AtomicReference<String?> = AtomicReference(null)

    interface Host {
        fun observe(): Flow<Snapshot>

        suspend fun getActiveConfig(): ActiveConfig?

        suspend fun resolveFresh(): BootstrapResolution?

        suspend fun updatePeers(peers: List<PeerSection>)

        suspend fun bounce(withFreshResolution: Boolean): Boolean

        fun updateActiveTunnel(transform: (ActiveTunnel) -> ActiveTunnel)

        suspend fun emit(event: TunnelEvent)
    }

    data class Snapshot(
        val transportState: Tunnel.State?,
        val lastResolvedPeers: Map<PublicKey, DnsBootstrapResult>?,
        val networkUsable: Boolean,
        val networkHasIpv6: Boolean,
        val activeNetworkKey: String?,
    )

    fun start(scope: CoroutineScope): Job = scope.launch {
        with(recovery) {
            if (ipv4Fallback || seamlessRecovery || dynamicDnsRecovery) {
                launch { runFailureRecovery() }
            }
        }
        if (recovery.ipv6Recovery) {
            launch { runHealthyIpv6Upgrade() }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun CoroutineScope.runFailureRecovery() {

        launch {
            host.observe().collectLatest { snap ->
                if (!isFailing(snap)) return@collectLatest

                // try DDNS recovery first if tunnel is a DDNS tunnel
                if (recovery.dynamicDnsRecovery) {
                    delay(stabilizeWindow)
                    tryDynamicDnsRecovery()
                }

                // try IPv4 fallback when applicable
                if (
                    recovery.ipv4Fallback &&
                        snap.activeNetworkKey != null &&
                        snap.activeNetworkKey != lastIpv4FallbackNetworkKey.load()
                ) {
                    delay(stabilizeWindow)
                    tryLightIpv4Fallback(snap)
                }

                // Still failing, we do a full tunnel bounce
                if (!recovery.seamlessRecovery) return@collectLatest
                // Gate feature to only fire when device is not asleep and is interactive
                if (!isDeviceAwakeEnoughForRecovery()) return@collectLatest
                delay(failureThreshold)
                // Still interactive after delay
                if (!isDeviceAwakeEnoughForRecovery()) return@collectLatest
                tryFullTunnelBounce()
            }
        }
    }

    private fun isFailing(snap: Snapshot): Boolean =
        snap.transportState is Tunnel.State.Up.HandshakeFailure && snap.networkUsable

    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun tryDynamicDnsRecovery() {
        Timber.i("DDNS Recovery: attempting dynamic DNS recovery for tunnel $tunnelId")
        val freshBootstrapResolution =
            host.resolveFresh()
                ?: run {
                    Timber.w("DDNS Recovery: DNS resolution failed for peers")
                    return
                }

        val activeConfig = host.getActiveConfig() ?: return
        val mismatches =
            activeConfig.findEndpointMismatches(
                freshBootstrapResolution.peerKeyResults,
                FamilyOverride.MatchCurrent,
            )

        if (mismatches.isEmpty()) {
            Timber.w("DDNS Recovery: no new IPs found")
            return
        }

        val resolved = mode.config.buildResolvedPeers(mismatches)

        Timber.i("DDNS Recovery: Found new IPs for peers, updating tunnel with new endpoints...")

        host.updatePeers(resolved)

        // Update the cache
        host.updateActiveTunnel { it.copy(lastBootstrapResolution = freshBootstrapResolution) }
        host.emit(TunnelEvent.DynamicDnsUpdate(tunnelId, mismatches.keys.toList()))
    }

    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun tryLightIpv4Fallback(snap: Snapshot) {
        val activeConfig = host.getActiveConfig() ?: return
        val hasIpv6Peers = activeConfig.hasIpv6Peers()

        // Always record the network key so we never retry light recovery again for this network
        lastIpv4FallbackNetworkKey.store(snap.activeNetworkKey)

        if (!hasIpv6Peers || snap.lastResolvedPeers.isNullOrEmpty()) return

        val mismatches =
            activeConfig.findEndpointMismatches(snap.lastResolvedPeers, FamilyOverride.ForceIpv4)

        if (mismatches.isEmpty()) return

        Timber.i("Ipv4 Fallback: performing IPv4 fallback peer update for tunnel $tunnelId")
        val resolved = mode.config.buildResolvedPeers(mismatches)
        host.updatePeers(resolved)
        host.emit(TunnelEvent.FallbackToIpv4(tunnelId))
    }

    // Full bounce now only does a fresh DNS request if tunnel is a DDNS tunnel
    private suspend fun tryFullTunnelBounce() {
        withContext(NonCancellable) {
            Timber.i(
                "Seamless Recovery: bouncing tunnel $tunnelId (with fresh DNS request=${recovery.dynamicDnsRecovery})"
            )
            val didBounce = host.bounce(withFreshResolution = recovery.dynamicDnsRecovery)
            if (didBounce) {
                host.updateActiveTunnel {
                    it.copy(
                        recoveryAttempts = it.recoveryAttempts + 1,
                        lastRecoveryAttemptMs = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    fun isDeviceAwakeEnoughForRecovery(): Boolean {
        val pm = context.getSystemService<PowerManager>() ?: return true
        // screen on / device interactive
        if (!pm.isInteractive) return false
        // deep idle
        if (pm.isDeviceIdleMode) return false
        return true
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun isIpv6Recoverable(snap: Snapshot): Boolean {
        val key = snap.activeNetworkKey
        return snap.networkHasIpv6 &&
            snap.transportState is Tunnel.State.Up.Healthy &&
            key != null &&
            key != lastIpv4FallbackNetworkKey.load()
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun CoroutineScope.runHealthyIpv6Upgrade() {
        launch {
            host.observe().collectLatest { snap ->
                if (!isIpv6Recoverable(snap)) return@collectLatest
                delay(stabilizeWindow)
                val activeConfig = host.getActiveConfig() ?: return@collectLatest
                if (activeConfig.hasIpv6Peers()) return@collectLatest

                if (snap.lastResolvedPeers.isNullOrEmpty()) return@collectLatest

                val mismatches =
                    activeConfig.findEndpointMismatches(
                        snap.lastResolvedPeers,
                        FamilyOverride.ForceIpv6,
                    )
                if (mismatches.isEmpty()) return@collectLatest

                Timber.i("Ipv6 Recovery: tunnel $tunnelId upgrading to IPv6")
                val resolved = mode.config.buildResolvedPeers(mismatches)
                host.updatePeers(resolved)
                host.emit(TunnelEvent.RecoveredToIpv6(tunnelId))
            }
        }
    }
}
