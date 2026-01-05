package com.lo.fpsflux;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.EnumMap;
import java.util.Map;

public class CommandFPSFlux extends CommandBase {
    @Override
    public String getName() {
        return "fpsflux";
    }
    
    @Override
    public String getUsage(ICommandSender sender) {
        return "/fpsflux status - Show entity culling statistics";
    }
    
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || args[0].equals("status")) {
            Map<CullingTier, Integer> counts = new EnumMap<>(CullingTier.class);
            for (CullingTier tier : CullingTier.values()) {
                counts.put(tier, 0);
            }
            
            // Count entities per tier
            for (Entity entity : sender.getEntityWorld().loadedEntityList) {
                if (entity instanceof net.minecraft.entity.EntityLiving) {
                    CullingTier tier = CullingManager.getInstance()
                        .calculateTier(entity, sender.getEntityWorld());
                    counts.put(tier, counts.get(tier) + 1);
                }
            }
            
            sender.sendMessage(new TextComponentString("§6FPSFlux Entity Distribution:"));
            sender.sendMessage(new TextComponentString(
                String.format("§aFULL: %d  §eMINIMAL: %d  §6MODERATE: %d  §cAGGRESSIVE: %d",
                    counts.get(CullingTier.FULL),
                    counts.get(CullingTier.MINIMAL),
                    counts.get(CullingTier.MODERATE),
                    counts.get(CullingTier.AGGRESSIVE))
            ));
        }
    }
    
    @Override
    public int getRequiredPermissionLevel() {
        return 0; // Anyone can use
    }
}
Register command in FPSFlux.java:
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod.EventHandler
public void serverStart(FMLServerStartingEvent event) {
    event.registerServerCommand(new CommandFPSFlux());
}
