package com.lo.fpsflux;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CullingManager {
    private static final CullingManager INSTANCE = new CullingManager();
    
    // Cache distances for 5 ticks before recalculating
    private final Map<UUID, CachedTier> tierCache = new HashMap<>();
    private static final int CACHE_DURATION = 5;
    
    public static CullingManager getInstance() {
        return INSTANCE;
    }
    
    public CullingTier calculateTier(Entity entity, World world) {
        UUID id = entity.getPersistentID();
        CachedTier cached = tierCache.get(id);
        
        // Use cached value if recent
        if (cached != null && (entity.ticksExisted - cached.tickCalculated) < CACHE_DURATION) {
            return cached.tier;
        }
        
        // Find nearest player
        EntityPlayer nearest = world.getClosestPlayerToEntity(entity, -1.0);
        if (nearest == null) {
            return CullingTier.AGGRESSIVE; // No players nearby
        }
        
        // Use Java 21 Math.fma for precise distance calculation
        double dx = entity.posX - nearest.posX;
        double dy = entity.posY - nearest.posY;
        double dz = entity.posZ - nearest.posZ;
        
        // FMA chain: dx² + (dy² + dz²)
        double distSq = Math.fma(dx, dx, Math.fma(dy, dy, dz * dz));
        
        CullingTier tier = CullingTier.fromDistanceSquared(distSq);
        
        // Cache result
        tierCache.put(id, new CachedTier(tier, entity.ticksExisted));
        
        return tier;
    }
    
    public void clearCache() {
        tierCache.clear();
    }
    
    private record CachedTier(CullingTier tier, int tickCalculated) {}
}
