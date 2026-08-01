package com.zaneschepke.tunnel.util

import com.zaneschepke.wireguardautotunnel.parser.Config

fun Config.hasDynamicEndpoints(): Boolean {
    return peers.any { !it.isStaticallyConfigured && it.endpoint != null }
}
