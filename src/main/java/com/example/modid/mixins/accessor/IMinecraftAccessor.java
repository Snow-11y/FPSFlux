package com.example.modid.mixins.accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * IMinecraftAccessor - Direct field access for Minecraft class.
 */
@Mixin(Minecraft.class)
public interface IMinecraftAccessor {

    @Accessor("timer")
    Timer fpsflux$getTimer();

    @Accessor("fpsCounter")
    int fpsflux$getFpsCounter();

    @Accessor("debugFPS")
    static int fpsflux$getDebugFPS() {
        throw new AssertionError();
    }
}
