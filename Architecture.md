# Potential Features
Ideas that made us go, "Yeah, these make a lot of sense."

Potential: Name of Feature
Description of the feature
Estimated amount of work
Why it was deffered/chosen as important/labeled as never.

Auto claim rewards
Auto claim needs to be choosable per player and control of what gets auto claimed (loot tables, loot boxes, choice boxes, command rewards, item rewards).
Moderate, needs changes to the settings system and needs either config or an imgame UI for setting up auto-claim settings.
The way other mods handle auto-claim rewards annoyed me as a player so I want Chronicles to have more power here.

Chest authoring import/exporting.
Chest authored loot tables/choiced rewards.
Small, just needs a command to see the container of the chest and bring the ItemStack of each item into the list.
Small packdev qol that some people have asked for.


Hide Reward/Task portions.
Hiding specific parts of rewards/tasks. Useful for example showing needing iron but not showing the amount until you actually get what was needed.
Moderate, needs changes to the reward/task editor and the quest viewer card rendering.
Specific asked for feature I feel Chronicles would do well to have.

Image rotation.
Allowing rotation of images inside Chronicles. 
Moderate, requires changing how images are handled in the canvas/quest viewers.
It's important for visuals and it was missed in the original design.

Setting whole chapters/categories as dependencies. 
Allowing quests to use entire chapters/categories being complete as a dependency.
Small, just needs to allow a reference to a chapter's ID/add them to the parent selector screen.
Good qol for more strict packs and not too much effort.

Emergency Items
Per chapter and/or per chapter emergency items that can be repeatable and/or timed. Currently in progress but not complete.
High, requires changes to quest nodes, chapters, a bunch of rendering, and a new screen for collecting them (mostly piggybacking off of the collect rewards screen).
It's an expectation of questbook mods and has certainly saved me as a player in some cases.

Themable line colors.
Allowing the dep line colors (inactive, hovered, complete) to be different per theme.
Small, it's mostly implemented but has a bug which makes it currently not work.
Imo important visual design that is currently bugged.

Recipe tasks.
Allowing packdevs to require players to do a gt/otherwise recipe a set number of times. 
Small, just needs a small reuse of the existing picker screens and a new task type.
A nessecary sister to the crafting table quest.

Narrated quests.
Voice line/audio quests. This would probably be a system of setting voice lines to auto start or be clicked by players for the chronicles side and have support in the wiki side
for in markdown voicelines/audio and would not show visually when smth doesnt have a voiceline/audio clip attached.
High, more UI work on the already heavy quest viewer screens and plumbing for sound.
Something I really want from Chronicles and has been suggested a few times.


Bulk adding items to multi item selections.
Having the ability to say multi select collect item tasks to give some authoring qol. Also copy/paste for rewards/tasks.
High, risky touches of the internal undo/redo stack along with edge cases of many task/reward types.
Highly requested feature that does help packdevs.

Typewriter text.
Adding the ability to add typewriting text for more mysterious information with control of the speed and reveal per action. Would be built into Wiki
Moderate, a new rich text entry with some wacky edge cases with phcc/text animator.
Its something I want to use for a lot of my own quests.

Archive entry reward type.
Adding the ability to autogen an archive entry through chronicles. 
High, archives already has the api to externally make entries but it's a lot of UI work and usability decisions.
THE most requested cross mod feature and it is something I want to use myself.

Music support. 
Adding the ability to add per chapter/per quest music that properly handles fade in/out and volume. 
High, having to deal with vannila's music, handling audio registry itself, adding the UI for all of this.
Something that would really help the ambience. 

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

Chat links.
Clicking a button in the quest viewer to paste a link to the quest in chat.
Small, small button that pastes the usage of the previously built "jump to quest" links.
Deferred due to no demand.

Per player progress.
Being able to see what you contributed to a team's shared progress of a task/the qb as a whole.
High, state managment of multiple players, making sure that the changes didnt break pooled progress as a whole, and some real visual noise into an already small space.
Deferred due to being a lot of work and risk of visual noise.

Pooled team progress config.
A config to handle turning off pooled progress when being in an team or guild.
Small, a config to tell TeamProgress to not count it on them. Would have to be server side though.
Deferred due to no current demand and the multiplayer aspect.

Disable toasts config.
A config to disable toasts for completing/reward claims.
Small, a single value.
Deferred due to being multiplayer and no demand yet.

Global Rewards
The ability to mark a reward as only certain amount of people who can claim it.
Moderate, needs more work to the reward type.
Deferred due to being multiplayer.

Image click actions beyond links.
Pretty much archives hotspot images.
Moderate, requiries a rework of images.
Deffered due to lack of demand and difficulty.

Place block/create structure tasks.
Placing block/making a structure task types, would include making a gt multi/making an nbt structure.
Moderate, requires parsing more file stuff.
Deffered due to no demand.

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

Parallax backgrounds.
Adding support for parallax in the canvas/quest veiwers.
High, performance and visual concerns.
Deferred due to difficulty.

Chapter completion stamp.
Nice little visual indicator of a chapter being complete.
Small, the art portion is the difficult part.
Deferred due to not having the art.

Personal notes.
We have dev notes but these would be player facing.
Moderate, adding an ui section for holding them and persisting them
Deferred due to no demand.

Shareable completion cards.
Packdev defined completion cards that can be exported into images and shared.
High, it's a lot of work on both chronicles and the packdevs
Deferred due to difficulty.

Personal Stats.
A screen showing quests per session, longest streak, rarest rewards, time per chapter, total quest progress.
Moderate, a redesign of the existing page.
Deferred due to no demand

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





