# Canvas Controls

## Mouse

- **Left drag** — Pan the canvas
- **Scroll wheel** — Zoom in / out
- **Left click node** — Select / open quest detail
- **Hover node** — Tooltip shows just the title/subtitle
- **Shift + hover node** — Tooltip expands to state, tasks, prereqs, validation warnings (dev)
- **Right click node** — Context menu: Edit Quest (has its own Tasks/Rewards button), Texts, Design toast…, Set Icon… (item/fluid/texture), Resize…, Delete, Move chapter…
- **Right click canvas** — Context menu: Add quest, Add group, Chapter theme…, Add picture…, dep-line settings
- **Right click picture** — Its own menu: Resize, Resize (scroll+drag)…, Move chapter, Delete
- **Shift + drag** — Move a node, group, or background picture (dev mode)
- **Alt + drag node** — Draw dependency line to another node
- **Middle click** — Reset pan offset

## Keyboard (all rebindable via Options → Controls → Phoenix Chronicles unless noted otherwise - these fire while the quest screen has focus, so they still work even though they're not "global" keybinds.)

- **Home** — Fit all nodes to view (default binding - was "F" before it became rebindable; F is now Search instead)
- **F** — Open quest search overlay (used to require Ctrl+F)
- **L** — Toggle line style (Spline ↔ Straight)
- **M** — Toggle minimap
- **K** — Open quest book (global, works outside the quest screen too)
- **U** — Hold an item, press this: jump to quests requiring it
- **P** — Pin/unpin whichever quest is under the mouse to the HUD tracker
- **Shift (hold, while scrolling)** — Zoom anchors to the cursor instead of canvas center

(the default is to always zoom toward the canvas center now - a quest map,
not a world map - hold this key for the old cursor-anchored behavior)

- **V** — Toggle validation panel (dev only, and not while holding Ctrl)
- **S** — Toggle Quest Stats panel (dev only)
- **G** — Toggle Subgraph mode (dev only)
- **I** — Run FTB import (dev only)
- **/** — Open this wiki (dev only) - shown as "?" in the toolbar button
- **ESC** — Close menus / deselect

## Undo / clipboard (dev mode - NOT rebindable, fixed OS-style combos)

- **Ctrl+Z** — Undo last node/group/picture move, dep-line change, paste, etc.
- **Ctrl+Y / Ctrl+Shift+Z** — Redo
- **Ctrl+C** — Copy the selected quest's SNBT to the clipboard
- **Ctrl+V** — Paste clipboard SNBT as a brand-new quest

Copy/paste is also on the right-click quest menu, not just the key combo.

## Multi-select (dev mode)

- **Shift+click** — Add node to selection
- **Ctrl+drag** — Box-select nodes
- **Ctrl+G** — Group selected nodes
- **Del** — Delete selected nodes

## Zoom

- **Range** — 12% – 250% (scroll or pinch)
- **Step** — 12% per scroll tick

## Grid snapping (node placement)

A grid-size pill in the title bar (left of the zoom %) controls snap size.

- **Click pill** — Cycle: 1 → 4 → 8 → 16 → 32 → 1
- **1 (free)** — Pixel-perfect: no snapping, any position
- **4 / 8 / 16 / 32** — Snap to that many logical-unit grid squares
- **Shift-drag** — Always bypasses snapping regardless of pill setting

## 🎁 Claim All Rewards (every player)

A 🎁 N pill appears in the header when you have unclaimed rewards anywhere
in the tree (N = count). Click it to open a list of every completed,
unclaimed, non-choice-reward quest, with per-row Claim or a Claim All
button - no need to hunt down every finished quest individually.
reward_choice quests are excluded (you have to pick, so they're claimed
from that quest's own reward panel).

## Dev tools (right-click empty canvas, dev mode only)

Dev-only controls live in the right-click context menu, not the toolbar.

- **Test mode** — Simulate player state: see quests as a normal player would
- **↺ Reset** — Visible in Test mode only: clears simulated progress
- **Subgraph** — Highlight the selected node's transitive dependency tree
- **Stats** — Overlay a small stats card (quest counts, load errors)

## Toolbar (always visible in dev mode)

Toolbar holds: chapter selector, filter pills, zoom pill, ? wiki button.
Dev toggles and destructive actions are in the right-click menu to avoid
toolbar overflow at smaller window sizes.
