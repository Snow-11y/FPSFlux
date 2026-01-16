package com.example.modid.bridge;

import com.example.modid.ecs.ComponentRegistry;
import net.minecraft.entity.Entity;

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;

/**
 * BridgeComponents - Memory-efficient component definitions using Panama FFM.
 * 
 * <h2>Design Principles:</h2>
 * <ul>
 *   <li>Cache-line aligned for optimal CPU cache utilization</li>
 *   <li>Packed structs to minimize memory footprint</li>
 *   <li>VarHandle accessors for JIT-optimized field access</li>
 *   <li>Immutable layout definitions (thread-safe by design)</li>
 * </ul>
 */
public final class BridgeComponents {
    
    private BridgeComponents() {} // Prevent instantiation

    // ========================================================================
    // TRANSFORM COMPONENT
    // ========================================================================

    /**
     * Position and rotation data.
     * Layout: [x:f64, y:f64, z:f64, yaw:f32, pitch:f32, _pad:8bytes]
     * Total: 40 bytes, aligned to 64 bytes for cache efficiency.
     */
    @ComponentRegistry.ComponentMeta(
        size = 40, 
        alignment = 64,
        gpuSync = true,
        description = "Entity position and rotation in world space"
    )
    public static final class Transform {
        
        public static final StructLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_DOUBLE.withName("x"),
            ValueLayout.JAVA_DOUBLE.withName("y"),
            ValueLayout.JAVA_DOUBLE.withName("z"),
            ValueLayout.JAVA_FLOAT.withName("yaw"),
            ValueLayout.JAVA_FLOAT.withName("pitch"),
            MemoryLayout.paddingLayout(8)
        ).withName("Transform").withByteAlignment(64);

