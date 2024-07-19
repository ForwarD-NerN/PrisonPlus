package ru.nern.prisonplus.mixin;

import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.nern.prisonplus.utils.ILivingEntityAccessor;

@Mixin(LeashKnotEntity.class)
public class LeashKnotEntityMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void prisonplus$disablePrisonerLeashInteraction(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if(((ILivingEntityAccessor)player).prisonplus$isLeashed()){
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
