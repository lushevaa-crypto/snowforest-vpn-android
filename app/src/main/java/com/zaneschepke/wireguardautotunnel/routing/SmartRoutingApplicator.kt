package com.zaneschepke.wireguardautotunnel.routing

import android.content.Context
import android.util.Log
import com.zaneschepke.wireguardautotunnel.parser.Config

/**
 * Snow Forest VPN — Smart Routing v2
 *
 * Применяет умную маршрутизацию к конфигу при старте туннеля.
 * Оригинальный конфиг в БД НЕ ИЗМЕНЯЕТСЯ.
 * AllowedIPs меняется только в runtime.
 *
 * Логика: 0.0.0.0/0 минус российские подсети = весь мир кроме России.
 * Российский трафик идёт напрямую, зарубежный — через VPN.
 */
object SmartRoutingApplicator {

    private const val TAG = "SF_SmartRouting"

    /**
     * Применяет Smart Routing к конфигу.
     * При любой ошибке возвращает оригинальный конфиг без изменений.
     */
    fun apply(config: Config, context: Context): Config {
        return try {
            val startTime = System.currentTimeMillis()
            val routes = computeRoutes(context)

            if (routes.isEmpty()) {
                Log.w(TAG, "Empty routes — using original AllowedIPs")
                return config
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "Smart Routing: ${routes.size} routes in ${elapsed}ms")

            val routeString = routes.joinToString(", ")
            val patchedPeers = config.peers.map { peer ->
                peer.copy(allowedIPs = routeString)
            }

            config.copy(peers = patchedPeers)

        } catch (e: Exception) {
            Log.e(TAG, "Smart Routing failed, using original: ${e.message}")
            config
        }
    }

    private fun computeRoutes(context: Context): List<String> {
        val ruPrefixes = loadRuPrefixes(context)
        if (ruPrefixes.isEmpty()) return emptyList()

        Log.d(TAG, "RU prefixes: ${ruPrefixes.size}")
        val result = subtractRuFromInternet(ruPrefixes)
        Log.d(TAG, "Result routes: ${result.size}")

        return result + listOf("::/0")
    }

    private fun subtractRuFromInternet(ruPrefixes: List<String>): List<String> {
        var remaining = mutableListOf(IpRange(0L, 0xFFFFFFFFL))

        for (prefix in ruPrefixes) {
            val parsed = parsePrefix(prefix) ?: continue
            val (network, mask) = parsed
            val size = 1L shl (32 - mask)
            val start = network
            val end = network + size - 1

            val newRemaining = mutableListOf<IpRange>()
            for (range in remaining) {
                newRemaining.addAll(subtractRange(range, start, end))
            }
            remaining = newRemaining
        }

        return remaining.flatMap { rangeToCidrs(it.start, it.end) }
    }

    private fun subtractRange(range: IpRange, subStart: Long, subEnd: Long): List<IpRange> {
        if (subEnd < range.start || subStart > range.end) return listOf(range)
        val result = mutableListOf<IpRange>()
        if (range.start < subStart) result.add(IpRange(range.start, subStart - 1))
        if (range.end > subEnd) result.add(IpRange(subEnd + 1, range.end))
        return result
    }

    private fun rangeToCidrs(start: Long, end: Long): List<String> {
        val cidrs = mutableListOf<String>()
        var current = start
        while (current <= end) {
            var maxBits = 32
            var bit = 0
            while (bit < 32) {
                if ((current shr bit) and 1L == 1L) break
                bit++
            }
            maxBits = minOf(maxBits, 32 - bit)
            while (current + (1L shl (32 - maxBits)) - 1 > end) maxBits++
            cidrs.add("${longToIp(current)}/$maxBits")
            current += 1L shl (32 - maxBits)
            if (current > 0xFFFFFFFFL) break
        }
        return cidrs
    }

    private fun parsePrefix(prefix: String): Pair<Long, Int>? {
        val parts = prefix.trim().split("/")
        if (parts.size != 2) return null
        val mask = parts[1].toIntOrNull() ?: return null
        if (mask < 0 || mask > 32) return null
        val ip = ipToLong(parts[0]) ?: return null
        val masked = if (mask == 0) 0L else ip and (0xFFFFFFFFL shl (32 - mask))
        return Pair(masked and 0xFFFFFFFFL, mask)
    }

    private fun ipToLong(ip: String): Long? {
        val parts = ip.split(".")
        if (parts.size != 4) return null
        return parts.fold(0L) { acc, s ->
            val octet = s.toLongOrNull() ?: return null
            if (octet < 0 || octet > 255) return null
            (acc shl 8) or octet
        }
    }

    private fun longToIp(n: Long): String =
        "${(n shr 24) and 0xFF}.${(n shr 16) and 0xFF}.${(n shr 8) and 0xFF}.${n and 0xFF}"

    private data class IpRange(val start: Long, val end: Long)

    private fun loadRuPrefixes(context: Context): List<String> {
        return try {
            context.assets.open("ru_prefixes.txt")
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ru_prefixes.txt: ${e.message}")
            emptyList()
        }
    }
}
