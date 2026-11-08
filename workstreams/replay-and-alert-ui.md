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
