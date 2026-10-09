package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.common.model.EmergencyKit;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestReward;
import net.phoenixvine.chronicles.common.registry.ChapterEmergencyItems;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmergencyItemsScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int HEADER_H = 20;
    private static final int TAB_H = 22;
    private static final int INFO_H = 14;
    private static final int ROW_H = 18;
    private static final int VISIBLE_ROWS = 5;
    private static final int TIMER_H = 62;
    private static final int FOOTER_H = 26;

    private static final Pattern DURATION = Pattern.compile(
            "^(?:(\\d+)\\s*d)?\\s*(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s?)?$",
            Pattern.CASE_INSENSITIVE);

    private final Screen parent;
    @Nullable
    private final QuestNode node;
    private final Supplier<String> chapterSupplier;

    private enum Scope {
        QUEST,
        CHAPTER,
        QUESTBOOK
    }

    private Scope scope = Scope.QUEST;
    private EmergencyKit storedKit = new EmergencyKit();
    private boolean storedKitLoaded = false;

    private int scroll = 0;
    private int panelX, panelY, panelH;
    private EditBox cooldownBox;

    public EmergencyItemsScreen(Screen parent, QuestNode node, Supplier<String> chapterSupplier) {
        super(Component.literal("Emergency Items"));
        this.parent = parent;
        this.node = node;
        this.chapterSupplier = chapterSupplier;
    }

    public EmergencyItemsScreen(Screen parent, String chapter) {
        this(parent, null, () -> chapter);
        this.scope = Scope.CHAPTER;
    }

    private String chapter() {
        String c = chapterSupplier != null ? chapterSupplier.get() : node != null ? node.getChapter() : "";
        return c == null ? "" : c.trim();
    }

    private static Path configDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");
    }

    private EmergencyKit kit() {
        if (scope == Scope.QUEST) return node != null ? node.getEmergencyKit() : new EmergencyKit();
        if (!storedKitLoaded) {
            storedKit = scope == Scope.CHAPTER ? ChapterEmergencyItems.get(chapter()) :
                    ChapterEmergencyItems.getQuestbook();
            storedKitLoaded = true;
        }
        return storedKit;
    }

    private void persist() {
        if (scope == Scope.CHAPTER) ChapterEmergencyItems.save(configDir(), chapter(), kit());
        else if (scope == Scope.QUESTBOOK) ChapterEmergencyItems.saveQuestbook(configDir(), kit());
        resyncEmergencyState();
    }

    private static void resyncEmergencyState() {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        java.util.UUID playerId = mc.player.getUUID();
        server.execute(() -> {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) QuestProgressTracker.sendProgressSync(player);
        });
    }

    private void setScope(Scope next) {
        persist();
        this.scope = next;
        storedKitLoaded = false;
        scroll = 0;
        init();
    }

    @Override
    protected void init() {
        clearWidgets();
        panelH = HEADER_H + TAB_H + INFO_H + VISIBLE_ROWS * ROW_H + 22 + TIMER_H + FOOTER_H;
        panelX = width / 2 - PANEL_W / 2;
        panelY = height / 2 - panelH / 2;

        boolean hasChapter = !chapter().isEmpty();
        int tabY = panelY + HEADER_H + 3;
        int tabW = (PANEL_W - 24) / 3;
        Button questTab = Button.builder(
                Component.literal(scope == Scope.QUEST ? "§f▸ This Quest" : "§7This Quest"),
                b -> setScope(Scope.QUEST))
                .bounds(panelX + 6, tabY, tabW, 16)
                .tooltip(Tooltip.create(Component.literal(
                        "Claimable while this quest is active.")))
                .build();
        questTab.active = scope != Scope.QUEST && node != null;
        addRenderableWidget(questTab);

        Button chapterTab = Button.builder(
                Component.literal(scope == Scope.CHAPTER ? "§f▸ Chapter" : "§7Chapter"),
                b -> setScope(Scope.CHAPTER))
                .bounds(panelX + 12 + tabW, tabY, tabW, 16)
                .tooltip(Tooltip.create(Component.literal(hasChapter ?
                        "Claimable from the emergency screen any time, even if every quest in " + chapter() +
                                " is locked." :
                        "Pick a chapter for this quest first.")))
                .build();
        chapterTab.active = hasChapter && scope != Scope.CHAPTER;
        addRenderableWidget(chapterTab);

        Button questbookTab = Button.builder(
                Component.literal(scope == Scope.QUESTBOOK ? "§f▸ Questbook" : "§7Questbook"),
                b -> setScope(Scope.QUESTBOOK))
                .bounds(panelX + 18 + tabW * 2, tabY, tabW, 16)
                .tooltip(Tooltip.create(Component.literal(
                        "Claimable from the emergency screen any time, whatever chapter the player is in.")))
                .build();
        questbookTab.active = scope != Scope.QUESTBOOK;
        addRenderableWidget(questbookTab);

        EmergencyKit kit = kit();
        List<QuestReward> list = kit.getRewards();
        int maxScroll = Math.max(0, list.size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int listTop = panelY + HEADER_H + TAB_H + INFO_H;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int idx = scroll + row;
            if (idx >= list.size()) break;
            int y = listTop + row * ROW_H + 1;
            final int index = idx;
            addRenderableWidget(Button.builder(Component.literal("§c×"), b -> {
                List<QuestReward> next = new ArrayList<>(kit().getRewards());
                if (index < next.size()) next.remove(index);
                kit().setRewards(next);
                persist();
                init();
            }).bounds(panelX + PANEL_W - 20, y, 14, 16)
                    .tooltip(Tooltip.create(Component.literal("Remove")))
                    .build());
        }

        int editY = listTop + VISIBLE_ROWS * ROW_H + 3;
        addRenderableWidget(Button.builder(Component.literal("§a✎ Edit Rewards…"), b -> {
            if (minecraft == null) return;
            String label = switch (scope) {
                case CHAPTER -> chapter() + " (chapter)";
                case QUESTBOOK -> "entire questbook";
                case QUEST -> node != null ? node.getId().getPath() : "";
            };
            minecraft.setScreen(new TaskRewardEditorScreen(this, scope == Scope.QUEST ? node : null,
                    new ArrayList<>(kit().getRewards()), updated -> {
                        kit().setRewards(updated);
                        persist();
                    }, label));
        }).bounds(panelX + 6, editY, PANEL_W - 12, 16)
                .tooltip(Tooltip.create(Component.literal(
                        "Add items, fluids, XP, commands, loot tables and more.\n" +
                                "Choice boxes aren't available here.")))
                .build());

        int timerY = editY + 22;
        addRenderableWidget(Button.builder(Component.literal(modeLabel(kit.getRepeat())), b -> {
            EmergencyKit.Repeat next = switch (kit().getRepeat()) {
                case INHERIT -> EmergencyKit.Repeat.ONCE;
                case ONCE -> EmergencyKit.Repeat.REPEATABLE;
                case REPEATABLE -> EmergencyKit.Repeat.INHERIT;
            };
            kit().setRepeat(next);
            init();
        }).bounds(panelX + 6, timerY + 12, PANEL_W - 12, 16)
                .tooltip(Tooltip.create(Component.literal(
                        "Inherit: use the engine default from engine_settings.snbt.\n" +
                                "One use: each player can claim it once.\n" +
                                "Repeatable: claimable again after the cooldown below.")))
                .build());

        cooldownBox = new EditBox(font, panelX + 6, timerY + 32, 110, 16, Component.empty());
        cooldownBox.setMaxLength(24);
        cooldownBox.setHint(Component.literal("§8inherit"));
        cooldownBox.setValue(kit.getCooldownSeconds() >= 0 ?
                QuestProgressTracker.formatDuration(kit.getCooldownSeconds() * 1000L) : "");
        cooldownBox.setResponder(text -> {
            int parsed = parseSeconds(text);
            if (parsed == -2) return;
            kit().setCooldownSeconds(parsed);
        });
        cooldownBox.setTooltip(Tooltip.create(Component.literal(
                "Time between claims for repeatable kits, e.g. 90, 5m, 1h 30m or 2d.\n" +
                        "Leave empty to inherit. 0 means no wait.")));
        addRenderableWidget(cooldownBox);

        int footY = panelY + panelH - FOOTER_H + 5;
        addRenderableWidget(Button.builder(Component.literal("§cClear Rewards"), b -> {
            kit().setRewards(List.of());
            persist();
            scroll = 0;
            init();
        }).bounds(panelX + 6, footY, 90, 16).build());
        addRenderableWidget(Button.builder(Component.literal("§fDone"), b -> onClose())
                .bounds(panelX + PANEL_W - 66, footY, 60, 16).build());
    }

    private static String modeLabel(EmergencyKit.Repeat repeat) {
        return "§8Claims: §7" + switch (repeat) {
            case INHERIT -> "Inherit";
            case ONCE -> "One use";
            case REPEATABLE -> "Repeatable";
        } + " §8▾";
    }

    static int parseSeconds(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return -1;
        Matcher m = DURATION.matcher(trimmed);
        if (!m.matches()) return -2;
        long total = 0;
        boolean any = false;
        long[] unit = { 86400, 3600, 60, 1 };
        for (int g = 1; g <= 4; g++) {
            if (m.group(g) == null) continue;
            any = true;
            total += Long.parseLong(m.group(g)) * unit[g - 1];
        }
        if (!any) return -2;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private String effectiveSummary() {
        EmergencyKit kit = kit();
        boolean repeatable;
        int cooldown;
        if (scope != Scope.QUEST || node == null) {
            repeatable = kit.resolveRepeatable(EmergencyKit.Repeat.INHERIT);
            cooldown = kit.resolveCooldownSeconds(-1);
        } else {
            repeatable = node.isEmergencyRepeatable();
            cooldown = node.getEmergencyCooldownSeconds();
        }
        if (!repeatable) return "One use per player.";
        return cooldown > 0 ? "Repeatable, " + QuestProgressTracker.formatDuration(cooldown * 1000L) + " cooldown." :
                "Repeatable, no cooldown.";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int total = kit().getRewards().size();
        if (total > VISIBLE_ROWS) {
            scroll = Math.max(0, Math.min(total - VISIBLE_ROWS, scroll - (int) Math.signum(delta)));
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        persist();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelX, panelY, PANEL_W, panelH, HEADER_H,
                "Emergency Items");

        EmergencyKit kit = kit();
        List<QuestReward> list = kit.getRewards();
        String info = switch (scope) {
            case CHAPTER -> "§7Claimable any time for " + chapter() + ", even if its quests are locked.";
            case QUESTBOOK -> "§7Claimable any time, whatever the player has unlocked.";
            case QUEST -> list.isEmpty() ? "§8No fallback rewards for this quest." :
                    "§7Claimable while this quest is active.";
        };
        g.drawString(font, ChroniclesUIKit.fitText(font, info, PANEL_W - 12), panelX + 6,
                panelY + HEADER_H + TAB_H + 3, ChroniclesThemePalette.TEXT_DIM, false);

        int listTop = panelY + HEADER_H + TAB_H + INFO_H;
        List<Component> hoveredTip = List.of();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int idx = scroll + row;
            if (idx >= list.size()) break;
            QuestReward reward = list.get(idx);
            int y = listTop + row * ROW_H;
            int textX = panelX + 8;
            if (reward instanceof QuestReward.ItemReward itemReward) {
                ItemStack stack = new ItemStack(itemReward.getItem(), itemReward.getCount());
                g.renderItem(stack, panelX + 6, y + 1);
                textX = panelX + 26;
                if (mouseX >= panelX + 6 && mouseX < panelX + PANEL_W - 24 && mouseY >= y && mouseY < y + ROW_H) {
                    hoveredTip = Screen.getTooltipFromItem(minecraft, stack);
                }
            }
            String label = reward.getSummary().getString();
            g.drawString(font, "§f" + ChroniclesUIKit.fitText(font, label, panelX + PANEL_W - 28 - textX), textX,
                    y + 5, ChroniclesThemePalette.TEXT, false);
        }
        if (list.size() > VISIBLE_ROWS) {
            String more = (scroll + 1) + "-" + Math.min(list.size(), scroll + VISIBLE_ROWS) + " of " +
                    list.size() + "  (scroll)";
            g.drawString(font, "§8" + more, panelX + PANEL_W - 6 - font.width(more),
                    listTop + VISIBLE_ROWS * ROW_H - 8, ChroniclesThemePalette.TEXT_DIM, false);
        }

        int timerY = listTop + VISIBLE_ROWS * ROW_H + 3 + 22;
        g.fill(panelX + 6, timerY - 3, panelX + PANEL_W - 6, timerY - 2, ChroniclesThemePalette.BORDER);
        g.drawString(font, "§8Timer", panelX + 6, timerY + 2, ChroniclesThemePalette.TEXT_FAINT, false);
        g.drawString(font, "§8Cooldown", panelX + 122, timerY + 36, ChroniclesThemePalette.TEXT_FAINT, false);
        g.drawString(font, ChroniclesUIKit.fitText(font, "§7Now: §f" + effectiveSummary(), PANEL_W - 12),
                panelX + 6, timerY + 52, ChroniclesThemePalette.TEXT, false);

        super.render(g, mouseX, mouseY, partialTick);
        if (!hoveredTip.isEmpty()) g.renderComponentTooltip(font, hoveredTip, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
