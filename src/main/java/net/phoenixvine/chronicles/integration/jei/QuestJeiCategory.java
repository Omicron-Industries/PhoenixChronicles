package net.phoenixvine.chronicles.integration.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.tasks.CraftItemTask;
import net.phoenixvine.chronicles.common.tasks.FluidRequirementTask;
import net.phoenixvine.chronicles.common.tasks.ItemRequirementTask;
import net.phoenixvine.chronicles.integration.QuestRewardDisplay;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class QuestJeiCategory implements IRecipeCategory<QuestNode> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "quests");
    public static final RecipeType<QuestNode> TYPE = RecipeType.create("phoenix_chronicles", "quests", QuestNode.class);

    private static final int WIDTH = 160, HEIGHT = 125;
    private static final int TITLE_X = 4, TITLE_Y = 2, TITLE_H = 20;

    private final IDrawable background;
    private final IDrawable icon;

    public QuestJeiCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Items.BOOK));
    }

    @Override
    public RecipeType<QuestNode> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("phoenix_chronicles.jei.category.quests");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, QuestNode recipe, IFocusGroup focuses) {
        int slotY = 92, taskX = 4, rewardX = 100;

        if (recipe.getTasks() != null) {
            int i = 0;
            for (QuestTask task : recipe.getTasks()) {
                if (i >= 3) break;
                if (task instanceof ItemRequirementTask itemTask) {
                    ItemStack stack = new ItemStack(itemTask.getItem(), itemTask.getRequiredCount());
                    if (itemTask.getNbtFilter() != null && !itemTask.getNbtFilter().isEmpty()) {
                        stack.setTag(itemTask.getNbtFilter().copy());
                    }
                    builder.addSlot(RecipeIngredientRole.INPUT, taskX + i * 18, slotY)
                            .setStandardSlotBackground().addItemStack(stack);
                    i++;
                } else if (task instanceof CraftItemTask craftTask) {
                    Item item = ForgeRegistries.ITEMS.getValue(craftTask.getItemId());
                    if (item != null && item != Items.AIR) {
                        builder.addSlot(RecipeIngredientRole.INPUT, taskX + i * 18, slotY)
                                .setStandardSlotBackground()
                                .addItemStack(new ItemStack(item, craftTask.getRequiredCount()));
                        i++;
                    }
                } else if (task instanceof FluidRequirementTask fluidTask) {
                    Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidTask.getFluidId());
                    if (fluid != null && fluid != Fluids.EMPTY) {
                        builder.addSlot(RecipeIngredientRole.INPUT, taskX + i * 18, slotY)
                                .setStandardSlotBackground().addFluidStack(fluid, fluidTask.getRequiredAmount());
                        i++;
                    }
                }
            }
        }

        List<QuestRewardDisplay.Entry> entries = QuestRewardDisplay.entries(recipe);
        if (entries.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, rewardX, slotY)
                    .setStandardSlotBackground().addItemStack(new ItemStack(Items.BOOK));
            return;
        }
        for (int r = 0; r < entries.size(); r++) {
            QuestRewardDisplay.Entry entry = entries.get(r);
            if (r < 3) {
                var slot = builder.addSlot(RecipeIngredientRole.OUTPUT, rewardX + r * 18, slotY)
                        .setStandardSlotBackground();
                if (entry.isFluid()) {
                    if (entry.fluidNbt() != null) slot.addFluidStack(entry.fluid(), entry.fluidAmountMb(),
                            entry.fluidNbt());
                    else slot.addFluidStack(entry.fluid(), entry.fluidAmountMb());
                } else {
                    slot.addItemStack(entry.stack());
                }
                if (!entry.tooltip().isEmpty()) {
                    slot.addTooltipCallback((view, tooltip) -> tooltip.addAll(entry.tooltip()));
                }
            } else {

                var hidden = builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT);
                if (entry.isFluid()) {
                    assert entry.fluid() != null;
                    hidden.addFluidStack(entry.fluid(), entry.fluidAmountMb());
                } else hidden.addItemStack(entry.stack());
            }
        }
    }

    @Override
    public void draw(QuestNode recipe, @NotNull IRecipeSlotsView recipeSlotsView, GuiGraphics g, double mouseX,
                     double mouseY) {
        Font font = Minecraft.getInstance().font;

        List<FormattedCharSequence> titleLines = font.split(recipe.getTitle(), WIDTH - 8);
        int y = TITLE_Y;
        for (int i = 0; i < Math.min(titleLines.size(), 2); i++) {
            g.drawString(font, titleLines.get(i), TITLE_X, y, isOverTitle(mouseX, mouseY) ? 0x3A7BFF : 0x1A56C4, false);
            y += 10;
        }

        Component desc = recipe.getDescription();
        if (desc != null) {
            List<FormattedCharSequence> lines = font.split(desc, WIDTH - 8);
            int bottom = 81;
            int maxLines = Math.max(1, (bottom - y) / 10);
            int textY = y + 2;
            for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
                g.drawString(font, lines.get(i), TITLE_X, textY, 0x555555, false);
                textY += 10;
            }
        }

        g.drawString(font, "Tasks", 4, 81, 0x333333, false);
        g.drawString(font, "Rewards", 100, 81, 0x333333, false);

        int extra = QuestRewardDisplay.entries(recipe).size() - 3;
        if (extra > 0) g.drawString(font, "+" + extra + " more", 100, 112, 0x777777, false);
    }

    @Override
    public List<Component> getTooltipStrings(QuestNode recipe, IRecipeSlotsView recipeSlotsView,
                                             double mouseX, double mouseY) {
        return isOverTitle(mouseX, mouseY) ?
                List.of(Component.translatable("phoenix_chronicles.jei.click_to_open")) : List.of();
    }

    @Override
    public boolean handleInput(QuestNode recipe, double mouseX, double mouseY, InputConstants.Key input) {
        if (!isOverTitle(mouseX, mouseY)) return false;
        if (input.getType() != InputConstants.Type.MOUSE || input.getValue() != 0) return false;

        Minecraft mc = Minecraft.getInstance();
        ChronicleOverviewScreen screen = new ChronicleOverviewScreen();
        mc.setScreen(screen);
        screen.navigateToNode(recipe);
        return true;
    }

    private static boolean isOverTitle(double mouseX, double mouseY) {
        return mouseX >= TITLE_X && mouseX < WIDTH - TITLE_X && mouseY >= TITLE_Y && mouseY < TITLE_Y + TITLE_H;
    }
}
