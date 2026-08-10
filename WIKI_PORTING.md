# In-game wiki

The in-game dev wiki (opened via `ChronicleOverviewScreen.openWiki()`) is no longer a
copy-paste engine local to this mod - it's backed by the standalone jar-in-jar'd
[Phoenix Wiki](../PhoenixWiki) library mod (`net.phoenixvine.wiki`, embedded via `jarJar`
in `build.gradle`). This mod's own wiki content still lives here, at
`src/main/resources/assets/phoenix_chronicles/wiki/en_us/*.md` (and its in-game-editable
overlay at `config/phoenix_chronicles/wiki/en_us/`) - only the rendering engine moved out.

To reuse the wiki engine in another mod, embed `PhoenixWiki` the same way and see its own
README for the API and markdown syntax reference: `../PhoenixWiki/README.md`.
