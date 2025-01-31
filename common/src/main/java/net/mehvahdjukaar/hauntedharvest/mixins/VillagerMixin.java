package net.mehvahdjukaar.hauntedharvest.mixins;

import net.mehvahdjukaar.hauntedharvest.HauntedHarvest;
import net.mehvahdjukaar.hauntedharvest.ai.IHalloweenVillager;
import net.mehvahdjukaar.hauntedharvest.reg.ModRegistry;
import net.mehvahdjukaar.moonlight.api.platform.ForgeHelper;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager implements IHalloweenVillager {

    //adults they have already asked candies
    private final Map<UUID, Integer> adultCandyCooldown = new ConcurrentHashMap<>();

    protected VillagerMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }


    @Shadow
    public abstract Brain<Villager> getBrain();

    @Inject(method = ("customServerAiStep"), at = @At("RETURN"))
    protected void customServerAiStep(CallbackInfo ci) {
        for (var e : adultCandyCooldown.entrySet()) {
            UUID id = e.getKey();
            Integer i = e.getValue();
            if (i <= 0) {
                adultCandyCooldown.remove(id);
            } else {
                adultCandyCooldown.put(id, i - 1);
            }
        }
    }

    @Override
    public boolean isEntityOnCooldown(Entity e) {
        return adultCandyCooldown.containsKey(e.getUUID());
    }

    @Override
    public void setEntityOnCooldown(Entity e) {
        this.setEntityOnCooldown(e, 50);
    }

    @Override
    public void setEntityOnCooldown(Entity e, int cooldownSec) {
        adultCandyCooldown.put(e.getUUID(), 20 * (cooldownSec + e.level.random.nextInt(20)));
    }

    @Inject(method = ("wantsToPickUp"), at = @At("HEAD"), cancellable = true)
    protected void wantsToPickUp(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        //hax. pickup candy
        if (HauntedHarvest.IS_TRICK_OR_TREATING.test(this) && HauntedHarvest.isCandyOrApple(stack)) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public void onItemPickup(ItemEntity itemEntity) {
        super.onItemPickup(itemEntity);
        if (HauntedHarvest.IS_TRICK_OR_TREATING.test(this) && HauntedHarvest.isCandyOrApple(itemEntity.getItem())) {
            this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (!this.level.isClientSide) {
                this.level.broadcastEntityEvent(this, (byte) 14);
            }
        }
    }

    //called server side. needs syncing with entity event
    @Override
    public void startConverting() {
        if (!this.isConverting()) {
            this.conversionTime = 60 * 20;
            this.level.broadcastEntityEvent(this, EntityEvent.ZOMBIE_CONVERTING);
            this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60 * 20, 2));
        }
    }

    private int conversionTime = -1;

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    public void addAdditionalSaveData(CompoundTag tag, CallbackInfo ci) {
        tag.putInt("ConversionTime", this.conversionTime);

        //can't get thingie brain memory saving to work
        //TODO: figure out why it's not read after getting saved
        if (this.getBrain().hasMemoryValue(ModRegistry.PUMPKIN_POS.get())) {
            GlobalPos globalpos = this.getBrain().getMemory(ModRegistry.PUMPKIN_POS.get()).get();
            if (globalpos.dimension() == this.level.dimension()) {
                tag.put("Pumpkin", NbtUtils.writeBlockPos(globalpos.pos()));
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    public void readAdditionalSaveData(CompoundTag compoundNBT, CallbackInfo ci) {
        this.conversionTime = compoundNBT.getInt("ConversionTime");

        if (compoundNBT.contains("Pumpkin")) {
            try {
                this.getBrain().setMemory(ModRegistry.PUMPKIN_POS.get(), GlobalPos.of(this.level.dimension(),
                        NbtUtils.readBlockPos(compoundNBT.getCompound("Pumpkin"))));
            } catch (Exception ignored) {
            }
        }

    }

    @Override
    public boolean isConverting() {
        return this.conversionTime > 0;
    }

    private void doWitchConversion() {

        float yBodyRot = this.yBodyRot;
        float yHeadRot = this.yHeadRot;
        float yBodyRotO = this.yBodyRotO;
        float yHeadRotO = this.yHeadRotO;

        //remove all items
        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            ItemStack itemstack = this.getItemBySlot(equipmentSlot);
            if (!itemstack.isEmpty()) {
                double d0 = this.getEquipmentDropChance(equipmentSlot);
                if (d0 > 1.0D) {
                    this.spawnAtLocation(itemstack);
                }
            }
            this.setItemSlot(equipmentSlot, ItemStack.EMPTY);
        }
        //rest of the inventory gets discarded

        Witch witch = this.convertTo(EntityType.WITCH, true);
        if (witch != null) {

            witch.yBodyRot = yBodyRot;
            witch.yHeadRot = yHeadRot;
            //witch.yBodyRotO = yBodyRotO;
            witch.yHeadRotO = yHeadRotO;

            witch.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));


            ForgeHelper.onLivingConvert(this, witch);
        }

        if (!this.isSilent()) {
            this.level.levelEvent(null, 1027, this.blockPosition(), 0);
        }
    }


    @Inject(method = "handleEntityEvent", at = @At(value = "HEAD"), cancellable = true)
    public void handleEntityEvent(byte pId, CallbackInfo ci) {
        if (pId == EntityEvent.ZOMBIE_CONVERTING) {
            this.conversionTime = 60 * 20;
            if (!this.isSilent()) {
                this.level.playLocalSound(this.getX(), this.getEyeY(), this.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, this.getSoundSource(), 1.0F + this.random.nextFloat(), this.random.nextFloat() * 0.7F + 0.3F, false);
            }
            ci.cancel();
        }
        //only get angry when going out of bed
        else if (pId == 26) {
            if (this.getPose() == Pose.SLEEPING) {
                this.addParticlesAroundSelf(ParticleTypes.ANGRY_VILLAGER);
            }
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At(value = "HEAD"))
    public void tick(CallbackInfo ci) {
        if (!this.level.isClientSide && this.isAlive() && !this.isNoAi()) {
            if (this.isConverting()) {
                --this.conversionTime;

                if (this.conversionTime == 0) {
                    this.doWitchConversion();
                }
            }
        }
    }

    @Inject(method = "mobInteract", at = @At(value = "HEAD"), cancellable = true)
    public void interact(Player pPlayer, InteractionHand pHand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack itemstack = pPlayer.getItemInHand(pHand);
        if (itemstack.is(Items.MILK_BUCKET) && this.isConverting()) {
            this.conversionTime = -1;
            itemstack.finishUsingItem(this.level, this);
            this.eat(this.level, itemstack);
            pPlayer.setItemInHand(pHand, new ItemStack(Items.BUCKET));
            cir.cancel();
            cir.setReturnValue(InteractionResult.sidedSuccess(pPlayer.level.isClientSide));
        }
    }

}