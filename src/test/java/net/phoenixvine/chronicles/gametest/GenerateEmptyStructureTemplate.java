package net.phoenixvine.chronicles.gametest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Disabled("one-off generator, not a real test - see class javadoc")
class GenerateEmptyStructureTemplate {

    @Test
    void generate() throws IOException {
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", 3465);

        ListTag size = new ListTag();
        size.add(net.minecraft.nbt.IntTag.valueOf(1));
        size.add(net.minecraft.nbt.IntTag.valueOf(1));
        size.add(net.minecraft.nbt.IntTag.valueOf(1));
        root.put("size", size);

        root.put("entities", new ListTag());

        CompoundTag paletteEntry = new CompoundTag();
        paletteEntry.put("Name", StringTag.valueOf("minecraft:air"));
        ListTag palette = new ListTag();
        palette.add(paletteEntry);
        root.put("palette", palette);

        CompoundTag block = new CompoundTag();
        block.put("pos", new IntArrayTag(new int[] { 0, 0, 0 }));
        block.putInt("state", 0);
        ListTag blocks = new ListTag();
        blocks.add(block);
        root.put("blocks", blocks);

        Path out = Path.of("src", "main", "resources", "data", "phoenix_chronicles", "structures",
                "gametest_empty.nbt");
        Files.createDirectories(out.getParent());
        NbtIo.writeCompressed(root, out.toFile());

        System.out.println("Wrote " + out.toAbsolutePath());
    }
}
