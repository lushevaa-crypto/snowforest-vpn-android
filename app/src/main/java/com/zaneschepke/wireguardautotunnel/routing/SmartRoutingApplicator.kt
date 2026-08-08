package com.zaneschepke.wireguardautotunnel.routing

import android.content.Context
import android.util.Log
import com.zaneschepke.wireguardautotunnel.parser.Config
import java.net.URL

/**
 * Snow Forest VPN — Smart Routing
 *
 * Применяет умную маршрутизацию к конфигу при старте туннеля.
 * Оригинальный конфиг в БД НЕ ИЗМЕНЯЕТСЯ.
 * AllowedIPs меняется только в runtime.
 *
 * Источники RU подсетей (по приоритету):
 * 1. antifilter.download/list/ip.lst (основной, обновляется раз в 24ч)
 * 2. vpn.snowforest.xyz/routes.json (резервный)
 * 3. Локальный кэш (если источники недоступны)
 * 4. Оригинальный 0.0.0.0/0 (если кэша нет — VPN работает в full-tunnel режиме)
 */
object SmartRoutingApplicator {

    private const val TAG = "SF_SmartRouting"
    private const val PRIMARY_URL = "https://antifilter.download/list/ip.lst"
    private const val FALLBACK_URL = "https://vpn.snowforest.xyz/routes.json"
    private const val TIMEOUT_MS = 10_000

    fun apply(config: Config, context: Context): Config {
        return try {
            val startTime = System.currentTimeMillis()

            val ruPrefixes = getRuPrefixes(context)

            if (ruPrefixes.isEmpty()) {
                Log.w(TAG, "No RU prefixes available — using original AllowedIPs (full tunnel)")
                return config
            }

            val routes = computeNonRuRoutes(ruPrefixes)

            if (routes.isEmpty()) {
                Log.w(TAG, "Route computation failed — using original AllowedIPs")
                return config
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "Smart Routing applied: ${routes.size} routes in ${elapsed}ms")

            val routeString = routes.joinToString(", ")
            val patchedPeers = config.peers.map { peer ->
                peer.copy(allowedIPs = routeString)
            }

            config.copy(peers = patchedPeers)

        } catch (e: Exception) {
            Log.e(TAG, "Smart Routing failed — using original AllowedIPs: ${e.message}")
            config
        }
    }

    /**
     * Получаем список RU подсетей:
     * - Если кэш свежий (< 24ч) — используем кэш
     * - Если кэш устарел или отсутствует — скачиваем с antifilter.download
     * - При ошибке скачивания — используем устаревший кэш
     * - Если кэша нет совсем — возвращаем пустой список (full tunnel)
     */
    private fun getRuPrefixes(context: Context): List<String> {
        val cache = RouteCache.read(context)

        // Кэш свежий — используем без скачивания
        if (cache != null && !cache.isStale) {
            Log.d(TAG, "Using fresh cache (age=${cache.ageMs / 1000}s)")
            return cache.prefixes
        }

        // Кэш устарел или отсутствует — скачиваем
        Log.d(TAG, "Cache ${if (cache == null) "missing" else "stale"} — downloading...")

        val downloaded = downloadPrefixes()

        return when {
            downloaded != null -> {
                Log.i(TAG, "Downloaded ${downloaded.size} RU prefixes from network")
                RouteCache.write(context, downloaded)
                downloaded
            }
            cache != null -> {
                Log.w(TAG, "Download failed — using stale cache (age=${cache.ageMs / 1000}s)")
                cache.prefixes
            }
            else -> {
                Log.e(TAG, "No cache and no network — using full tunnel")
                emptyList()
            }
        }
    }

    /**
     * Скачиваем список RU подсетей.
     * Сначала primary, потом fallback.
     */
    private fun downloadPrefixes(): List<String>? {
        return downloadFrom(PRIMARY_URL) ?: downloadFrom(FALLBACK_URL)
    }

    private fun downloadFrom(url: String): List<String>? {
        return try {
            Log.d(TAG, "Downloading from $url")
            val connection = URL(url).openConnection()
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val content = connection.getInputStream().bufferedReader().readText()

            val prefixes = content.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .filter { it.matches(Regex("""\d+\.\d+\.\d+\.\d+/\d+""")) }

            if (prefixes.isEmpty()) {
                Log.w(TAG, "Downloaded empty list from $url")
                null
            } else {
                Log.d(TAG, "Downloaded ${prefixes.size} prefixes from $url")
                prefixes
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download from $url: ${e.message}")
            null
        }
    }

    /**
     * Вычисляет маршруты: 0.0.0.0/0 минус RU подсети + ::/0
     */
    private fun computeNonRuRoutes(ruPrefixes: List<String>): List<String> {
        var remaining = mutableListOf(IpRange(0L, 0xFFFFFFFFL))

        for (prefix in ruPrefixes) {
            val parsed = parsePrefix(prefix) ?: continue
            val (network, mask) = parsed
            val size = 1L shl (32 - mask)
            val newRemaining = mutableListOf<IpRange>()
            for (range in remaining) {
                newRemaining.addAll(subtractRange(range, network, network + size - 1))
            }
            remaining = newRemaining
        }

        return remaining.flatMap { rangeToCidrs(it.start, it.end) } + listOf("::/0")
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
}
