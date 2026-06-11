package com.mipuble.domain.model

/**
 * A user-defined shelf. [colorArgb] is a packed ARGB int — kept primitive so
 * the domain stays free of Android/Compose color types.
 */
data class Category(
    val id: Long,
    val name: String,
    val colorArgb: Int,
)
