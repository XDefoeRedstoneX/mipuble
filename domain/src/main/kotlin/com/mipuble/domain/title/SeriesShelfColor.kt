package com.mipuble.domain.title

/**
 * Picks a stable shelf color for an auto-created per-series category. Pure (an
 * ARGB int, no Android color types) so the domain stays platform-free and the
 * mapping is unit-testable. The same series name always yields the same color.
 */
object SeriesShelfColor {

    // A small, legible palette (Material-ish 400s) packed as opaque ARGB.
    private val PALETTE = intArrayOf(
        0xFFEF5350.toInt(), // red
        0xFFAB47BC.toInt(), // purple
        0xFF5C6BC0.toInt(), // indigo
        0xFF42A5F5.toInt(), // blue
        0xFF26A69A.toInt(), // teal
        0xFF66BB6A.toInt(), // green
        0xFFFFCA28.toInt(), // amber
        0xFFFF7043.toInt(), // deep orange
        0xFF8D6E63.toInt(), // brown
        0xFF78909C.toInt(), // blue grey
    )

    fun forName(name: String): Int {
        val index = Math.floorMod(name.lowercase().hashCode(), PALETTE.size)
        return PALETTE[index]
    }
}
