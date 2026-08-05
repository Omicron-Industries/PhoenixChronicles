package net.phoenixvine.chronicles.integration.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

final class CuriosCompatImpl {

    private CuriosCompatImpl() {}

    static List<ItemStack> getEquippedCurios(Player player) {
        List<ItemStack> result = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            IItemHandlerModifiable inv = handler.getEquippedCurios();
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack stack = inv.getStackInSlot(i);
                if (!stack.isEmpty()) result.add(stack);
            }
        });
        return result;
    }
}
