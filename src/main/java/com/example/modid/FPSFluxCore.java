package com.example.modid;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@IFMLLoadingPlugin.Name("FPSFluxCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class FPSFluxCore implements IFMLLoadingPlugin, IEarlyMixinLoader {
    
    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.fpsflux.json");
    }
    
    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }
    
    @Override
    public String getModContainerClass() {
        return null;
    }
    
    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }
    
    @Override
    public void injectData(Map<String, Object> data) {
        // Can access MC version, runtimeDeobf flag, etc. if needed later
    }
    
    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
