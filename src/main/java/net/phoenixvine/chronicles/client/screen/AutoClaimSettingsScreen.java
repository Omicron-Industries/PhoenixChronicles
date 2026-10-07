package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.common.model.AutoClaimCategory;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.C2SSetAutoClaimPacket;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * The player's own auto-claim settings. Turning it on claims the covered rewards of every already-completed quest
 * right away, then of each quest as it completes. Rewards in a category left off are not touched.
 */
public class AutoClaimSettingsScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int HEADER_H = 20;
    private static final int ROW_H = 20;
    private static final int FOOTER_H = 26;

    private final Screen parent;
    private int panelX, panelY, panelH;

    public AutoClaimSettingsScreen(Screen parent) {
        super(Component.literal("Auto-claim Rewards"));
        this.parent = parent;
    }

    private @Nullable PlayerQuestData data() {
        return minecraft != null && minecraft.player != null ?
                minecraft.player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).orElse(null) : null;
    }

    private void apply(boolean enabled, EnumSet<AutoClaimCategory> categories) {
        PlayerQuestData data = data();
        if (data != null) {
            data.setAutoClaimEnabled(enabled);
            data.setAutoClaimCategories(categories);
        }
        ChronicleNetwork.CHANNEL.sendToServer(new C2SSetAutoClaimPacket(enabled, AutoClaimCategory.toMask(categories)));
    }

    @Override
    protected void init() {
        clearWidgets();
        AutoClaimCategory[] all = AutoClaimCategory.values();
        panelH = HEADER_H + 30 + ROW_H + all.length * ROW_H + 30 + FOOTER_H;
        panelX = width / 2 - PANEL_W / 2;
        panelY = height / 2 - panelH / 2;

        PlayerQuestData data = data();
        boolean enabled = data != null && data.isAutoClaimEnabled();
        EnumSet<AutoClaimCategory> categories = data != null ? data.getAutoClaimCategories() :
                EnumSet.noneOf(AutoClaimCategory.class);

        int y = panelY + HEADER_H + 8;
        addRenderableWidget(Button.builder(
                Component.literal(enabled ? "§a⚡ Auto-claim: ON" : "§7⚡ Auto-claim: OFF"),
                b -> {
                    apply(!enabled, categories);
                    init();
                })
                .bounds(panelX + 6, y, PANEL_W - 12, 16)
                .tooltip(Tooltip.create(Component.literal(
                        "Claim rewards for you as quests complete.\n" +
                                "Turning it on also claims quests you already finished that it covers.")))
                .build());

        y += ROW_H + 14;
        for (AutoClaimCategory category : all) {
            boolean on = categories.contains(category);
            addRenderableWidget(Button.builder(
                    Component.literal((on ? "§a✔ " : "§8☐ ") + (on ? "§f" : "§7") + category.label()),
                    b -> {
                        EnumSet<AutoClaimCategory> next = EnumSet.copyOf(categories.isEmpty() ?
                                EnumSet.noneOf(AutoClaimCategory.class) : categories);
                        if (!next.remove(category)) next.add(category);
                        apply(enabled, next);
                        init();
                    })
                    .bounds(panelX + 6, y, PANEL_W - 12, 16)
                    .tooltip(Tooltip.create(Component.literal(category.description())))
                    .build());
            y += ROW_H;
        }

        addRenderableWidget(Button.builder(Component.literal("§fDone"), b -> onClose())
                .bounds(panelX + PANEL_W - 66, panelY + panelH - FOOTER_H + 5, 60, 16).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelX, panelY, PANEL_W, panelH, HEADER_H,
                "Auto-claim Rewards");
        int hintY = panelY + HEADER_H + 8 + ROW_H + 2;
        g.drawString(font, ChroniclesUIKit.fitText(font, "§7Ticked kinds are claimed for you; the rest wait for you.",
                PANEL_W - 12), panelX + 6, hintY, ChroniclesThemePalette.TEXT_DIM, false);
        g.drawString(font, ChroniclesUIKit.fitText(font, "§8Anything left over stays in the claim screen.",
                PANEL_W - 12), panelX + 6, panelY + panelH - FOOTER_H - 12, ChroniclesThemePalette.TEXT_FAINT, false);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
