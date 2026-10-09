# Architecture.
Phoenix Chronicles is a questbook mod with integrations into many mods, purposeful design to work with the rest of the
PhoenixSuite, a focus on showing off documentation style information to players but still allowing much visual power, 
and a focus on a good Developer Experience. 

This page will explain what mods are included in the architecture, why some nessecary mods are needed, a fine grained list
of Chronicle's features, a graph of it's file system, and why some decisions were made.

## Sidecar mods.
Chronicles has compat into the following mods with more planned in the future.
- Curios: Allowing Chronicles to see items inside Curios slots.
- AE2: Allowing Chronicles to see inside ae2 networks and to insert items/fluids into ae2.
- Phoenix Archives: Allowing Chronicles to link to Archive entries, create Archive entries, and share some of itself.
- Conflux of Research: Allowing Chronicles to know when a Conflux node is complete.
- Emi/Jei: Allowing Chronicles to jump to the installed recipe viewer and make custom questbook entries into the recipe viewer.
- GTCEu: Allows Chronicles to know when a multiblock is formed and using EU in the energy task.
- KubeJS: Allows Chronicle packdevs to use it's API in KubeJS and not just Java.
- Phantasia: Allows Chronicles to know when a guide/script/scene is viewed by a player.
- Text Animator: Allows Chronicles to not overwrite TextAnimator's effects.
- Phoenix's Chromatic Codes: Allows Chronicles to not overwrite its codes.
- Phoenix Guilds: Allows Chronicles to use its Guilds for shared questbook progress.
- FTBTeams: Allows Chronicles to use its Teams for pooled progress.

Along with the compat mods, a few mods are required.

Chronicles needs a team system: without FTBTeams or Phoenix Guilds it defaults to scoreboard teams.

Chronicles needs PhoenixWiki to run as it provides the theming, markdown rendering/parsing, and Chronicle's in game wiki.

## File System Graph
<img src="https://raw.githubusercontent.com/P-H-O-E-N-I-X-PackForge/PhoenixChronicles/main/src/main/resources/assets/phoenix_chronicles/images/chronicles-dep-chart.png" width="400" alt="Roadmap Chart">

`PhoenixChronicles` is the main mod file where everything eventually goes through.
`ChroniclesGTAddon` is the main gt addon file for Chronicle's GTCEu support. 
It is not technically nessecary to have right now but is included for the future.
`QuestAPI` is the main entry point for addons to hook into Chronicle's API.

The **Capability** package holds the player nbt based `PlayerQuestData` system as well as a sub-package
for the *FTBQuests* importer. 

The **Client** package holds the rendering, screens, client side registry, canvas profiler, and the shader support.

The **Common** package holds the item registry, file handling, filtering system, gametests, flag/variance system,
quest progress tracking, the data for handling the quest tree, the quest tree data itself, and task registry.

The **Integration** package holds the integrations into AE2, EMI, JEI, Phoenix Archives, Conflux of Research, Curios, 
KubeJS, GTCEu, and Phantasia compat.

The **Mixin** package holds an example mixin, Chronicle's mixin plugin, and a mixin into GTCEu's 
`MultiblockWorldSavedData` class.

The **Network** package holds the network class and the packets. 

## List of features.
An extensive list of features, to be extended as they come in.

### Quest rendering.
Icon Rendering
Custom Node shapes
Built in node shapes
"None" node shape
Sizing
choosable text location

### Player qol
checking ae2 for items/fluids
saving progress as you go
checking what you have in your hand
checking what you just crafted
player stats screen
fullscreen and compact quest viewer screens
pinning quests
resizing the fullscreen quest viewer
checking a quest's unlock path
questbook item to open the questbook
keybind to open the questbook
visiblity buttons
ingame settings
fit all quests to view button


### Dependencies.
Spline/Straight dep lines
Themable Dep lines.
Addon custom dep lines.
Parent selector screen.
Handling deps

### Quest Authoring.
Quest creator screen
Quest editor screen
Quest flags
Quest Variants
Markdown in quest descriptions
Quest descriptions
Quest subitltes
Quest titles
Quest hiding
Quest disabling
Quest deleting
Quest Variants.
Item filters
Fluid filters
quest groups
importing from ftbq
resetting/deleting quests/chapters/categories.
toast designer
linked quests
moving quests
choiced/lootbox rewards
test rolling a bunch of lootbox runs
ingame settings
hidden/complete/active/disabled/optional quests
chapter minimap
chapter subgraph
lang editor
dev notes
extensive api
kubejs plugin
conditioanl backgrounds/sidebar shaders
auto arranging quests
rotating entire chapters 90 degrees
manual coord placement
enable if (conditonal quests)
grid based movement (can be turned off)
advancement task
phoenix archive entry task
biome task (id or tag)
dimension task
block break/interact task
conflux task
checkmark task
readme task
craft item task
enchant task
energy task
expereicne task
external task 
filter fluid/item task
fluid task
kill mob task
location task
screen opened task
script task
stat tracker task
structure task
tag item task
timer task
view guide/script/scene task (phantasia)
vein gtm machine forming task
repeatable quests
ability to have a quest go straight to a custom screen on click (scren must be registered)
preview of a machine's script in a quest through phantasia's api
choosing of unlock/complete sound per quest
choosing whether rewards on a quest get autoclaimed
undo/redo stack for most dev actions
toolbar buttons for common actions
the ability to move the sidebar tabs via click and drag
bulk placing quests mode
bulk connecting quests mode
command rewards
external rewards (custom)
archives lore entry reward
item reward
fluid reward
option to have fluid/item rewards to go directly into ae2
open screen reward
quest mutation reward
the ability to jump to an archives entry in quests

