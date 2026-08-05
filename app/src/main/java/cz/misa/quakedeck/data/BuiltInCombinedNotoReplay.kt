package cz.misa.quakedeck.data

/**
 * Deterministic coexistence fixture based on the 1 January 2024 Noto event.
 *
 * It intentionally overlaps an active code-556 EEW warning with code-552
 * tsunami bulletins so the header, cards, P/S fronts, arrival countdowns and
 * coastline overlay can be exercised together. Historical event parameters and
 * tsunami progression follow the published Noto sequence; the compact P2PQuake
 * envelopes and timing are reconstructed integration-test data.
 */
object BuiltInCombinedNotoReplay {
    const val DEFAULT_START_DELAY_MILLIS = 5_000L

    data class Packet(
        val offsetMillis: Long,
        val json: String
    )

    val packets: List<Packet> = listOf(
        Packet(
            0L,
            """
                {
                  "id":"builtin-noto-20240101-eew-detection",
                  "code":554,
                  "time":"2024/01/01 16:10:29.200",
                  "type":"緊急地震速報（警報）"
                }
            """.trimIndent()
        ),
        Packet(
            800L,
            eewPacket(
                id = "builtin-noto-20240101-eew-r01",
                serial = 1,
                issueTime = "2024/01/01 16:10:30.000",
                latitude = 37.50,
                longitude = 137.25,
                magnitude = 7.2,
                ishikawaScaleFrom = 60
            )
        ),
        Packet(
            4_000L,
            eewPacket(
                id = "builtin-noto-20240101-eew-r04",
                serial = 4,
                issueTime = "2024/01/01 16:10:34.000",
                latitude = 37.50,
                longitude = 137.27,
                magnitude = 7.4,
                ishikawaScaleFrom = 60
            )
        ),
        Packet(
            8_500L,
            eewPacket(
                id = "builtin-noto-20240101-eew-r09",
                serial = 9,
                issueTime = "2024/01/01 16:10:39.000",
                latitude = 37.49,
                longitude = 137.27,
                magnitude = 7.6,
                ishikawaScaleFrom = 70
            )
        ),
        // The first tsunami bulletin arrives while the EEW remains active.
        Packet(14_000L, BuiltInTsunamiReplay.packets[1].json),
        Packet(
            22_000L,
            eewPacket(
                id = "builtin-noto-20240101-eew-r14",
                serial = 14,
                issueTime = "2024/01/01 16:10:52.000",
                latitude = 37.49,
                longitude = 137.27,
                magnitude = 7.6,
                ishikawaScaleFrom = 70
            )
        ),
        Packet(30_000L, BuiltInTsunamiReplay.packets[2].json),
        // Confirmation closes the EEW, while tsunami information remains active.
        Packet(42_000L, BuiltInTsunamiReplay.packets[0].json),
        Packet(60_000L, BuiltInTsunamiReplay.packets[3].json),
        Packet(80_000L, BuiltInTsunamiReplay.packets[4].json)
    )

    const val COMPLETE_AFTER_MILLIS = 92_000L

    private fun eewPacket(
        id: String,
        serial: Int,
        issueTime: String,
        latitude: Double,
        longitude: Double,
        magnitude: Double,
        ishikawaScaleFrom: Int
    ): String = """
        {
          "id":"$id",
          "code":556,
          "time":"$issueTime",
          "cancelled":false,
          "issue":{
            "time":"$issueTime",
            "eventId":"20240101161022",
            "serial":"$serial"
          },
          "earthquake":{
            "originTime":"2024/01/01 16:10:22.500",
            "arrivalTime":"2024/01/01 16:10:24.900",
            "condition":"",
            "hypocenter":{
              "name":"石川県能登地方",
              "reduceName":"能登",
              "latitude":$latitude,
              "longitude":$longitude,
              "depth":10,
              "magnitude":$magnitude
            }
          },
          "areas":[
            {
              "pref":"石川県",
              "name":"石川県能登",
              "scaleFrom":$ishikawaScaleFrom,
              "scaleTo":99,
              "kindCode":"19",
              "arrivalTime":"2024/01/01 16:10:35.000"
            },
            {
              "pref":"石川県",
              "name":"石川県加賀",
              "scaleFrom":50,
              "scaleTo":60,
              "kindCode":"19",
              "arrivalTime":"2024/01/01 16:10:43.000"
            },
            {
              "pref":"富山県",
              "name":"富山県西部",
              "scaleFrom":50,
              "scaleTo":60,
              "kindCode":"19",
              "arrivalTime":"2024/01/01 16:10:45.000"
            },
            {
              "pref":"新潟県",
              "name":"新潟県上越",
              "scaleFrom":45,
              "scaleTo":55,
              "kindCode":"19",
              "arrivalTime":"2024/01/01 16:10:51.000"
            },
            {
              "pref":"長野県",
              "name":"長野県北部",
              "scaleFrom":40,
              "scaleTo":50,
              "kindCode":"19",
              "arrivalTime":"2024/01/01 16:11:02.000"
            },
            {
              "pref":"東京都",
              "name":"東京地方",
              "scaleFrom":20,
              "scaleTo":30,
              "kindCode":"19",
              "arrivalTime":"2024/01/01 16:11:38.000"
            }
          ]
        }
    """.trimIndent()
}
