package com.example.modid.mixins.accessor;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * IEntityAccessor - Direct field access for Entity class.
 */
@Mixin(Entity.class)
public interface IEntityAccessor {

    @Accessor("isInWeb")
    boolean fpsflux$isInWeb();

    @Accessor("isInWeb")
    void fpsflux$setInWeb(boolean value);

    @Accessor("fire")
    int fpsflux$getFire();

    @Accessor("fire")
    void fpsflux$setFire(int value);

    @Accessor("firstUpdate")
    boolean fpsflux$isFirstUpdate();

    @Accessor("entityUniqueID")
    java.util.UUID fpsflux$getUUID();
}
