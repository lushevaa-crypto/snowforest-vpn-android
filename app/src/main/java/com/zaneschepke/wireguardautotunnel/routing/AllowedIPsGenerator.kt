package com.zaneschepke.wireguardautotunnel.routing

import timber.log.Timber

/**
 * Вычисляет AllowedIPs = весь интернет минус российские подсети.
 * Алгоритм: разбиение диапазонов IP на непересекающиеся CIDR блоки.
 */
object AllowedIPsGenerator {

    private const val TAG = "AllowedIPsGenerator"

    fun generate(source: RouteSource): GeneratorResult {
        val startTime = System.currentTimeMillis()

        val ruPrefixes = source.loadRuPrefixes()
        if (ruPrefixes.isEmpty()) {
            Timber.tag(TAG).w("No RU prefixes from ${source.name}, falling back to full tunnel")
            return GeneratorResult(
                allowedIPs = listOf("0.0.0.0/0", "::/0"),
                routeCount = 2,
                source = source.name,
                generationMs = System.currentTimeMillis() - startTime,
                isFallback = true,
            )
        }

        val ipv4Routes = subtractFromInternet(ruPrefixes)
        // IPv6: для POC пускаем весь IPv6 через VPN
        val allRoutes = ipv4Routes + listOf("::/0")

        val elapsed = System.currentTimeMillis() - startTime

        Timber.tag(TAG).d(
            """
            Route source: ${source.name}
            Generated routes: ${allRoutes.size}
            Generation: ${elapsed}ms
            """.trimIndent()
        )

        return GeneratorResult(
            allowedIPs = allRoutes,
            routeCount = allRoutes.size,
            source = source.name,
            generationMs = elapsed,
            isFallback = false,
        )
    }

    private fun subtractFromInternet(ruPrefixes: List<String>): List<String> {
        var remaining = mutableListOf(IpRange(0L, 0xFFFFFFFFL))

        for (prefix in ruPrefixes) {
            try {
                val (network, mask) = parsePrefix(prefix) ?: continue
                val size = 1L shl (32 - mask)
                val start = network
                val end = network + size - 1
                val newRemaining = mutableListOf<IpRange>()
                for (range in remaining) {
                    newRemaining.addAll(subtractRange(range, start, end))
                }
                remaining = newRemaining
            } catch (e: Exception) {
                Timber.tag(TAG).w("Skipping invalid prefix: $prefix")
            }
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
        return Pair(masked, mask)
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

    private fun longToIp(long: Long): String =
        "${(long shr 24) and 0xFF}.${(long shr 16) and 0xFF}.${(long shr 8) and 0xFF}.${long and 0xFF}"

    private data class IpRange(val start: Long, val end: Long)

    data class GeneratorResult(
        val allowedIPs: List<String>,
        val routeCount: Int,
        val source: String,
        val generationMs: Long,
        val isFallback: Boolean,
    )
}
