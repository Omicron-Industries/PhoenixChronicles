package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestReward;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class ItemLookup {

    private ItemLookup() {}

    private static final Field HOVERED_SLOT_FIELD = ObfuscationReflectionHelper.findField(AbstractContainerScreen.class,
            "hoveredSlot");

    public static void performLookup() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack stack = getInspectedItem(mc);
        if (stack.isEmpty()) {
            mc.player.displayClientMessage(Component.literal("§7Hold or hover an item, then press the lookup key."),
                    true);
            return;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return;

        List<QuestNode> matches = findQuestsRequiring(stack, itemId);
        if (matches.isEmpty()) {
            mc.player.displayClientMessage(Component.literal(
                    "§7No quest requires " + stack.getHoverName().getString() + "§7."), true);
            return;
        }

        if (matches.size() == 1) {
            QuestNode target = matches.get(0);
            ChronicleOverviewScreen screen = new ChronicleOverviewScreen();
            mc.setScreen(screen);
            screen.navigateToNode(target);
            return;
        }

        mc.setScreen(new net.phoenixvine.chronicles.client.screen.ItemLookupResultsScreen(stack, matches));
    }

    private static ItemStack getInspectedItem(Minecraft mc) {
        if (mc.screen instanceof AbstractContainerScreen<?> containerScreen && HOVERED_SLOT_FIELD != null) {
            try {
                Slot hovered = (Slot) HOVERED_SLOT_FIELD.get(containerScreen);
                if (hovered != null && hovered.hasItem()) return hovered.getItem();
            } catch (Exception ignored) {}
        }
        ItemStack held = mc.player.getMainHandItem();
        if (!held.isEmpty()) return held;
        return mc.player.getOffhandItem();
    }

    private static List<QuestNode> findQuestsRequiring(ItemStack stack, ResourceLocation itemId) {
        List<QuestNode> matches = new ArrayList<>();
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            if (node.isLinkStub()) continue;
            if (referencesItem(node, stack, itemId)) matches.add(node);
        }
        return matches;
    }

    private static boolean referencesItem(QuestNode node, ItemStack stack, ResourceLocation itemId) {
        for (QuestTask task : node.getTasks()) {

            if (task.matchesItem(stack)) return true;
        }
        for (QuestReward reward : node.getRewards()) {
            if (reward instanceof QuestReward.ItemReward ir) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(ir.getItem());
                if (id != null && id.equals(itemId)) return true;
            }
        }
        if (node.getIconItem() != null) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(node.getIconItem());
            if (id != null && id.equals(itemId)) return true;
        }
        return false;
    }
}
