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

* Validate the first UI pass in Android Studio and on a device. The [continuation notes](workstreams/replay-and-alert-ui.md) cover History, felt reports, alert focus and dismissal, station cards, Data Sources, and diagnostic export.
* Continue standardizing row spacing and clickable text across the other menus and dialogs after the Data Sources pass.
* Begin preparing Google Maps-friendly English station addresses alongside the [station location research](workstreams/differentiate-city-reporting-units.md).
