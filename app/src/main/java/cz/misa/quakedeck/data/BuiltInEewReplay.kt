package cz.misa.quakedeck.data

/**
 * Deterministic, offline EEW integration fixture based on the 5 May 2023
 * Noto Peninsula earthquake.
 *
 * The hypocentre/report progression follows JMA's published EEW revisions.
 * The P2PQuake JSON envelopes and forecast-area list are reconstructed for app
 * testing because the official rotating sandbox does not expose an event
 * selector or archived raw-frame download. This is a UI/integration fixture,
 * not an evidentiary copy of the original P2PQuake packets.
 */
object BuiltInEewReplay {
    const val DEFAULT_START_DELAY_MILLIS = 5_000L

    data class Packet(
        val offsetMillis: Long,
        val json: String
    )

    /**
     * Reports are intentionally compressed after the initial five-second arm
     * delay. Their embedded historical timestamps remain authentic to the JMA
     * report progression and are rebased by P2pQuakeProvider onto the live clock.
     */
    val packets: List<Packet> = listOf(
        Packet(
            offsetMillis = 0L,
            json = """
                {
                  "id":"builtin-noto-20230505-detection",
                  "code":554,
                  "time":"2023/05/05 14:42:13.900",
                  "type":"緊急地震速報（警報）"
                }
            """.trimIndent()
        ),
        Packet(
            offsetMillis = 800L,
            json = eewPacket(
                id = "builtin-noto-20230505-r07",
                serial = 7,
                issueTime = "2023/05/05 14:42:14.200",
                place = "石川県能登地方",
                latitude = 37.4,
                longitude = 137.2,
                depthKm = 10,
                magnitude = 7.0,
                areas = strongWarningAreas()
            )
        ),
        Packet(
            offsetMillis = 3_200L,
            json = eewPacket(
                id = "builtin-noto-20230505-r10",
                serial = 10,
                issueTime = "2023/05/05 14:42:17.400",
                place = "能登半島沖",
                latitude = 37.6,
                longitude = 137.3,
                depthKm = 10,
                magnitude = 7.0,
                areas = strongWarningAreas()
            )
        ),
        Packet(
            offsetMillis = 5_400L,
            json = eewPacket(
                id = "builtin-noto-20230505-r11",
                serial = 11,
                issueTime = "2023/05/05 14:42:19.600",
                place = "能登半島沖",
                latitude = 37.6,
                longitude = 137.3,
                depthKm = 20,
                magnitude = 7.0,
                areas = strongWarningAreas()
            )
        ),
        Packet(
            offsetMillis = 10_000L,
            json = eewPacket(
                id = "builtin-noto-20230505-r16",
                serial = 16,
                issueTime = "2023/05/05 14:42:26.200",
                place = "石川県能登地方",
                latitude = 37.5,
                longitude = 137.3,
                depthKm = 10,
                magnitude = 7.0,
                areas = strongWarningAreas()
            )
        ),
        Packet(
            offsetMillis = 16_000L,
            json = eewPacket(
                id = "builtin-noto-20230505-r20",
                serial = 20,
                issueTime = "2023/05/05 14:42:50.500",
                place = "石川県能登地方",
                latitude = 37.5,
                longitude = 137.3,
                depthKm = 10,
                magnitude = 6.9,
                areas = strongWarningAreas()
            )
        ),
        Packet(
            offsetMillis = 22_000L,
            json = eewPacket(
                id = "builtin-noto-20230505-r26",
                serial = 26,
                issueTime = "2023/05/05 14:44:04.700",
                place = "石川県能登地方",
                latitude = 37.5,
                longitude = 137.3,
                depthKm = 10,
                magnitude = 6.8,
                areas = strongWarningAreas()
            )
        ),
        // The warning-area arrivals end before the confirmed detail report.
        // This deliberately exercises QuakeDeck's estimated EEW-end transition
        // and the later camera refit when observed station areas arrive.
        Packet(
            offsetMillis = 72_000L,
            json = confirmedQuakePacket()
        )
    )

    /** Reconnect the rotating official sandbox shortly after confirmation. */
    const val COMPLETE_AFTER_MILLIS = 75_000L

    private fun eewPacket(
        id: String,
        serial: Int,
        issueTime: String,
        place: String,
        latitude: Double,
        longitude: Double,
        depthKm: Int,
        magnitude: Double,
        areas: String
    ): String = """
        {
          "id":"$id",
          "code":556,
          "time":"$issueTime",
          "cancelled":false,
          "issue":{
            "time":"$issueTime",
            "eventId":"20230505144204",
            "serial":"$serial"
          },
          "earthquake":{
            "originTime":"2023/05/05 14:42:04.100",
            "arrivalTime":"2023/05/05 14:42:06.900",
            "condition":"",
            "hypocenter":{
              "name":"$place",
              "reduceName":"能登",
              "latitude":$latitude,
              "longitude":$longitude,
              "depth":$depthKm,
              "magnitude":$magnitude
            }
          },
          "areas":$areas
        }
    """.trimIndent()

    private fun strongWarningAreas(): String = """
        [
          {
            "pref":"石川県",
            "name":"石川県能登",
            "scaleFrom":60,
            "scaleTo":99,
            "kindCode":"19",
            "arrivalTime":"2023/05/05 14:42:18.000"
          },
          {
            "pref":"富山県",
            "name":"富山県西部",
            "scaleFrom":45,
            "scaleTo":50,
            "kindCode":"19",
            "arrivalTime":"2023/05/05 14:42:29.000"
          },
          {
            "pref":"富山県",
            "name":"富山県東部",
            "scaleFrom":40,
            "scaleTo":45,
            "kindCode":"19",
            "arrivalTime":"2023/05/05 14:42:35.000"
          },
          {
            "pref":"新潟県",
            "name":"新潟県上越",
            "scaleFrom":40,
            "scaleTo":45,
            "kindCode":"19",
            "arrivalTime":"2023/05/05 14:42:38.000"
          },
          {
            "pref":"石川県",
            "name":"石川県加賀",
            "scaleFrom":40,
            "scaleTo":45,
            "kindCode":"19",
            "arrivalTime":"2023/05/05 14:42:40.000"
          },
          {
            "pref":"長野県",
            "name":"長野県北部",
            "scaleFrom":40,
            "scaleTo":45,
            "kindCode":"19",
            "arrivalTime":"2023/05/05 14:42:48.000"
          }
        ]
    """.trimIndent()

    private fun confirmedQuakePacket(): String = """
        {
          "id":"builtin-noto-20230505-confirmed",
          "code":551,
          "time":"2023/05/05 14:44:20.000",
          "issue":{
            "source":"気象庁",
            "time":"2023/05/05 14:44:20.000",
            "type":"DetailScale"
          },
          "earthquake":{
            "time":"2023/05/05 14:42:04.100",
            "hypocenter":{
              "name":"能登半島沖",
              "latitude":37.538,
              "longitude":137.303,
              "depth":12,
              "magnitude":6.5
            },
            "maxScale":60,
            "domesticTsunami":"Checking"
          },
          "points":[
            {"pref":"石川県","addr":"珠洲市正院町","scale":60,"isArea":false},
            {"pref":"石川県","addr":"珠洲市三崎町","scale":50,"isArea":false},
            {"pref":"石川県","addr":"能登町松波","scale":50,"isArea":false},
            {"pref":"石川県","addr":"輪島市鳳至町","scale":45,"isArea":false},
            {"pref":"富山県","addr":"舟橋村仏生寺","scale":40,"isArea":false}
          ],
          "comments":{"freeFormComment":"Built-in deterministic replay fixture."}
        }
    """.trimIndent()
}
