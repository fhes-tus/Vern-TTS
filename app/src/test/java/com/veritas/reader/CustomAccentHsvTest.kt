package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class CustomAccentHsvTest {
    @Test fun hueStripMatchesPrimaryAndSecondaryColoursAndWrapsAtRed() {
        val expected = listOf("#FF0000", "#FFFF00", "#00FF00", "#00FFFF", "#0000FF", "#FF00FF", "#FF0000")
        expected.forEachIndexed { index, hex -> assertEquals(hex, AccentHsv(index * 60f, 1f, 1f).toHex()) }
    }

    @Test fun fieldCornersAreWhiteHueAndBlackWithClampedEdges() {
        val blue = AccentHsv(240f, .5f, .5f)
        assertEquals("#FFFFFF", blue.atFieldPosition(0f, 0f).toHex())
        assertEquals("#0000FF", blue.atFieldPosition(1f, 0f).toHex())
        assertEquals("#000000", blue.atFieldPosition(0f, 1f).toHex())
        assertEquals("#000000", blue.atFieldPosition(1f, 1f).toHex())
        assertEquals("#0000FF", blue.atFieldPosition(2f, -1f).toHex())
    }

    @Test fun rgbValuesRoundTripWithoutChangingTheChosenColour() {
        for (hex in listOf("#12ABEF", "#FE0100", "#010203", "#808080", "#FFFFFF", "#000000", "#43A047")) {
            assertEquals(hex, AccentHsv.fromHex(hex.lowercase())!!.toHex())
        }
        assertNull(AccentHsv.fromHex("#bad"))
        assertNull(AccentHsv.fromHex("#12345678"))
    }

    @Test fun achromaticColoursRetainTheHueForLaterFieldSelection() {
        for (hex in listOf("#000000", "#808080", "#FFFFFF")) {
            val colour = AccentHsv.fromHex(hex, 240f)!!
            assertEquals(240f, colour.hue, 0f)
            assertEquals(0f, colour.saturation, 0f)
            assertEquals("#0000FF", colour.atFieldPosition(1f, 0f).toHex())
        }
    }

    @Test fun nonFiniteCoordinatesAlwaysProduceAnOpaqueValidColour() {
        val colour = AccentHsv(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        assertEquals("#000000", colour.toHex())
        assertEquals(255, colour.toArgb() ushr 24)
        assertEquals("#FFFFFF", AccentHsv(-1f, -1f, 2f).toHex())
        assertEquals("#000000", colour.atFieldPosition(Float.NaN, Float.NaN).toHex())
    }

    @Test fun accessibilityNamesDescribeColoursWithoutExposingCodes() {
        assertEquals("Blue", AccentHsv(240f, 1f, 1f).colourName())
        assertEquals("Black", AccentHsv(240f, 1f, 0f).colourName())
        assertEquals("White", AccentHsv(0f, 0f, 1f).colourName())
        assertEquals("Grey", AccentHsv(0f, 0f, .6f).colourName())
        assertEquals("Dark red", AccentHsv(0f, 1f, .3f).colourName())
    }
}
