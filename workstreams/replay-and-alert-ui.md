# Replay, map alerts, and Data Sources UI

Begin the September 26 UI backlog in `TBD.md`. This pass is in the permanent local project on `main`, based on `311dae5`. The user asked to consolidate the previous development entries under `v0.10.3` and start this work at `0.10.4-dev.1` (248).

## Implemented

- The History loading indicator stays at the bottom while the list scrolls. The replay header reserves its issue and status rows. History opens on the earliest mappable earthquake or EEW frame and recent-report selections also focus automatically.
- Replay navigation steps through earthquake and EEW frames. Distinct felt reports remain in Associated reports. The replay projection removes repeated and regressive cumulative updates, preserves meaningful changes, and leaves stored packets intact.
- The focused latest report has a Close Report action. Active EEW and tsunami badges sit above the map legend and restore and refocus their own alerts when tapped. Closing active details keeps the badges and live wave or coastline rendering. Dismissal survives revisions of the same EEW or continuous tsunami warning.
- Station address text fits at layout time. The earthquake and station views reserve the same measured height when a station is selected.
- Data Sources uses consistent action and selection rows. Delivery diagnostics displays Connection and Last transport issue. Export is available in both source modes and works on retained packets. Last event uses the most recently received identifiable event bulletin and groups by event ID across sources. For public packets without an event ID, confirmed reports use their origin time and tsunami reports use their bulletin ID. With connection packets selected, Last event adds connection context from its earliest retained matching packet onward. Unidentified event packets remain available in the time ranges and All. The checkbox is off by default.
- The user clarified that the P2PQuake switch must remain available. It is now labelled P2PQuake felt-report counts in English, Czech, and Japanese. The current setting and the live association rules still determine when those counts appear on confirmed reports and early warnings.

## Continue in Android Studio

1. Build and run unit tests, including `DiagnosticExportTest`. Check the export ranges, packet categories, and document picker in FREE and DM-D.S.S modes.
2. Scroll History during catalogue and replay loads. Check missing versus populated issue timestamps, offshore focus, arrow stepping, and direct selection of felt updates that have replay frames.
3. Verify recent and latest report focus and closure. Test EEW and tsunami badge placement, dismissal through revisions, repeat refocus, simultaneous alerts, and map overlays in portrait and landscape.
4. Select a long-address station, a short-address station, then the first again. Toggle earthquake and station details in both directions at standard and larger font sizes.
5. Continue the styling pass in other menus. Prepare the English address format with the station location research, preserving the existing source address and provenance.

Source review, whitespace checks, and resource validation were performed. Android Studio build, unit-test execution, and device validation are pending. This workstream has no new commit.

## November 9 Miyagi follow-up

The `0.10.4-dev.1` FULL export identifies Miyagi offshore as JMA event `20261109051637`. Forecast #5 was issued at 05:17:26 JST and used a 05:16:28 origin. DM-D.S.S received VXSE51 at 05:18:10, VXSE52 at 05:18:43 and VXSE53 at 05:19:51. The official location and detailed bulletins report 05:16:00, 38.3° N, 141.9° E, M4.5, depth 50 km and maximum Shindo 3. The P2PQuake copies followed within about three seconds.

The last accepted forecast set a deadline of 05:20:26 JST. QuakeDeck processed its freshness timeout at 05:20:30.662 and posted the Ended notification immediately afterward. The export records a local timeout and does not establish the cause of the 4.662-second scheduling gap. The existing three-minute window begins at the latest accepted bulletin's issue time.

The user confirmed that the official reports appeared only after the EEW ended, despite checking the open EEW card while the warning was active. The old combined presentation classified the visible card as EEW and omitted the matching confirmed record from Recent Earthquakes while the forecast was active. The EEW card suppressed the confirmed bulletin label, official link and observed-intensity view. Expiry lifted the Recent filter, although the terminal snapshot could briefly put the old Forecast in the latest slot. The follow-up keeps the confirmed record under its canonical identity, preserves the active warning and its forecast origin separately, and retains the confirmed latest record across the end notification. It gives the JMA ID priority over time matching and leaves the deadline policy intact.

This follow-up was implemented in `0.10.4-dev.1a` / 249 on `main`, based on `da4f6a3`. Run the association and runtime routing regression tests in Android Studio. On a device, verify that matching initial, location and detailed reports update the latest card while the forecast remains active, that dismissal leaves confirmed details reachable, and that timeout removes the active badge and waves while preserving the confirmed report. Build and device validation are pending.

## November 12 Chiba felt reports

The `0.10.4-dev.1a` FULL export and screenshot cover northeastern Chiba, JMA event `20261112222726`. The primary P2PQuake cluster began at 22:27:35.786 JST. Its retained count reached 39 at 22:29:00.125, 95 at 22:30:19.609, and 103 at 22:30:36.499. DM-D.S.S received the detailed VXSE53 at 22:30:20.817. P2PQuake delivered its copy at 22:30:41.719. A second cluster with a different start time reached three at 22:30:41.721 and four at 22:30:44.677. The screenshot at 22:31:04 shows four on the confirmed card.

Both clusters met the existing time window. The second supplied area data, so it passed the existing informativeness gate despite a zero aggregate confidence score. Live ownership and the combined card both allowed an incoming cluster to replace a cluster already attached to the same earthquake. The fix retains the established cluster through report updates and warning expiry while merging cumulative updates within that cluster.

The live fix includes a Chiba regression for the paid-warning handoff. Verify on a device that the count reaches 103, remains there after the competing cluster reaches four, and stays visible on the confirmed report after the warning expires.

The user requested repair of the existing Recent Earthquakes entry and numeric development versions. Local `0.10.4-dev.2` / 251 carries the original 103-report aggregate and all 19 area entries verified against the export. QuakeDeck applies it to the matching confirmed report when loading Recent Earthquakes and writes the corrected cache through the normal save path. It preserves higher cumulative counts from the original cluster. The repair needs a matching confirmed report already on the device or received from a report source. Android Studio build, regression execution and device checks are pending.
