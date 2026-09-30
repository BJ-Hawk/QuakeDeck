Outstanding from earlier entries

* Device validation of the implemented observed-intensity hierarchy and deep-map UI:
  * English JMA-area headers, including island areas.
  * Prefecture/JMA-area/municipality focus reticles and true-marker labels.
  * Dim inherited area/prefecture colours for municipalities without direct observations.
  * Closing station info restores the camera correctly; switching information mode preserves station selection.
* \[OPTIONAL IN SETTINGS] Deeper zoom - above 112x switch to neighborhood border display (data need to be prepared first), following same logic as other levels as for the coloring etc.
* \[OPTIONAL IN SETTINGS] Above zoom 112x show semitransparent OSM background

The hierarchy, station-information card, focus reticles, translucent marker labels, and inherited intensity colours are implemented. Their original feature requests are removed from the backlog; remaining device checks are listed above. See [the workstream](workstreams/observed-intensity-hierarchy-and-deep-map-ui.md).

New 2026/09/26
* UI work:
  * Past reports replay - loading bar should be probably anchored to bottom so it's visible when scrolled and doesn't evoke the feeling of app not doing anything wehn loading an event for replay
  * Unify the looks of all menues and windows (mainly Data Sources first) - spacing of rows, unified clickable text
  * To prevent flashing the Historical events replay window (the blue report type/info on top of event card) should have constant rows. Right now it jumps when issued is empty/filled
  * Remove "P2PQuake crowd signals" from the "Data & Connection" menu in Settings - it's no longer used (confirm in code first)
  * In-progress station location research: start "Englifying" the adresses to be in Google Maps format. On that note, make sure the adress row text (in station detail view) is consistently resized. It is now resized only once, going to different staion with default text size and back doesn't resize it again.
  * Make sure the Event detail and Station detail are the same size at all times. Switching between Earthquake info and Station info (the <-> to the left of Shindo badge) does a small resize (the station info being slightly bigger)
  * Reduce the "Delivery diagnostic" info - keep only "Connection" and "Last Transport Issue" with shortened info to only time and short reason. No more "show newest 20) and live packet count. Keep Export raw diagnostic JSON, but reanmed to "Export diagnostic" and now expanded in a new selection window before export:
    * Export last event
    * Export last hour
    * Export last day (24 hours)
    * Export last week 
    * Export all
    * (This selection window should have a checkmark "Include connection packets". With it deselected, only event packets are exported. Further clarification of the function and meaning by you is encouraged if unsure, don't assume)
  
* Selecting past event should auto-focus the event
* If the latest event (not the one in the Recent Earthquakes) is focused, add in "Close Report" button like for all the past events, allowing to clear the map without having to press "Fit Japan" button
* Past report replay - browsing (Next/Prev) should skip P2PQuake felt reports, but show them in the list.
* Past report replay - P2PQuake felt reports are not deduplicated
* During an active EEW, the EEW/Tsunami badges in the top right of map are overlayed on top of the legend (the (?)) button. That button should move below the EEW/Tsunami badges. EEW/Tsunami badges should become interactive and focus on the ongoing EEW/Tsunami event (which ever was clicked on) if not already focused. If already focused, refocus.
* During an active EEW/Tsunami event, allow to close (unfocus) the event and to stop showing info about it (keep rendering P/S waves and coastlines), keeping only the EEW/Tsunami badge in the top right as it is now
