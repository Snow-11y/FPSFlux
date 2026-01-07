package com.example.modid.mixins;

import com.example.modid.CullingManager;
import com.example.modid.CullingTier;
import net.minecraft.entity.EntityLiving;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityLiving.class)
public class MixinEntityLiving {
    
    @Inject(method = "updateEntityActionState", at = @At("HEAD"), cancellable = true)
    private void fpsflux$cullAI(CallbackInfo ci) {
        EntityLiving self = (EntityLiving)(Object)this;
        CullingTier tier = CullingManager.getInstance().calculateTier(self, self.world);
        
        if (!tier.shouldTickAI()) {
            ci.cancel();
        }
    }
}
