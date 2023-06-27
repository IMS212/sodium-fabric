package me.jellysquid.mods.sodium.client.model.light;

import me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess;
import me.jellysquid.mods.sodium.client.model.light.flat.FlatLightPipeline;
import me.jellysquid.mods.sodium.client.model.light.flat.FlatLightPipelineFRAPI;
import me.jellysquid.mods.sodium.client.model.light.smooth.SmoothLightPipeline;
import me.jellysquid.mods.sodium.client.model.light.smooth.SmoothLightPipelineFRAPI;

import java.util.EnumMap;

public class LightPipelineProviderFRAPI {
    private final EnumMap<LightMode, LightPipelineFRAPI> lighters = new EnumMap<>(LightMode.class);

    public LightPipelineProviderFRAPI(LightDataAccess cache) {
        this.lighters.put(LightMode.SMOOTH, new SmoothLightPipelineFRAPI(cache));
        this.lighters.put(LightMode.FLAT, new FlatLightPipelineFRAPI(cache));
    }

    public LightPipelineFRAPI getLighter(LightMode type) {
        LightPipelineFRAPI pipeline = this.lighters.get(type);

        if (pipeline == null) {
            throw new NullPointerException("No lighter exists for mode: " + type.name());
        }

        return pipeline;
    }
}