### Lang Features
Questbook wide searching
automatic lang handling

## Chapter/Category Authoring.
Add chapter/category screen
Naming the questbook
Selecting icons for chapters/categories/qb


## Pure Visuals.
Theming
Quest shaders.
Canvas background shaders.
Chapter/Category sidebar shaders.


## Potential Features
Ideas that made us go, "Yeah, these make a lot of sense."

Potential: Name of Feature
Description of the feature
Estimated amount of work
Why it was deffered/chosen as important/labeled as never.


## Purposefully deferred.
The ideas below might be reconsidered at some point but were put away for sanity and polish reasons.

Quest Barrier and Stage Barrier blocks that only let players through once a quest or stage is done.

Quest Barriers.
Blocks that do not let you/mobs (configurable) through until a quest is complete.
Moderate, a new block entity and a new reward type.
Deferred due to them not being requested yet/me not seeing a use case for myself.

Loot crates.
Adding loot crates into hostile/passive/neutral mobs with different rates, chests, etc.
High, a lot of balance decisions and stability questions, also a new set of UIs and a block entity.
Deferred due to the work nessecary without any real demand for them.

Hold item, interact entity, and find entity tasks.
More useful task types, with hold item probably being expanded to also be offhand/armor slots.
Moderate, mostly self contained task types.
Deferred due to no demand.

Milestone rewards.
Allowing people to get rewards for the partial completion of a task.
High, balance, UI work, and state design questions that all need a lookover from a server owner.
Deferred due to not really having any multiplayer users yet.

Per player progress.
Being able to see what you contributed to a team's shared progress of a task/the qb as a whole.
High, state managment of multiple players, making sure that the changes didnt break pooled progress as a whole, and some real visual noise into an already small space.
Deferred due to being a lot of work and risk of visual noise.

Pooled team progress config.
A config to handle turning off pooled progress when being in an team or guild.
Small, a config to tell TeamProgress to not count it on them. Would have to be server side though.
Deferred due to no current demand and the multiplayer aspect.

Global Rewards
The ability to mark a reward as only certain amount of people who can claim it.
Moderate, needs more work to the reward type.
Deferred due to being multiplayer.

Image click actions beyond links.
Pretty much archives hotspot images.
Moderate, requiries a rework of images.
Deffered due to lack of demand and difficulty.

Adaptive quests.
Quests that scale with the progress a team/player has towards a goal.
Extreme, a lot of decisions about what this actually means and what it entials.
Deffered due to difficulty.

Team voting.
Voting on branching progress. 
High, a lot of decisions.
Deferred due to ambiguity.

Dialogue-tree quests.
Branching quest text based on choices with dialogue.
High, awesome idea but its not clear what it means.
Deferred due to ambiguity.

Adaptive shaders.
Allowing quest node shaders to take in variables and quest state to be reactive to player action.
Moderate, the shader system will need a bit of a rework to allow the injection.
Deferred due to no demand.

Reveal cards for choice rewards.
Making cards that you can flip over to choose a reward. Would be very cool
Moderate, a new UI for the cards
Deferred due to being mostly visual and niche

Personal notes.
We have dev notes but these would be player facing.
Moderate, adding an ui section for holding them and persisting them
Deferred due to no demand.

Shareable completion cards.
Packdev defined completion cards that can be exported into images and shared.
High, it's a lot of work on both chronicles and the packdevs
Deferred due to difficulty.

## Chronicles took a different path
Ideas that were good solutions to real problems in other mods that we already solved differently.

Task screens
These were the solution to submitting fluids/energy to the questbook. 
Chronicles already has the ability to grab directly from containers and ae2 so these aren't nessecary.

Quest gated recipes.
Chronicles already has an external reward system that could be used in tandem with the mod recipe stages/
With this being said, PFT already has it's own system with Conflux of Research 
so even with the ideal of "control the tech stack" it's not nessecary for this to be a first class feature.

Wagers for submitting an item to lose or beat a challenge for reward.
You can already technically do this as a packdev with the flags/conditonal quest content. 
And we do not want to encourage risking your items in chronicles.

Quests as world events. 
Other mods can already use the API to do this externally, so it's not nessecary as a first class feature.
It's also really niche.

Quests that open instanced dimensions.
Possible externally quite easily, doesn't need to be first class.





