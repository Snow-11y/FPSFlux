package com.example.modid.mixins.accessor;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * IWorldAccessor - Direct field access for World class.
 */
@Mixin(World.class)
public interface IWorldAccessor {

    @Accessor("loadedEntityList")
    List<Entity> fpsflux$getLoadedEntityList();

    @Accessor("unloadedEntityList")
    List<Entity> fpsflux$getUnloadedEntityList();

    @Accessor("updateLCG")
    int fpsflux$getUpdateLCG();

    @Accessor("updateLCG")
    void fpsflux$setUpdateLCG(int value);
}
