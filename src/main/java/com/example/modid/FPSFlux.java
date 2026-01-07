package com.example.modid;

import com.example.modid.gl.CompatibilityLayer;
import com.example.modid.gl.GLOptimizer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = FPSFlux.MODID,
    name = FPSFlux.NAME,
    version = FPSFlux.VERSION,
    acceptableRemoteVersions = "*"
)
public class FPSFlux {
    public static final String MODID = "fpsflux";
    public static final String NAME = "FPSFlux";
    public static final String VERSION = "0.1.0";
    
    public static final Logger LOGGER = LogManager.getLogger(NAME);
    
    private static boolean compatibilityMessageShown = false;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("FPSFlux PreInit - Java version: {}", System.getProperty("java.version"));
        
        // Register event handler for client tick
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("FPSFlux Init - Starting culling engine");
    }
    
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // Initialize GL optimizer after OpenGL context is ready
        LOGGER.info("FPSFlux PostInit - Initializing GL optimizer");
        GLOptimizer.initialize();
        
        LOGGER.info("FPSFlux GL Optimizer: {}", GLOptimizer.isEnabled() ? "ENABLED" : "DISABLED");
        
        // Print full report to console
        if (GLOptimizer.isEnabled()) {
            System.out.println(GLOptimizer.getDetailedReport());
        }
    }
    
    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandFPSFlux());
    }
    
    @Mod.EventHandler
    public void serverStop(FMLServerStoppingEvent event) {
        if (GLOptimizer.isEnabled()) {
            GLOptimizer.printStats();
        }
    }
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // Show compatibility message once when player joins world
        if (event.phase == TickEvent.Phase.END && !compatibilityMessageShown) {
            if (net.minecraft.client.Minecraft.getMinecraft().player != null) {
                CompatibilityLayer.displayInGameMessage();
                compatibilityMessageShown = true;
            }
        }
    }
}
