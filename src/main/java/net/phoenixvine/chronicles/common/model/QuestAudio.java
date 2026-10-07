package net.phoenixvine.chronicles.common.model;

import net.minecraft.nbt.CompoundTag;

/**
 * A quest's narration and music. {@code voiceId} is a sound event played as a voice line (optionally the moment the
 * quest viewer opens), {@code musicId} a sound event looped while the quest is on screen. Blank ids mean none.
 *
 * @param voiceAuto   play the voice line automatically when the quest viewer opens
 * @param musicVolume 0..1 multiplier on the player's music volume
 * @param fadeMs      fade in / out time for the music, in milliseconds
 */
public record QuestAudio(String voiceId, boolean voiceAuto, String musicId, float musicVolume, int fadeMs) {

    public static final int DEFAULT_FADE_MS = 1500;
    public static final QuestAudio NONE = new QuestAudio("", false, "", 1f, DEFAULT_FADE_MS);

    public QuestAudio {
        voiceId = voiceId == null ? "" : voiceId.trim();
        musicId = musicId == null ? "" : musicId.trim();
        musicVolume = Math.max(0f, Math.min(1f, musicVolume));
        fadeMs = Math.max(0, fadeMs);
    }

    public boolean isEmpty() {
        return voiceId.isEmpty() && musicId.isEmpty();
    }

    public boolean hasVoice() {
        return !voiceId.isEmpty();
    }

    public boolean hasMusic() {
        return !musicId.isEmpty();
    }

    public QuestAudio withVoice(String id, boolean auto) {
        return new QuestAudio(id, auto, musicId, musicVolume, fadeMs);
    }

    public QuestAudio withMusic(String id, float volume, int fade) {
        return new QuestAudio(voiceId, voiceAuto, id, volume, fade);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        if (hasVoice()) tag.putString("voice", voiceId);
        if (voiceAuto) tag.putBoolean("voice_auto", true);
        if (hasMusic()) {
            tag.putString("music", musicId);
            if (musicVolume != 1f) tag.putFloat("music_volume", musicVolume);
            if (fadeMs != DEFAULT_FADE_MS) tag.putInt("fade_ms", fadeMs);
        }
        return tag;
    }

    public static QuestAudio fromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return NONE;
        return new QuestAudio(tag.getString("voice"), tag.getBoolean("voice_auto"), tag.getString("music"),
                tag.contains("music_volume") ? tag.getFloat("music_volume") : 1f,
                tag.contains("fade_ms") ? tag.getInt("fade_ms") : DEFAULT_FADE_MS);
    }
}
