package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.client.ChapterConfig;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import java.util.List;

public class ItemLookupResultsScreen extends Screen {

    private static final int HEADER_H = 32;
    private static final int FOOTER_H = 28;
    private static final int MARGIN = 12;
    private static final int CARD_H = 36;
    private static final int CARD_GAP = 6;
    private static final int ICON_SZ = 20;

    private final ItemStack lookupStack;
    private final List<QuestNode> matches;
    private int scrollY = 0;

    public ItemLookupResultsScreen(ItemStack lookupStack, List<QuestNode> matches) {
        super(Component.literal("Item Lookup"));
        this.lookupStack = lookupStack;
        this.matches = matches;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("§7‹ Close"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(null);
                })
                .bounds(width / 2 - 40, height - FOOTER_H + 5, 80, 18).build());
    }

    private int listTop() {
        return HEADER_H + 6;
    }

    private int listBottom() {
        return height - FOOTER_H - 6;
    }

    private int cardsLeft() {
        return Math.max(MARGIN, width / 2 - 220);
    }

    private int cardsRight() {
        return Math.min(width - MARGIN, width / 2 + 220);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        PhoenixTheme t = PhoenixTheme.current();
        int bg = t.bg.getColor();
        int panel = t.panel.getColor();
        int header = t.header.getColor();
        int border = t.border.getColor();
        int text = t.text.getColor();
        int textDim = t.textDim.getColor();
        int accent = t.accent.getColor();

        g.fill(0, 0, width, height, bg);
        g.fill(0, 0, width, HEADER_H, header);
        g.fill(0, HEADER_H - 1, width, HEADER_H, border);

        String itemName = lookupStack.getHoverName().getString();
        g.drawCenteredString(font, "§f" + itemName + " §8— §7used by " + matches.size() + " quest(s)", width / 2,
                10, text);

        g.fill(0, height - FOOTER_H, width, height, header);
        g.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, border);

        int cardsLeft = cardsLeft();
        int cardsRight = cardsRight();
        int cardW = cardsRight - cardsLeft;

        g.enableScissor(0, listTop(), width, listBottom());
        int cy = listTop() - scrollY;
        for (QuestNode node : matches) {
            if (cy + CARD_H > listTop() && cy < listBottom()) {
                boolean hov = mx >= cardsLeft && mx < cardsRight && my >= cy && my < cy + CARD_H;
                g.fill(cardsLeft, cy, cardsRight, cy + CARD_H, hov ? blend(panel) : panel);
                net.phoenixvine.chronicles.client.render.ChroniclesUIKit.drawBorder(g, cardsLeft, cy, cardW, CARD_H,
                        hov ? accent : border);

                int iconX = cardsLeft + 8;
                int iconY = cy + (CARD_H - ICON_SZ) / 2;
                g.fill(iconX, iconY, iconX + ICON_SZ, iconY + ICON_SZ, 0xFF0B0B0F);
                Item icon = resolveCardIcon(node);
                if (icon != null) {
                    g.pose().pushPose();
                    g.pose().translate(iconX + 2, iconY + 2, 0f);
                    g.renderItem(new ItemStack(icon), 0, 0);
                    g.pose().popPose();
                }

                int textX = iconX + ICON_SZ + 10;
                int maxTextW = cardsRight - textX - 8;
                String title = node.getTitle().getString();
                if (font.width(title) > maxTextW) title = font.plainSubstrByWidth(title, maxTextW - 6) + "…";
                g.drawString(font, "§f" + title, textX, cy + 8, text, false);

                String chapter = node.getChapter();
                String chapterLabel = chapter != null ? ChapterConfig.getResolvedDisplayName(chapter) : null;
                if (chapterLabel == null) chapterLabel = chapter;
                if (chapterLabel != null) {
                    if (font.width(chapterLabel) > maxTextW)
                        chapterLabel = font.plainSubstrByWidth(chapterLabel, maxTextW - 6) + "…";
                    g.drawString(font, "§8" + chapterLabel, textX, cy + 20, textDim, false);
                }

                if (hov) g.drawString(font, "§7→", cardsRight - 16, cy + (CARD_H - 8) / 2, accent, false);
            }
            cy += CARD_H + CARD_GAP;
        }
        g.disableScissor();

        if (matches.isEmpty()) {
            g.drawCenteredString(font, "§8No matches.", width / 2, (listTop() + listBottom()) / 2, textDim);
        }

        super.render(g, mx, my, partial);
    }

    private static int blend(int base) {
        return base | 0x22FFFFFF;
    }

    private static Item resolveCardIcon(QuestNode node) {
        Item icon = node.getIconItem();
        if (icon != null && icon != Items.AIR) return icon;
        for (QuestTask task : node.getTasks()) {
            ResourceLocation id = task.getDisplayItemId();
            if (id == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) return item;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            int cardsLeft = cardsLeft();
            int cardsRight = cardsRight();
            int cy = listTop() - scrollY;
            for (QuestNode node : matches) {
                if (my >= cy && my < cy + CARD_H && mx >= cardsLeft && mx < cardsRight &&
                        cy >= listTop() - CARD_H && cy < listBottom()) {
                    if (minecraft != null) {
                        ChronicleOverviewScreen screen = new ChronicleOverviewScreen();
                        minecraft.setScreen(screen);
                        screen.navigateToNode(node);
                    }
                    return true;
                }
                cy += CARD_H + CARD_GAP;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int contentH = matches.size() * (CARD_H + CARD_GAP);
        int maxScroll = Math.max(0, contentH - (listBottom() - listTop()));
        scrollY = Math.max(0, Math.min(maxScroll, scrollY - (int) (delta * (CARD_H + CARD_GAP))));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            if (minecraft != null) minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
