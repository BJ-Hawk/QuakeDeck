package cz.misa.quakedeck.data

/** JMA tsunami forecast-area labels used by P2PQuake code 552. */
object TsunamiAreaCatalog {
    private data class Entry(
        val english: String,
        val prefectures: List<String>
    )

    private val entries = mapOf(
        "北海道太平洋沿岸東部" to Entry("Eastern Pacific coast of Hokkaido", listOf("北海道")),
        "北海道太平洋沿岸中部" to Entry("Central Pacific coast of Hokkaido", listOf("北海道")),
        "北海道太平洋沿岸西部" to Entry("Western Pacific coast of Hokkaido", listOf("北海道")),
        "北海道日本海沿岸北部" to Entry("Northern Sea of Japan coast of Hokkaido", listOf("北海道")),
        "北海道日本海沿岸南部" to Entry("Southern Sea of Japan coast of Hokkaido", listOf("北海道")),
        "オホーツク海沿岸" to Entry("Okhotsk Sea coast", listOf("北海道")),
        "青森県日本海沿岸" to Entry("Sea of Japan coast of Aomori", listOf("青森県")),
        "青森県太平洋沿岸" to Entry("Pacific coast of Aomori", listOf("青森県")),
        "陸奥湾" to Entry("Mutsu Bay", listOf("青森県")),
        "岩手県" to Entry("Iwate", listOf("岩手県")),
        "宮城県" to Entry("Miyagi", listOf("宮城県")),
        "福島県" to Entry("Fukushima", listOf("福島県")),
        "秋田県" to Entry("Akita", listOf("秋田県")),
        "山形県" to Entry("Yamagata", listOf("山形県")),
        "茨城県" to Entry("Ibaraki", listOf("茨城県")),
        "千葉県九十九里・外房" to Entry("Kujukuri and outer Boso coast", listOf("千葉県")),
        "千葉県内房" to Entry("Inner Boso coast", listOf("千葉県")),
        "東京湾内湾" to Entry("Inner Tokyo Bay", listOf("東京都", "神奈川県", "千葉県")),
        "伊豆諸島" to Entry("Izu Islands", listOf("東京都")),
        "小笠原諸島" to Entry("Ogasawara Islands", listOf("東京都")),
        "相模湾・三浦半島" to Entry("Sagami Bay and Miura Peninsula", listOf("神奈川県")),
        "新潟県上中下越" to Entry("Joetsu, Chuetsu and Kaetsu coasts of Niigata", listOf("新潟県")),
        "佐渡" to Entry("Sado", listOf("新潟県")),
        "富山県" to Entry("Toyama", listOf("富山県")),
        "石川県能登" to Entry("Noto coast of Ishikawa", listOf("石川県")),
        "石川県加賀" to Entry("Kaga coast of Ishikawa", listOf("石川県")),
        "福井県" to Entry("Fukui", listOf("福井県")),
        "静岡県" to Entry("Shizuoka", listOf("静岡県")),
        "愛知県外海" to Entry("Outer coast of Aichi", listOf("愛知県")),
        "伊勢・三河湾" to Entry("Ise and Mikawa Bays", listOf("愛知県", "三重県")),
        "三重県南部" to Entry("Southern Mie", listOf("三重県")),
        "大阪府" to Entry("Osaka", listOf("大阪府")),
        "和歌山県" to Entry("Wakayama", listOf("和歌山県")),
        "兵庫県瀬戸内海沿岸" to Entry("Seto Inland Sea coast of Hyogo", listOf("兵庫県")),
        "淡路島南部" to Entry("Southern Awaji Island", listOf("兵庫県")),
        "兵庫県北部" to Entry("Northern Hyogo", listOf("兵庫県")),
        "京都府" to Entry("Kyoto", listOf("京都府")),
        "鳥取県" to Entry("Tottori", listOf("鳥取県")),
        "島根県出雲・石見" to Entry("Izumo and Iwami coasts of Shimane", listOf("島根県")),
        "隠岐" to Entry("Oki Islands", listOf("島根県")),
        "岡山県" to Entry("Okayama", listOf("岡山県")),
        "広島県" to Entry("Hiroshima", listOf("広島県")),
        "徳島県" to Entry("Tokushima", listOf("徳島県")),
        "香川県" to Entry("Kagawa", listOf("香川県")),
        "愛媛県宇和海沿岸" to Entry("Uwa Sea coast of Ehime", listOf("愛媛県")),
        "愛媛県瀬戸内海沿岸" to Entry("Seto Inland Sea coast of Ehime", listOf("愛媛県")),
        "高知県" to Entry("Kochi", listOf("高知県")),
        "山口県日本海沿岸" to Entry("Sea of Japan coast of Yamaguchi", listOf("山口県")),
        "山口県瀬戸内海沿岸" to Entry("Seto Inland Sea coast of Yamaguchi", listOf("山口県")),
        "福岡県瀬戸内海沿岸" to Entry("Seto Inland Sea coast of Fukuoka", listOf("福岡県")),
        "福岡県日本海沿岸" to Entry("Sea of Japan coast of Fukuoka", listOf("福岡県")),
        "佐賀県北部" to Entry("Northern Saga", listOf("佐賀県")),
        "壱岐・対馬" to Entry("Iki and Tsushima", listOf("長崎県")),
        "有明・八代海" to Entry("Ariake and Yatsushiro Seas", listOf("福岡県", "佐賀県", "長崎県", "熊本県")),
        "長崎県西方" to Entry("Western Nagasaki", listOf("長崎県")),
        "熊本県天草灘沿岸" to Entry("Amakusa-nada coast of Kumamoto", listOf("熊本県")),
        "大分県瀬戸内海沿岸" to Entry("Seto Inland Sea coast of Oita", listOf("大分県")),
        "大分県豊後水道沿岸" to Entry("Bungo Channel coast of Oita", listOf("大分県")),
        "宮崎県" to Entry("Miyazaki", listOf("宮崎県")),
        "鹿児島県東部" to Entry("Eastern Kagoshima", listOf("鹿児島県")),
        "種子島・屋久島地方" to Entry("Tanegashima and Yakushima", listOf("鹿児島県")),
        "奄美群島・トカラ列島" to Entry("Amami Islands and Tokara Islands", listOf("鹿児島県")),
        "奄美諸島・トカラ列島" to Entry("Amami Islands and Tokara Islands", listOf("鹿児島県")),
        "鹿児島県西部" to Entry("Western Kagoshima", listOf("鹿児島県")),
        "沖縄本島地方" to Entry("Okinawa Main Island region", listOf("沖縄県")),
        "大東島地方" to Entry("Daito Islands region", listOf("沖縄県")),
        "宮古島・八重山地方" to Entry("Miyako and Yaeyama Islands", listOf("沖縄県"))
    )


    /** Official geometry is loaded from the bundled JMA tsunami GIS layer. */

    fun displayName(japanese: String, language: PlaceNameLanguage): String =
        if (PlaceNameTranslator.shouldUseEnglish(language)) {
            entries[japanese]?.english ?: japanese
        } else {
            japanese
        }

    fun prefectures(japanese: String): List<String> = entries[japanese]?.prefectures.orEmpty()
}
