package com.mipuble.domain.util

/**
 * Returns a copy of the list with the element at [from] moved to [to],
 * shifting everything between — the semantics a drag-and-drop reorder needs
 * (not a swap). Out-of-range indices return the list unchanged.
 */
fun <T> List<T>.move(from: Int, to: Int): List<T> {
    if (from == to) return this
    if (from !in indices || to !in indices) return this
    val result = toMutableList()
    val item = result.removeAt(from)
    result.add(to, item)
    return result
}
