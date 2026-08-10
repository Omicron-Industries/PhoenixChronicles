package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.model.CategoryDefinition;
import net.phoenixvine.chronicles.registry.CategoryRegistry;

import org.jetbrains.annotations.NotNull;

public class CategoryThemeScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int MARGIN = 12;
    private static final int FIELD_H = 13;
    private static final int STRIDE = FIELD_H + 8;

    private static final int[] ROW_H_TABLE = { STRIDE + 10, STRIDE, STRIDE + 10, STRIDE + 10 };
    private static final int ROW_NAME = 0, ROW_ICON = 1, ROW_COLOR = 2, ROW_NAME_COLOR = 3;
    private static final int PANEL_H;

    static {
        int h = 28;
        for (int rh : ROW_H_TABLE) h += rh;
        PANEL_H = h + 12 + 24 + 10 + 18 + 10;
    }

    private static final int ACCENT = 0xFF884499;

    private final Screen parent;
    private final String categoryId;

    private String cachedDisplayName;
    private String cachedIcon;
    private int cachedColor;
    private int cachedNameColor;

    private EditBox nameBox;
    private EditBox colorBox;
    private EditBox nameColorBox;

    private int panelLeft, panelTop;

    public CategoryThemeScreen(Screen parent, String categoryId) {
        super(Component.literal("Category Theme"));
        this.parent = parent;
        this.categoryId = categoryId;

        CategoryDefinition cat = CategoryRegistry.get(categoryId);
        this.cachedDisplayName = cat != null ? cat.displayName() : categoryId;
        this.cachedIcon = cat != null ? cat.icon() : "";
        this.cachedColor = cat != null ? cat.color() : 0;
        this.cachedNameColor = cat != null ? cat.nameColor() : 0;
    }

    private int rowTop(int index) {
        int y = panelTop + 28;
        for (int i = 0; i < index; i++) y += ROW_H_TABLE[i];
        return y;
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_W) / 2;
        panelTop = (height - PANEL_H) / 2;

        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;

        nameBox = new EditBox(font, fx, rowTop(ROW_NAME) + 11, fw, FIELD_H, Component.empty());
        nameBox.setMaxLength(64);
        nameBox.setHint(Component.literal("§8&c etc. for color codes"));
        nameBox.setValue(cachedDisplayName);
        nameBox.setResponder(v -> cachedDisplayName = v.replace('&', '§').trim());
        addRenderableWidget(nameBox);

        int iconPreviewW = FIELD_H + 4;
        addRenderableWidget(Button.builder(Component.literal("§7Change Icon"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                        cachedIcon = key != null ? key.toString() : "";
                    }));
                })
                .bounds(fx + iconPreviewW + 4, rowTop(ROW_ICON), fw - iconPreviewW - 4, FIELD_H).build());

        colorBox = new EditBox(font, fx, rowTop(ROW_COLOR) + 11, fw, FIELD_H, Component.empty());
        colorBox.setMaxLength(7);
        colorBox.setHint(Component.literal("§8#RRGGBB  (empty = default)"));
        colorBox.setValue(cachedColor != 0 ? ChroniclesUIKit.formatHexColor(cachedColor) : "");
        colorBox.setResponder(v -> cachedColor = ChroniclesUIKit.parseHexColor(v, 0));
        addRenderableWidget(colorBox);

        nameColorBox = new EditBox(font, fx, rowTop(ROW_NAME_COLOR) + 11, fw, FIELD_H, Component.empty());
        nameColorBox.setMaxLength(7);
        nameColorBox.setHint(Component.literal("§8#RRGGBB  (empty = use accent color)"));
        nameColorBox.setValue(cachedNameColor != 0 ? ChroniclesUIKit.formatHexColor(cachedNameColor) : "");
        nameColorBox.setResponder(v -> cachedNameColor = ChroniclesUIKit.parseHexColor(v, 0));
        addRenderableWidget(nameColorBox);

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
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(cachedIcon));
                if (item != null && item != Items.AIR) return item;
            } catch (Exception ignored) {}
        }
        return Items.CHEST;
    }

    private void save() {
        CategoryRegistry.renameCategory(categoryId, cachedDisplayName.isEmpty() ? categoryId : cachedDisplayName);
        CategoryRegistry.updateTheme(categoryId, cachedColor, cachedIcon, cachedNameColor);
        CategoryRegistry.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);

        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelLeft, panelTop, PANEL_W, PANEL_H, 22,
                "§dCategory Theme — §7" + categoryId, ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ACCENT, ChroniclesThemePalette.TEXT);

        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;

        g.drawString(font, "§8Display Name", fx, rowTop(ROW_NAME), ChroniclesThemePalette.TEXT_FAINT);

        int iconRowY = rowTop(ROW_ICON);
        g.fill(fx, iconRowY, fx + FIELD_H + 4, iconRowY + FIELD_H + 4, 0xFF0B0B0F);
        ChroniclesUIKit.drawBorder(g, fx, iconRowY, FIELD_H + 4, FIELD_H + 4, 0xFF333344);
        g.renderItem(new ItemStack(resolveIconPreview()), fx + 1, iconRowY + 1);

        g.drawString(font, "§8Accent Color", fx, rowTop(ROW_COLOR), ChroniclesThemePalette.TEXT_FAINT);
        g.drawString(font, "§8Display Name Color", fx, rowTop(ROW_NAME_COLOR), ChroniclesThemePalette.TEXT_FAINT);

        int previewY = rowTop(ROW_NAME_COLOR) + ROW_H_TABLE[ROW_NAME_COLOR] + 12;
        int bg = (cachedColor != 0) ? (0xFF000000 | cachedColor) : 0xFF0B0B0F;
        g.fill(fx, previewY, fx + fw, previewY + 24, bg);
        ChroniclesUIKit.drawBorder(g, fx, previewY, fw, 24, 0xFF333344);
        g.drawCenteredString(font, "§7preview", fx + fw / 2, previewY + 8, 0xFFAAAAAA);

        g.flush();
        super.render(g, mx, my, partial);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && (mx < panelLeft || mx >= panelLeft + PANEL_W || my < panelTop || my >= panelTop + PANEL_H)) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
