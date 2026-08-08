package com.zaneschepke.wireguardautotunnel.routing

import android.content.Context
import android.util.Log
import com.zaneschepke.wireguardautotunnel.parser.Config
import org.json.JSONObject
import java.net.URL

/**
 * Snow Forest VPN — Smart Routing
 *
 * Логика: 0.0.0.0/0 минус российские подсети = весь мир кроме России.
 *
 * Источники RU подсетей:
 * 1. RIPE NCC API (основной — полный официальный список)
 * 2. vpn.snowforest.xyz/routes.json (резервный)
 * 3. Локальный кэш в filesDir (обновляется при успешной загрузке)
 * 4. При отсутствии всего — full tunnel 0.0.0.0/0 (VPN всё равно работает)
 *
 * Кэш: при старте используем кэш мгновенно, обновляем в фоне если > 24ч.
 */
object SmartRoutingApplicator {

    private const val TAG = "SF_SmartRouting"
    private const val PRIMARY_URL =
        "https://stat.ripe.net/data/country-resource-list/data.json?resource=RU"
    private const val FALLBACK_URL = "https://vpn.snowforest.xyz/routes.json"
    private const val TIMEOUT_MS = 10_000

    /**
     * Применяет Smart Routing к конфигу при старте туннеля.
     * Использует кэш немедленно — не блокирует подключение ожиданием сети.
     * При любой ошибке возвращает оригинальный конфиг.
     */
    fun apply(config: Config, context: Context): Config {
        return try {
            val startTime = System.currentTimeMillis()
            val cache = RouteCache.read(context)

            // Определяем какой список использовать
            val ruPrefixes = when {
                cache != null -> {
                    // Есть кэш — используем сразу, обновляем в фоне если устарел
                    if (cache.isStale) {
                        Log.d(TAG, "Cache stale (${cache.ageMs / 3600000}h) — will update in background")
                        Thread { updateCache(context) }.start()
                    } else {
                        Log.d(TAG, "Cache fresh (${cache.ageMs / 3600000}h)")
                    }
                    cache.prefixes
                }
                else -> {
                    // Нет кэша — пробуем скачать синхронно (первый запуск)
                    Log.d(TAG, "No cache — downloading synchronously")
                    val downloaded = downloadRuPrefixes()
                    if (downloaded != null) {
                        RouteCache.write(context, downloaded)
                        downloaded
                    } else {
                        Log.w(TAG, "Download failed, no cache — using full tunnel")
                        return config
                    }
                }
            }

            if (ruPrefixes.isEmpty()) {
                Log.w(TAG, "Empty prefix list — using full tunnel")
                return config
            }

            val routes = computeNonRuRoutes(ruPrefixes)

            if (routes.isEmpty()) {
                Log.w(TAG, "Route computation failed — using full tunnel")
                return config
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "SmartRouting: source=cache routes=${routes.size} elapsed=${elapsed}ms")

            val routeString = routes.joinToString(", ")
            config.copy(peers = config.peers.map { it.copy(allowedIPs = routeString) })

        } catch (e: Exception) {
            Log.e(TAG, "SmartRouting failed — using full tunnel: ${e.message}")
            config
        }
    }

    private fun updateCache(context: Context) {
        val prefixes = downloadRuPrefixes()
        if (prefixes != null) {
            RouteCache.write(context, prefixes)
            Log.i(TAG, "Background cache update: ${prefixes.size} prefixes")
        } else {
            Log.w(TAG, "Background cache update failed")
        }
    }

    /**
     * Скачивает список RU подсетей.
     * Primary: RIPE NCC API (JSON формат)
     * Fallback: наш сервер
     */
    private fun downloadRuPrefixes(): List<String>? {
        return downloadFromRipe() ?: downloadFromFallback()
    }

    /**
     * Скачиваем с RIPE NCC API.
     * Формат: JSON {"data":{"resources":{"ipv4":["5.16.0.0/14","..."]}}}
     * Есть CIDR ("5.16.0.0/14") и диапазоны ("31.135.244.0-31.135.251.255").
     * Берём только CIDR, диапазоны отбрасываем.
     */
    private fun downloadFromRipe(): List<String>? {
        return try {
            Log.d(TAG, "Downloading from RIPE API")
            val connection = URL(PRIMARY_URL).openConnection()
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            val content = connection.getInputStream().bufferedReader().readText()

            val json = JSONObject(content)
            val ipv4Array = json
                .getJSONObject("data")
                .getJSONObject("resources")
                .getJSONArray("ipv4")

            val prefixes = mutableListOf<String>()
            for (i in 0 until ipv4Array.length()) {
                val entry = ipv4Array.getString(i)
                // Берём только CIDR, отбрасываем диапазоны (содержат '-')
                if (entry.contains('/') && !entry.contains('-')) {
                    prefixes.add(entry)
                }
            }

            Log.d(TAG, "RIPE: ${prefixes.size} CIDR prefixes")
            if (prefixes.isEmpty()) null else prefixes

        } catch (e: Exception) {
            Log.w(TAG, "RIPE download failed: ${e.message}")
            null
        }
    }

    /**
     * Резервный источник — наш сервер (plain text, одна подсеть на строку).
     */
    private fun downloadFromFallback(): List<String>? {
        return try {
            Log.d(TAG, "Downloading from fallback: $FALLBACK_URL")
            val connection = URL(FALLBACK_URL).openConnection()
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val content = connection.getInputStream().bufferedReader().readText()

            val prefixes = content.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") && it.contains('/') && !it.contains('-') }

            Log.d(TAG, "Fallback: ${prefixes.size} prefixes")
            if (prefixes.isEmpty()) null else prefixes

        } catch (e: Exception) {
            Log.w(TAG, "Fallback download failed: ${e.message}")
            null
        }
    }

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
