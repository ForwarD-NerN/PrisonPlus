package ru.nern.prisonplus.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nern.prisonplus.PrisonPlus;
import ru.nern.prisonplus.structure.PrisonItems;
import ru.nern.prisonplus.structure.PrisonLeashEntity;
import ru.nern.prisonplus.utils.ILivingEntityAccessor;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements ILivingEntityAccessor {
    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Shadow public abstract boolean addStatusEffect(StatusEffectInstance effect);
    @Unique private MobEntity leash;
    @Unique private boolean handcuffsOn = false;

    @Override
    public boolean prisonplus$isLeashed() {
        return handcuffsOn || this.leash != null;
    }

    @Override
    public void prisonplus$setLeashed(MobEntity leash) {
        this.leash = leash;
        this.handcuffsOn = true;

        LivingEntity livingEntity = (LivingEntity) (Object) this;
        ItemStack chestplate = livingEntity.getEquippedStack(EquipmentSlot.CHEST);
        if(!chestplate.isEmpty() && !(chestplate.hasNbt() && chestplate.getNbt().contains("HandcuffsChestplate"))) {
            if(livingEntity instanceof ServerPlayerEntity) {
                ((ServerPlayerEntity)livingEntity).getInventory().offerOrDrop(livingEntity.getEquippedStack(EquipmentSlot.CHEST));
            }else{
                livingEntity.dropStack(livingEntity.getEquippedStack(EquipmentSlot.CHEST));
            }
        }
        livingEntity.equipStack(EquipmentSlot.CHEST, PrisonItems.CHESTPLATE.copy());
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void prisonplus$writeLeashedData(NbtCompound nbt, CallbackInfo ci) {
        if(handcuffsOn) nbt.putBoolean("HandcuffsOn", true);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void prisonplus$readLeashedData(NbtCompound nbt, CallbackInfo ci) {
        if(nbt.contains("HandcuffsOn")) this.handcuffsOn = true;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void prisonplus$tickHandcuffs(CallbackInfo ci) {
        if(handcuffsOn && leash == null){
            this.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, PrisonPlus.config.items.slownessLevelHandcuffs, false, false, true));
            this.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 100, PrisonPlus.config.items.jumpBoostLevelHandcuffs, false, false, true));
        }
    }

    //Full - true - полное избавление от наручников
    //Full - false - избавление лишь от поводка
    @Override
    public void prisonplus$detach(boolean full) {
        if(full) {
            if(PrisonPlus.config.items.dropHandcuffs) dropStack(PrisonItems.HANDCUFFS.copy());
            this.handcuffsOn = false;
            ItemStack chestplate = ((LivingEntity)(Object) this).getEquippedStack(EquipmentSlot.CHEST);
            if(chestplate.hasNbt() && chestplate.getNbt().contains("HandcuffsChestplate"))
                ((LivingEntity)(Object)this).equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);

            ServerScoreboard scoreboard = getServer().getScoreboard();
            Team team = scoreboard.getTeam(PrisonLeashEntity.TEAM_NAME);
            if(scoreboard.getPlayerTeam(getEntityName()) == team && team != null) scoreboard.removePlayerFromTeam(getEntityName(), team);
        }
        if(this.leash != null) this.leash.detachLeash(true, true);
        this.leash = null;

    }
    @Override
    public MobEntity prisonplus$getLeashEntity() {
        return this.leash;
    }


}
