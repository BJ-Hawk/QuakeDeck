package cz.misa.quakedeck.data

/** Two synthetic warnings on one clock, with independent cancellations. */
internal object BuiltInOverlappingEewReplay {
    const val DEFAULT_START_DELAY_MILLIS = BuiltInEewReplay.DEFAULT_START_DELAY_MILLIS
    const val FIRST_TIMESTAMP_JST = "2024-01-02 12:00:10"
    const val COMPLETE_AFTER_MILLIS = 100_000L

    private const val NOTO_ID = "sandbox-overlap-noto"
    private const val SHIZUOKA_ID = "sandbox-overlap-shizuoka"

    val packets = listOf(
        BuiltInEewReplay.Packet(
            0L,
            warning(
                eventId = NOTO_ID,
                issued = "12:00:10",
                origin = "12:00:00",
                place = "石川県能登地方",
                latitude = 37.4,
                longitude = 137.2,
                prefecture = "石川県",
                forecastArea = "石川県能登"
            )
        ),
        BuiltInEewReplay.Packet(
            12_000L,
            warning(
                eventId = SHIZUOKA_ID,
                issued = "12:00:22",
                origin = "12:00:12",
                place = "静岡県東部",
                latitude = 35.3,
                longitude = 139.0,
                prefecture = "静岡県",
                forecastArea = "静岡県東部"
            )
        ),
        BuiltInEewReplay.Packet(75_000L, cancelled(NOTO_ID, "12:01:25")),
        BuiltInEewReplay.Packet(95_000L, cancelled(SHIZUOKA_ID, "12:01:45"))
    )

    private fun warning(
        eventId: String,
        issued: String,
        origin: String,
        place: String,
        latitude: Double,
        longitude: Double,
        prefecture: String,
        forecastArea: String
    ): String = """
        {
          "id": "$eventId-1",
          "code": 556,
          "time": "2024/01/02 $issued",
          "issue": {"time": "2024/01/02 $issued", "eventId": "$eventId", "serial": "1"},
          "earthquake": {
            "originTime": "2024/01/02 $origin",
            "hypocenter": {
              "name": "$place",
              "latitude": $latitude,
              "longitude": $longitude,
              "depth": 10,
              "magnitude": 5.8
            }
          },
          "areas": [{"pref": "$prefecture", "name": "$forecastArea", "scaleFrom": 45, "scaleTo": 45}]
        }
    """.trimIndent()

    private fun cancelled(eventId: String, issued: String): String = """
        {
          "id": "$eventId-2",
          "code": 556,
          "time": "2024/01/02 $issued",
          "cancelled": true,
          "issue": {"time": "2024/01/02 $issued", "eventId": "$eventId", "serial": "2"}
        }
    """.trimIndent()
}
