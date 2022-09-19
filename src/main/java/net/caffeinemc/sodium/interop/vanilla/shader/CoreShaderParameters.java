package net.caffeinemc.sodium.interop.vanilla.shader;

import io.github.douira.glsl_transformer.job_parameter.JobParameters;
import net.caffeinemc.gfx.api.shader.ShaderType;

class CoreShaderParameters extends JobParameters{
    public final float alpha;
    public int maxBatchSize;
    public boolean baseInstanced;
    public final float vertScale;

    public CoreShaderParameters(float alpha, float vertScale, int maxBatchSize, boolean baseInstanced) {
        this.alpha = alpha;
        this.vertScale = vertScale;
        this.maxBatchSize = maxBatchSize;
        this.baseInstanced = baseInstanced;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CoreShaderParameters other = (CoreShaderParameters) obj;
        if (alpha == -1) {
            if (other.alpha != -1)
                return false;
        } else if (!(alpha == other.alpha))
            return false;
        if (Float.floatToIntBits(vertScale) != Float.floatToIntBits(other.vertScale))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        return -1;
    }
}