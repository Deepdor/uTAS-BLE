package com.example.minimalnfc

class TagDataParser {
    // Method to parse temperature from block data
    fun parseTemperature(block: ByteArray): Double {
        val wholeDegrees = block[0].toInt() and 0xFF // byte 0
        val hundredths = block[1].toInt() and 0xFF   // byte 1
        return wholeDegrees + (hundredths / 100.0)
    }

    // Method to parse humidity from block data
    fun parseHumidity(block: ByteArray): Double {
        val wholePercentage = block[2].toInt() and 0xFF // byte 2
        val hundredths = block[3].toInt() and 0xFF      // byte 3
        return wholePercentage + (hundredths / 100.0)
    }
}

