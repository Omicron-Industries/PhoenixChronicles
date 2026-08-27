package net.phoenixvine.chronicles.integration.emi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestReward;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.tasks.CraftItemTask;
import net.phoenixvine.chronicles.tasks.FluidRequirementTask;
import net.phoenixvine.chronicles.tasks.ItemRequirementTask;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class QuestEmiRecipe implements EmiRecipe {

    private final QuestNode node;
    private final ResourceLocation id;

    public QuestEmiRecipe(QuestNode node) {
        this.node = node;
        this.id = ResourceLocation.fromNamespaceAndPath(node.getId().getNamespace(), "quest/" + node.getId().getPath());
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return QuestEmiCategory.CATEGORY;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        List<EmiIngredient> dynamicInputs = new ArrayList<>();
        if (node.getTasks() == null) return dynamicInputs;

        for (QuestTask task : node.getTasks()) {
            if (task instanceof ItemRequirementTask itemTask) {
                ItemStack itemStack = new ItemStack(itemTask.getItem(), itemTask.getRequiredCount());
                if (itemTask.getNbtFilter() != null && !itemTask.getNbtFilter().isEmpty()) {
                    itemStack.setTag(itemTask.getNbtFilter().copy());
                }
                dynamicInputs.add(EmiStack.of(itemStack));
            } else if (task instanceof CraftItemTask craftTask) {
                Item item = ForgeRegistries.ITEMS.getValue(craftTask.getItemId());
                if (item != null && item != Items.AIR) {
                    dynamicInputs.add(EmiStack.of(item, craftTask.getRequiredCount()));
                }
            } else if (task instanceof FluidRequirementTask fluidTask) {
                Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidTask.getFluidId());
                if (fluid != null && fluid != Fluids.EMPTY) {
                    dynamicInputs.add(EmiStack.of(fluid, fluidTask.getRequiredAmount()));
                }
            }
        }
        return dynamicInputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        List<EmiStack> dynamicOutputs = new ArrayList<>();
        if (node.getRewards() == null || node.getRewards().isEmpty()) {
            dynamicOutputs.add(EmiStack.of(Items.BOOK));
            return dynamicOutputs;
        }

        for (QuestReward reward : node.getRewards()) {
            if (reward == null) continue;

            if (reward instanceof QuestReward.ItemReward ir) {
                Item rewardItem = ir.getItem();
                if (rewardItem != null && rewardItem != Items.AIR) {
                    dynamicOutputs.add(EmiStack.of(rewardItem, ir.getCount()));
                }
            } else if (reward instanceof QuestReward.XPReward xp) {
                dynamicOutputs.add(EmiStack.of(Items.EXPERIENCE_BOTTLE, xp.getLevels()));
            } else
                if (reward instanceof QuestReward.LootTableReward || reward instanceof QuestReward.RewardTableReward) {
                    dynamicOutputs.add(EmiStack.of(Items.BUNDLE, 1));
                } else if (reward instanceof QuestReward.LootCrateReward) {
                    Item crate = ForgeRegistries.ITEMS
                            .getValue(ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "loot_crate"));
                    dynamicOutputs.add(EmiStack.of(crate != null && crate != Items.AIR ? crate : Items.CHEST, 1));
                } else if (reward instanceof QuestReward.CommandReward ||
                        reward instanceof QuestReward.ScriptEventReward) {
                            dynamicOutputs.add(EmiStack.of(Items.COMMAND_BLOCK, 1));
                        } else {
                            dynamicOutputs.add(EmiStack.of(Items.PAPER, 1));
                        }
        }

        if (dynamicOutputs.isEmpty()) {
            dynamicOutputs.add(EmiStack.of(Items.BOOK));
        }
        return dynamicOutputs;
    }

    @Override
    public int getDisplayWidth() {
        return 160;
    }

    @Override
    public int getDisplayHeight() {
        return 125;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        Font font = Minecraft.getInstance().font;

        widgets.add(new JumpToQuestWidget(node.getTitle().getVisualOrderText(), 4, 4, this::jumpToQuest));

        Component descComp = node.getDescription();
        if (descComp != null) {
            List<FormattedCharSequence> lines = font.split(descComp, 152);
            int maxVisibleLines = 4;
            int textY = 16;

            for (int i = 0; i < Math.min(lines.size(), maxVisibleLines); i++) {
                widgets.addText(lines.get(i), 4, textY, 0x555555, false);
                textY += 10;
            }

            if (lines.size() > maxVisibleLines) {
                widgets.addText(Component.literal("§7... (View full log in Quest Book)").getVisualOrderText(), 4, textY,
                        0x888888, false);
            }
        }

        int slotY = 92;

        int taskX = 4;
        widgets.addText(Component.literal("Tasks").getVisualOrderText(), taskX, slotY - 11, 0x333333, false);
        List<EmiIngredient> currentInputs = getInputs();
        for (int i = 0; i < Math.min(currentInputs.size(), 3); i++) {
            widgets.addSlot(currentInputs.get(i), taskX + (i * 18), slotY).drawBack(true);
        }

        List<EmiStack> currentOutputs = getOutputs();
        if (!currentInputs.isEmpty() && !currentOutputs.isEmpty()) {
            widgets.addTexture(EmiTexture.EMPTY_ARROW, 68, slotY + 1);
        }

        int rewardX = 100;
        widgets.addText(Component.literal("Rewards").getVisualOrderText(), rewardX, slotY - 11, 0x333333, false);

        List<QuestReward> originalRewards = node.getRewards();
        for (int i = 0; i < Math.min(currentOutputs.size(), 3); i++) {
            SlotWidget slot = widgets.addSlot(currentOutputs.get(i), rewardX + (i * 18), slotY)
                    .drawBack(true);

            if (originalRewards != null && i < originalRewards.size()) {
                slot.appendTooltip(originalRewards.get(i).getSummary());
            } else {
                slot.appendTooltip(Component.literal("§7Quest Completion Record"));
            }
        }
    }

    private void jumpToQuest() {
        Minecraft mc = Minecraft.getInstance();
        ChronicleOverviewScreen screen = new ChronicleOverviewScreen();
        mc.setScreen(screen);
        screen.navigateToNode(node);
    }

    // A plain TextWidget has no click handling of its own (Widget#mouseClicked defaults to a
    // no-op) - this makes the quest title double as a "jump back to this quest" link so players
    // browsing recipes in EMI aren't stuck re-finding the quest by hand.
    private static final class JumpToQuestWidget extends TextWidget {

        private final Runnable onClick;

        JumpToQuestWidget(FormattedCharSequence text, int x, int y, Runnable onClick) {
            super(text, x, y, 0x1A56C4, false);
            this.onClick = onClick;
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button != 0 || !getBounds().contains(mouseX, mouseY)) return false;
            onClick.run();
            return true;
        }
    }
}
