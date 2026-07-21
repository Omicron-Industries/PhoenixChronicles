package net.phoenixvine.chronicles.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;

public class ChronicleBookItem extends Item {

    public ChronicleBookItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            openGui();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @OnlyIn(Dist.CLIENT)
    private static void openGui() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new ChronicleOverviewScreen());
    }
}

