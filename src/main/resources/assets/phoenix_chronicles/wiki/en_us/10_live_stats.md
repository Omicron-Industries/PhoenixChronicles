{scale:1.1}
# Live Registry Stats

Data pulled from the in-memory registry at render time. (This whole page opts
into a slightly larger base text size via a `{scale:1.1}` directive on its
first line - see [Rich Text](wiki:rich_text) for how that works.)

- **Total quests:** {scale:1.5}{{total_quests}}{reset}
- **Categories:** {{category_count}}

## Per-chapter

{{per_chapter_block}}

## Task types

- **Registered:** {{task_types_registered}}

## Repeat modes in use

{{repeat_mode_block}}

## Quests with tutorials

- **Total:** {{tutorial_count}}

---

{{load_errors_block}}
