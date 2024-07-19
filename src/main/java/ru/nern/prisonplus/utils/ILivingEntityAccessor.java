package ru.nern.prisonplus.utils;

import net.minecraft.entity.mob.MobEntity;

public interface ILivingEntityAccessor
{
    boolean prisonplus$isLeashed();
    MobEntity prisonplus$getLeashEntity();
    void prisonplus$setLeashed(MobEntity leash);
    void prisonplus$detach(boolean full);
}
