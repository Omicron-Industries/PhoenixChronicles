<h1 align="center">
  <img src="https://raw.githubusercontent.com/P-H-O-E-N-I-X-PackForge/PhoenixChronicles/main/src/main/resources/assets/phoenix_chronicles/images/chronicle.png" alt="Phoenix Chronicles" width="280">
</h1>

<p align="center">
  <strong>Create your favorite documentation; thinly disguised as a mod or an interconnected story. 
    It's your choice (or not).</strong>
</p>

<p align="center">
  <a href="https://www.curseforge.com/minecraft/mc-mods/phoenix-chronicles-quests">
    <img alt="CurseForge" height="50" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/available/curseforge_vector.svg"></a>
  <a href="https://discord.gg/4jch9Rs2Cq">
    <img alt="Discord" height="50" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/social/discord-singular_vector.svg"></a>
  <a href="https://ko-fi.com/phoenixvine">
    <img alt="Ko-fi" height="50" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/donate/kofi-singular_vector.svg"></a>
</p>


# Phoenix Chronicles
Chronicles is a simple 1.20.1 questbook mod made to solve the niche and vast issues when making a questbook for your modpack.

It is focused on trying to integrate with the community, provide everything a questbook mod would need, and throw some more into the mix.

It has compat for
- Gregtech:Modern
- Phoenix Guilds
- Phantasia
- PhoenixCore
- FTBTeams
- AE2
- Curios
- Phoenix Archives
- EMI/JEI
- KubeJS
- Phoenix's Chromatic Codes

It also depends on PhoenixWiki for the shared theming, ingame wiki, and rich text/markdown it provides. 

Made to be an alternative to **FTBQuests** (or ftbq) but still have its own unique feel and cadence. 
There is a built-in **FTBQuests** importer that can handle importing 
*quests, player progress, chapters, categories, rewards, and tasks*.
Though `filter tasks` will have to be remade since those use a seperate mod not in base **FTBQuests**.

## Wiki link
We have a small in progress wiki for all PhoenixSuite mods, if it is missing any important info or you would like to help,
feel free to ping me on discord by the username of Phoenixvine.

[Wiki](https://p-h-o-e-n-i-x-packforge.github.io/PhoenixSuite/wiki/) 

## Getting started as a dev and/or packdev.
You will need the minecraft development plugin and the mermaid plugin.
The minecraft formatting colors plugin is also highly suggested. 
Checking the wiki is also very helpful as well as there being some more resources down below.
And ofc last but not least feel free to ask for help in the discord

## Major feature list.
Below is a short explanation of the major features in Chronicles.
For more information on any of the features, check the wiki (or if that doesn't exist) ping Phoenixvine on discord.

> NOTE. All FTBQ comparisons are against **base FTB Quests on 1.20.1**. 
> These comparisons do not cover FTBQ addons or the 1.21.1 version of FTB Quests.

Chronicles prides itself on its 
- Automatic lang handling.
- Visual Customization.
- Quest authoring.
- Rich/Markdown text rendering.
- Questbook searcher.
- Overall visual feel.
- Task/Reward selection.
- Custom Tasks.
- In-House filtering system.
- Open community.
- Open licensing.
- Player and Packdev QOL.
- FTBImporter.
- Ingame settings.

And last but not least, we are really focused on learning and improving consistently.
We manage a lot of mods and interconnected systems, so all feedback/usage is greatly appreciated!

### What does chronicles do better than FTBQuests?
We believe Chronicles handles visuals, filtering, customization, 
external mod compat, community openness, quest variance, and lang better than 1.20.1 FTB Quests.

## Roadmap
Below is a loose roadmap of what we plan to do in the future.
![Roadmap Chart](https://raw.githubusercontent.com/P-H-O-E-N-I-X-PackForge/PhoenixChronicles/main/src/main/resources/assets/phoenix_chronicles/images/roadmap_chart.png)

## Small snippt of the markdown of quests.
![Markdown Example](https://raw.githubusercontent.com/P-H-O-E-N-I-X-PackForge/PhoenixChronicles/main/src/main/resources/assets/phoenix_chronicles/images/markdown.webp)

```
// Heading. Levels 1-6(# -> ######) ({flat} if you don't want any level of header to collapse).
"# Power Basics 
// Supports all base mc formatting codes.
Unlike §aRF power§r, EU power requires care. Match cable amperage to your machine's draw.

## Cable Ratings

// Traditional markdown table, renders how you would expect ingame.
| Tier | Amps | Cable |
|---|---|---|
| LV | 1A | Tin |
| MV | 2A | Copper |
| HV | 4A | Gold |

// Renders a red warning callout with the error emoji.
:::warning Don't Skimp
Undersized cable §cburns and is destroyed§r. Always round up.
:::

// Collapsed by default section.
:::spoiler Why this matters
Fixed in the 0-loss cable rework: multi-amp-source and multi-face-input bugs no longer apply, so a single overloaded cable segment is the only real risk now.
:::

// Tick boxes that are persisted on client.
- [ ] Craft a Tin Cable
- [ ] Wire your first LV machine
- [x] Read this page

// Code block (which are click to copy), hover tooltip, and item rendering examples.
Need the item? `minecraft:redstone` -- click to copy the id, or hover this: [item:minecraft:redstone|Used for basic circuits]

// Opening a PhoenixWiki page from either chronicles or another mod using it, or opening an online link. 
See also [Rich text guide](wiki:phoenix_chronicles/wiki#rich_text) or the [Archive entry](https://www.youtube.com/watch?v=b5l5UodFzMo) for lore.

// Conditonal statements, works with choronicle flags, checking if a mod is loaded, and many more. Check the wiki for more examples (currently WIP).
:::if flag:mod:gtceu
GregTech is loaded, so cable coverings are available too.
:::

// Renders the text below it larger (or smaller) depending on the given scale.
{scale:2.0}
This whole block renders 20% larger.

// The below two are an example of a footnote (defined at bottom, can be rendered anywhere.)
Footnote example[^amps].

[^amps]: Amps determine how much current a cable can carry before burning out.",
```

## Credits
- Thanks to FTBQuests for some feature ideas and things to do a bit better.
- Thanks to PlasmaticVoid for their help in refining the project.
- Thanks to [Jambon](https://github.com/Jambon123), FyreDrakon, and KaiTheExaminer
for helping test out the mod and giving some suggestions.
- Thanks to [Rose](https://github.com/Lilac-Rose) for adding porting over ftb quest player data. 

## Ai disclosure.
A sizeable portion of the code has been written by ai. Refactoring is currently ongoing.
All text in the mod is currently being taken apart and being rewritten by a real human, and all documentation is written 
by humans.

## Where to go next.
Contributing
See CONTRIBUTING.md. (Note, WIP. Feel free to ask Phoenix questions if anything is unclear)

Architecture Decisions 
See Architecture.md. (Note, WIP. Feel free to ask Phoenix questions if anything is unclear)

Frequently Asked Questions
See FAQ.md (Note, this is not complete. More entries will be added as questions are asked.)

Known Issues
See KNOWN-ISSUES.md (Note, currently empty. Want to be the first?)
