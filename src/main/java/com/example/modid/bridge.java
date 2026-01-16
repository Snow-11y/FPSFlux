package com.example.modid.bridge;

import com.example.modid.ecs.World;
import com.example.modid.ecs.Entity;
import com.example.modid.ecs.Archetype;
import com.example.modid.ecs.ComponentRegistry;
// Import your specific components here (e.g., PositionComponent, VelocityComponent)

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The Central Nervous System of FPSFlux.
 * Mixins call methods here. This class manages the ECS World and Data Synchronization.
 */
public class SnowyManager {

    private static final SnowyManager INSTANCE = new SnowyManager();
    public static SnowyManager get() { return INSTANCE; }

    // The High-Performance ECS World
    private final World ecsWorld;
    
    // Map Minecraft EntityID -> ECS Entity Handle
    // Using FastUtil for performance (primitive int keys)
    private final Int2ObjectMap<Entity> entityMap = new Int2ObjectOpenHashMap<>();
    
    // Reusable buffers for data transfer (Zero Allocation)
    private final ByteBuffer posBuffer;
    private final ByteBuffer velBuffer;

    private boolean initialized = false;

    private SnowyManager() {
        // Initialize your ECS World with the config you defined earlier
        this.ecsWorld = new World("SnowiumECS");
        
        // Allocate direct buffers for pushing data to ECS (float x, y, z)
        this.posBuffer = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder());
        this.velBuffer = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder());
    }

    /**
     * Called by MixinMinecraft during startup
     */
    public void init() {
        if (initialized) return;
        System.out.println("[SnowyManager] Initializing ECS Engine...");
        
        // Register your components here if not done elsewhere
        // ComponentRegistry.get().register(PositionComponent.class);
        
        ecsWorld.initialize();
        initialized = true;
    }

    /**
     * Called by MixinWorld when an entity joins
     */
    public void onEntityJoin(net.minecraft.entity.Entity mcEntity) {
        if (!initialized || mcEntity == null) return;
        
        // We usually only care about things that move or render
        if (!(mcEntity instanceof EntityLivingBase)) return;

        // Create ECS Entity
        Entity ecsEntity = ecsWorld.createEntity();
        
        // Map MC ID to ECS Entity
        entityMap.put(mcEntity.getEntityId(), ecsEntity);
        
        // Initialize components (Position, Velocity, RenderData)
        // syncToECS(mcEntity, ecsEntity); // Initial sync
    }

    /**
     * Called by MixinWorld when an entity is removed/killed
     */
    public void onEntityLeave(net.minecraft.entity.Entity mcEntity) {
        if (!initialized || mcEntity == null) return;

        Entity ecsEntity = entityMap.remove(mcEntity.getEntityId());
        if (ecsWorld.isValid(ecsEntity)) {
            ecsWorld.destroyEntityDeferred(ecsEntity);
        }
    }

    /**
     * Called by MixinWorld during the tick loop.
     * This acts as the "System Runner".
     */
    public void onWorldTick(net.minecraft.world.World mcWorld) {
        if (!initialized || mcWorld.isRemote) return; // Client-side logic for now

        // 1. SYNC: Push Minecraft data into ECS
        // This iterates active MC entities and updates their ECS counterparts
        for (net.minecraft.entity.Entity mcEntity : mcWorld.loadedEntityList) {
            Entity ecsEntity = entityMap.get(mcEntity.getEntityId());
            if (ecsWorld.isValid(ecsEntity)) {
                syncToECS(mcEntity, ecsEntity);
            }
        }

        // 2. RUN: Execute ECS Systems (Physics, AI, etc.)
        // 0.05f is standard 20TPS delta time
        ecsWorld.update(0.05f); 
        
        // 3. REFLECT: (Optional) If ECS handles physics, pull data back to MC
        // syncFromECS(...)
    }

    /**
     * Called by MixinMinecraft during render frame
     */
    public void onRenderFrame(float partialTicks) {
        if (!initialized) return;
        
        // If you are using the Vulkan/Meshlet renderer, 
        // this is where you trigger the draw commands based on ECS state.
        // ecsWorld.getScheduler().executeStage(SystemScheduler.Stage.RENDER, partialTicks);
    }
    
    /**
     * Called on Shutdown
     */
    public void shutdown() {
        ecsWorld.shutdown();
        entityMap.clear();
    }

    // ========================================================================
    // INTERNAL SYNC HELPERS
    // ========================================================================

    private void syncToECS(net.minecraft.entity.Entity mc, Entity ecs) {
        // Example: Sync Position
        // Reset buffer
        posBuffer.clear();
        posBuffer.putFloat((float)mc.posX);
        posBuffer.putFloat((float)mc.posY);
        posBuffer.putFloat((float)mc.posZ);
        posBuffer.flip();
        
        // Use the fast ECS setter (assuming you have a PositionComponent class)
        // ecsWorld.addComponent(ecs, PositionComponent.class, posBuffer);
    }
}
