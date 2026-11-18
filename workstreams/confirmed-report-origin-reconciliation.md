# Confirmed reports with revised origin times

Investigate and correct the November 18 Sea of Japan duplicate shown in Recent Earthquakes on `0.10.4-dev.5` FULL. The local base is `c026b38` on `main`.

## Evidence

DM-D.S.S uses the same JMA event ID `20261118183714` for seven EEW revisions and the VXSE51/52/53 confirmed reports. The early report uses an 18:37 origin. VXSE52 and VXSE53 use 18:36 as origin and 18:37 as arrival time. The matching P2PQuake Destination and DetailScale copies keep 18:37 as earthquake time. Their detailed bulletin issue time is 18:40:58 JST, with the same 38.8° N, 137.9° E hypocenter, M5.0, 10 km depth, maximum Shindo 3 and 96 stations. The screenshots show 38 felt reports on the 18:36 record.

The provider previously matched reports with one missing JMA ID only within two seconds of origin time. The public copy has a timestamp-based ID, so it remained separate from the JMA-ID record. The export also records fallback and reconnect activity before the screenshots. It lacks report-by-report cache/list insertion diagnostics, so the exact callback that first inserted the second record is uncertain.

## Change

Use shared publication time, type, correction, and hypocenter evidence to reconcile source copies with an origin discrepancy of up to 90 seconds. Distinct known JMA IDs stay separate, and ambiguous unknown-ID matches cannot join multiple known incidents. Where publication and report stage are equal, the record carrying a JMA ID supplies the official data.

Fold matching records already in Recent Earthquakes as they are restored or updated. Retain the established UI identity, merge observed data and preserve the associated felt cluster. Save the normalized Recent cache through its existing path.

The implementation is `0.10.4-dev.6` / 255. Validate the shared-bulletin regressions and device cache restoration, including selecting both former rows before updating and verifying one 18:36 entry with 38 felt reports afterward.

FULL compiled and passed all 219 unit tests with zero failures, errors or skipped tests. This includes six shared-bulletin regressions. Device cache normalization is pending. The export records packet arrival and recovery requests but lacks the per-row writes needed to establish exactly which history-loading path introduced the duplicate.
