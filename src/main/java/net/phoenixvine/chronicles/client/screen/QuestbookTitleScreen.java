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
 * Inline popup for naming and icon-ing the questbook itself.
 * Cleaned up with standard padding, comfortable spacing, and standard component heights.
 */
public class QuestbookTitleScreen extends Screen {

    // --- Layout Constants ---
    private static final int PANEL_W = 240;        // Slightly wider for better text flow
    private static final int PANEL_H = 145;        // Tall enough to comfortably fit elements
    private static final int MARGIN = 16;          // Generous breathing room from borders
    private static final int COMPONENT_H = 20;     // Minecraft standard height for buttons/inputs
    private static final int SPACING = 6;          // Vertical gap between related items
    private static final int SECTION_GAP = 14;     // Vertical gap between different sections
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
        int fw = PANEL_W - (MARGIN * 2);

        // Start below the header title area
        int currentY = panelTop + 28;

        // --- 1. Name Field Configuration ---
        // Title label is drawn in render(), field goes right under it
        nameBox = new EditBox(font, fx, currentY + 12, fw, COMPONENT_H, Component.empty());
        nameBox.setMaxLength(48);
        nameBox.setHint(Component.literal("§8Quest Book"));
        nameBox.setValue(cachedName.equals("Quest Book") ? "" : cachedName);
        nameBox.setResponder(v -> cachedName = v.trim());
        addRenderableWidget(nameBox);

        // Advance past label + text field + section break
        currentY += 12 + COMPONENT_H + SECTION_GAP;

        // --- 2. Icon Field Configuration ---
        int iconPreviewW = COMPONENT_H; // Keep it perfectly square with standard height
        addRenderableWidget(Button.builder(Component.literal("§7Change icon…"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                        cachedIcon = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())
                                .toString();
                    }));
                })
                .bounds(fx + iconPreviewW + 8, currentY + 12, fw - iconPreviewW - 8, COMPONENT_H).build());

        // --- 3. Bottom Action Buttons ---
        int btnY = panelTop + PANEL_H - MARGIN - COMPONENT_H;
        int halfBtnW = (fw - 6) / 2;

        addRenderableWidget(Button.builder(Component.literal("§aSave"), b -> save())
                .bounds(fx, btnY, halfBtnW, COMPONENT_H).build());

        addRenderableWidget(Button.builder(Component.literal("§7Cancel"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(parent);
                })
                .bounds(fx + halfBtnW + 6, btnY, halfBtnW, COMPONENT_H).build());
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

        g.flush();
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);

        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelLeft, panelTop, PANEL_W, PANEL_H, 22,
                "§bQuestbook Title", ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ACCENT, ChroniclesThemePalette.TEXT);

        int fx = panelLeft + MARGIN;
        int currentY = panelTop + 28;

        // Draw Name Label
        g.drawString(font, "§8Name", fx, currentY, ChroniclesThemePalette.TEXT_FAINT);

        // Step down to Icon section matching the init() layout math
        currentY += 12 + COMPONENT_H + SECTION_GAP;

        // Draw Icon Label
        g.drawString(font, "§8Icon", fx, currentY, ChroniclesThemePalette.TEXT_FAINT);

        // Render Icon Preview Slot (Now vertically perfectly aligned with the change button)
        int iconY = currentY + 12;
        g.fill(fx, iconY, fx + COMPONENT_H, iconY + COMPONENT_H, 0xFF0B0B0F);
        ChroniclesUIKit.drawBorder(g, fx, iconY, COMPONENT_H, COMPONENT_H, 0xFF333344);

        // Center item rendering inside the 20x20 slot (Minecraft items are 16x16)
        g.renderItem(new ItemStack(resolveIconPreview()), fx + 2, iconY + 2);

        g.flush();
        super.render(g, mx, my, partial);
        g.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
