package net.phoenixvine.chronicles.client.util;

import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.model.FullQuestData;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public final class MarkdownUtils {

    private MarkdownUtils() {}

    public static FullQuestData loadMarkdownContent(Path mdPath) {
        Component title = Component.empty();
        StringBuilder desc = new StringBuilder();

        boolean pendingParagraphBreak = false;
        try (BufferedReader r = Files.newBufferedReader(mdPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("# ") && title.getString().isEmpty()) {
                    title = Component.literal(t.substring(2).trim());
                } else if (t.matches("-{3,}")) {
                    if (!desc.isEmpty()) desc.append("\n\n");
                    desc.append(t);
                    pendingParagraphBreak = true;
                } else if (!t.startsWith("#") && !t.isEmpty()) {
                    if (!desc.isEmpty()) desc.append(pendingParagraphBreak ? "\n\n" : ' ');
                    desc.append(t);
                    pendingParagraphBreak = false;
                } else if (t.isEmpty()) {
                    pendingParagraphBreak = true;
                }
            }
        } catch (IOException ignored) {}

        return new FullQuestData(title, Component.literal(desc.toString().trim()), List.of(), Collections.emptyList(),
                Collections.emptyList());
    }
}
