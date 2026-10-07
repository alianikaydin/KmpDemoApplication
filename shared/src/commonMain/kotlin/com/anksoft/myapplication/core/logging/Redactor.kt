package com.anksoft.myapplication.core.logging

/**
 * Masks sensitive values in log text: JWTs, bearer tokens, token/password key-value pairs and
 * e-mail addresses. Pattern based, so it is a safety net; the main defence is not logging PII.
 *
 * Patterns avoid inline flags, lookbehind and named groups so they behave the same on JVM, JS
 * and wasm.
 */
object Redactor {
    private const val MASK = "***"

    private val jwt = Regex("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
    private val bearer = Regex("bearer\\s+[A-Za-z0-9._~+/=-]+", RegexOption.IGNORE_CASE)
    private val keyValue = Regex(
        "\\b(access_?token|refresh_?token|token|password|passwd|parola|secret)" +
            "(\"?\\s*[:=]\\s*\"?)[^\\s\",&;]+",
        RegexOption.IGNORE_CASE
    )
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    fun redact(text: String): String {
        var result = jwt.replace(text, MASK)
        result = bearer.replace(result, "Bearer $MASK")
        result = keyValue.replace(result) { it.groupValues[1] + it.groupValues[2] + MASK }
        return email.replace(result, MASK)
    }
}
