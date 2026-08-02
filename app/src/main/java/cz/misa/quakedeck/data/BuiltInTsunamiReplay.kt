package cz.misa.quakedeck.data

/**
 * Deterministic offline tsunami integration fixture based on the 1 January 2024
 * Noto Peninsula earthquake.
 *
 * The earthquake parameters and warning progression are based on JMA's
 * published event record. The P2PQuake JSON envelopes are reconstructed because
 * the public API does not expose an event-selectable raw-frame archive. Every
 * packet still enters P2pQuakeProvider through the same code-551/code-552 parser
 * used by the production WebSocket.
 */
object BuiltInTsunamiReplay {
    const val DISPLAY_NAME = "1 January 2024 Noto tsunami"
    const val DEFAULT_START_DELAY_MILLIS = 5_000L

    data class Packet(
        val offsetMillis: Long,
        val json: String
    )

    val packets: List<Packet> = listOf(
        Packet(0L, confirmedQuakePacket()),
        Packet(
            1_800L,
            tsunamiPacket(
                id = "builtin-noto-tsunami-20240101-initial",
                issueTime = "2024/01/01 16:12:00.000",
                issueType = "TsunamiWarning",
                areas = initialAreas()
            )
        ),
        Packet(
            15_000L,
            tsunamiPacket(
                id = "builtin-noto-tsunami-20240101-update",
                issueTime = "2024/01/01 16:22:00.000",
                issueType = "TsunamiWarningUpdate",
                areas = arrivalUpdateAreas()
            )
        ),
        Packet(
            35_000L,
            tsunamiPacket(
                id = "builtin-noto-tsunami-20240101-downgrade",
                issueTime = "2024/01/01 20:30:00.000",
                issueType = "TsunamiWarningUpdate",
                areas = downgradedAreas()
            )
        ),
        Packet(
            55_000L,
            json = """
                {
                  "id":"builtin-noto-tsunami-20240101-cancel",
                  "code":552,
                  "time":"2024/01/02 10:00:00.000",
                  "expire":"2024/01/02 11:00:00.000",
                  "cancelled":true,
                  "issue":{
                    "source":"気象庁",
                    "time":"2024/01/02 10:00:00.000",
                    "type":"TsunamiWarningCancellation"
                  },
                  "areas":[]
                }
            """.trimIndent()
        )
    )

    const val COMPLETE_AFTER_MILLIS = 70_000L

    private fun tsunamiPacket(
        id: String,
        issueTime: String,
        issueType: String,
        areas: String
    ): String = """
        {
          "id":"$id",
          "code":552,
          "time":"$issueTime",
          "expire":"2024/01/02 11:00:00.000",
          "cancelled":false,
          "issue":{
            "source":"気象庁",
            "time":"$issueTime",
            "type":"$issueType"
          },
          "areas":$areas
        }
    """.trimIndent()

    private fun initialAreas(): String = """
        [
          {
            "name":"石川県能登",
            "grade":"Warning",
            "immediate":true,
            "firstHeight":{"condition":"津波到達中と推測"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"山形県",
            "grade":"Watch",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 17:00:00.000"},
            "maxHeight":{"description":"1m","value":1.0}
          },
          {
            "name":"新潟県上中下越",
            "grade":"Warning",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 16:50:00.000"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"佐渡",
            "grade":"Warning",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 16:30:00.000"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"富山県",
            "grade":"Warning",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 16:30:00.000"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"石川県加賀",
            "grade":"Warning",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 16:40:00.000"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"福井県",
            "grade":"Watch",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 17:00:00.000"},
            "maxHeight":{"description":"1m","value":1.0}
          },
          {
            "name":"北海道日本海沿岸南部",
            "grade":"Watch",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 18:00:00.000"},
            "maxHeight":{"description":"1m","value":1.0}
          },
          {
            "name":"青森県日本海沿岸",
            "grade":"Watch",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 18:00:00.000"},
            "maxHeight":{"description":"1m","value":1.0}
          },
          {
            "name":"京都府",
            "grade":"Watch",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 17:30:00.000"},
            "maxHeight":{"description":"1m","value":1.0}
          },
          {
            "name":"兵庫県北部",
            "grade":"Watch",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 17:30:00.000"},
            "maxHeight":{"description":"1m","value":1.0}
          },
          {
            "name":"鳥取県",
            "grade":"Watch",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 18:00:00.000"},
            "maxHeight":{"description":"1m","value":1.0}
          }
        ]
    """.trimIndent()

