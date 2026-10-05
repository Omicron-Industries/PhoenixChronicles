package net.phoenixvine.chronicles.common.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.phoenixvine.chronicles.common.codec.QuestFileWatcher;
import net.phoenixvine.chronicles.common.model.RewardTable;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class RewardTableRegistry {

    private static final Map<String, RewardTable> TABLES = new LinkedHashMap<>();
    private static final Map<String, Path> FILES = new HashMap<>();

    public static void clear() {
        TABLES.clear();
        FILES.clear();
    }

    @Nullable
    public static Path getFile(String id) {
        return FILES.get(id);
    }

    public static Path save(Path configDir, RewardTable table) throws IOException {
        Path file = FILES.get(table.id());
        if (file == null) file = configDir.resolve("reward_tables").resolve(table.id() + ".snbt");
        Files.createDirectories(file.getParent());

        QuestFileWatcher.suppressNextReload();
        if (Files.exists(file)) {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(file, table.serializeNBT().toString(), StandardCharsets.UTF_8);
        TABLES.put(table.id(), table);
        FILES.put(table.id(), file);
        return file;
    }

    @Nullable
    public static RewardTable get(String id) {
        return TABLES.get(id);
    }

    public static Map<String, RewardTable> getAll() {
        return Collections.unmodifiableMap(TABLES);
    }

    public static void load(Path configDir) {
        TABLES.clear();
        FILES.clear();
        Path tablesDir = configDir.resolve("reward_tables");
        if (!Files.exists(tablesDir)) return;

        try (Stream<Path> walk = Files.walk(tablesDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".snbt"))
                    .forEach(file -> {
                        try {
                            String raw = Files.readString(file, StandardCharsets.UTF_8);
                            CompoundTag tag = TagParser.parseTag(raw);

                            if (!tag.contains("id") || tag.getString("id").isBlank()) {
                                String fname = file.getFileName().toString();
                                tag.putString("id", fname.substring(0, fname.lastIndexOf('.')));
                            }
                            RewardTable table = RewardTable.deserialize(tag);
                            if (table != null) {
                                TABLES.put(table.id(), table);
                                FILES.put(table.id(), file);
                            }
                        } catch (Exception e) {
                            System.err.println("[Phoenix Chronicles] Failed to load reward table '" +
                                    file.getFileName() + "': " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to walk reward_tables dir: " + e.getMessage());
            return;
        }

        System.out.println("[Phoenix Chronicles] Loaded " + TABLES.size() + " reward table(s).");
    }
}
