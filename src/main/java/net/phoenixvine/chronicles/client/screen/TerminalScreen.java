package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.C2SClaimQuestRewardPacket;
import net.phoenixvine.chronicles.network.packet.C2SSetQuestStatePacket;
import net.phoenixvine.chronicles.registry.ChroniclesTheme;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import com.mojang.blaze3d.platform.InputConstants;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public class TerminalScreen extends Screen {

    private final Screen parent;
    private final List<String> consoleHistory = new ArrayList<>();

    private String inputBuffer = "";
    private int cursorPosition = 0;
    private int frameTickCounter = 0;

    // Command history (up/down arrow)
    private final Deque<String> cmdHistory = new ArrayDeque<>();
    private int cmdHistoryIdx = -1;
    private static final int MAX_CMD_HISTORY = 50;

    public TerminalScreen(Screen parent) {
        super(Component.literal("PHOENIX SYSTEM TERMINAL"));
        this.parent = parent;

        // Initial system boot logs
        consoleHistory.add("§a[SYS] Initializing Core Terminal Protocol...");
        consoleHistory.add("§e[WARN] Remote connection unencrypted.");
        consoleHistory.add("§b[READY] Enter directives below. Support color tags (& or §).");
    }

    @Override
    protected void init() {
        this.clearWidgets();

        // Return button placed out of the terminal layout boundaries
        this.addRenderableWidget(Button.builder(Component.literal("§c[ DISCONNECT ]"), b -> {
            if (this.minecraft != null) this.minecraft.setScreen(parent);
        }).bounds(this.width - 115, this.height - 30, 100, 20).build());
    }

    /**
     * Captures raw alphanumeric string typed characters globally from the OS hook loop.
     */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // Only accept standard printable keyboard characters
        if (codePoint >= 32 && codePoint != 127) {
            // Midline Insertion: Split string at cursor pointer location and stitch the new character in
            String left = inputBuffer.substring(0, cursorPosition);
            String right = inputBuffer.substring(cursorPosition);

            inputBuffer = left + codePoint + right;
            cursorPosition++; // Shift cursor index right
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    /**
     * Intercepts function keys, escape mechanics, backspaces, and directional navigation vectors.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (this.minecraft != null) this.minecraft.setScreen(parent);
            return true;
        }

        // 1. Cursor Backspace Action (Delete character BEHIND cursor)
        if (keyCode == InputConstants.KEY_BACKSPACE && cursorPosition > 0 && !inputBuffer.isEmpty()) {
            String left = inputBuffer.substring(0, cursorPosition - 1);
            String right = inputBuffer.substring(cursorPosition);
            inputBuffer = left + right;
            cursorPosition--;
            return true;
        }

        // 2. Cursor Delete Action (Delete character In FRONT of cursor)
        if (keyCode == InputConstants.KEY_DELETE && cursorPosition < inputBuffer.length()) {
            String left = inputBuffer.substring(0, cursorPosition);
            String right = inputBuffer.substring(cursorPosition + 1);
            inputBuffer = left + right;
            return true;
        }

        // 3. Move Cursor Left
        if (keyCode == InputConstants.KEY_LEFT) {
            if (cursorPosition > 0) {
                cursorPosition--;
            }
            return true;
        }

        // 4. Move Cursor Right
        if (keyCode == InputConstants.KEY_RIGHT) {
            if (cursorPosition < inputBuffer.length()) {
                cursorPosition++;
            }
            return true;
        }

        // 5. Jump to Start (HOME Key)
        if (keyCode == InputConstants.KEY_HOME) {
            cursorPosition = 0;
            return true;
        }

        // 6. Jump to End (END Key)
        if (keyCode == InputConstants.KEY_END) {
            cursorPosition = inputBuffer.length();
            return true;
        }

        // 7. Command history navigation (UP/DOWN arrows)
        if (keyCode == InputConstants.KEY_UP) {
            List<String> hist = new ArrayList<>(cmdHistory);
            if (!hist.isEmpty()) {
                cmdHistoryIdx = Math.min(cmdHistoryIdx + 1, hist.size() - 1);
                inputBuffer = hist.get(cmdHistoryIdx);
                cursorPosition = inputBuffer.length();
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_DOWN) {
            if (cmdHistoryIdx > 0) {
                cmdHistoryIdx--;
                List<String> hist = new ArrayList<>(cmdHistory);
                inputBuffer = hist.get(cmdHistoryIdx);
            } else {
                cmdHistoryIdx = -1;
                inputBuffer = "";
            }
            cursorPosition = inputBuffer.length();
            return true;
        }

        // 8. Execution (ENTER Key Command Processing)
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            if (!inputBuffer.trim().isEmpty()) {
                executeTerminalDirective(inputBuffer.trim());
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void executeTerminalDirective(String raw) {
        // Push to history
        cmdHistory.addFirst(raw);
        if (cmdHistory.size() > MAX_CMD_HISTORY) cmdHistory.removeLast();
        cmdHistoryIdx = -1;

        consoleHistory.add("§7$ " + raw.replace("&", "§"));

        String[] parts = raw.split("\\s+", 3);
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "clear" -> {
                consoleHistory.clear();
                consoleHistory.add("§b[SYS] Console buffer cleared.");
            }
            case "help" -> cmdHelp(parts);
            case "quests" -> cmdQuests(parts);
            case "quest" -> cmdQuestInfo(parts);
            case "activate" -> cmdSetState(parts, true);
            case "deactivate" -> cmdSetState(parts, false);
            case "claim" -> cmdClaim(parts);
            case "theme" -> cmdTheme(parts);
            case "whoami" -> cmdWhoami();
            case "progress" -> cmdProgress(parts);
            default -> consoleHistory.add("§cERR: Unknown directive '§f" + cmd + "§c' — type §fhelp§c for commands.");
        }

        inputBuffer = "";
        cursorPosition = 0;
    }

    private void cmdHelp(String[] parts) {
        if (parts.length >= 2) {
            switch (parts[1].toLowerCase()) {
                case "quests" -> {
                    consoleHistory.add("§aquests §7[active|all|done]§f - list quest nodes");
                    return;
                }
                case "quest" -> {
                    consoleHistory.add("§aquest §7<id>§f - show detail for a quest node");
                    return;
                }
                case "activate" -> {
                    consoleHistory.add("§aactivate §7<id>§f - begin tracking an unlocked quest");
                    return;
                }
                case "deactivate" -> {
                    consoleHistory.add("§adeactivate §7<id>§f - stop tracking an active quest");
                    return;
                }
                case "claim" -> {
                    consoleHistory.add("§aclaim §7<id>§f - claim rewards for a completed quest");
                    return;
                }
                case "theme" -> {
                    consoleHistory.add("§atheme §7<dark|light|amber|solarized>§f - switch UI theme");
                    return;
                }
                case "progress" -> {
                    consoleHistory.add("§aprogress §7<id>§f - show per-task progress for a quest");
                    return;
                }
                case "whoami" -> {
                    consoleHistory.add("§awhoami§f - display current player identity");
                    return;
                }
            }
        }
        consoleHistory.add("§aAvailable directives:");
        consoleHistory.add("§f  quests [active|all|done]    §8— list quest nodes");
        consoleHistory.add("§f  quest <id>                  §8— inspect a quest");
        consoleHistory.add("§f  progress <id>               §8— per-task progress");
        consoleHistory.add("§f  activate / deactivate <id>  §8— toggle tracking");
        consoleHistory.add("§f  claim <id>                  §8— claim rewards");
        consoleHistory.add("§f  theme <name>                §8— switch UI theme");
        consoleHistory.add("§f  whoami                      §8— player identity");
        consoleHistory.add("§f  clear                       §8— clear terminal");
        consoleHistory.add("§8Type §fhelp <cmd>§8 for details.");
    }

    private void cmdWhoami() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            consoleHistory.add("§cERR: No local player.");
            return;
        }
        consoleHistory.add("§aIdentity: §f" + mc.player.getName().getString());
        consoleHistory.add("§8  UUID: §7" + mc.player.getStringUUID());
        PlayerQuestData data = getPlayerData();
        if (data != null) {
            Map<ResourceLocation, QuestState> states = data.getAllStates();
            long active = states.values().stream().filter(s -> s == QuestState.ACTIVE).count();
            long done = states.values().stream().filter(s -> s == QuestState.COMPLETED).count();
            consoleHistory.add("§8  Quests — §aactive: §f" + active + "  §adone: §f" + done);
        }
    }

    private void cmdQuests(String[] parts) {
        String filter = parts.length >= 2 ? parts[1].toLowerCase() : "active";
        PlayerQuestData data = getPlayerData();
        if (data == null) {
            consoleHistory.add("§cERR: Quest data unavailable.");
            return;
        }

        Map<ResourceLocation, QuestState> states = data.getAllStates();
        boolean any = false;
        for (Map.Entry<ResourceLocation, QuestState> e : states.entrySet()) {
            QuestState s = e.getValue();
            boolean show = switch (filter) {
                case "all" -> true;
                case "done" -> s == QuestState.COMPLETED;
                default -> s == QuestState.ACTIVE;
            };
            if (!show) continue;
            QuestNode node = QuestTreeRegistry.getQuest(e.getKey());
            String title = node != null ? node.getTitle().getString() : e.getKey().getPath();
            String stateTag = switch (s) {
                case ACTIVE -> "§e[ACTIVE]";
                case COMPLETED -> "§a[DONE]";
                case LOCKED -> "§8[LOCKED]";
                default -> "§7[" + s.name() + "]";
            };
            consoleHistory.add(stateTag + " §f" + title + " §8" + e.getKey());
            any = true;
        }
        if (!any) consoleHistory.add("§8(no " + filter + " quests)");
    }

    private void cmdQuestInfo(String[] parts) {
        if (parts.length < 2) {
            consoleHistory.add("§cUsage: quest <id>");
            return;
        }
        ResourceLocation id = safeRL(parts[1]);
        if (id == null) {
            consoleHistory.add("§cERR: Invalid id '" + parts[1] + "'");
            return;
        }
        QuestNode node = QuestTreeRegistry.getQuest(id);
        if (node == null) {
            consoleHistory.add("§cERR: Quest '" + id + "' not found.");
            return;
        }

        consoleHistory.add("§a== " + node.getTitle().getString() + " §8[" + id + "]");
        if (node.getCategory() != null) consoleHistory.add("§8  Category: §7" + node.getCategory());

        PlayerQuestData data = getPlayerData();
        QuestState state = data != null ? data.getQuestState(id, QuestState.LOCKED) : QuestState.LOCKED;
        String stateStr = switch (state) {
            case ACTIVE -> "§eACTIVE";
            case COMPLETED -> "§aCOMPLETED";
            case UNLOCKED -> "§7UNLOCKED";
            default -> "§8LOCKED";
        };
        consoleHistory.add("§8  State: " + stateStr);
        consoleHistory.add("§8  Tasks: §f" + node.getTasks().size() + "  Rewards: §f" + node.getRewards().size());
        List<QuestNode> prereqs = node.getPrerequisites();
        if (!prereqs.isEmpty()) {
            StringBuilder sb = new StringBuilder("§8  Prereqs: §7");
            for (int i = 0; i < prereqs.size(); i++) {
                if (i > 0) sb.append("§8, §7");
                sb.append(prereqs.get(i).getTitle().getString());
            }
            consoleHistory.add(sb.toString());
        }
    }

    private void cmdProgress(String[] parts) {
        if (parts.length < 2) {
            consoleHistory.add("§cUsage: progress <id>");
            return;
        }
        ResourceLocation id = safeRL(parts[1]);
        if (id == null) {
            consoleHistory.add("§cERR: Invalid id '" + parts[1] + "'");
            return;
        }
        QuestNode node = QuestTreeRegistry.getQuest(id);
        if (node == null) {
            consoleHistory.add("§cERR: Quest '" + id + "' not found.");
            return;
        }

        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            consoleHistory.add("§cERR: No local player.");
            return;
        }
        consoleHistory.add("§aProgress for: §f" + node.getTitle().getString());
        for (QuestTask task : node.getTasks()) {
            boolean done = task.isCompletedFor(mc.player);
            String prog = task.getProgressString(mc.player);
            String desc = task.getDescription().getString();
            consoleHistory
                    .add("  " + (done ? "§a✔" : "§c✗") + " §f" + desc + (prog != null ? " §8[§7" + prog + "§8]" : ""));
        }
    }

    private void cmdSetState(String[] parts, boolean activate) {
        if (parts.length < 2) {
            consoleHistory.add("§cUsage: " + (activate ? "activate" : "deactivate") + " <quest-id>");
            return;
        }
        ResourceLocation id = safeRL(parts[1]);
        if (id == null) {
            consoleHistory.add("§cERR: Invalid id '" + parts[1] + "'");
            return;
        }
        if (QuestTreeRegistry.getQuest(id) == null) {
            consoleHistory.add("§cERR: Quest '" + id + "' not found.");
            return;
        }
        ChronicleNetwork.CHANNEL.sendToServer(new C2SSetQuestStatePacket(id, activate));
        consoleHistory.add("§a[NET] Sent " + (activate ? "activate" : "deactivate") + " request for §f" + id);
    }

    private void cmdClaim(String[] parts) {
        if (parts.length < 2) {
            consoleHistory.add("§cUsage: claim <quest-id>");
            return;
        }
        ResourceLocation id = safeRL(parts[1]);
        if (id == null) {
            consoleHistory.add("§cERR: Invalid id '" + parts[1] + "'");
            return;
        }
        if (QuestTreeRegistry.getQuest(id) == null) {
            consoleHistory.add("§cERR: Quest '" + id + "' not found.");
            return;
        }
        PlayerQuestData data = getPlayerData();
        QuestState state = data != null ? data.getQuestState(id, QuestState.LOCKED) : QuestState.LOCKED;
        if (state != QuestState.COMPLETED) {
            consoleHistory.add("§cERR: Quest not completed (state: " + state.name() + ").");
            return;
        }
        ChronicleNetwork.CHANNEL.sendToServer(new C2SClaimQuestRewardPacket(id, -1));
        consoleHistory.add("§a[NET] Sent reward claim for §f" + id);
    }

    private void cmdTheme(String[] parts) {
        if (parts.length < 2) {
            consoleHistory.add("§8Loaded themes: §f" + String.join("  ", ChroniclesTheme.REGISTRY.keySet()));
            consoleHistory.add("§8Active: §f" + ChroniclesTheme.getActiveName());
            return;
        }
        String name = parts[1].toUpperCase(java.util.Locale.ROOT);
        if (!ChroniclesTheme.REGISTRY.containsKey(name)) {
            consoleHistory.add("§cERR: Unknown theme '§f" + parts[1] + "§c'. Available: §f" +
                    String.join("  ", ChroniclesTheme.REGISTRY.keySet()));
            return;
        }
        ChroniclesTheme.setCurrent(name);
        consoleHistory.add("§a[OK] Theme set to §f" + name);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PlayerQuestData getPlayerData() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return mc.player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).orElse(null);
    }

    private static ResourceLocation safeRL(String s) {
        try {
            if (!s.contains(":")) s = "minecraft:" + s; // default namespace fallback
            return new ResourceLocation(s);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        frameTickCounter++; // Driving variable used to compute blinking cursor visibility updates
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        // Core Layout Positioning Definitions
        int termX = 20;
        int termY = 20;
        int termW = this.width - 40;
        int termH = this.height - 60;

        // Draw solid black retro backing frame
        graphics.fill(termX, termY, termX + termW, termY + termH, 0xFA050805);
        graphics.renderOutline(termX, termY, termW, termH, 0xFF33AA33);

        // --- Render Scrolling Historical Command Output Area ---
        int maxVisibleLines = (termH - 25) / 12;
        int startLineIdx = Math.max(0, consoleHistory.size() - maxVisibleLines);
        int currentLineY = termY + 10;

        for (int i = startLineIdx; i < consoleHistory.size(); i++) {
            String logLine = consoleHistory.get(i).replace("&", "§");
            graphics.drawString(this.font, logLine, termX + 12, currentLineY, 0xFFFFFF);
            currentLineY += 12;
        }

        // --- Render Interactive CommandLine Input Area ---
        int inputLineY = termY + termH - 16;
        graphics.fill(termX + 5, inputLineY - 3, termX + termW - 5, inputLineY + 11, 0xFF0D130D);
        graphics.renderOutline(termX + 5, inputLineY - 3, termW - 10, 14, 0xFF225522);

        // Auto-convert color identifiers live while writing
        String renderText = inputBuffer.replace("&", "§");
        String fixedPrompt = "§aroot@phoenix:~# §f" + renderText;
        graphics.drawString(this.font, fixedPrompt, termX + 10, inputLineY, 0xFFFFFF);

        // --- PERFORMANCE TRIUMPH: Custom Midline Blinking Cursor Rendering Engine ---
        // Calculate the physical offset length of the user input string up to the cursor pointer position index
        String stringUpToCursor = "root@phoenix:~# " + inputBuffer.substring(0, cursorPosition);

        // Strip out formatting characters (§ + code) from the spatial layout tracking calculations,
        // otherwise the cursor will render shifted far too many pixels to the right!
        String cleanStringForLength = stringUpToCursor.replaceAll("§[0-9a-fk-orA-FK-ORxX]", "");
        int pixelCursorOffset = this.font.width(cleanStringForLength);

        // Blink logic calculation cycle rules (Changes visibility flags every 10 ticks)
        if ((frameTickCounter / 10) % 2 == 0) {
            int cursorRenderX = termX + 10 + pixelCursorOffset;
            graphics.fill(cursorRenderX, inputLineY - 1, cursorRenderX + 2, inputLineY + 9, 0xFF00FF00);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
