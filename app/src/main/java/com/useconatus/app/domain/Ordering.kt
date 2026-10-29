package com.useconatus.app.domain

// A port of the fractional-indexing package the server orders projects, sections and tasks with
// (default alphabet only), so the phone can place things offline exactly where the server would.
// Keys sort as plain strings: "a0" < "a0V" < "a1" < "b00".

private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
private const val INT_DIGITS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
private const val SMALLEST = "A00000000000000000000000000"

private fun digit(c: Char) = DIGITS.indexOf(c)

private fun midpoint(a: String, b: String?): String {
    require(b == null || a < b) { "$a >= $b" }
    require(!a.endsWith('0') && (b == null || !b.endsWith('0'))) { "trailing zero" }
    if (b != null) {
        // Drop the common prefix, padding a with zeros as we go.
        var n = 0
        while ((a.getOrNull(n) ?: '0') == b[n]) n++
        if (n > 0) return b.take(n) + midpoint(a.drop(n), b.drop(n))
    }
    val digitA = if (a.isEmpty()) 0 else digit(a[0])
    val digitB = if (b != null) digit(b[0]) else DIGITS.length
    if (digitB - digitA > 1) return DIGITS[Math.round(0.5 * (digitA + digitB)).toInt()].toString()
    return if (b != null && b.length > 1) b.take(1) else DIGITS[digitA] + midpoint(a.drop(1), null)
}

private fun integerLength(head: Char): Int {
    val i = INT_DIGITS.indexOf(head)
    require(i >= 0) { "invalid order key head: $head" }
    val half = INT_DIGITS.length / 2
    return if (i < half) half - i + 1 else i - half + 2
}

private fun integerPart(key: String): String {
    val length = integerLength(key[0])
    require(length <= key.length) { "invalid order key: $key" }
    return key.take(length)
}

private fun validate(key: String) {
    require(key != SMALLEST) { "invalid order key: $key" }
    val int = integerPart(key)
    require(!key.drop(int.length).endsWith('0')) { "invalid order key: $key" }
}

private fun incrementInteger(x: String): String? {
    require(x.length == integerLength(x[0])) { "invalid integer part of order key: $x" }
    var trailing = ""
    for (i in x.length - 1 downTo 1) {
        val d = digit(x[i]) + 1
        if (d == DIGITS.length) trailing = "0$trailing" else return x[0] + x.substring(1, i) + DIGITS[d] + trailing
    }
    val headIndex = INT_DIGITS.indexOf(x[0])
    if (headIndex == INT_DIGITS.length - 1) return null
    val h = INT_DIGITS[headIndex + 1]
    val delta = integerLength(h) - integerLength(x[0])
    return h + when {
        delta > 0 -> trailing + "0"
        delta < 0 -> trailing.drop(1)
        else -> trailing
    }
}

private fun decrementInteger(x: String): String? {
    require(x.length == integerLength(x[0])) { "invalid integer part of order key: $x" }
    var trailing = ""
    for (i in x.length - 1 downTo 1) {
        val d = digit(x[i]) - 1
        if (d == -1) trailing = "z$trailing" else return x[0] + x.substring(1, i) + DIGITS[d] + trailing
    }
    val headIndex = INT_DIGITS.indexOf(x[0])
    if (headIndex == 0) return null
    val h = INT_DIGITS[headIndex - 1]
    val delta = integerLength(h) - integerLength(x[0])
    return h + when {
        delta > 0 -> trailing + "z"
        delta < 0 -> trailing.drop(1)
        else -> trailing
    }
}

/** A key that sorts between [lower] and [upper]; null means the start or the end. */
fun generateKeyBetween(lower: String?, upper: String?): String {
    lower?.let(::validate)
    upper?.let(::validate)
    var a = lower
    var b = upper
    if (a != null && b != null && a > b) a = b.also { b = a }
    if (a == null) {
        if (b == null) return "a0"
        val ib = integerPart(b)
        val fb = b.drop(ib.length)
        if (ib == SMALLEST) return ib + midpoint("", fb)
        if (ib < b) return ib
        return decrementInteger(ib) ?: throw IllegalStateException("cannot decrement any more")
    }
    val ia = integerPart(a)
    val fa = a.drop(ia.length)
    if (b == null) return incrementInteger(ia) ?: (ia + midpoint(fa, null))
    val ib = integerPart(b)
    if (ia == ib) return ia + midpoint(fa, b.drop(ib.length))
    val i = incrementInteger(ia) ?: throw IllegalStateException("cannot increment any more")
    return if (i < b) i else ia + midpoint(fa, null)
}

private fun isKey(key: String?) = key != null && runCatching { validate(key) }.isSuccess

/**
 * The key for a place between two neighbours as the phone has them. A neighbour with no valid
 * key (a task made before keys existed on the phone) counts as the start or the end.
 */
fun keyBetween(lower: String?, upper: String?): String {
    val a = lower.takeIf(::isKey)
    val b = upper.takeIf(::isKey)
    return runCatching { generateKeyBetween(a, b) }.getOrElse { generateKeyBetween(a, null) }
}

/** The key after the last of [keys]. */
fun keyAfter(keys: Collection<String>): String = keyBetween(keys.filter(::isKey).maxOrNull(), null)
