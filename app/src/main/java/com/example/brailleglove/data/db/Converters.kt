package com.example.brailleglove.data.db

import androidx.room.TypeConverter

/**
 * Room TypeConverters for non-primitive types.
 */
class Converters {

    // Char ↔ String (Room doesn't natively support Char)
    @TypeConverter
    fun charToString(value: Char): String = value.toString()

    @TypeConverter
    fun stringToChar(value: String): Char = value.firstOrNull() ?: ' '

    // BooleanArray ↔ String (compact bit string, e.g. "101010")
    @TypeConverter
    fun booleanArrayToString(value: BooleanArray): String =
        value.joinToString("") { if (it) "1" else "0" }

    @TypeConverter
    fun stringToBooleanArray(value: String): BooleanArray =
        BooleanArray(6) { i -> value.getOrNull(i) == '1' }
}