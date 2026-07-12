package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;

import org.jetbrains.annotations.NotNull;

/**
 * Inline popup for naming and icon-ing the questbook itself, shown above the category list -
 * the pack-level equivalent of CategoryThemeScreen's per-chapter name/icon editing.
 */
public class QuestbookTitleScreen extends Screen {

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 92;
    private static final int MARGIN = 12;
    private static final int FIELD_H = 13;
    private static final int STRIDE = FIELD_H + 8;
    private static final int ACCENT = 0xFF4499CC;

    private final Screen parent;
    private String cachedName;
    private String cachedIcon;

    private EditBox nameBox;
    private int panelLeft, panelTop;

    public QuestbookTitleScreen(Screen parent) {
        super(Component.literal("Questbook Title"));
        this.parent = parent;
        QuestChroniclesSettings s = QuestChroniclesSettings.get();
        this.cachedName = s.getQuestbookName();
        this.cachedIcon = s.getQuestbookIcon();
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_W) / 2;
        panelTop = (height - PANEL_H) / 2;

        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;
        int y = panelTop + 28;

        nameBox = new EditBox(font, fx, y + 9, fw, FIELD_H, Component.empty());
        nameBox.setMaxLength(48);
        nameBox.setHint(Component.literal("§8Quest Book"));
        nameBox.setValue(cachedName.equals("Quest Book") ? "" : cachedName);
        nameBox.setResponder(v -> cachedName = v.trim());
        addRenderableWidget(nameBox);
        y += STRIDE + 10;

        int iconPreviewW = FIELD_H + 4;
        addRenderableWidget(Button.builder(Component.literal("§7Change icon…"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                        cachedIcon = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())
                                .toString();
                    }));
                })
                .bounds(fx + iconPreviewW + 4, y, fw - iconPreviewW - 4, FIELD_H).build());
        y += STRIDE + 6;

        int btnY = panelTop + PANEL_H - 10 - 18;
        int half = (fw - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("§aSave"), b -> save())
                .bounds(fx, btnY, half, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§7Cancel"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(parent);
                })
                .bounds(fx + half + 6, btnY, half, 18).build());
    }

    private Item resolveIconPreview() {
        if (cachedIcon != null && !cachedIcon.isEmpty()) {
            try {
                Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(new net.minecraft.resources.ResourceLocation(cachedIcon));
                if (item != null && item != Items.AIR) return item;
            } catch (Exception ignored) {}
        }
        return Items.WRITTEN_BOOK;
    }

    private void save() {
        QuestChroniclesSettings s = QuestChroniclesSettings.get();
        s.setQuestbookName(cachedName);
        s.setQuestbookIcon(cachedIcon);
        s.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) { /* parent renders behind us */ }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        if (parent != null) parent.render(g, -1, -1, partial);

        // See CategoryThemeScreen/ToastDesignerScreen's render() for the full writeup - this
        // pack's rendering pipeline needs the parent's queued draws forced out before this
        // screen's own content, or the parent bleeds through what should be an opaque panel.
        g.flush();

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);

        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelLeft, panelTop, PANEL_W, PANEL_H, 22,
                "§bQuestbook Title", ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ACCENT, ChroniclesThemePalette.TEXT);

        int fx = panelLeft + MARGIN;
        int y = panelTop + 28;
        g.drawString(font, "§8Name", fx, y, ChroniclesThemePalette.TEXT_FAINT);
        y += STRIDE + 10;

        Item iconItem = resolveIconPreview();
        g.fill(fx, y, fx + FIELD_H + 4, y + FIELD_H + 4, 0xFF0B0B0F);
        ChroniclesUIKit.drawBorder(g, fx, y, FIELD_H + 4, FIELD_H + 4, 0xFF333344);
        g.renderItem(new ItemStack(iconItem), fx + 2, y + 2);

        g.flush();
        super.render(g, mx, my, partial);
        g.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
