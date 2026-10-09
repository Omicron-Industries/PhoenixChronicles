package net.phoenixvine.chronicles.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.common.model.QuestAudio;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ChroniclesAudio {

    public interface AudioSource {

        QuestAudio desiredAudio();
    }

    private static final String SCREEN_PACKAGE = "net.phoenixvine.chronicles.client.screen";
    private static final float DUCK_TARGET = 0.35f;

    private static MusicTrack current;
    private static final List<MusicTrack> fadingOut = new ArrayList<>();
    private static SoundInstance voice;
    private static float duck = 1f;

    private ChroniclesAudio() {}

    private static SoundEvent eventFor(String id) {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.parse(id));
    }

    public static void playVoice(String id) {
        stopVoice();
        if (id == null || id.isBlank()) return;
        try {
            SoundEvent event = eventFor(id.trim());
            voice = new SimpleSoundInstance(event.getLocation(), SoundSource.VOICE, 1f, 1f, RandomSource.create(),
                    false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            Minecraft.getInstance().getSoundManager().play(voice);
        } catch (Exception ignored) {
            voice = null;
        }
    }

    public static void stopVoice() {
        if (voice != null) {
            Minecraft.getInstance().getSoundManager().stop(voice);
            voice = null;
        }
    }

    public static boolean isVoicePlaying() {
        return voice != null && Minecraft.getInstance().getSoundManager().isActive(voice);
    }

    public static void toggleVoice(String id) {
        if (isVoicePlaying()) stopVoice();
        else playVoice(id);
    }

    private static void applyMusic(QuestAudio audio) {
        if (!audio.hasMusic()) {
            clearMusic(audio.fadeMs());
            return;
        }
        if (current != null && !current.fadingOut && current.id.equals(audio.musicId())) {
            current.target = audio.musicVolume();
            current.fadeTicks = fadeTicks(audio.fadeMs());
            return;
        }
        clearMusic(audio.fadeMs());
        try {
            current = new MusicTrack(audio.musicId(), eventFor(audio.musicId()), audio.musicVolume(),
                    fadeTicks(audio.fadeMs()));
            Minecraft.getInstance().getSoundManager().play(current);
        } catch (Exception ignored) {
            current = null;
        }
    }

    private static void clearMusic(int fadeMs) {
        if (current == null) return;
        current.fadingOut = true;
        current.target = 0f;
        current.fadeTicks = fadeTicks(fadeMs);
        fadingOut.add(current);
        current = null;
    }

    private static int fadeTicks(int fadeMs) {
        return Math.max(1, fadeMs / 50);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        if (mc.screen instanceof AudioSource source) {
            applyMusic(source.desiredAudio());
        } else if (mc.screen == null || !mc.screen.getClass().getName().startsWith(SCREEN_PACKAGE)) {
            clearMusic(QuestAudio.DEFAULT_FADE_MS);
            if (isVoicePlaying() && mc.screen == null) stopVoice();
        }

        float duckGoal = isVoicePlaying() ? DUCK_TARGET : 1f;
        duck += (duckGoal - duck) * 0.15f;

        fadingOut.removeIf(t -> t.isStopped());
        if (current != null || !fadingOut.isEmpty()) mc.getMusicManager().stopPlaying();
    }

    private static final class MusicTrack extends AbstractSoundInstance implements TickableSoundInstance {

        final String id;
        float target;
        int fadeTicks;
        boolean fadingOut;
        private boolean stopped;

        MusicTrack(String id, SoundEvent event, float target, int fadeTicks) {
            super(event, SoundSource.MUSIC, RandomSource.create());
            this.id = id;
            this.target = target;
            this.fadeTicks = fadeTicks;
            this.looping = true;
            this.delay = 0;
            this.volume = 0f;
            this.attenuation = Attenuation.NONE;
            this.relative = true;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        @Override
        public void tick() {
            float goal = fadingOut ? 0f : target * duck;
            float step = Math.max(0.0005f, 1f / fadeTicks);
            if (volume < goal) volume = Math.min(goal, volume + step);
            else if (volume > goal) volume = Math.max(goal, volume - step);
            if (fadingOut && volume <= 0.001f) stopped = true;
        }
    }
}
