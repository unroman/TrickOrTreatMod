package net.mehvahdjukaar.hauntedharvest.forge;

import net.mehvahdjukaar.hauntedharvest.blocks.ModCarvedPumpkinBlockTile;
import net.mehvahdjukaar.hauntedharvest.reg.ModRegistry;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;

public class HHPlatformStuffImpl {
    public static void setItemLifespan(ItemEntity item, int lifespan) {
        item.lifespan = lifespan;
    }

    public static boolean isTopCarver(ItemStack stack) {
        return stack.canPerformAction(ToolActions.SHEARS_CARVE);
    }

    public static void addPumpkinData(ModCarvedPumpkinBlockTile tile, SnowGolem snowGolem) {
        if (snowGolem instanceof ICustomPumpkinHolder customPumpkinHolder) {
            var s = tile.getItemWithNBT();
            ItemStack stack = ModRegistry.MOD_CARVED_PUMPKIN_ITEM.get().getDefaultInstance();
            stack.setTag(s.getTag());
            customPumpkinHolder.setCustomPumpkin(stack);
        }
    }

    public static ShaderInstance getBlur() {
        return HauntedHarvestForgeClient.getBlur();
    }
}
