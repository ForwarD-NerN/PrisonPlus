package ru.nern.prisonplus.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nern.prisonplus.PrisonPlus;
import ru.nern.prisonplus.structure.Prison;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Shadow @Nullable public ClientWorld world;

    @Inject(method = "joinWorld", at = @At(value = "TAIL"))
    private void prisonplus$recheckDimension(ClientWorld world, CallbackInfo ci) {
        for(Prison prison : PrisonPlus.RENDERED_PRISONS.values())
        {
            prison.checkDimension(world.getDimensionKey().getValue());
        }
    }
}
