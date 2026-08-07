QuakeDeck Map Editor + two-tier boundary repair
================================================

INSTALL
-------
Extract this ZIP directly over the QuakeDeck project root and replace existing files.

NO REPAIR COMMAND IS REQUIRED AFTER EXTRACTION.
The repaired runtime resources are already included in this package.

The repair .bat remains in tools/ only as a future rebuild utility if map geometry is regenerated later.

WHAT IS INCLUDED
----------------
1. Current visual map editor with the fixed top toolbar and geometry editing tools.
2. Deep municipality boundary repair:
   - only exact two-owner edges are emitted into municipality/warning/prefecture stroke resources.
3. Middle JMA reporting-layer boundary repair:
   - only exact two-owner edges are emitted into fine/prefecture stroke resources.
   - one-owner prefecture tails are suppressed.
   - every internal JMA edge is emitted once, not once per owner.
4. Both builder scripts and the combined future repair utility.

VALIDATED OUTPUT
----------------
Deep municipality layer:
  110,359 emitted internal edges total
  75,880 municipality/fine
  18,783 warning-zone
  15,696 prefecture
  0 emitted one-owner edges

Middle JMA reporting layer:
  24,125 emitted internal edges total
  14,002 fine reporting borders
  10,123 prefecture borders
  0 emitted one-owner edges
  0 duplicated emitted edges
  0 fine/prefecture overlap

START EDITOR
------------
Double-click:
  tools\map-editor\launch_map_editor.bat
