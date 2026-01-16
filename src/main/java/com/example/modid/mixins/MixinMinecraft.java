package com.example.modid.mixins;

import com.example.modid.bridge.SnowyManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "init", at = @At("RETURN"))
    private void fpsflux$initEngine(CallbackInfo ci) {
        SnowyManager.get().init();
    }

    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;updateCameraAndRender(FJ)V", shift = At.Shift.AFTER))
    private void fpsflux$onRenderTick(CallbackInfo ci) {
        // 'getRenderPartialTicks' gets the interpolation time
        float partialTicks = Minecraft.getMinecraft().getRenderPartialTicks();
        SnowyManager.get().onRenderFrame(partialTicks);
    }
    
    @Inject(method = "shutdown", at = @At("HEAD"))
    private void fpsflux$shutdown(CallbackInfo ci) {
        SnowyManager.get().shutdown();
    }
}
