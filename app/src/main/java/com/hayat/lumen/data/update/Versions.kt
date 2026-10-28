package com.hayat.lumen.data.update

/** Version names like "0.2.0" (or release tags like "v0.2.0"). */
object Versions {
    /** The numbers in a version, so "v1.10.2" → [1, 10, 2]. Null if it isn't a plain dotted version. */
    fun parse(version: String): List<Int>? {
        val parts = version.trim().removePrefix("v").removePrefix("V").split('.')
        return parts.map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }
    }

    /** True if [candidate] is a later version than [current]. Missing parts count as 0 ("1.2" = "1.2.0"). */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
