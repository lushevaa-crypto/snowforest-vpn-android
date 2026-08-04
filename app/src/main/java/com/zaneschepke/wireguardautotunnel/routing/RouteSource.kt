package com.zaneschepke.wireguardautotunnel.routing

/**
 * Источник российских подсетей для вычисления маршрутов.
 * Расширяемый интерфейс — в будущем можно добавить:
 * - AntifilterRouteSource
 * - RipeRouteSource
 * - SnowForestApiRouteSource
 */
interface RouteSource {
    val name: String
    fun loadRuPrefixes(): List<String>
}
