package ru.nern.prisonplus.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nern.prisonplus.PrisonPlusClient;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractInventoryScreen<PlayerScreenHandler> {
    public InventoryScreenMixin(PlayerScreenHandler screenHandler, PlayerInventory playerInventory, Text text) {
        super(screenHandler, playerInventory, text);
    }

    @Inject(method = "drawForeground", at = @At("TAIL"))
    private void prisonplus$displayTimeLeft(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        int ticks = PrisonPlusClient.ticksLeft;
        if(ticks > 0) {
            //1000
            //Days hours:minutes:seconds
            /*
            int days = Math.round((float) ticks / 1728000);
            int hours = Math.round((float) ticks / 72000);
            int minutes = Math.round((float) ticks / 1200);
            int seconds = MathHelper.clamp(Math.round((float) ticks / 20), 0, 60);

             */
            int totalSeconds = ticks / 20;
            int seconds = totalSeconds % 60;

            int totalMinutes = totalSeconds / 60;
            int minutes = totalMinutes % 60;

            int totalHours = totalMinutes / 60;
            int hours = totalHours % 24;
            int days = totalHours / 24;

            String text = (days > 0 ? days + " " : "") + ((hours > 10) ? hours : "0"+hours)
                    + ":"+((minutes > 10) ? minutes : "0"+minutes) + ":" +((seconds > 10) ? seconds : "0"+seconds) + (PrisonPlusClient.isIRL ? " IRL" : " IGT");
            int k = this.backgroundWidth - 8 - this.textRenderer.getWidth(text) - 2;
            context.drawTextWithShadow(this.textRenderer, text, k - 80, -10, 0xFFFFFF);
        }
    }
}
