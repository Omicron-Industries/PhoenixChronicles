package net.phoenixvine.chronicles.client;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * In-memory "I just edited this locally, show it now" override for quest/task translation keys.
 *
 * Quest/task text normally resolves through vanilla's own I18n/Language system so each player's
 * selected game language picks their own translation (see ChroniclesLangPack). That system only
 * refreshes on a full {@code Minecraft.reloadResourcePacks()}, which used to get triggered on
 * every quest save so a title/description edit wouldn't keep showing an OLD, already-registered
 * translation for that key - but a full resource-pack reload is exactly the "whole client
 * reloads" flash pack devs were rightly complaining about, for what's really just a one-key text
 * change.
 *
 * This map is checked FIRST, ahead of I18n, by QuestNode/QuestTask's own getters - it only ever
 * gets populated by THIS client's own setters (see QuestNode#setTitle etc.), so it never affects
 * how any OTHER player's client resolves the same key against their own selected language; it
 * only makes the editing client's own live preview correct immediately, with no reload of any
 * kind. Purely in-memory (not persisted) - a restart or fresh join re-derives everything from the
 * synced quest data and whatever lang files are actually loaded, same as before this existed.
 */
public class ClientTextOverrides {

    private static final Map<String, String> overrides = new ConcurrentHashMap<>();

    public static void put(String key, String value) {
        overrides.put(key, value);
    }

    public static String get(String key) {
        return overrides.get(key);
    }
}
