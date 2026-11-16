package com.libremobile.core.model.common

data class DocumentMetadata(
    val title: String = "Untitled",
    val author: String = "LibreMobile User",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val revision: Int = 1
)

data class Point2D(val x: Float, val y: Float)
data class Size2D(val width: Float, val height: Float)
data class Rect2D(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class ColorRgba(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int = 255
) {
    fun toHex(): String = String.format("#%02X%02X%02X", red, green, blue)
    fun toArgbLong(): Long = ((alpha.toLong() and 0xFF) shl 24) or
            ((red.toLong() and 0xFF) shl 16) or
            ((green.toLong() and 0xFF) shl 8) or
            (blue.toLong() and 0xFF)

    companion object {
        val BLACK = ColorRgba(0, 0, 0)
        val WHITE = ColorRgba(255, 255, 255)
        val TRANSPARENT = ColorRgba(0, 0, 0, 0)
        val GRAY = ColorRgba(128, 128, 128)
        val LIGHT_GRAY = ColorRgba(240, 240, 240)
        val BLUE = ColorRgba(33, 150, 243)
        val RED = ColorRgba(244, 67, 54)
        val GREEN = ColorRgba(76, 175, 80)

        fun fromHex(hex: String): ColorRgba {
            val clean = hex.removePrefix("#")
            return try {
                when (clean.length) {
                    6 -> {
                        val r = clean.substring(0, 2).toInt(16)
                        val g = clean.substring(2, 4).toInt(16)
                        val b = clean.substring(4, 6).toInt(16)
                        ColorRgba(r, g, blue = b)
                    }
                    8 -> {
                        val a = clean.substring(0, 2).toInt(16)
                        val r = clean.substring(2, 4).toInt(16)
                        val g = clean.substring(4, 6).toInt(16)
                        val b = clean.substring(6, 8).toInt(16)
                        ColorRgba(r, g, b, a)
                    }
                    else -> BLACK
                }
            } catch (e: Exception) {
                BLACK
            }
        }
    }
}
