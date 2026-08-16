package cz.misa.quakedeck

import org.junit.Assert.assertEquals
import org.junit.Test

class MapPrefectureMatchingTest {
    private val prefectures = listOf("東京都", "京都府", "神奈川県")

    @Test
    fun tokyoDoesNotAlsoMatchKyoto() {
        assertEquals(
            listOf("東京都"),
            matchMapPrefectures("東京都", prefectures)
        )
    }

    @Test
    fun suffixFreeForecastLabelStillMatchesTokyo() {
        assertEquals(
            listOf("東京都"),
            matchMapPrefectures("東京地方", prefectures)
        )
    }

    @Test
    fun multipleCompletePrefectureNamesRemainSupported() {
        assertEquals(
            listOf("東京都", "神奈川県"),
            matchMapPrefectures("東京都・神奈川県", prefectures)
        )
    }
}
