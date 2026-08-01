package com.zaneschepke.tunnel.util

import com.zaneschepke.tunnel.enums.FamilyOverride
import com.zaneschepke.tunnel.model.DnsBootstrapResult
import com.zaneschepke.tunnel.model.ResolvedHost
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig

internal fun ActiveConfig.findEndpointMismatches(
    freshDns: Map<PublicKey, DnsBootstrapResult>,
    familyOverride: FamilyOverride = FamilyOverride.MatchCurrent,
): Map<PublicKey, ResolvedHost> {
    val currentByKey = peers.associateBy { it.publicKey }
    return freshDns
        .mapNotNull { (pubKey, dns) ->
            val current = currentByKey[pubKey] ?: return@mapNotNull null
            val currentHost = current.host ?: return@mapNotNull null

            val hasIp4p = dns.ipv6.any { DnsHostUtils.decodeIp4p(it) != null }
            if (hasIp4p && familyOverride == FamilyOverride.ForceIpv6) return@mapNotNull null

            val freshHost =
                dns.selectHostForPeer(current.endpoint, familyOverride) ?: return@mapNotNull null

            if (freshHost != currentHost) {
                pubKey to ResolvedHost(host = freshHost)
            } else {
                null
            }
        }
        .toMap()
}

fun ActiveConfig.hasIpv6Peers(): Boolean {
    return this.peers.any { it.endpoint?.contains("[") == true }
}