        // Precomputed offsets for direct memory access
        private static final long X_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("x"));
        private static final long Y_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("y"));
        private static final long Z_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("z"));
        private static final long YAW_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("yaw"));
        private static final long PITCH_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("pitch"));

        // VarHandles for atomic-capable access
        private static final VarHandle X = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("x"));
        private static final VarHandle Y = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("y"));
        private static final VarHandle Z = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("z"));
        private static final VarHandle YAW = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("yaw"));
        private static final VarHandle PITCH = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("pitch"));

        /**
         * Initializes transform from a Minecraft entity.
         */
        public static void initialize(MemorySegment seg, Entity mcEntity) {
            setX(seg, mcEntity.posX);
            setY(seg, mcEntity.posY);
            setZ(seg, mcEntity.posZ);
            setYaw(seg, mcEntity.rotationYaw);
            setPitch(seg, mcEntity.rotationPitch);
        }

        // Direct offset-based accessors (fastest for sequential access)
        public static void setX(MemorySegment seg, double val) { 
            seg.set(ValueLayout.JAVA_DOUBLE, X_OFFSET, val); 
        }
        public static double getX(MemorySegment seg) { 
            return seg.get(ValueLayout.JAVA_DOUBLE, X_OFFSET); 
        }
        
        public static void setY(MemorySegment seg, double val) { 
            seg.set(ValueLayout.JAVA_DOUBLE, Y_OFFSET, val); 
        }
        public static double getY(MemorySegment seg) { 
            return seg.get(ValueLayout.JAVA_DOUBLE, Y_OFFSET); 
        }
        
        public static void setZ(MemorySegment seg, double val) { 
            seg.set(ValueLayout.JAVA_DOUBLE, Z_OFFSET, val); 
        }
        public static double getZ(MemorySegment seg) { 
            return seg.get(ValueLayout.JAVA_DOUBLE, Z_OFFSET); 
        }

        public static void setYaw(MemorySegment seg, float val) { 
            seg.set(ValueLayout.JAVA_FLOAT, YAW_OFFSET, val); 
        }
        public static float getYaw(MemorySegment seg) { 
            return seg.get(ValueLayout.JAVA_FLOAT, YAW_OFFSET); 
        }

        public static void setPitch(MemorySegment seg, float val) { 
            seg.set(ValueLayout.JAVA_FLOAT, PITCH_OFFSET, val); 
        }
        public static float getPitch(MemorySegment seg) { 
            return seg.get(ValueLayout.JAVA_FLOAT, PITCH_OFFSET); 
        }

        // Atomic accessors for concurrent modification (uses VarHandle)
        public static void setXAtomic(MemorySegment seg, double val) { 
            X.setVolatile(seg, 0L, val); 
        }
        public static double getXAtomic(MemorySegment seg) { 
            return (double) X.getVolatile(seg, 0L); 
        }

        /**
         * Copies all transform data from source to destination.
         */
        public static void copy(MemorySegment src, MemorySegment dst) {
            MemorySegment.copy(src, 0, dst, 0, LAYOUT.byteSize());
        }

        /**
         * Linearly interpolates between two transforms.
         */
        public static void lerp(MemorySegment from, MemorySegment to, MemorySegment out, float t) {
            setX(out, getX(from) + (getX(to) - getX(from)) * t);
            setY(out, getY(from) + (getY(to) - getY(from)) * t);
            setZ(out, getZ(from) + (getZ(to) - getZ(from)) * t);
            setYaw(out, lerpAngle(getYaw(from), getYaw(to), t));
            setPitch(out, getPitch(from) + (getPitch(to) - getPitch(from)) * t);
        }

        private static float lerpAngle(float from, float to, float t) {
            float diff = ((to - from + 540) % 360) - 180;
            return from + diff * t;
        }
    }

    // ========================================================================
    // PREVIOUS TRANSFORM (For Interpolation)
    // ========================================================================

    @ComponentRegistry.ComponentMeta(size = 40, alignment = 64)
    public static final class PreviousTransform {
        
        public static final StructLayout LAYOUT = Transform.LAYOUT.withName("PreviousTransform");
        
        public static void initialize(MemorySegment seg, Entity mcEntity) {
            Transform.initialize(seg, mcEntity);
        }
        
        public static double getX(MemorySegment seg) { return Transform.getX(seg); }
        public static double getY(MemorySegment seg) { return Transform.getY(seg); }
        public static double getZ(MemorySegment seg) { return Transform.getZ(seg); }
        public static float getYaw(MemorySegment seg) { return Transform.getYaw(seg); }
        public static float getPitch(MemorySegment seg) { return Transform.getPitch(seg); }
    }

    // ========================================================================
    // VELOCITY COMPONENT
    // ========================================================================

    @ComponentRegistry.ComponentMeta(size = 24, alignment = 32)
    public static final class Velocity {
        
        public static final StructLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_DOUBLE.withName("vx"),
            ValueLayout.JAVA_DOUBLE.withName("vy"),
            ValueLayout.JAVA_DOUBLE.withName("vz")
        ).withName("Velocity").withByteAlignment(32);

        private static final long VX_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("vx"));
        private static final long VY_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("vy"));
        private static final long VZ_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("vz"));

        public static void initialize(MemorySegment seg, Entity mcEntity) {
            setX(seg, mcEntity.motionX);
            setY(seg, mcEntity.motionY);
            setZ(seg, mcEntity.motionZ);
        }

        public static void setX(MemorySegment seg, double val) { 
            seg.set(ValueLayout.JAVA_DOUBLE, VX_OFFSET, val); 
        }
        public static double getX(MemorySegment seg) { 
            return seg.get(ValueLayout.JAVA_DOUBLE, VX_OFFSET); 
        }
        
        public static void setY(MemorySegment seg, double val) { 
            seg.set(ValueLayout.JAVA_DOUBLE, VY_OFFSET, val); 
        }
        public static double getY(MemorySegment seg) { 
            return seg.get(ValueLayout.JAVA_DOUBLE, VY_OFFSET); 
        }
        
        public static void setZ(MemorySegment seg, double val) { 
            seg.set(ValueLayout.JAVA_DOUBLE, VZ_OFFSET, val); 
        }
        public static double getZ(MemorySegment seg) { 
            return seg.get(ValueLayout.JAVA_DOUBLE, VZ_OFFSET); 
        }

        public static double magnitudeSquared(MemorySegment seg) {
            double vx = getX(seg), vy = getY(seg), vz = getZ(seg);
            return vx * vx + vy * vy + vz * vz;
        }
    }

    // ========================================================================
    // METADATA COMPONENT
    // ========================================================================

    /**
     * Bridge metadata for tracking entity state and flags.
     */
    @ComponentRegistry.ComponentMeta(size = 16, alignment = 16)
    public static final class Metadata {
        
        /** Flag: Entity position was modified by ECS this tick */
        public static final int FLAG_TRANSFORM_DIRTY = 1 << 0;
        /** Flag: Entity velocity was modified by ECS this tick */
        public static final int FLAG_VELOCITY_DIRTY = 1 << 1;
        /** Flag: Entity should be synchronized to server */
        public static final int FLAG_NEEDS_SYNC = 1 << 2;
        /** Flag: Entity is currently being processed */
        public static final int FLAG_PROCESSING = 1 << 3;
        /** Flag: Entity was teleported externally */
        public static final int FLAG_EXTERNAL_TELEPORT = 1 << 4;

        public static final StructLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("mcEntityId"),
            ValueLayout.JAVA_INT.withName("flags"),
            ValueLayout.JAVA_LONG.withName("lastSyncTick")
        ).withName("Metadata").withByteAlignment(16);

        private static final long ID_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("mcEntityId"));
        private static final long FLAGS_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("flags"));
        private static final long SYNC_TICK_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("lastSyncTick"));

        // VarHandle for atomic flag operations
        private static final VarHandle FLAGS = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("flags"));

        public static void initialize(MemorySegment seg, int entityId) {
            setEntityId(seg, entityId);
            setFlags(seg, 0);
            setLastSyncTick(seg, 0);
        }

        public static int getEntityId(MemorySegment seg) { 
            return seg.get(ValueLayout.JAVA_INT, ID_OFFSET); 
        }
        public static void setEntityId(MemorySegment seg, int id) { 
            seg.set(ValueLayout.JAVA_INT, ID_OFFSET, id); 
        }
        
        public static int getFlags(MemorySegment seg) { 
            return seg.get(ValueLayout.JAVA_INT, FLAGS_OFFSET); 
        }
        public static void setFlags(MemorySegment seg, int flags) { 
            seg.set(ValueLayout.JAVA_INT, FLAGS_OFFSET, flags); 
        }

        public static long getLastSyncTick(MemorySegment seg) { 
            return seg.get(ValueLayout.JAVA_LONG, SYNC_TICK_OFFSET); 
        }
        public static void setLastSyncTick(MemorySegment seg, long tick) { 
            seg.set(ValueLayout.JAVA_LONG, SYNC_TICK_OFFSET, tick); 
        }

        // Atomic flag operations
        public static boolean hasFlag(MemorySegment seg, int flag) {
            return (getFlags(seg) & flag) != 0;
        }

        public static void setFlag(MemorySegment seg, int flag) {
            int current, updated;
            do {
                current = (int) FLAGS.getVolatile(seg, 0L);
                updated = current | flag;
            } while (!FLAGS.compareAndSet(seg, 0L, current, updated));
        }

        public static void clearFlag(MemorySegment seg, int flag) {
            int current, updated;
            do {
                current = (int) FLAGS.getVolatile(seg, 0L);
                updated = current & ~flag;
            } while (!FLAGS.compareAndSet(seg, 0L, current, updated));
        }

        public static int getAndClearFlags(MemorySegment seg) {
            return (int) FLAGS.getAndSet(seg, 0L, 0);
        }
    }
}
