# Getting Started

The actual step-by-step path from an empty questbook to a working
questline. Everything here links to a page with the full details.

## 1. Make a Chapter an/or Category

- **Sidebar "+" pill**: Opens the new-chapter/category dialog widget.

A `Category` is a loose collection of chapters to help organization. 
You can choose an icon/color for them (they default to no icon, gray name). 
A `Chapter` is a collection of `Quests` 

## 2. Add your first quest

- **Right-click empty canvas → "+ New quest"** — Opens the quest creator

Set a title (required), pick a shape/icon, and place it - see Quest Fields
for every field this form can set.

## 3. Give it something to do

- **Right-click the quest → "Edit Tasks & Rewards"** — Or the button inside the creator

Add one or more tasks (Tasks page has every type) and whatever rewards
should be granted on completion (Rewards page).

## 4. Chain it to the next quest

- **Alt + drag from one node to another** — Draws a prerequisite dependency line

There's no menu item for this - prerequisites are drawn directly on the
canvas. The target quest stays LOCKED until its prerequisite(s) complete.

## 5. Write the description

Click the description box in the quest detail view (edit mode) to open the
text editor - see Rich Text for colour/image/link syntax and the "---"
page-break marker for long lore.

---

## 6. Test it as a player would

- **Right-click empty canvas → "⏵ Enter Player Mode"** — Simulates real progress

Test mode uses its own throwaway progress data (nothing server-side, no
other player is affected) and disables editing while it's on - click a
quest to toggle it COMPLETED/LOCKED and watch prerequisites cascade for
real. "↺ Reset Player Mode Data" clears it; exit the same way you entered.

## Optional, once the basics work

- **Chapter theme** — Right-click canvas → "Edit chapter theme…" - see Customization
- **Per-quest text/design** — Right-click a quest → "Edit Texts…", "Design toast…"
- **Variants** — "◈ Variants" button in the quest creator - see the Variants page
- **KubeJS/Java integration** — See API Reference once you need code, not just SNBT

## If something looks wrong

- **/chronicles validate** — Reports load errors and common config mistakes
- **Live Stats page** — Same load-error list, plus per-chapter counts

Most "my quest doesn't show up" reports turn out to be a typo'd chapter
name or a prerequisite pointing at an ID that doesn't exist.
