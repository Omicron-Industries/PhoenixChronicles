package net.phoenixvine.chronicles.client.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.phoenixvine.chronicles.common.model.QuestNode;

public final class QuestChatLinks {

    private QuestChatLinks() {}

    public static String commandFor(QuestNode node) {
        return "/chronicles_goto " + node.getId();
    }

    public static void copyToClipboard(QuestNode node) {
        Minecraft mc = Minecraft.getInstance();
        String command = commandFor(node);
        mc.keyboardHandler.setClipboard(command);
        Component link = Component.literal("[" + node.getTitle().getString() + "]")
                .withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to put the command in the chat box, then press Enter"))));
        mc.gui.getChat().addMessage(Component.literal("Quest link copied - paste it in chat to share: ")
                .withStyle(ChatFormatting.GRAY).append(link));
    }
}
