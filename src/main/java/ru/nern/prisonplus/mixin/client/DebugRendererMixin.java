package ru.nern.prisonplus.mixin.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nern.prisonplus.PrisonPlus;
import ru.nern.prisonplus.structure.Prison;
import ru.nern.prisonplus.structure.PrisonCell;

@Mixin(DebugRenderer.class)
public class DebugRendererMixin
{
    @Inject(method = "render", at = @At(value = "TAIL"))
    private void onRender(MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        for(Prison prison : PrisonPlus.RENDERED_PRISONS.values()) {
            if(prison.shouldRender()) {
                VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
                WorldRenderer.drawBox(matrices, vertexConsumer, prison.getBounds().getMinX() - cameraX, prison.getBounds().getMinY() - cameraY, prison.getBounds().getMinZ() - cameraZ, prison.getBounds().getMaxX()+1 - cameraX, prison.getBounds().getMaxY()+1 - cameraY, prison.getBounds().getMaxZ()+1 - cameraZ, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f);
                for(PrisonCell cell : prison.getCells().values())
                {
                    WorldRenderer.drawBox(matrices, vertexConsumer, cell.getBounds().getMinX() - cameraX, cell.getBounds().getMinY() - cameraY, cell.getBounds().getMinZ() - cameraZ, cell.getBounds().getMaxX()+1 - cameraX, cell.getBounds().getMaxY()+1 - cameraY, cell.getBounds().getMaxZ()+1 - cameraZ, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f);
                }
            }
        }
    }
}
