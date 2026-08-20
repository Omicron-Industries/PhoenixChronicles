package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.chronicles.model.QuestGroup;
import net.phoenixvine.chronicles.model.QuestGroupManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class QuestGroupEditorScreen extends Screen {

    private static final int DIALOG_W = 250;
    private static final int ROW_H = 22;
    private static final int FIELD_H = 14;
    private static final boolean PHANTASIA = PhantasiaCompat.isAvailable();
    private static final int PREVIEW_H = 70;

    private int dialogH;
    private int dx, dy;

    private static final int C_BORDER = 0xFF8844AA;

    private final Screen parent;
    private final String chapter;
    @Nullable
    private final QuestGroup existing;
    private final int canvasX;
    private final int canvasY;

    private EditBox labelBox;
    private EditBox colorBox;
    private EditBox borderColorBox;
    private EditBox widthBox;
    private EditBox heightBox;
    private EditBox phantasiaIdBox;
    private String errorMsg = "";

    private final List<QuestGroup.GroupIcon> icons = new ArrayList<>();
    private int hoveredIconIndex = -1;
    private int iconStripY;

    @Nullable
    private Object phantasiaPreview = null;
    private int previewX, previewY, previewW;

    public QuestGroupEditorScreen(Screen parent, String chapter, @Nullable QuestGroup existing,
                                  int canvasX, int canvasY) {
        super(Component.literal(existing == null ? "New Quest Group" : "Edit Quest Group"));
        this.parent = parent;
        this.chapter = chapter;
        this.existing = existing;
        this.canvasX = canvasX;
        this.canvasY = canvasY;
        if (existing != null) icons.addAll(existing.getIcons());
    }

    @Override
    protected void init() {
        dialogH = 30 + ROW_H * 4 + 20 + 16 + 24 + 30;
        if (PHANTASIA) dialogH += ROW_H + PREVIEW_H + 6;

        dx = (width - DIALOG_W) / 2;
        dy = (height - dialogH) / 2;

        int fieldX = dx + 10;
        int fieldW = DIALOG_W - 20;
        int row = dy + 30;

        labelBox = new EditBox(font, fieldX, row, fieldW, FIELD_H, Component.empty());
        labelBox.setHint(Component.literal("§8Group label…"));
        labelBox.setMaxLength(48);
        if (existing != null) labelBox.setValue(existing.getLabel());
        addRenderableWidget(labelBox);
        row += ROW_H;

        colorBox = new EditBox(font, fieldX, row, fieldW, FIELD_H, Component.empty());
        colorBox.setHint(Component.literal("§8#AARRGGBB fill color"));
        colorBox.setMaxLength(10);
        colorBox.setValue(existing != null ? QuestGroupManager.formatColor(existing.getColor()) : "#22FFFFFF");
        addRenderableWidget(colorBox);
        row += ROW_H;

        borderColorBox = new EditBox(font, fieldX, row, fieldW, FIELD_H, Component.empty());
        borderColorBox.setHint(Component.literal("§8#AARRGGBB border color"));
        borderColorBox.setMaxLength(10);
        borderColorBox
                .setValue(existing != null ? QuestGroupManager.formatColor(existing.getBorderColor()) : "#44FFFFFF");
        addRenderableWidget(borderColorBox);
        row += ROW_H;

        int halfW = (fieldW - 6) / 2;
        widthBox = new EditBox(font, fieldX, row, halfW, FIELD_H, Component.empty());
        widthBox.setHint(Component.literal("§8Width"));
        widthBox.setMaxLength(5);
        widthBox.setValue(String.valueOf(existing != null ? existing.getWidth() : 120));
        addRenderableWidget(widthBox);
        heightBox = new EditBox(font, fieldX + halfW + 6, row, halfW, FIELD_H, Component.empty());
        heightBox.setHint(Component.literal("§8Height"));
        heightBox.setMaxLength(5);
        heightBox.setValue(String.valueOf(existing != null ? existing.getHeight() : 80));
        addRenderableWidget(heightBox);
        row += ROW_H;

        int iconBtnW = (fieldW - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("§7+ Item"), b -> {
            if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id != null) icons.add(new QuestGroup.GroupIcon(QuestGroup.IconKind.ITEM, id.toString()));
            }));
        }).bounds(fieldX, row, iconBtnW, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("§3+ Fluid"), b -> {
            if (minecraft != null) minecraft.setScreen(
                    new FluidPickerScreen(this,
                            fluidId -> icons.add(new QuestGroup.GroupIcon(QuestGroup.IconKind.FLUID, fluidId))));
        }).bounds(fieldX + iconBtnW + 4, row, iconBtnW, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("§d+ Texture"), b -> {
            if (minecraft != null) minecraft.setScreen(new TextureBrowserScreen(this,
                    texId -> icons.add(new QuestGroup.GroupIcon(QuestGroup.IconKind.TEXTURE, texId))));
        }).bounds(fieldX + (iconBtnW + 4) * 2, row, iconBtnW, FIELD_H).build());
        row += FIELD_H + 6;

        iconStripY = row;
        row += 20;

        if (PHANTASIA) {
            phantasiaIdBox = new EditBox(font, fieldX, row, fieldW - 60, FIELD_H, Component.empty());
            phantasiaIdBox.setHint(Component.literal("§8Phantasia machine id (optional)"));
            phantasiaIdBox.setMaxLength(128);
            if (existing != null) phantasiaIdBox.setValue(existing.getPhantasiaMachineId());
            addRenderableWidget(phantasiaIdBox);
            addRenderableWidget(Button.builder(Component.literal("§7▶ Preview"), b -> refreshPhantasiaPreview())
                    .bounds(fieldX + fieldW - 56, row, 56, FIELD_H).build());
            row += ROW_H;

            previewX = fieldX;
            previewY = row;
            previewW = fieldW;
            row += PREVIEW_H + 6;

            if (existing != null && !existing.getPhantasiaMachineId().isEmpty()) refreshPhantasiaPreview();
        }

        row += 6;

        int btnY = dy + dialogH - 24;
        int btnW = existing != null ? (DIALOG_W - 30) / 3 : (DIALOG_W - 20) / 2;

        addRenderableWidget(Button.builder(Component.literal("§aSave"), b -> onSave())
                .bounds(dx + 10, btnY, btnW, 14).build());

        if (existing != null) {
            addRenderableWidget(Button.builder(Component.literal("§cDelete"), b -> onDelete())
                    .bounds(dx + 10 + btnW + 5, btnY, btnW, 14).build());
            addRenderableWidget(Button.builder(Component.literal("§8Cancel"), b -> close())
                    .bounds(dx + 10 + (btnW + 5) * 2, btnY, btnW, 14).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("§8Cancel"), b -> close())
                    .bounds(dx + 10 + btnW + 5, btnY, btnW, 14).build());
        }
    }

    private void refreshPhantasiaPreview() {
        if (phantasiaPreview != null) {
            PhantasiaCompat.closePreview(phantasiaPreview);
            phantasiaPreview = null;
        }
        if (phantasiaIdBox == null) return;
        String id = phantasiaIdBox.getValue().trim();
        if (!id.isEmpty()) phantasiaPreview = PhantasiaCompat.createPreview(id);
    }

    private void onSave() {
        String label = labelBox.getValue().trim();
        if (label.isEmpty()) {
            errorMsg = "Label cannot be empty.";
            return;
        }

        String colorStr = colorBox.getValue().trim();
        String borderStr = borderColorBox.getValue().trim();

        if (!isValidColor(colorStr)) {
            errorMsg = "Invalid fill color (use #AARRGGBB).";
            return;
        }
        if (!isValidColor(borderStr)) {
            errorMsg = "Invalid border color (use #AARRGGBB).";
            return;
        }

        int w = parseSize(widthBox.getValue(), 120);
        int h = parseSize(heightBox.getValue(), 80);

        QuestGroup group;
        if (existing != null) {
            group = existing;
        } else {
            group = new QuestGroup(QuestGroupManager.generateId(), label, chapter);
            group.setX(canvasX);
            group.setY(canvasY);
        }

        group.setLabel(label);
        group.setColor(QuestGroupManager.parseColor(colorStr));
        group.setBorderColor(QuestGroupManager.parseColor(borderStr));
        group.setChapter(chapter);
        group.setSize(w, h);
        group.clearIcons();
        for (QuestGroup.GroupIcon icon : icons) group.addIcon(icon.kind, icon.id);
        group.setPhantasiaMachineId(phantasiaIdBox != null ? phantasiaIdBox.getValue().trim() : "");

        QuestGroupManager.put(group);
        QuestGroupManager.save(configPath());

        close();
    }

    private static int parseSize(String s, int fallback) {
        try {
            return Math.max(20, Integer.parseInt(s.trim()));
        } catch (Exception e) {
            return fallback;
        }
    }

    private void onDelete() {
        if (existing != null) {
            QuestGroupManager.remove(existing.getId());
            QuestGroupManager.save(configPath());
        }
        close();
    }

    private void close() {
        if (phantasiaPreview != null) PhantasiaCompat.closePreview(phantasiaPreview);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (phantasiaPreview != null) PhantasiaCompat.closePreview(phantasiaPreview);
        super.onClose();
    }

    private boolean isValidColor(String s) {
        if (s == null) return false;
        String hex = s.startsWith("#") ? s.substring(1) : s;
        return (hex.length() == 6 || hex.length() == 8) && hex.matches("[0-9A-Fa-f]+");
    }

    private Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles");
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        g.flush();

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);

        g.fill(dx, dy, dx + DIALOG_W, dy + dialogH, ChroniclesThemePalette.PANEL);
        ChroniclesUIKit.drawBorder(g, dx, dy, DIALOG_W, dialogH, C_BORDER);

        g.fill(dx + 1, dy + 1, dx + DIALOG_W - 1, dy + 16, ChroniclesThemePalette.HEADER);
        g.drawCenteredString(font, "§d" + this.title.getString(), dx + DIALOG_W / 2, dy + 4,
                ChroniclesThemePalette.TEXT);

        int row = dy + 30;

        g.drawString(font, "§8Label", dx + 10, row - 11, ChroniclesThemePalette.TEXT_FAINT);
        row += ROW_H;
        g.drawString(font, "§8Fill color  §7(#AARRGGBB)", dx + 10, row - 11, ChroniclesThemePalette.TEXT_FAINT);
        row += ROW_H;
        g.drawString(font, "§8Border color  §7(#AARRGGBB)", dx + 10, row - 11, ChroniclesThemePalette.TEXT_FAINT);
        row += ROW_H;
        g.drawString(font, "§8Size (W × H)", dx + 10, row - 11, ChroniclesThemePalette.TEXT_FAINT);
        row += ROW_H;

        g.drawString(font, "§8Icons  §7(click to remove)", dx + 10, row - 8, ChroniclesThemePalette.TEXT_FAINT);
        row += FIELD_H + 6;
        renderIconStrip(g, mx, my, dx + 10, iconStripY);
        row += 20;

        if (PHANTASIA) {
            g.drawString(font, "§8Phantasia preview §7(optional)", dx + 10, row - 11,
                    ChroniclesThemePalette.TEXT_FAINT);
            row += ROW_H;
            renderPhantasiaPreview(g, mx, my, partial);
            row += PREVIEW_H + 6;
        }

        row += 6;

        g.drawString(font, "§8Chapter: §7" + friendlyChapter(), dx + 10, row + 2, ChroniclesThemePalette.TEXT_DIM);

        if (!errorMsg.isEmpty()) {
            g.drawCenteredString(font, "§c" + errorMsg, dx + DIALOG_W / 2, dy + dialogH - 36, 0xFFCC4444);
        }

        g.flush();
        super.render(g, mx, my, partial);

        g.flush();
        g.pose().popPose();
    }

    private void renderIconStrip(GuiGraphics g, int mx, int my, int x, int y) {
        int sz = 14, gap = 3;
        hoveredIconIndex = -1;
        if (icons.isEmpty()) {
            g.drawString(font, "§8(none)", x, y + 3, ChroniclesThemePalette.TEXT_FAINT, false);
            return;
        }
        int ix = x;
        for (int i = 0; i < icons.size(); i++) {
            boolean hov = mx >= ix && mx < ix + sz && my >= y && my < y + sz;
            if (hov) hoveredIconIndex = i;
            renderIcon(g, icons.get(i), ix, y, sz);
            if (hov) {
                g.fill(ix, y, ix + sz, y + sz, 0x66CC2222);
                g.drawCenteredString(font, "§c✕", ix + sz / 2, y + sz / 2 - 4, 0xFFFFFFFF);
            }
            ix += sz + gap;
        }
    }

    private void renderIcon(GuiGraphics g, QuestGroup.GroupIcon icon, int x, int y, int size) {
        try {
            switch (icon.kind) {
                case ITEM -> {
                    Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(icon.id));
                    if (item == null || item == Items.AIR) return;
                    float scale = size / 16f;
                    g.pose().pushPose();
                    g.pose().translate(x + size / 2f, y + size / 2f, 0f);
                    g.pose().scale(scale, scale, scale);
                    g.renderItem(new ItemStack(item), -8, -8);
                    g.pose().popPose();
                }
                case FLUID -> {
                    Fluid fluid = ForgeRegistries.FLUIDS.getValue(ResourceLocation.parse(icon.id));
                    ChroniclesUIKit.drawFluidIcon(g, fluid, x, y, size);
                    ChroniclesUIKit.drawBorder(g, x, y, size, size, 0xFF444455);
                }
                case TEXTURE -> g.blit(ResourceLocation.parse(icon.id), x, y, 0, 0, size, size, size, size);
            }
        } catch (Exception ignored) {

        }
    }

    private void renderPhantasiaPreview(GuiGraphics g, int mx, int my, float partial) {
        g.fill(previewX, previewY, previewX + previewW, previewY + PREVIEW_H, 0xFF0A0A10);
        ChroniclesUIKit.drawBorder(g, previewX, previewY, previewW, PREVIEW_H, ChroniclesThemePalette.BORDER);
        if (phantasiaPreview == null) {
            g.drawCenteredString(font, "§8Enter a machine id and press Preview",
                    previewX + previewW / 2, previewY + PREVIEW_H / 2 - 4, ChroniclesThemePalette.TEXT_FAINT);
            return;
        }
        PhantasiaCompat.tickPreview(phantasiaPreview);
        PhantasiaCompat.renderPreview(phantasiaPreview, g, previewX + 1, previewY + 1, previewW - 2, PREVIEW_H - 2,
                partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && hoveredIconIndex >= 0 && hoveredIconIndex < icons.size()) {
            icons.remove(hoveredIconIndex);
            hoveredIconIndex = -1;
            return true;
        }
        if (phantasiaPreview != null && PhantasiaCompat.previewMouseClicked(phantasiaPreview, mx, my, previewX + 1,
                previewY + 1, previewW - 2, PREVIEW_H - 2, this)) {
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    private String friendlyChapter() {
        if (chapter == null || chapter.equals("ALL")) return "All Chapters";
        StringBuilder sb = new StringBuilder();
        for (String w : chapter.toLowerCase().replace("_", " ").split(" ")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