    private fun arrivalUpdateAreas(): String = """
        [
          {
            "name":"石川県能登",
            "grade":"MajorWarning",
            "immediate":true,
            "firstHeight":{"condition":"第１波の到達を確認"},
            "maxHeight":{"description":"5m","value":5.0}
          },
          {
            "name":"山形県",
            "grade":"Warning",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 17:00:00.000"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"新潟県上中下越",
            "grade":"Warning",
            "immediate":true,
            "firstHeight":{"condition":"津波到達中と推測"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"佐渡",
            "grade":"Warning",
            "immediate":true,
            "firstHeight":{"condition":"津波到達中と推測"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"富山県",
            "grade":"Warning",
            "immediate":true,
            "firstHeight":{"condition":"津波到達中と推測"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"石川県加賀",
            "grade":"Warning",
            "immediate":true,
            "firstHeight":{"condition":"津波到達中と推測"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"福井県",
            "grade":"Warning",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 17:00:00.000"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"北海道日本海沿岸南部",
            "grade":"Watch",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 18:00:00.000"},
            "maxHeight":{"description":"1m","value":1.0}
          },
          {
            "name":"青森県日本海沿岸",
            "grade":"Watch",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 18:00:00.000"},
            "maxHeight":{"description":"1m","value":1.0}
          },
          {
            "name":"京都府",
            "grade":"Watch",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 17:30:00.000"},
            "maxHeight":{"description":"1m","value":1.0}
          },
          {
            "name":"兵庫県北部",
            "grade":"Warning",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 17:30:00.000"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"鳥取県",
            "grade":"Watch",
            "immediate":false,
            "firstHeight":{"arrivalTime":"2024/01/01 18:00:00.000"},
            "maxHeight":{"description":"1m","value":1.0}
          }
        ]
    """.trimIndent()

    private fun downgradedAreas(): String = """
        [
          {
            "name":"石川県能登",
            "grade":"Warning",
            "immediate":true,
            "firstHeight":{"condition":"第１波の到達を確認"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"山形県",
            "grade":"Warning",
            "immediate":true,
            "firstHeight":{"condition":"第１波の到達を確認"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"新潟県上中下越",
            "grade":"Warning",
            "immediate":true,
            "firstHeight":{"condition":"第１波の到達を確認"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"佐渡",
            "grade":"Warning",
            "immediate":true,
            "firstHeight":{"condition":"第１波の到達を確認"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"富山県",
            "grade":"Warning",
            "immediate":true,
            "firstHeight":{"condition":"第１波の到達を確認"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"石川県加賀",
            "grade":"Warning",
            "immediate":true,
            "firstHeight":{"condition":"第１波の到達を確認"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"福井県",
            "grade":"Warning",
            "immediate":true,
            "firstHeight":{"condition":"第１波の到達を確認"},
            "maxHeight":{"description":"3m","value":3.0}
          },
          {
            "name":"兵庫県北部",
            "grade":"Warning",
            "immediate":true,
            "firstHeight":{"condition":"第１波の到達を確認"},
            "maxHeight":{"description":"3m","value":3.0}
}
        ]
    """.trimIndent()

    private fun confirmedQuakePacket(): String = """
        {
          "id":"builtin-noto-20240101-confirmed",
          "code":551,
          "time":"2024/01/01 16:24:00.000",
          "issue":{
            "source":"気象庁",
            "time":"2024/01/01 16:24:00.000",
            "type":"DetailScale"
          },
          "earthquake":{
            "time":"2024/01/01 16:10:22.500",
            "hypocenter":{
              "name":"石川県能登地方",
              "latitude":37.495,
              "longitude":137.270,
              "depth":16,
              "magnitude":7.6
            },
            "maxScale":70,
            "domesticTsunami":"Warning"
          },
          "points":[
            {"pref":"石川県","addr":"輪島市門前町走出","scale":70,"isArea":false},
            {"pref":"石川県","addr":"羽咋郡志賀町香能","scale":60,"isArea":false},
            {"pref":"石川県","addr":"七尾市垣吉町","scale":60,"isArea":false},
            {"pref":"新潟県","addr":"長岡市中之島","scale":50,"isArea":false},
            {"pref":"富山県","addr":"富山市新桜町","scale":50,"isArea":false}
          ]
        }
    """.trimIndent()
}
