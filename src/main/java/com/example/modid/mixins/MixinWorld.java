package com.example.modid.mixins;

import com.example.modid.bridge.SnowyManager;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class MixinWorld {

    /**
     * Triggered when an entity is added to the world.
     */
    @Inject(method = "spawnEntity", at = @At("RETURN"))
    private void fpsflux$onSpawnEntity(Entity entityIn, CallbackInfoReturnable<Boolean> cir) {
        // Only if spawn was successful
        if (cir.getReturnValue()) {
            SnowyManager.get().onEntityJoin(entityIn);
        }
    }

    /**
     * Triggered when an entity is removed (death, despawn, chunk unload).
     */
    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void fpsflux$onRemoveEntity(Entity entityIn, CallbackInfo ci) {
        SnowyManager.get().onEntityLeave(entityIn);
    }

    /**
     * Triggered every game tick (20 times a second).
     */
    @Inject(method = "updateEntities", at = @At("HEAD"))
    private void fpsflux$onWorldTick(CallbackInfo ci) {
        SnowyManager.get().onWorldTick((World)(Object)this);
    }
}
