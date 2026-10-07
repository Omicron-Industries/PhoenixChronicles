package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class EntityIdPickerScreen extends Screen {

    private static final int COL_HOVER = 0xFF1E1E2A;

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 220;
    private static final int HEADER_H = 22;
    private static final int SEARCH_H = 16;
    private static final int FOOTER_H = 16;
    private static final int ROW_H = 22;
    private static final int ICON_W = 22;

    private final Screen parent;
    private final String headerLabel;
    private final List<ResourceLocation> allIds;
    private final List<ResourceLocation> displayIds = new ArrayList<>();
    private final Consumer<ResourceLocation> onPick;

    private final Map<ResourceLocation, Optional<LivingEntity>> previewCache = new HashMap<>();

    private boolean mobsFiltered = false;
    private int scrollOffset = 0;
    private ResourceLocation hoveredId = null;

    private EditBox searchBox;
    private String searchQuery = "";

    private int panelLeft, panelTop;

    public EntityIdPickerScreen(Screen parent, String headerLabel, Collection<ResourceLocation> ids,
                                Consumer<ResourceLocation> onPick) {
        super(Component.literal(headerLabel));
        this.parent = parent;
        this.headerLabel = headerLabel;
        this.allIds = new ArrayList<>(ids);
        this.allIds.sort(null);
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        clearWidgets();
        panelLeft = (width - PANEL_W) / 2;
        panelTop = (height - PANEL_H) / 2;

        int searchY = panelTop + HEADER_H + 2;
        keepMobsOnly();

        searchBox = new EditBox(font, panelLeft + 4, searchY, PANEL_W - 8, SEARCH_H, Component.empty());
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("phoenix_chronicles.ui.search_hint"));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(q -> {
            searchQuery = q;
            scrollOffset = 0;
            rebuildList();
        });
        addRenderableWidget(searchBox);

        rebuildList();
    }

    /**
     * The registry holds every entity - items, projectiles, boats, paintings, the player. Only things that can
     * actually be killed or fought are useful here, so keep the ones that spawn as a living entity.
     */
    private void keepMobsOnly() {
        if (mobsFiltered || minecraft == null || minecraft.level == null) return;
        mobsFiltered = true;
        allIds.removeIf(id -> previewFor(id).map(e -> e instanceof ArmorStand).orElse(true));
    }

    private void rebuildList() {
        displayIds.clear();
        String q = searchQuery.toLowerCase().trim();
        for (ResourceLocation id : allIds) {
            if (q.isEmpty() || id.toString().contains(q)) displayIds.add(id);
        }
    }

    private Optional<LivingEntity> previewFor(ResourceLocation id) {
        return previewCache.computeIfAbsent(id, i -> {
            if (minecraft == null || minecraft.level == null) return Optional.empty();
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(i);
            if (type == null) return Optional.empty();
            // The player type can't be created from the registry; the local player stands in for its preview.
            if (type == EntityType.PLAYER) return Optional.ofNullable(minecraft.player);
            try {
                Optional<LivingEntity> living = Optional.ofNullable(type.create(minecraft.level))
                        .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast);
                living.ifPresent(EntityIdPickerScreen::faceViewer);
                return living;
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    /**
     * Entities default to yaw 0, which faces away from the screen here; inventory previews turn them half way round.
     */
    private static void faceViewer(LivingEntity entity) {
        float yaw = 180f + 25f;
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setXRot(0f);
        entity.xRotO = 0f;
        entity.yBodyRot = yaw;
        entity.yBodyRotO = yaw;
        entity.yHeadRot = yaw;
        entity.yHeadRotO = yaw;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        g.flush();

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);
        g.flush();
        g.fill(0, 0, width, height, ChroniclesThemePalette.BG);

        g.fill(panelLeft, panelTop, panelLeft + PANEL_W, panelTop + PANEL_H, ChroniclesThemePalette.PANEL);
        ChroniclesUIKit.drawBorder(g, panelLeft, panelTop, PANEL_W, PANEL_H, ChroniclesThemePalette.BORDER_LIT);

        g.fill(panelLeft, panelTop, panelLeft + PANEL_W, panelTop + HEADER_H, ChroniclesThemePalette.HEADER);
        g.fill(panelLeft, panelTop + HEADER_H - 1, panelLeft + PANEL_W, panelTop + HEADER_H,
                ChroniclesThemePalette.BORDER);
        g.drawCenteredString(font, "§f" + headerLabel, panelLeft + PANEL_W / 2, panelTop + 7,
                ChroniclesThemePalette.TEXT);

        int footerY = panelTop + PANEL_H - FOOTER_H;
        g.fill(panelLeft, footerY, panelLeft + PANEL_W, footerY + 1, ChroniclesThemePalette.BORDER);
        g.fill(panelLeft, footerY, panelLeft + PANEL_W, panelTop + PANEL_H, ChroniclesThemePalette.PANEL_DARK);
        g.drawString(font, "§8" + displayIds.size() + " - Esc to cancel", panelLeft + 4, footerY + 4,
                ChroniclesThemePalette.TEXT_FAINT);

        super.render(g, mx, my, partial);

        int listTop = panelTop + HEADER_H + SEARCH_H + 4;
        int listBottom = footerY - 2;
        int visRows = Math.max(1, (listBottom - listTop) / ROW_H);

        g.enableScissor(panelLeft, listTop, panelLeft + PANEL_W, listBottom);
        hoveredId = null;

        for (int i = 0; i < visRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= displayIds.size()) break;
            ResourceLocation id = displayIds.get(idx);
            int ry = listTop + i * ROW_H;

            boolean hov = mx >= panelLeft + 2 && mx < panelLeft + PANEL_W - 2 && my >= ry && my < ry + ROW_H;
            if (hov) g.fill(panelLeft + 2, ry, panelLeft + PANEL_W - 2, ry + ROW_H, COL_HOVER);

            previewFor(id).ifPresent(entity -> renderIcon(g, entity, panelLeft + 2 + ICON_W / 2, ry + ROW_H - 3));

            String idStr = id.toString();
            int textX = panelLeft + 4 + ICON_W;
            int maxW = PANEL_W - 8 - ICON_W;
            if (font.width(idStr) > maxW) idStr = font.plainSubstrByWidth(idStr, maxW - 6) + "…";
            g.drawString(font, (hov ? "§f" : "§7") + idStr, textX, ry + ROW_H / 2 - 4,
                    ChroniclesThemePalette.TEXT_DIM);

            if (hov) hoveredId = id;
        }
        g.disableScissor();

        if (hoveredId != null) {
            g.renderTooltip(font, Component.literal(hoveredId.toString()), mx, my);
        }

        g.pose().popPose();
    }

    private void renderIcon(GuiGraphics g, LivingEntity entity, int x, int bottomY) {
        // renderEntityInInventory's scale is pixels per block, so the mob is drawn bbHeight * scale tall. Fit both its
        // height and its width into the icon cell; a fixed multiplier drew most mobs several times too big, and the
        // cell's scissor then clipped them down to an unrecognizable slice of the model.
        float fitHeight = (ICON_W - 4) / Math.max(0.2f, entity.getBbHeight());
        float fitWidth = (ICON_W - 2) / Math.max(0.2f, entity.getBbWidth());
        int scale = Math.max(1, Math.round(Math.min(fitHeight, fitWidth)));
        PoseStack pose = g.pose();
        pose.pushPose();
        g.enableScissor(x - ICON_W / 2, bottomY - ICON_W, x + ICON_W / 2, bottomY);
        // The local player is the real, moving entity, so turn it for the preview and then put it back.
        boolean isLocalPlayer = entity == minecraft.player;
        float yRot = entity.getYRot(), xRot = entity.getXRot();
        float bodyRot = entity.yBodyRot, headRot = entity.yHeadRot;
        float yRotO = entity.yRotO, xRotO = entity.xRotO, bodyRotO = entity.yBodyRotO, headRotO = entity.yHeadRotO;
        if (isLocalPlayer) faceViewer(entity);
        try {
            InventoryScreen.renderEntityInInventory(g, x, bottomY, scale,
                    new Quaternionf().rotateZ((float) Math.PI), null, entity);
        } finally {
            if (isLocalPlayer) {
                entity.setYRot(yRot);
                entity.setXRot(xRot);
                entity.yBodyRot = bodyRot;
                entity.yHeadRot = headRot;
                entity.yRotO = yRotO;
                entity.xRotO = xRotO;
                entity.yBodyRotO = bodyRotO;
                entity.yHeadRotO = headRotO;
            }
        }
        g.disableScissor();
        pose.popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && hoveredId != null) {
            onPick.accept(hoveredId);
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        int footerY = panelTop + PANEL_H - FOOTER_H;
        int listTop = panelTop + HEADER_H + SEARCH_H + 4;
        int listBottom = footerY - 2;
        int visRows = Math.max(1, (listBottom - listTop) / ROW_H);
        int maxScroll = Math.max(0, displayIds.size() - visRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(delta), maxScroll));
        return true;
    }
}
