package cz.misa.quakedeck.data

/** A lightweight geographic label rendered over the seismic map. */
data class MapLabel(
    val english: String,
    val japanese: String,
    val latitude: Double,
    val longitude: Double,
    val major: Boolean = false
)

/**
 * Prefectural capitals plus a small set of country-scale anchor cities.
 * Coordinates are deliberately city-centre level: these are orientation labels,
 * not seismic observation coordinates.
 */
object MapLabels {
    val capitals: List<MapLabel> = listOf(
        MapLabel("Sapporo", "札幌", 43.0618, 141.3545, true),
        MapLabel("Aomori", "青森", 40.8244, 140.7400),
        MapLabel("Morioka", "盛岡", 39.7036, 141.1527),
        MapLabel("Sendai", "仙台", 38.2682, 140.8694, true),
        MapLabel("Akita", "秋田", 39.7199, 140.1026),
        MapLabel("Yamagata", "山形", 38.2404, 140.3633),
        MapLabel("Fukushima", "福島", 37.7608, 140.4747),
        MapLabel("Mito", "水戸", 36.3659, 140.4714),
        MapLabel("Utsunomiya", "宇都宮", 36.5551, 139.8828),
        MapLabel("Maebashi", "前橋", 36.3895, 139.0634),
        MapLabel("Saitama", "さいたま", 35.8617, 139.6455),
        MapLabel("Chiba", "千葉", 35.6073, 140.1063),
        MapLabel("Tokyo", "東京", 35.6762, 139.6503, true),
        MapLabel("Yokohama", "横浜", 35.4437, 139.6380),
        MapLabel("Niigata", "新潟", 37.9161, 139.0364),
        MapLabel("Toyama", "富山", 36.6953, 137.2113),
        MapLabel("Kanazawa", "金沢", 36.5613, 136.6562),
        MapLabel("Fukui", "福井", 36.0641, 136.2196),
        MapLabel("Kofu", "甲府", 35.6639, 138.5683),
        MapLabel("Nagano", "長野", 36.6486, 138.1948),
        MapLabel("Gifu", "岐阜", 35.4233, 136.7607),
        MapLabel("Shizuoka", "静岡", 34.9756, 138.3828),
        MapLabel("Nagoya", "名古屋", 35.1815, 136.9066, true),
        MapLabel("Tsu", "津", 34.7303, 136.5086),
        MapLabel("Otsu", "大津", 35.0179, 135.8546),
        MapLabel("Kyoto", "京都", 35.0116, 135.7681),
        MapLabel("Osaka", "大阪", 34.6937, 135.5023, true),
        MapLabel("Kobe", "神戸", 34.6901, 135.1955),
        MapLabel("Nara", "奈良", 34.6851, 135.8048),
        MapLabel("Wakayama", "和歌山", 34.2305, 135.1708),
        MapLabel("Tottori", "鳥取", 35.5011, 134.2351),
        MapLabel("Matsue", "松江", 35.4723, 133.0505),
        MapLabel("Okayama", "岡山", 34.6551, 133.9195),
        MapLabel("Hiroshima", "広島", 34.3853, 132.4553, true),
        MapLabel("Yamaguchi", "山口", 34.1858, 131.4714),
        MapLabel("Tokushima", "徳島", 34.0703, 134.5548),
        MapLabel("Takamatsu", "高松", 34.3428, 134.0466),
        MapLabel("Matsuyama", "松山", 33.8392, 132.7657),
        MapLabel("Kochi", "高知", 33.5597, 133.5311),
        MapLabel("Fukuoka", "福岡", 33.5904, 130.4017, true),
        MapLabel("Saga", "佐賀", 33.2494, 130.2988),
        MapLabel("Nagasaki", "長崎", 32.7503, 129.8777),
        MapLabel("Kumamoto", "熊本", 32.8031, 130.7079),
        MapLabel("Oita", "大分", 33.2396, 131.6093),
        MapLabel("Miyazaki", "宮崎", 31.9077, 131.4202),
        MapLabel("Kagoshima", "鹿児島", 31.5966, 130.5571, true),
        MapLabel("Naha", "那覇", 26.2124, 127.6809, true)
    )
}
