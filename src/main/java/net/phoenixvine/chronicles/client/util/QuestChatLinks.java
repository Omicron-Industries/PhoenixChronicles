package net.phoenixvine.chronicles.client.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.phoenixvine.chronicles.common.model.QuestNode;

/** Shareable quest links: {@code /chronicles goto <quest id>} opens the questbook on that quest. */
public final class QuestChatLinks {

    private QuestChatLinks() {}

    public static String commandFor(QuestNode node) {
        return "/chronicles goto " + node.getId();
    }

    /** Copies the link command to the clipboard and shows a clickable confirmation in the local chat. */
    public static void copyToClipboard(QuestNode node) {
        Minecraft mc = Minecraft.getInstance();
        String command = commandFor(node);
        mc.keyboardHandler.setClipboard(command);
        Component link = Component.literal("[" + node.getTitle().getString() + "]")
                .withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Open this quest"))));
        mc.gui.getChat().addMessage(Component.literal("Quest link copied - paste it in chat to share: ")
                .withStyle(ChatFormatting.GRAY).append(link));
    }
}
