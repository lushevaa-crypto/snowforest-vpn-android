package com.zaneschepke.wireguardautotunnel.routing

import android.content.Context
import android.util.Log
import com.zaneschepke.wireguardautotunnel.parser.Config
import com.zaneschepke.wireguardautotunnel.parser.PeerSection
import inet.ipaddr.IPAddressString

/**
 * Snow Forest VPN — Smart Routing
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

            val routeString = routes.joinToString(", ")
            val elapsed = System.currentTimeMillis() - startTime

            Log.i(TAG, "Smart Routing: ${routes.size} routes computed in ${elapsed}ms")

            val patchedPeers = config.peers.map { peer ->
                peer.copy(allowedIPs = routeString)
            }

            config.copy(peers = patchedPeers).also {
                Log.i(TAG, "Smart Routing applied: ${routes.size} routes")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Smart Routing failed, using original config: ${e.message}")
            config
        }
    }

    /**
     * Вычисляет маршруты: 0.0.0.0/0 минус RU подсети + ::/0
     */
    private fun computeRoutes(context: Context): List<String> {
        val ruPrefixes = loadRuPrefixes(context)

        if (ruPrefixes.isEmpty()) {
            Log.w(TAG, "No RU prefixes loaded")
            return emptyList()
        }

        Log.d(TAG, "Computing routes, RU prefixes: ${ruPrefixes.size}")

        var remaining = listOf(
            IPAddressString("0.0.0.0/0").address
                ?: return emptyList()
        )

        for (prefix in ruPrefixes) {
            val ruNetwork = try {
                IPAddressString(prefix.trim()).address ?: continue
            } catch (e: Exception) {
                continue
            }

            remaining = remaining.flatMap { range ->
                range.subtract(ruNetwork)?.toList() ?: listOf(range)
            }
        }

        val ipv4Routes = remaining.map { it.toNormalizedString() }
        Log.d(TAG, "IPv4 routes after subtraction: ${ipv4Routes.size}")

        return ipv4Routes + listOf("::/0")
    }

    private fun loadRuPrefixes(context: Context): List<String> {
        return try {
            context.assets.open("ru_prefixes.txt")
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() }
                .also { Log.d(TAG, "Loaded ${it.size} RU prefixes from assets") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ru_prefixes.txt: ${e.message}")
            emptyList()
        }
    }
}
