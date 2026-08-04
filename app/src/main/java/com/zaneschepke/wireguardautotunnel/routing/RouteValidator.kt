package com.zaneschepke.wireguardautotunnel.routing

import timber.log.Timber

/**
 * Валидирует сгенерированный список маршрутов.
 * Защита от пустых списков и аномально больших/маленьких результатов.
 */
object RouteValidator {

    private const val TAG = "RouteValidator"
    private const val MIN_ROUTES = 10
    private const val MAX_ROUTES = 30_000

    fun validate(result: AllowedIPsGenerator.GeneratorResult): ValidationResult {
        if (result.isFallback) {
            Timber.tag(TAG).w("Using fallback routes — validation skipped")
            return ValidationResult.Ok
        }

        if (result.routeCount < MIN_ROUTES) {
            val msg = "Too few routes: ${result.routeCount} (min $MIN_ROUTES)"
            Timber.tag(TAG).e(msg)
            return ValidationResult.Error(msg)
        }

        if (result.routeCount > MAX_ROUTES) {
            val msg = "Too many routes: ${result.routeCount} (max $MAX_ROUTES)"
            Timber.tag(TAG).e(msg)
            return ValidationResult.Error(msg)
        }

        Timber.tag(TAG).d("Validation OK: ${result.routeCount} routes from ${result.source}")
        return ValidationResult.Ok
    }

    sealed class ValidationResult {
        object Ok : ValidationResult()
        data class Error(val reason: String) : ValidationResult()
    }
}
