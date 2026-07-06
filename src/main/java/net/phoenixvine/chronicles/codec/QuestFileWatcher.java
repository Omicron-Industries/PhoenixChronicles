package net.phoenixvine.chronicles.codec;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.network.PacketDistributor;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.S2CSyncQuestsPacket;
import net.phoenixvine.chronicles.registry.CategoryFlagRegistry;
import net.phoenixvine.chronicles.registry.ChapterFolderRegistry;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.registry.RewardTableRegistry;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Watches the Phoenix Chronicles config directory for .snbt file changes and
 * automatically reloads quests without requiring /chronicles reload.
 *
 * Uses a single daemon thread with a 600ms debounce to coalesce rapid saves
 * (e.g. a text editor writing multiple temp files before the final save).
 *
 * Start: QuestFileWatcher.start(server, configDir)
 * Stop: QuestFileWatcher.stop()
 */
public final class QuestFileWatcher {

    private static final long DEBOUNCE_MS = 600;

    private static Thread watchThread = null;
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicLong lastEventMs = new AtomicLong(0);
    private static volatile Supplier<MinecraftServer> serverRef = null;
    private static volatile Path watchedDir = null;

    private QuestFileWatcher() {}

    public static void start(MinecraftServer server, Path configDir) {
        stop(); // stop any previous watcher

        serverRef = () -> server;
        watchedDir = configDir;
        running.set(true);

        watchThread = new Thread(() -> {
            try (WatchService ws = FileSystems.getDefault().newWatchService()) {
                // Register the root dir and all subdirs
                registerRecursive(ws, configDir);

                while (running.get()) {
                    WatchKey key = ws.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (key == null) {
                        maybeReload();
                        continue;
                    }
                    boolean relevant = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) continue;
                        Path changed = ((WatchEvent<Path>) event).context();
                        String name = changed.toString();
                        if (name.endsWith(".snbt") || name.endsWith(".txt")) {
                            relevant = true;
                        }
                        // If a new directory was created, register it too
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            Path full = ((Path) key.watchable()).resolve(changed);
                            if (Files.isDirectory(full)) {
                                try {
                                    full.register(ws,
                                            StandardWatchEventKinds.ENTRY_CREATE,
                                            StandardWatchEventKinds.ENTRY_MODIFY,
                                            StandardWatchEventKinds.ENTRY_DELETE);
                                } catch (IOException ignored) {}
                            }
                        }
                    }
                    if (relevant) lastEventMs.set(System.currentTimeMillis());
                    key.reset();
                    maybeReload();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                System.err.println("[Phoenix Chronicles] File watcher failed: " + e.getMessage());
            }
        }, "chronicles-file-watcher");

        watchThread.setDaemon(true);
        watchThread.start();
        System.out.println("[Phoenix Chronicles] File watcher started on " + configDir);
    }

    public static void stop() {
        running.set(false);
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
        serverRef = null;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static void maybeReload() {
        long last = lastEventMs.get();
        if (last == 0) return;
        if (System.currentTimeMillis() - last < DEBOUNCE_MS) return;
        lastEventMs.set(0); // consume the event

        MinecraftServer server = serverRef != null ? serverRef.get() : null;
        if (server == null || !server.isRunning()) return;

        // Schedule reload on the server thread (WatchService callback is off-thread)
        server.execute(() -> {
            Path configDir = watchedDir;
            if (configDir == null) return;

            QuestTreeRegistry.clearConfigQuests();
            CategoryFlagRegistry.load(configDir);
            net.phoenixvine.chronicles.registry.CategoryPrereqDefaults.load(configDir);
            RewardTableRegistry.load(configDir);
            ChapterFolderRegistry.load(configDir);
            QuestFileLoader.loadAdditiveFromDisk(configDir);

            int questCount = QuestTreeRegistry.getAllQuests().size();
            // Same reasoning as the FTB import command: encoding every quest into one
            // S2CSyncQuestsPacket is heavy enough on its own to risk keepalive timeouts, and this
            // watcher fires on ANY .snbt change - including a large batch import, which ALSO
            // triggers its own reload/sync. On an integrated/singleplayer server the client
            // shares the same config directory, so it's far cheaper to have it re-read the files
            // itself than to re-serialize and transmit the whole quest tree over the network.
            int playerCount = 0;
            if (server.isSingleplayer()) {
                net.phoenixvine.chronicles.network.packet.S2CReloadQuestsFromDiskPacket reloadPacket = new net.phoenixvine.chronicles.network.packet.S2CReloadQuestsFromDiskPacket();
                for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                    ChronicleNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), reloadPacket);
                    playerCount++;
                }
            } else {
                S2CSyncQuestsPacket syncPacket = new S2CSyncQuestsPacket(QuestTreeRegistry.getAllQuests());
                for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                    ChronicleNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), syncPacket);
                    playerCount++;
                }
            }
            System.out.printf("[Phoenix Chronicles] Auto-reloaded %d quest(s) from disk (synced to %d player(s))%n",
                    questCount, playerCount);
        });
    }

    private static void registerRecursive(WatchService ws, Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walk(root)
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        dir.register(ws,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_DELETE);
                    } catch (IOException ignored) {}
                });
    }
}
