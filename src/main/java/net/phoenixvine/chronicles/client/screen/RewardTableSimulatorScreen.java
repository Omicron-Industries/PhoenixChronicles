package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.common.model.QuestReward;
import net.phoenixvine.chronicles.common.model.RewardTable;
import net.phoenixvine.chronicles.common.registry.RewardTableRegistry;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RewardTableSimulatorScreen extends Screen {

    private static final int ROLLS = 1000;

    private final Screen parent;
    private final String tableId;
    private final RewardTable directTable;

    private record Result(String summary, int hits) {}

    private List<Result> results = List.of();
    private String statusLine = "";

    public RewardTableSimulatorScreen(Screen parent, String tableId) {
        super(Component.translatable("phoenix_chronicles.screen.reward_simulator.title"));
        this.parent = parent;
        this.tableId = tableId;
        this.directTable = null;
    }

    public RewardTableSimulatorScreen(Screen parent, RewardTable table) {
        super(Component.translatable("phoenix_chronicles.screen.reward_simulator.title"));
        this.parent = parent;
        this.tableId = null;
        this.directTable = table;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int midX = width / 2;

        addRenderableWidget(
                Button.builder(Component.translatable("phoenix_chronicles.screen.reward_simulator.roll_again", ROLLS),
                        b -> roll()).bounds(midX - 100, height / 2 + 60, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("phoenix_chronicles.ui.done_bracketed_lower"), b -> {
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(midX - 50, height / 2 + 84, 100, 20).build());

        roll();
    }

    private void roll() {
        RewardTable table = directTable != null ? directTable : RewardTableRegistry.get(tableId);
        if (table == null) {
            statusLine = "§cTable '" + tableId + "' not found -- check the id and that it's loaded.";
            results = List.of();
            return;
        }
        if (table.entries().isEmpty()) {
            statusLine = "§7Table has no entries.";
            results = List.of();
            return;
        }
        if (table.pickCount() <= 0) {
            statusLine = "§7No randomness -- this table grants every entry, every time.";
            results = table.entries().stream()
                    .map(e -> new Result(e.reward() != null ? e.reward().getSummary().getString() : "?", ROLLS))
                    .toList();
            return;
        }

        Map<QuestReward, Integer> hits = new IdentityHashMap<>();
        Random random = new Random();
        for (int i = 0; i < ROLLS; i++) {
            for (QuestReward r : QuestReward.pickWeighted(table.entries(), table.pickCount(), random)) {
                hits.merge(r, 1, Integer::sum);
            }
        }

        List<Result> out = new ArrayList<>();
        for (var entry : table.entries()) {
            if (entry.reward() == null) continue;
            out.add(new Result(entry.reward().getSummary().getString(), hits.getOrDefault(entry.reward(), 0)));
        }
        out.sort((a, c) -> Integer.compare(c.hits(), a.hits()));
        results = out;
        statusLine = "§7" + table.displayName() + " -- §f" + table.pickCount() + "§7/" + table.entries().size() +
                " picked per roll, " + ROLLS + " rolls simulated";
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int panelW = 300, panelH = 220;
        int panelX = width / 2 - panelW / 2, panelY = height / 2 - panelH / 2;
        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelX, panelY, panelW, panelH, 20,
                "Reward Table Simulator");

        g.drawCenteredString(font, statusLine, width / 2, panelY + 30, ChroniclesThemePalette.TEXT);

        int rowY = panelY + 46;
        int rowH = 11;
        int maxRows = Math.max(0, (height / 2 + 54 - rowY) / rowH);
        for (int i = 0; i < Math.min(results.size(), maxRows); i++) {
            Result r = results.get(i);
            double pct = 100.0 * r.hits() / ROLLS;
            String line = ChroniclesUIKit.fitText(font, r.summary(), panelW - 90);
            g.drawString(font, "§7" + line, panelX + 10, rowY, ChroniclesThemePalette.TEXT_DIM, false);
            String pctStr = String.format("§f%.1f%% §8(%d/%d)", pct, r.hits(), ROLLS);
            g.drawString(font, pctStr, panelX + panelW - 10 - font.width(pctStr), rowY,
                    ChroniclesThemePalette.TEXT, false);
            rowY += rowH;
        }
        if (results.size() > maxRows) {
            g.drawCenteredString(font, "§8… " + (results.size() - maxRows) + " more", width / 2, rowY,
                    ChroniclesThemePalette.TEXT_FAINT);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
